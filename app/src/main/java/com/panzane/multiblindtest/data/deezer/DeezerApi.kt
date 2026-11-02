package com.panzane.multiblindtest.data.deezer

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DeezerApi {

    @GET("search")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 30,
    ): DeezerPage<DeezerTrackDto>

    @GET("chart/0/tracks")
    suspend fun topTracks(
        @Query("limit") limit: Int = 30,
    ): DeezerPage<DeezerTrackDto>

    @GET("playlist/{id}/tracks")
    suspend fun playlistTracks(
        @Path("id") playlistId: Long,
        @Query("limit") limit: Int = 500,
    ): DeezerPage<DeezerTrackDto>

    companion object {
        const val BASE_URL = "https://api.deezer.com/"
    }
}
