package com.panzane.multiblindtest.audio

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.panzane.multiblindtest.data.deezer.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Joue plusieurs extraits Deezer simultanément, en boucle : un ExoPlayer par chanson.
 * La gestion du focus audio est désactivée (par défaut) pour que les lecteurs ne s'interrompent pas entre eux.
 */
class MultiPreviewPlayer(private val context: Context) {

    private val players = mutableMapOf<Long, ExoPlayer>()

    /** Chansons prêtes à jouer, ou en erreur : dans les deux cas, inutile de les attendre. */
    private val settledIds = MutableStateFlow<Set<Long>>(emptySet())

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun load(tracks: List<Track>) {
        release()
        tracks.forEach { track ->
            players[track.id] = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) settledIds.update { it + track.id }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        settledIds.update { it + track.id }
                    }
                })
                setMediaItem(MediaItem.fromUri(track.previewUrl))
                prepare()
            }
        }
    }

    /** Attend que tous les extraits soient chargés, pour qu'ils démarrent ensemble. */
    suspend fun awaitReady(timeoutMs: Long = 10_000) {
        withTimeoutOrNull(timeoutMs) { settledIds.first { it.containsAll(players.keys) } }
    }

    /** Relance depuis le début les chansons [ids] et coupe toutes les autres. */
    fun playFromStart(ids: Set<Long>) {
        // Baisse le volume quand beaucoup de chansons se superposent, pour éviter la saturation
        val volume = (2f / ids.size.coerceAtLeast(1)).coerceAtMost(1f)
        players.forEach { (id, player) ->
            if (id in ids) {
                player.volume = volume
                player.seekTo(0)
                player.play()
            } else {
                player.pause()
            }
        }
        _isPlaying.value = ids.isNotEmpty()
    }

    /** Reprend là où elles en étaient les chansons [ids] uniquement ; les autres restent coupées. */
    fun resume(ids: Set<Long>) {
        players.forEach { (id, player) -> if (id in ids) player.play() else player.pause() }
        _isPlaying.value = ids.isNotEmpty()
    }

    fun pause() {
        players.values.forEach { it.pause() }
        _isPlaying.value = false
    }

    fun release() {
        players.values.forEach { it.release() }
        players.clear()
        settledIds.value = emptySet()
        _isPlaying.value = false
    }
}
