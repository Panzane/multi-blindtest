package com.panzane.multiblindtest.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.panzane.multiblindtest.audio.MultiPreviewPlayer
import com.panzane.multiblindtest.data.deezer.DeezerRepository
import com.panzane.multiblindtest.data.deezer.Track
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val deezer = DeezerRepository()
    val audio = MultiPreviewPlayer(application)

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var nextPlayerId = 0
    private var searchJob: Job? = null
    private var audioJob: Job? = null

    // --- Joueurs -----------------------------------------------------------

    fun addPlayer(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _state.update {
            if (it.players.size >= MAX_PLAYERS) it
            else it.copy(players = it.players + Player(nextPlayerId++, trimmed))
        }
    }

    fun removePlayer(id: Int) {
        _state.update { s -> s.copy(players = s.players.filterNot { it.id == id }) }
    }

    fun setSongsPerRound(count: Int) {
        _state.update {
            it.copy(settings = it.settings.copy(songsPerRound = count.coerceIn(SONGS_PER_ROUND_RANGE)))
        }
    }

    fun setTurnsPerPlayer(count: Int) {
        _state.update {
            it.copy(settings = it.settings.copy(turnsPerPlayer = count.coerceIn(TURNS_PER_PLAYER_RANGE)))
        }
    }

    fun setRandomMasterOrder(enabled: Boolean) {
        _state.update { it.copy(settings = it.settings.copy(randomMasterOrder = enabled)) }
    }

    fun startGame() {
        _state.update { s ->
            val ids = s.players.map { it.id }
            s.copy(
                players = s.players.map { it.copy(score = 0) },
                masterOrder = if (s.settings.randomMasterOrder) ids.shuffled() else ids,
                roundIndex = 0,
                round = null,
            )
        }
        goToSelection()
    }

    // --- Sélection des chansons par le maître du jeu ---------------------------

    private fun goToSelection() {
        _state.update { it.copy(screen = Screen.SELECTION, selection = emptyList(), query = "", error = null) }
        search("")
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Recherche par titre / artiste ; un lien de playlist Deezer affiche ses titres. */
    fun search(query: String = _state.value.query) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching {
                val playlistId = DeezerRepository.parsePlaylistId(query)
                when {
                    playlistId != null -> deezer.playlistTracks(playlistId)
                    query.isBlank() -> deezer.topTracks()
                    else -> deezer.searchTracks(query)
                }
            }.onSuccess { tracks ->
                _state.update { it.copy(searchResults = tracks, isLoading = false) }
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.toUserMessage()) }
            }
        }
    }

    fun toggleSelection(track: Track) {
        _state.update { s ->
            when {
                s.selection.any { it.id == track.id } -> s.copy(selection = s.selection.filterNot { it.id == track.id })
                s.selection.size >= s.settings.songsPerRound -> s
                else -> s.copy(selection = s.selection + track)
            }
        }
    }

    // --- Manche ------------------------------------------------------------------

    fun launchRound() {
        val s = _state.value
        val master = s.master ?: return
        if (s.selection.size != s.settings.songsPerRound) return
        _state.update {
            it.copy(screen = Screen.PLAYING, round = Round(master.id, s.selection.shuffled().map(::Song)))
        }
        audioJob?.cancel()
        audioJob = viewModelScope.launch {
            audio.load(s.selection)
            audio.awaitReady()
            _state.update { it.copy(round = it.round?.copy(isAudioLoading = false)) }
            replayRemaining()
        }
    }

    fun togglePause() {
        if (audio.isPlaying.value) audio.pause() else audio.resume(remainingIds())
    }

    /** Relance depuis le début toutes les chansons pas encore trouvées. */
    fun replayRemaining() {
        audio.playFromStart(remainingIds())
    }

    private fun remainingIds(): Set<Long> =
        _state.value.round?.remaining?.map { it.track.id }?.toSet().orEmpty()

    /** Le maître du jeu coche une chanson trouvée : la musique s'arrête jusqu'à ce qu'il relance. */
    fun markFound(trackId: Long, playerId: Int) {
        audio.pause()
        setFoundBy(trackId, playerId)
    }

    fun unmarkFound(trackId: Long) {
        audio.pause()
        setFoundBy(trackId, null)
    }

    private fun setFoundBy(trackId: Long, playerId: Int?) {
        _state.update { s ->
            val round = s.round ?: return@update s
            val song = round.songs.firstOrNull { it.track.id == trackId } ?: return@update s
            s.copy(
                round = round.copy(songs = round.songs.map { if (it.track.id == trackId) it.copy(foundBy = playerId) else it }),
                players = s.players.map {
                    when (it.id) {
                        song.foundBy -> it.copy(score = it.score - 1)
                        playerId -> it.copy(score = it.score + 1)
                        else -> it
                    }
                },
            )
        }
    }

    /**
     * Dernière ligne droite : [playerId] a donné les deux chansons restantes (+1 par chanson),
     * ou personne (null). Dans les deux cas, on révèle les réponses.
     */
    fun resolveFinal(playerId: Int?) {
        val round = _state.value.round ?: return
        if (playerId != null) round.remaining.forEach { setFoundBy(it.track.id, playerId) }
        audio.release()
        _state.update { it.copy(screen = Screen.REVEAL) }
    }

    fun nextRound() {
        if (_state.value.isLastRound) {
            _state.update { it.copy(screen = Screen.RESULTS, round = null) }
        } else {
            _state.update { it.copy(roundIndex = it.roundIndex + 1, round = null) }
            goToSelection()
        }
    }

    // --- Fin de partie ---------------------------------------------------------

    fun newGame() {
        audioJob?.cancel()
        audio.release()
        _state.update { s ->
            s.copy(
                screen = Screen.PLAYERS,
                players = s.players.map { it.copy(score = 0) },
                roundIndex = 0,
                round = null,
                selection = emptyList(),
            )
        }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    override fun onCleared() {
        audio.release()
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is java.io.IOException -> "Connexion à Deezer impossible. Vérifiez votre accès Internet."
        else -> message ?: "Erreur inattendue."
    }
}
