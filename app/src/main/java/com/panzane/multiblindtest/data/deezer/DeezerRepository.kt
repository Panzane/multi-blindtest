package com.panzane.multiblindtest.data.deezer

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String?,
    val coverUrl: String?,
    val previewUrl: String,
)

class DeezerException(message: String) : Exception(message)

/**
 * Recherche de titres et extraits de 30 s via l'API publique Deezer (sans authentification).
 * Les URLs d'extraits sont signées et expirent : il faut les récupérer au lancement de la partie.
 */
class DeezerRepository(private val api: DeezerApi = createApi()) {

    suspend fun topTracks(): List<Track> =
        api.topTracks().orThrow().toPlayableTracks()

    suspend fun searchTracks(query: String): List<Track> =
        api.searchTracks(query).orThrow().toPlayableTracks()

    suspend fun playlistTracks(playlistId: Long): List<Track> =
        api.playlistTracks(playlistId).orThrow().toPlayableTracks()

    /** Ne garde que les pistes avec un extrait disponible. */
    private fun List<DeezerTrackDto>.toPlayableTracks(): List<Track> =
        filter { it.readable && !it.preview.isNullOrBlank() }
            .distinctBy { it.id }
            .map { it.toTrack() }

    private fun <T> DeezerPage<T>.orThrow(): List<T> {
        error?.let { throw DeezerException(it.message ?: "Erreur Deezer") }
        return data
    }

    private fun DeezerTrackDto.toTrack() = Track(
        id = id,
        title = title,
        artist = artist.name,
        album = album?.title,
        coverUrl = album?.coverBig,
        previewUrl = preview!!,
    )

    companion object {
        private val PLAYLIST_LINK = Regex("""deezer\.com/(?:[a-z]{2}/)?playlist/(\d+)""")

        /**
         * Extrait un identifiant de playlist d'un lien Deezer ou d'un identifiant brut.
         * Les nombres courts (« 2000 », « 1980 »…) restent des recherches par mots-clés.
         */
        fun parsePlaylistId(input: String): Long? {
            val trimmed = input.trim()
            if (trimmed.length >= 7) trimmed.toLongOrNull()?.let { return it }
            return PLAYLIST_LINK.find(trimmed)?.groupValues?.get(1)?.toLongOrNull()
        }

        private fun createApi(): DeezerApi {
            val json = Json { ignoreUnknownKeys = true }
            return Retrofit.Builder()
                .baseUrl(DeezerApi.BASE_URL)
                .client(OkHttpClient())
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(DeezerApi::class.java)
        }
    }
}
