package com.panzane.multiblindtest.game

import android.app.Application
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.wifi.WifiManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.panzane.multiblindtest.audio.MultiPreviewPlayer
import com.panzane.multiblindtest.data.deezer.DeezerRepository
import com.panzane.multiblindtest.data.deezer.Track
import com.panzane.multiblindtest.net.Answer
import com.panzane.multiblindtest.net.BuzzerHostService
import com.panzane.multiblindtest.net.BuzzerServer
import com.panzane.multiblindtest.net.LaunchAction
import com.panzane.multiblindtest.net.MarkFoundAction
import com.panzane.multiblindtest.net.MasterAction
import com.panzane.multiblindtest.net.MasterView
import com.panzane.multiblindtest.net.NextRoundAction
import com.panzane.multiblindtest.net.PauseAction
import com.panzane.multiblindtest.net.Phase
import com.panzane.multiblindtest.net.PlayerRef
import com.panzane.multiblindtest.net.PlayerView
import com.panzane.multiblindtest.net.RankEntry
import com.panzane.multiblindtest.net.RejectBuzzAction
import com.panzane.multiblindtest.net.ResolveFinalAction
import com.panzane.multiblindtest.net.SearchAction
import com.panzane.multiblindtest.net.SongInfo
import com.panzane.multiblindtest.net.ToggleTrackAction
import com.panzane.multiblindtest.net.TogglePauseAction
import com.panzane.multiblindtest.net.TrackInfo
import com.panzane.multiblindtest.net.UnmarkFoundAction
import com.panzane.multiblindtest.net.localIpv4Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class GameViewModel(application: Application) : AndroidViewModel(application), BuzzerServer.Handler {

    private val deezer = DeezerRepository()
    val audio = MultiPreviewPlayer(application)

    private val _state = MutableStateFlow(GameUiState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private var nextPlayerId = 0
    private var searchJob: Job? = null
    private var audioJob: Job? = null

    // --- Mode buzzers ----------------------------------------------------------

    private val server by lazy {
        val page = application.assets.open("buzzer.html").bufferedReader().use { it.readText() }
        BuzzerServer(page, this)
    }
    private var serverJob: Job? = null
    /** Démarrages / arrêts du serveur exécutés un par un, hors du thread principal. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serverDispatcher = Dispatchers.IO.limitedParallelism(1)
    /** Protège les ajouts de joueurs, faits depuis l'écran et depuis le serveur. */
    private val playersLock = Any()

    private val wifiLock: WifiManager.WifiLock =
        (application.getSystemService(Context.WIFI_SERVICE) as WifiManager).createWifiLock(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) WifiManager.WIFI_MODE_FULL_LOW_LATENCY
            else @Suppress("DEPRECATION") WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "multiblindtest:buzzers",
        ).apply { setReferenceCounted(false) }

    private val tone by lazy { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 100) }.getOrNull() }

    init {
        // Chaque changement d'état est répercuté sur les téléphones des joueurs
        viewModelScope.launch {
            combine(_state, audio.isPlaying, ::Pair).collect { (s, isPlaying) ->
                if (s.settings.buzzers) server.publish(playerViews(s, isPlaying))
            }
        }
    }

    // --- Joueurs -----------------------------------------------------------

    fun addPlayer(name: String) {
        addPlayer(name, token = null)
    }

    private fun addPlayer(name: String, token: String?): Boolean = synchronized(playersLock) {
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        if (trimmed.isEmpty() || _state.value.players.size >= MAX_PLAYERS) return false
        val player = Player(nextPlayerId++, trimmed, token = token)
        _state.update { it.copy(players = it.players + player) }
        true
    }

    fun removePlayer(id: Int) {
        val player = _state.value.players.firstOrNull { it.id == id } ?: return
        _state.update { s -> s.copy(players = s.players.filterNot { it.id == id }) }
        player.token?.let(server::kick)
    }

    /** Désigne le joueur qui joue sur ce téléphone (ou personne, s'il est déjà désigné). */
    fun setHostOwner(id: Int) {
        _state.update { s ->
            val wasOwner = s.players.firstOrNull { it.id == id }?.isHostOwner == true
            s.copy(players = s.players.map { it.copy(isHostOwner = !wasOwner && it.id == id) })
        }
    }

    fun setBuzzerMode(enabled: Boolean) {
        if (enabled == _state.value.settings.buzzers) return
        _state.update { it.copy(settings = it.settings.copy(buzzers = enabled)) }
        if (enabled) startServer() else stopServer()
    }

    private fun startServer() {
        serverJob?.cancel()
        serverJob = viewModelScope.launch {
            val port = withContext(serverDispatcher) { runCatching { server.start() } }.getOrElse {
                _state.update { it.copy(error = "Impossible de démarrer le serveur de buzzers.") }
                return@launch
            }
            wifiLock.acquire()
            BuzzerHostService.start(getApplication())
            launch {
                server.connectedTokens.collect { tokens ->
                    _state.update { s -> s.copy(connectedIds = s.players.filter { it.token in tokens }.map { it.id }.toSet()) }
                }
            }
            // L'adresse change si le téléphone passe du wifi au partage de connexion : on la surveille
            while (true) {
                val ip = withContext(Dispatchers.IO) { localIpv4Address() }
                _state.update { it.copy(hostUrl = ip?.let { address -> "http://$address:$port" }) }
                delay(3_000)
            }
        }
    }

    private fun stopServer() {
        serverJob?.cancel()
        serverJob = null
        wifiLock.release()
        BuzzerHostService.stop(getApplication())
        viewModelScope.launch(serverDispatcher) { server.stop() }
        _state.update { s ->
            s.copy(players = s.players.filterNot { it.hasBuzzer }, hostUrl = null, connectedIds = emptySet())
        }
    }

    /** Un téléphone rejoint la partie, ou s'y reconnecte avec le jeton reçu à l'inscription. */
    override fun onJoin(name: String, token: String?): BuzzerServer.JoinResult = synchronized(playersLock) {
        val s = _state.value
        if (token != null && s.players.any { it.token == token }) return BuzzerServer.JoinResult.Accepted(token)
        val trimmed = name.trim().take(MAX_NAME_LENGTH)
        val reason = when {
            !s.settings.buzzers -> "Le mode buzzers n'est plus actif."
            s.screen != Screen.PLAYERS -> "La partie a déjà commencé."
            trimmed.isEmpty() -> "Entre ton prénom."
            s.players.size >= MAX_PLAYERS -> "La partie est complète."
            s.players.any { it.name.equals(trimmed, ignoreCase = true) } -> "Ce prénom est déjà pris."
            else -> null
        }
        if (reason != null) return BuzzerServer.JoinResult.Rejected(reason)
        val newToken = UUID.randomUUID().toString()
        addPlayer(trimmed, newToken)
        BuzzerServer.JoinResult.Accepted(newToken)
    }

    override fun onBuzz(token: String) {
        _state.value.players.firstOrNull { it.token == token }?.let { buzz(it.id) }
    }

    /** Le joueur qui utilise le téléphone hôte buzze depuis l'écran de l'hôte. */
    fun hostBuzz() {
        _state.value.hostOwner?.let { buzz(it.id) }
    }

    /** Le premier buzz reçu prend la main : la musique s'arrête et les autres buzzers sont bloqués. */
    private fun buzz(playerId: Int) {
        while (true) {
            val s = _state.value
            val round = s.round ?: return
            val player = s.players.firstOrNull { it.id == playerId } ?: return
            val allowed = s.screen == Screen.PLAYING && !round.isAudioLoading && round.buzzerId == null &&
                player.id != s.master?.id && player.id !in round.lockedOut && round.remaining.isNotEmpty()
            if (!allowed) return
            if (_state.compareAndSet(s, s.copy(round = round.copy(buzzerId = player.id)))) break
        }
        viewModelScope.launch {
            audio.pause()
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
        }
    }

    /** Mauvaise réponse : le joueur est bloqué jusqu'à la prochaine chanson trouvée, et la musique reprend. */
    fun rejectBuzz() {
        _state.update { s ->
            val round = s.round ?: return@update s
            val buzzerId = round.buzzerId ?: return@update s
            s.copy(round = round.copy(buzzerId = null, lockedOut = round.lockedOut + buzzerId))
        }
        audio.resume(remainingIds())
    }

    /** Commandes du MJ qui pilote depuis son téléphone ; ignorées si elles ne viennent pas de lui. */
    override fun onMasterAction(token: String, action: MasterAction) {
        viewModelScope.launch {
            val s = _state.value
            if (!s.remoteMaster || s.master?.token != token) return@launch
            val round = s.round
            val isGuesser = { id: Int -> s.guessers.any { it.id == id } }
            when (action) {
                is SearchAction -> if (s.screen == Screen.SELECTION) {
                    onQueryChange(action.query)
                    search(action.query)
                }
                is ToggleTrackAction -> if (s.screen == Screen.SELECTION) {
                    (s.selection + s.searchResults).firstOrNull { it.id == action.trackId }?.let(::toggleSelection)
                }
                LaunchAction -> if (s.screen == Screen.SELECTION) launchRound()
                TogglePauseAction -> if (s.screen == Screen.PLAYING && round?.isAudioLoading == false) togglePause()
                PauseAction -> if (s.screen == Screen.PLAYING) pauseMusic()
                is MarkFoundAction -> if (s.screen == Screen.PLAYING && round?.isFinal == false && isGuesser(action.playerId)) {
                    if (round.songs.any { it.track.id == action.trackId && !it.isFound }) markFound(action.trackId, action.playerId)
                }
                is UnmarkFoundAction -> if (s.screen == Screen.PLAYING) unmarkFound(action.trackId)
                RejectBuzzAction -> if (s.screen == Screen.PLAYING) rejectBuzz()
                is ResolveFinalAction -> if (s.screen == Screen.PLAYING && round?.isFinal == true) {
                    if (action.playerId == null || isGuesser(action.playerId)) resolveFinal(action.playerId)
                }
                NextRoundAction -> if (s.screen == Screen.REVEAL) nextRound()
            }
        }
    }

    /** Le MJ ne peut plus piloter depuis son téléphone : ses commandes passent sur l'hôte jusqu'à la fin de la manche. */
    fun takeOverMaster() {
        _state.update { it.copy(masterOnHost = true) }
    }

    private fun playerViews(s: GameUiState, isPlaying: Boolean): Map<String, PlayerView> {
        val round = s.round
        val phase = when (s.screen) {
            Screen.PLAYERS -> Phase.LOBBY
            Screen.SELECTION -> Phase.SELECTION
            Screen.PLAYING -> Phase.PLAYING
            Screen.REVEAL -> Phase.REVEAL
            Screen.RESULTS -> Phase.RESULTS
        }
        val inRound = phase == Phase.SELECTION || phase == Phase.PLAYING || phase == Phase.REVEAL
        val answers = if (phase == Phase.REVEAL && round != null) {
            round.songs.map { Answer(it.track.title, it.track.artist, s.playerName(it.foundBy)) }
        } else {
            emptyList()
        }
        // Commandes du MJ, calculées une seule fois : seul le MJ qui pilote depuis son téléphone les reçoit
        val masterView = if (s.remoteMaster && inRound) masterView(s, isPlaying) else null
        return s.players.mapNotNull { player ->
            val token = player.token ?: return@mapNotNull null
            val isMaster = inRound && player.id == s.master?.id
            val lockedOut = round != null && player.id in round.lockedOut
            token to PlayerView(
                name = player.name,
                score = player.score,
                phase = phase,
                isMaster = isMaster,
                masterName = s.master?.name,
                roundNumber = s.roundIndex + 1,
                totalRounds = s.totalRounds,
                canBuzz = phase == Phase.PLAYING && round != null && !round.isAudioLoading &&
                    round.buzzerId == null && !isMaster && !lockedOut && round.remaining.isNotEmpty(),
                buzzerName = s.playerName(round?.buzzerId),
                youBuzzed = round?.buzzerId == player.id,
                lockedOut = lockedOut,
                isFinal = round?.isFinal == true,
                musicLoading = round?.isAudioLoading == true,
                remaining = round?.remaining?.size ?: 0,
                ranking = s.ranking.map { RankEntry(it.name, it.score, isYou = it.id == player.id) },
                answers = answers,
                masterOnHost = inRound && !s.remoteMaster,
                master = masterView.takeIf { isMaster },
            )
        }.toMap()
    }

    private fun masterView(s: GameUiState, isPlaying: Boolean): MasterView {
        val round = s.round
        return MasterView(
            songsPerRound = s.settings.songsPerRound,
            query = s.query,
            searching = s.isLoading,
            error = s.error,
            results = s.searchResults.map { it.toInfo() },
            selection = s.selection.map { it.toInfo() },
            songs = round?.songs.orEmpty().map {
                SongInfo(it.track.id, it.track.title, it.track.artist, it.track.coverUrl, it.foundBy, s.playerName(it.foundBy))
            },
            isPlaying = isPlaying,
            guessers = s.guessers.map { PlayerRef(it.id, it.name) },
            buzzerId = round?.buzzerId,
            lockedOut = round?.lockedOut.orEmpty().mapNotNull { s.playerName(it) },
            isLastRound = s.isLastRound,
            nextMasterName = s.nextMaster?.name,
        )
    }

    private fun Track.toInfo() = TrackInfo(id, title, artist, coverUrl)

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
        _state.update {
            it.copy(screen = Screen.SELECTION, selection = emptyList(), query = "", error = null, masterOnHost = false)
        }
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
        val before = _state.value
        val adding = before.selection.none { it.id == track.id } &&
            before.selection.size < before.settings.songsPerRound
        _state.update { s ->
            when {
                s.selection.any { it.id == track.id } -> s.copy(selection = s.selection.filterNot { it.id == track.id })
                s.selection.size >= s.settings.songsPerRound -> s
                else -> s.copy(selection = s.selection + track)
            }
        }
        // Après un ajout, on vide la recherche pour enchaîner sur la suivante
        // (sauf pour un lien de playlist, dont on garde la liste de titres).
        val query = before.query
        if (adding && query.isNotBlank() && DeezerRepository.parsePlaylistId(query) == null) {
            _state.update { it.copy(query = "") }
            search("")
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
        // Après un buzz, la musique ne reprend qu'une fois la réponse jugée
        if (_state.value.round?.buzzerId != null) return
        if (audio.isPlaying.value) audio.pause() else audio.resume(remainingIds())
    }

    /** Le MJ ouvre l'attribution d'un point : on coupe la musique pendant qu'il choisit. */
    fun pauseMusic() {
        audio.pause()
    }

    /** Lance depuis le début toutes les chansons pas encore trouvées (début de manche). */
    private fun replayRemaining() {
        audio.playFromStart(remainingIds())
    }

    private fun remainingIds(): Set<Long> =
        _state.value.round?.remaining?.map { it.track.id }?.toSet().orEmpty()

    /** Le maître du jeu coche une chanson trouvée : la musique s'arrête jusqu'à ce qu'il relance. */
    fun markFound(trackId: Long, playerId: Int) {
        audio.pause()
        setFoundBy(trackId, playerId)
        // Une chanson trouvée débloque tout le monde
        _state.update { it.copy(round = it.round?.copy(buzzerId = null, lockedOut = emptySet())) }
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
        _state.update { it.copy(screen = Screen.REVEAL, round = it.round?.copy(buzzerId = null)) }
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
        serverJob?.cancel()
        server.stop()
        wifiLock.release()
        BuzzerHostService.stop(getApplication())
        tone?.release()
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is java.io.IOException -> "Connexion à Deezer impossible. Vérifiez votre accès Internet."
        else -> message ?: "Erreur inattendue."
    }
}
