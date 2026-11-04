package com.panzane.multiblindtest.net

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Messages échangés en JSON (champ "type") entre la page buzzer et le téléphone hôte. */
val protocolJson = Json {
    classDiscriminator = "type"
    ignoreUnknownKeys = true
    encodeDefaults = true
}

// --- Page buzzer -> hôte ---------------------------------------------------------

@Serializable
sealed interface ClientMessage

/** Inscription, ou reconnexion si [token] est celui reçu lors d'une inscription précédente. */
@Serializable
@SerialName("join")
data class JoinMessage(val name: String, val token: String? = null) : ClientMessage

@Serializable
@SerialName("buzz")
data object BuzzMessage : ClientMessage

/** Commandes du maître du jeu quand il pilote la manche depuis son téléphone. */
@Serializable
sealed interface MasterAction : ClientMessage

@Serializable
@SerialName("search")
data class SearchAction(val query: String) : MasterAction

/** Ajoute ou retire une chanson de la sélection (parmi les résultats affichés ou la sélection). */
@Serializable
@SerialName("toggleTrack")
data class ToggleTrackAction(val trackId: Long) : MasterAction

@Serializable
@SerialName("launch")
data object LaunchAction : MasterAction

@Serializable
@SerialName("togglePause")
data object TogglePauseAction : MasterAction

@Serializable
@SerialName("pause")
data object PauseAction : MasterAction

@Serializable
@SerialName("markFound")
data class MarkFoundAction(val trackId: Long, val playerId: Int) : MasterAction

@Serializable
@SerialName("unmarkFound")
data class UnmarkFoundAction(val trackId: Long) : MasterAction

@Serializable
@SerialName("rejectBuzz")
data object RejectBuzzAction : MasterAction

/** Finale : [playerId] a donné les deux dernières chansons, ou personne (null). */
@Serializable
@SerialName("resolveFinal")
data class ResolveFinalAction(val playerId: Int? = null) : MasterAction

@Serializable
@SerialName("nextRound")
data object NextRoundAction : MasterAction

// --- Hôte -> page buzzer ---------------------------------------------------------

@Serializable
sealed interface ServerMessage

/** Inscription acceptée : la page garde [token] pour se reconnecter. */
@Serializable
@SerialName("welcome")
data class WelcomeMessage(val token: String) : ServerMessage

@Serializable
@SerialName("rejected")
data class RejectedMessage(val reason: String) : ServerMessage

/** Le MJ a retiré ce joueur de la partie. */
@Serializable
@SerialName("kicked")
data object KickedMessage : ServerMessage

/** Ce que voit un joueur sur son téléphone ; renvoyé à chaque changement. */
@Serializable
@SerialName("state")
data class PlayerView(
    val name: String,
    val score: Int,
    val phase: Phase,
    val isMaster: Boolean,
    val masterName: String?,
    val roundNumber: Int,
    val totalRounds: Int,
    val canBuzz: Boolean = false,
    /** Joueur qui a la main après un buzz. */
    val buzzerName: String? = null,
    val youBuzzed: Boolean = false,
    /** Mauvaise réponse : bloqué jusqu'à la prochaine chanson trouvée. */
    val lockedOut: Boolean = false,
    val isFinal: Boolean = false,
    val musicLoading: Boolean = false,
    val remaining: Int = 0,
    val ranking: List<RankEntry> = emptyList(),
    /** Réponses de la manche, une fois révélées. */
    val answers: List<Answer> = emptyList(),
    /** Le MJ de la manche joue sur le téléphone hôte (joueur sans téléphone, ou reprise de la main). */
    val masterOnHost: Boolean = false,
    /** Commandes de la manche, envoyées uniquement au MJ qui pilote depuis son téléphone. */
    val master: MasterView? = null,
) : ServerMessage

@Serializable
data class MasterView(
    val songsPerRound: Int,
    // Sélection des chansons
    val query: String = "",
    val searching: Boolean = false,
    val error: String? = null,
    val results: List<TrackInfo> = emptyList(),
    val selection: List<TrackInfo> = emptyList(),
    // Manche en cours
    val songs: List<SongInfo> = emptyList(),
    val isPlaying: Boolean = false,
    val guessers: List<PlayerRef> = emptyList(),
    val buzzerId: Int? = null,
    val lockedOut: List<String> = emptyList(),
    // Révélation
    val isLastRound: Boolean = false,
    val nextMasterName: String? = null,
)

@Serializable
data class TrackInfo(val id: Long, val title: String, val artist: String, val cover: String?)

@Serializable
data class SongInfo(
    val id: Long,
    val title: String,
    val artist: String,
    val cover: String?,
    val foundById: Int?,
    val foundBy: String?,
)

@Serializable
data class PlayerRef(val id: Int, val name: String)

@Serializable
enum class Phase {
    @SerialName("lobby") LOBBY,
    @SerialName("selection") SELECTION,
    @SerialName("playing") PLAYING,
    @SerialName("reveal") REVEAL,
    @SerialName("results") RESULTS,
}

@Serializable
data class RankEntry(val name: String, val score: Int, val isYou: Boolean)

@Serializable
data class Answer(val title: String, val artist: String, val foundBy: String?)
