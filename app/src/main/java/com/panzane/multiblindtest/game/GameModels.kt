package com.panzane.multiblindtest.game

import com.panzane.multiblindtest.data.deezer.Track

data class Player(
    val id: Int,
    val name: String,
    val score: Int = 0,
    /** Mode buzzers : identifie le téléphone du joueur ; null pour un joueur ajouté sur l'hôte (sans buzzer). */
    val token: String? = null,
    /** Mode buzzers : joueur qui utilise le téléphone hôte (il y buzze quand il n'est pas MJ). */
    val isHostOwner: Boolean = false,
) {
    val hasBuzzer: Boolean get() = token != null
}

/** Une des chansons de la manche ; [foundBy] est l'id du joueur qui l'a trouvée. */
data class Song(
    val track: Track,
    val foundBy: Int? = null,
) {
    val isFound: Boolean get() = foundBy != null
}

/**
 * Une manche : le maître du jeu choisit [GameSettings.songsPerRound] chansons, jouées toutes en même temps.
 * Les chansons trouvées sont retirées ; quand il n'en reste que [FINAL_SONGS], il faut donner les deux.
 */
data class Round(
    val masterId: Int,
    val songs: List<Song>,
    val isAudioLoading: Boolean = true,
    /** Mode buzzers : joueur qui a buzzé et attend la décision du MJ. */
    val buzzerId: Int? = null,
    /** Joueurs ayant donné une mauvaise réponse, bloqués jusqu'à la prochaine chanson trouvée. */
    val lockedOut: Set<Int> = emptySet(),
) {
    val remaining: List<Song> get() = songs.filterNot { it.isFound }
    val isFinal: Boolean get() = remaining.size <= FINAL_SONGS
}

/** Options choisies à la création de la partie. */
data class GameSettings(
    val songsPerRound: Int = DEFAULT_SONGS_PER_ROUND,
    /** Nombre de fois où chaque joueur est maître du jeu. */
    val turnsPerPlayer: Int = 1,
    /** Tire au sort l'ordre des maîtres du jeu au lancement ; il reste le même d'un tour à l'autre. */
    val randomMasterOrder: Boolean = false,
    /** Chaque joueur rejoint la partie avec son téléphone, qui lui sert de buzzer. */
    val buzzers: Boolean = false,
)

enum class Screen { PLAYERS, SELECTION, PLAYING, REVEAL, RESULTS }

data class GameUiState(
    val screen: Screen = Screen.PLAYERS,
    val players: List<Player> = emptyList(),
    val settings: GameSettings = GameSettings(),
    /** Ids des joueurs dans l'ordre où ils sont maîtres du jeu, fixé au lancement de la partie. */
    val masterOrder: List<Int> = emptyList(),
    /** Index du tour en cours (0-based) ; les joueurs sont maîtres du jeu à tour de rôle. */
    val roundIndex: Int = 0,
    val selection: List<Track> = emptyList(),
    val query: String = "",
    val searchResults: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val round: Round? = null,
    /** Mode buzzers : adresse de la page buzzer, null tant que le serveur n'est pas joignable. */
    val hostUrl: String? = null,
    /** Mode buzzers : joueurs dont le téléphone est connecté. */
    val connectedIds: Set<Int> = emptySet(),
    /** Le MJ de la manche a laissé la main au téléphone hôte (téléphone perdu, batterie vide…). */
    val masterOnHost: Boolean = false,
) {
    val totalRounds: Int get() = players.size * settings.turnsPerPlayer
    val master: Player? get() = masterAt(roundIndex)
    val nextMaster: Player? get() = masterAt(roundIndex + 1)
    val isLastRound: Boolean get() = roundIndex >= totalRounds - 1
    /** Joueurs pouvant marquer pendant la manche (tous sauf le maître du jeu). */
    val guessers: List<Player> get() = players.filterNot { it.id == master?.id }
    val ranking: List<Player> get() = players.sortedByDescending { it.score }
    /** Le MJ pilote la manche depuis son propre téléphone ; l'hôte ne montre alors pas les chansons. */
    val remoteMaster: Boolean get() = settings.buzzers && master?.hasBuzzer == true && !masterOnHost
    /** Joueur qui buzze sur le téléphone hôte. */
    val hostOwner: Player? get() = players.firstOrNull { it.isHostOwner }

    private fun masterAt(index: Int): Player? {
        val order = masterOrder.ifEmpty { players.map { it.id } }
        if (order.isEmpty() || index >= totalRounds) return null
        val id = order[index % order.size]
        return players.firstOrNull { it.id == id }
    }

    fun playerName(id: Int?): String? = players.firstOrNull { it.id == id }?.name
}

const val MIN_PLAYERS = 2
const val MAX_PLAYERS = 10
const val MAX_NAME_LENGTH = 20
const val FINAL_SONGS = 2
const val DEFAULT_SONGS_PER_ROUND = 6
val SONGS_PER_ROUND_RANGE = (FINAL_SONGS + 1)..10
val TURNS_PER_PLAYER_RANGE = 1..5
