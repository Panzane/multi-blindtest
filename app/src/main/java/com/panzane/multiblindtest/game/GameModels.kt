package com.panzane.multiblindtest.game

import com.panzane.multiblindtest.data.deezer.Track

data class Player(
    val id: Int,
    val name: String,
    val score: Int = 0,
)

/** Une des chansons de la manche ; [foundBy] est l'id du joueur qui l'a trouvée. */
data class Song(
    val track: Track,
    val foundBy: Int? = null,
) {
    val isFound: Boolean get() = foundBy != null
}

/**
 * Une manche : le maître du jeu choisit [SONGS_PER_ROUND] chansons, jouées toutes en même temps.
 * Les chansons trouvées sont retirées ; quand il n'en reste que [FINAL_SONGS], il faut donner les deux.
 */
data class Round(
    val masterId: Int,
    val songs: List<Song>,
    val isAudioLoading: Boolean = true,
) {
    val remaining: List<Song> get() = songs.filterNot { it.isFound }
    val isFinal: Boolean get() = remaining.size <= FINAL_SONGS
}

enum class Screen { PLAYERS, SELECTION, PLAYING, REVEAL, RESULTS }

data class GameUiState(
    val screen: Screen = Screen.PLAYERS,
    val players: List<Player> = emptyList(),
    /** Index du tour en cours (0-based) ; chaque joueur est maître du jeu une fois. */
    val roundIndex: Int = 0,
    val selection: List<Track> = emptyList(),
    val query: String = "",
    val searchResults: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val round: Round? = null,
) {
    val totalRounds: Int get() = players.size
    val master: Player? get() = players.getOrNull(roundIndex)
    val nextMaster: Player? get() = players.getOrNull(roundIndex + 1)
    val isLastRound: Boolean get() = roundIndex >= totalRounds - 1
    /** Joueurs pouvant marquer pendant la manche (tous sauf le maître du jeu). */
    val guessers: List<Player> get() = players.filterNot { it.id == master?.id }
    val ranking: List<Player> get() = players.sortedByDescending { it.score }

    fun playerName(id: Int?): String? = players.firstOrNull { it.id == id }?.name
}

const val MIN_PLAYERS = 2
const val MAX_PLAYERS = 10
const val SONGS_PER_ROUND = 6
const val FINAL_SONGS = 2
