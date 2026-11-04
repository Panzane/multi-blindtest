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
) : ServerMessage

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
