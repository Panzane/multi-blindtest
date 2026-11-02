package com.panzane.multiblindtest.data.deezer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Modèles bruts renvoyés par l'API publique Deezer (https://developers.deezer.com/api).
 * En cas d'erreur, Deezer répond en HTTP 200 avec un objet `error` au lieu de `data`.
 */
@Serializable
data class DeezerPage<T>(
    val data: List<T> = emptyList(),
    val total: Int? = null,
    val next: String? = null,
    val error: DeezerError? = null,
)

@Serializable
data class DeezerError(
    val type: String? = null,
    val message: String? = null,
    val code: Int? = null,
)

@Serializable
data class DeezerTrackDto(
    val id: Long,
    val title: String,
    val readable: Boolean = true,
    val preview: String? = null,
    val artist: DeezerArtistDto,
    val album: DeezerAlbumDto? = null,
)

@Serializable
data class DeezerArtistDto(
    val name: String,
)

@Serializable
data class DeezerAlbumDto(
    val title: String? = null,
    @SerialName("cover_big") val coverBig: String? = null,
)
