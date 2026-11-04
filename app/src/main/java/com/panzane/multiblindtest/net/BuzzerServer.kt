package com.panzane.multiblindtest.net

import io.ktor.http.ContentType
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Petit serveur web lancé sur le téléphone hôte en mode buzzers : il sert la page buzzer ([pageHtml])
 * et échange avec chaque téléphone joueur par WebSocket. Les décisions de jeu sont déléguées à [handler].
 */
class BuzzerServer(private val pageHtml: String, private val handler: Handler) {

    interface Handler {
        /** Appelé depuis un thread du serveur ; doit être thread-safe. */
        fun onJoin(name: String, token: String?): JoinResult

        /** Appelé depuis un thread du serveur, dans l'ordre d'arrivée des buzz. */
        fun onBuzz(token: String)
    }

    sealed interface JoinResult {
        data class Accepted(val token: String) : JoinResult
        data class Rejected(val reason: String) : JoinResult
    }

    private var engine: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null
    private val nextSessionId = AtomicLong()
    private val sessions = ConcurrentHashMap<Long, DefaultWebSocketSession>()
    /** Session -> token du joueur, une fois inscrit. */
    private val tokens = ConcurrentHashMap<Long, String>()
    /** Dernier état envoyé à chaque joueur, pour le renvoyer à la reconnexion. */
    private val lastViews = ConcurrentHashMap<String, PlayerView>()

    private val _connectedTokens = MutableStateFlow<Set<String>>(emptySet())
    /** Joueurs dont au moins un téléphone est connecté. */
    val connectedTokens: StateFlow<Set<String>> = _connectedTokens.asStateFlow()

    var port: Int? = null
        private set

    /** Démarre le serveur sur le premier port libre à partir de [PREFERRED_PORT]. Bloquant : à appeler hors du thread principal. */
    fun start(): Int {
        port?.let { return it }
        var lastError: Exception? = null
        for (candidate in PREFERRED_PORT until PREFERRED_PORT + 10) {
            try {
                engine = embeddedServer(CIO, port = candidate, host = "0.0.0.0") {
                    install(WebSockets) {
                        // Détecte vite les téléphones partis en veille
                        pingPeriodMillis = 5_000
                        timeoutMillis = 15_000
                    }
                    routing {
                        get("/") { call.respondText(pageHtml, ContentType.Text.Html) }
                        webSocket("/ws") { handleSession(this) }
                    }
                }.start(wait = false)
                port = candidate
                return candidate
            } catch (e: Exception) {
                lastError = e
                engine?.stop(0, 0)
                engine = null
            }
        }
        throw lastError ?: IllegalStateException("Aucun port disponible")
    }

    fun stop() {
        engine?.stop(500, 1000)
        engine = null
        port = null
        sessions.clear()
        tokens.clear()
        lastViews.clear()
        _connectedTokens.value = emptySet()
    }

    /** Met à jour l'écran de chaque joueur ; seuls les états qui ont changé sont envoyés. */
    fun publish(views: Map<String, PlayerView>) {
        views.forEach { (token, view) ->
            if (lastViews.put(token, view) != view) sendTo(token, view)
        }
        lastViews.keys.retainAll(views.keys)
    }

    /** Déconnecte un joueur retiré par le MJ. */
    fun kick(token: String) {
        lastViews.remove(token)
        sessionsOf(token).forEach { (id, session) ->
            session.outgoing.trySend(Frame.Text(encode(KickedMessage)))
            tokens.remove(id)
        }
        refreshConnected()
    }

    private suspend fun handleSession(session: DefaultWebSocketSession) {
        val id = nextSessionId.incrementAndGet()
        sessions[id] = session
        try {
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val message = runCatching {
                    protocolJson.decodeFromString(ClientMessage.serializer(), frame.readText())
                }.getOrNull() ?: continue
                when (message) {
                    is JoinMessage -> when (val result = handler.onJoin(message.name, message.token)) {
                        is JoinResult.Accepted -> {
                            tokens[id] = result.token
                            session.send(WelcomeMessage(result.token))
                            lastViews[result.token]?.let { session.send(it) }
                            refreshConnected()
                        }
                        is JoinResult.Rejected -> session.send(RejectedMessage(result.reason))
                    }
                    BuzzMessage -> tokens[id]?.let(handler::onBuzz)
                }
            }
        } finally {
            sessions.remove(id)
            tokens.remove(id)
            refreshConnected()
        }
    }

    private fun sendTo(token: String, message: ServerMessage) {
        val text = encode(message)
        sessionsOf(token).forEach { (_, session) -> session.outgoing.trySend(Frame.Text(text)) }
    }

    private fun sessionsOf(token: String): List<Pair<Long, DefaultWebSocketSession>> =
        tokens.filterValues { it == token }.keys.mapNotNull { id -> sessions[id]?.let { id to it } }

    private fun DefaultWebSocketSession.send(message: ServerMessage) {
        outgoing.trySend(Frame.Text(encode(message)))
    }

    private fun refreshConnected() {
        _connectedTokens.value = tokens.values.toSet()
    }

    private fun encode(message: ServerMessage) = protocolJson.encodeToString(ServerMessage.serializer(), message)

    companion object {
        const val PREFERRED_PORT = 8080
    }
}
