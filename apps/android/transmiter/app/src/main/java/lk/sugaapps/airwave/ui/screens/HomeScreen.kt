package lk.sugaapps.airwave.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lk.sugaapps.airwave.R
import lk.sugaapps.airwave.data.FlashNews
import lk.sugaapps.airwave.data.NewsItem
import lk.sugaapps.airwave.data.NewsManager
import lk.sugaapps.airwave.data.RjConfig
import lk.sugaapps.airwave.data.RjScheduler
import lk.sugaapps.airwave.data.RjStudio
import lk.sugaapps.airwave.data.TimeVoiceOver
import lk.sugaapps.airwave.engine.EngineRegistry
import lk.sugaapps.airwave.engine.Playlist
import lk.sugaapps.airwave.engine.StreamConfig
import lk.sugaapps.airwave.ui.theme.AirWaveTheme
import lk.sugaapps.airwave.ui.theme.BorderLight
import lk.sugaapps.airwave.ui.theme.BorderStrong
import lk.sugaapps.airwave.ui.theme.BrandBlue
import lk.sugaapps.airwave.ui.theme.DangerRed
import lk.sugaapps.airwave.ui.theme.OnAirTeal
import lk.sugaapps.airwave.ui.theme.PageBackground
import lk.sugaapps.airwave.ui.theme.SurfaceMuted
import lk.sugaapps.airwave.ui.theme.SurfaceWhite
import lk.sugaapps.airwave.ui.theme.TextMuted
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.withTimeoutOrNull
import lk.sugaapps.airwave.data.AuthRepository
import lk.sugaapps.airwave.data.ChatOnAirManager
import lk.sugaapps.airwave.data.SettingsStore
import lk.sugaapps.airwave.data.StationChatMessage
import lk.sugaapps.airwave.data.StationManager
import lk.sugaapps.airwave.data.UserStore
import lk.sugaapps.airwave.service.BroadcastForegroundService
import lk.sugaapps.airwave.ui.components.SectionCard
import lk.sugaapps.airwave.ui.theme.WarnAmber
import lk.sugaapps.airwave.ui.theme.TextPrimary
import lk.sugaapps.airwave.ui.theme.TextSecondary
import androidx.compose.ui.text.style.TextAlign

private enum class HomeTab { BROADCAST, MIXER, AI, TRACKS, SETTINGS }

/**
 * Home / broadcast console — recreates the legacy app's broadcast screen in the
 * current light theme. UI only: no native/streaming calls (local stubs).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf(HomeTab.BROADCAST) }
    var showEqualizer by rememberSaveable { mutableStateOf(false) }
    val saveableStateHolder = rememberSaveableStateHolder()

    val engine = remember { EngineRegistry.engine }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val musicPosition by engine.musicPosition.collectAsState()
    val connState by engine.connectionState.collectAsState()
    var lastPosition by remember { mutableFloatStateOf(0f) }

    // Interrupted song state: when flash news interrupts, save track info and resume on completion!
    var interruptedTrackUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var interruptedTrackPos by remember { mutableFloatStateOf(0f) }
    var interruptedTrackTitle by remember { mutableStateOf("") }
    var isPlayingFlashNews by remember { mutableStateOf(false) }

    // Incoming breaking news popup state from Firestore News feed
    var incomingAlertItem by remember { mutableStateOf<NewsItem?>(null) }
    var isBroadcastingNews by remember { mutableStateOf(false) }

    fun broadcastNewsImmediately(headline: String, content: String) {
        if (isBroadcastingNews) return
        isBroadcastingNews = true
        // Seamlessly switch to broadcast console so broadcaster sees the on-air cutover
        tab = HomeTab.BROADCAST
        scope.launch(Dispatchers.Main) {
            withContext(NonCancellable) {
                try {
                    // Keep music playing seamlessly while Gemini & TTS generate the bulletin in the background!
                    // No silence or dead air for listeners.
                    toast("Voicing Flash News… music continues on-air")
                    val item = FlashNews.create(context, headline, content)

                    // Flash news is now 100% generated and cached. Cut over seamlessly now!
                    val current = Playlist.current
                    if (current != null && !isPlayingFlashNews) {
                        interruptedTrackUri = current.uri
                        interruptedTrackPos = engine.musicPosition.value
                        interruptedTrackTitle = current.title
                    }

                    isPlayingFlashNews = true
                    val targetIdx = (Playlist.currentIndex + 1).coerceAtLeast(0)
                    Playlist.insert(targetIdx, item)
                    Playlist.play(targetIdx, engine, context)
                    toast("Broadcasting Flash News on-air!")
                } catch (e: Exception) {
                    toast("Broadcast failed: ${e.message ?: "Voice service error"}")
                    isPlayingFlashNews = false
                } finally {
                    isBroadcastingNews = false
                }
            }
        }
    }

    var currentBroadcaster by remember { mutableStateOf(AuthRepository.currentUser) }
    DisposableEffect(Unit) {
        val listener = AuthRepository.addAuthStateListener { user ->
            currentBroadcaster = user
        }
        onDispose { AuthRepository.removeAuthStateListener(listener) }
    }

    // Start realtime listener for listener chat on-air messages
    LaunchedEffect(currentBroadcaster?.uid) {
        val uid = currentBroadcaster?.uid
        if (!uid.isNullOrBlank()) {
            ChatOnAirManager.start(context, uid, scope)
            val tracks = Playlist.musicTracks.map {
                mapOf("title" to it.title, "artist" to it.artist)
            }
            if (tracks.isNotEmpty()) {
                StationManager.syncPlaylist(uid, tracks)
            }
        }
    }

    // Collect on-air notifications from ChatOnAirManager
    LaunchedEffect(Unit) {
        ChatOnAirManager.onAirNotificationFlow.collect { notification ->
            toast(notification)
        }
    }

    fun broadcastChatImmediately(msg: StationChatMessage) {
        ChatOnAirManager.broadcastNow(
            context = context,
            msg = msg,
            engine = engine,
            scope = scope,
            onInterruptedPos = { uri, pos, title ->
                interruptedTrackUri = uri
                interruptedTrackPos = pos
                interruptedTrackTitle = title
                isPlayingFlashNews = true
            }
        )
    }

    // Auto-advance or resume interrupted song when current track ends
    LaunchedEffect(musicPosition) {
        if (musicPosition >= 0.99f && lastPosition < 0.99f) {
            lastPosition = 1f
            if (isPlayingFlashNews && interruptedTrackUri != null) {
                // Flash news / interrupted shoutout ended! Resume the interrupted song at its saved position!
                isPlayingFlashNews = false
                val savedUri = interruptedTrackUri!!
                val savedPos = interruptedTrackPos
                val savedTitle = interruptedTrackTitle
                interruptedTrackUri = null

                // Remove the finished flash news / chat item from the playlist
                val curIdx = Playlist.currentIndex
                if (curIdx in Playlist.items.indices && Playlist.items[curIdx].uri != savedUri) {
                    val finishedItem = Playlist.items[curIdx]
                    ChatOnAirManager.markAsPlayedByTitle(context, finishedItem.title)
                    Playlist.remove(curIdx)
                }

                val savedIdx = Playlist.items.indexOfFirst { it.uri == savedUri }
                if (savedIdx >= 0) {
                    Playlist.play(savedIdx, engine, context, startPercent = savedPos)
                    scope.launch {
                        delay(250)
                        if (savedPos > 0.005f) {
                            engine.seek(savedPos)
                        }
                    }
                    toast("Resumed: $savedTitle")
                } else if (Playlist.hasNext()) {
                    Playlist.next(engine, context)
                } else if (Playlist.items.isNotEmpty() && (RjConfig.autoReplay || RjConfig.isTakeoverEnabled)) {
                    Playlist.play(0, engine, context)
                }
            } else {
                // Track finished! Check if it was an AI RJ shoutout or time check
                val curIdx = Playlist.currentIndex
                val finishedItem = if (curIdx in Playlist.items.indices) Playlist.items[curIdx] else null
                val isAiClip = finishedItem?.let {
                    it.title.startsWith("💬") ||
                    it.title.startsWith("Flash News") ||
                    it.artist.contains("Listener On-Air") ||
                    it.artist.contains("Co-Host")
                } == true

                if (isAiClip) {
                    val item = finishedItem!!
                    // Mark as played and remove from playlist so it doesn't replay, then seamlessly proceed to next song!
                    ChatOnAirManager.markAsPlayedByTitle(context, item.title)
                    Playlist.remove(curIdx)
                    if (Playlist.currentIndex in Playlist.items.indices) {
                        Playlist.play(Playlist.currentIndex, engine, context)
                    } else if (Playlist.items.isNotEmpty()) {
                        Playlist.play(0, engine, context)
                    } else {
                        engine.setMusicPlaying(false)
                        engine.seek(0f)
                    }
                } else if (Playlist.hasNext()) {
                    Playlist.next(engine, context)
                } else if (Playlist.items.isNotEmpty()) {
                    if (RjConfig.autoReplay || RjConfig.isTakeoverEnabled) {
                        Playlist.play(0, engine, context)
                        toast("Continuous Auto-Replay: Repeating playlist")
                    } else {
                        engine.setMusicPlaying(false)
                        engine.seek(0f)
                    }
                }
            }
        }
        lastPosition = musicPosition
    }

    // Initialize persistent playlist, Firestore News feed, and sync current track
    LaunchedEffect(Unit) {
        Playlist.initialize(context)
        NewsManager.start(context, scope)
        Playlist.current?.let { item ->
            engine.setCurrentTrackInfo(item.title, item.artist)
        }
    }

    // Automatically pop news dialog every 1 minute while user is live on-air
    LaunchedEffect(connState) {
        if (connState == 1) {
            while (isActive && connState == 1) {
                delay(60_000L) // 1 minute interval
                if (connState == 1 && incomingAlertItem == null && !isBroadcastingNews) {
                    // Suppress manual dialog popup when AI Full Takeover is actively broadcasting news
                    if (!RjConfig.isTakeoverEnabled) {
                        val nextItem = NewsManager.getNextUnbroadcastNews(context)
                        if (nextItem != null) {
                            incomingAlertItem = nextItem
                        }
                    }
                }
            }
        }
    }

    // Manage AI Full Takeover and automatic periodic RJ time announcements during live broadcast
    LaunchedEffect(connState) {
        if (connState == 1) {
            RjScheduler.onTriggerNewsNow = { headline, content ->
                broadcastNewsImmediately(headline, content)
            }
            RjScheduler.onTriggerNewsUpNext = { headline, content ->
                scope.launch(Dispatchers.Main) {
                    try {
                        val item = FlashNews.create(context, headline, content)
                        Playlist.enqueueNext(item)
                        toast("AI Takeover: News added to Up Next")
                    } catch (e: Exception) {
                        toast("Takeover news failed: ${e.message ?: "Voice error"}")
                    }
                }
            }
            RjScheduler.onTriggerTimeCheck = {
                scope.launch(Dispatchers.Main) {
                    try {
                        val curDurMs = Playlist.current?.durationMs ?: 0L
                        val balanceSec = Playlist.getSongBalanceTimeMs() / 1000L
                        val minRequiredSec = TimeVoiceOver.getMinimumRequiredBalanceSec()
                        if (Playlist.isMusicPlaying && curDurMs > 0 && balanceSec < minRequiredSec) {
                            android.util.Log.d("HomeScreen", "Takeover time check deferred: balance ${balanceSec}s < min ${minRequiredSec}s")
                            return@launch
                        }
                        val targetTimeMs = Playlist.calculateProjectedTimeCheckMs()
                        val current = Playlist.current
                        val next = Playlist.items.getOrNull(Playlist.currentIndex + 1)
                        val item = TimeVoiceOver.create(
                            context = context,
                            nowTrack = current?.title.orEmpty(),
                            nowArtist = current?.artist.orEmpty(),
                            nextTrack = next?.title.orEmpty(),
                            nextArtist = next?.artist.orEmpty(),
                            targetTimeMs = targetTimeMs,
                        )
                        Playlist.enqueueNext(item)
                        val timeLabel = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(targetTimeMs))
                        toast("AI Takeover: RJ Time Check ($timeLabel) queued")
                    } catch (e: Exception) {
                        android.util.Log.w("HomeScreen", "Takeover time announcement error", e)
                    }
                }
            }
            RjScheduler.start(context, scope)
        } else {
            RjScheduler.stop()
        }
    }

    BackHandler(enabled = showEqualizer) { showEqualizer = false }

    if (showEqualizer) {
        EqualizerScreen(modifier = modifier, onBack = { showEqualizer = false })
        return
    }

    // Breaking news popup alert during live broadcast!
    incomingAlertItem?.let { alert ->
        var isAlertVoicing by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                if (!isAlertVoicing) {
                    NewsManager.markBroadcast(context, alert.id)
                    incomingAlertItem = null
                }
            },
            icon = {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(DangerRed.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_ai), contentDescription = null, tint = DangerRed, modifier = Modifier.size(24.dp))
                }
            },
            title = {
                Text("Breaking Flash News!", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (alert.category.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BrandBlue.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = alert.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandBlue,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Text(
                        text = alert.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                    if (alert.description.isNotBlank() && alert.description != alert.title) {
                        Text(
                            text = alert.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 4,
                        )
                    }
                    if (alert.published.isNotBlank()) {
                        Text(
                            text = "Published: ${alert.published}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Would you like to interrupt current music, broadcast this flash news, and resume your song when finished?",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val title = alert.title
                        val desc = alert.description.ifBlank { alert.title }
                        NewsManager.markBroadcast(context, alert.id)
                        incomingAlertItem = null
                        broadcastNewsImmediately(title, desc)
                    },
                    enabled = !isAlertVoicing,
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White),
                ) {
                    if (isAlertVoicing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Voicing Bulletin…", fontWeight = FontWeight.Bold)
                    } else {
                        Text("Play Now (Pause & Resume)", fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val title = alert.title
                            val desc = alert.description.ifBlank { alert.title }
                            NewsManager.markBroadcast(context, alert.id)
                            incomingAlertItem = null
                            scope.launch {
                                try {
                                    val item = FlashNews.create(context, title, desc)
                                    Playlist.enqueueNext(item)
                                    toast("Added to Up Next!")
                                } catch (e: Exception) {
                                    toast("Failed: ${e.message ?: "Voice error"}")
                                }
                            }
                        },
                        enabled = !isAlertVoicing,
                    ) {
                        Text("Add to Up Next")
                    }

                    TextButton(
                        onClick = {
                            NewsManager.markBroadcast(context, alert.id)
                            incomingAlertItem = null
                        },
                        enabled = !isAlertVoicing,
                    ) {
                        Text("Dismiss")
                    }
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = PageBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.logo_mark),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.brand_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceWhite,
                    titleContentColor = TextPrimary,
                ),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = SurfaceWhite) {
                NavItem(tab == HomeTab.BROADCAST, R.string.tab_broadcast, R.drawable.ic_broadcast) { tab = HomeTab.BROADCAST }
                NavItem(tab == HomeTab.MIXER, R.string.tab_mixer, R.drawable.ic_mixer) { tab = HomeTab.MIXER }
                NavItem(tab == HomeTab.AI, R.string.tab_ai, R.drawable.ic_ai) { tab = HomeTab.AI }
                NavItem(tab == HomeTab.TRACKS, R.string.tab_tracks, R.drawable.ic_tracks) { tab = HomeTab.TRACKS }
                NavItem(tab == HomeTab.SETTINGS, R.string.tab_settings, R.drawable.ic_settings) { tab = HomeTab.SETTINGS }
            }
        },
    ) { innerPadding ->
        saveableStateHolder.SaveableStateProvider(key = tab) {
            when (tab) {
                HomeTab.BROADCAST -> BroadcastContent(
                    modifier = Modifier.padding(innerPadding),
                    isBroadcastingNews = isBroadcastingNews,
                    onBroadcastNewsNow = { headline, content -> broadcastNewsImmediately(headline, content) },
                    onBroadcastChatNow = { msg -> broadcastChatImmediately(msg) },
                )
                HomeTab.MIXER -> MixerScreen(Modifier.padding(innerPadding), onOpenEqualizer = { showEqualizer = true })
                HomeTab.AI -> AiStudioScreen(
                    modifier = Modifier.padding(innerPadding),
                    onBroadcastNewsNow = { headline, content -> broadcastNewsImmediately(headline, content) },
                    onEnqueueNews = { headline, content ->
                        scope.launch(Dispatchers.Main) {
                            withContext(NonCancellable) {
                                try {
                                    toast("Voicing Flash News for Up Next…")
                                    val item = FlashNews.create(context, headline, content)
                                    Playlist.enqueueNext(item)
                                    toast("Flash News added to Up Next")
                                } catch (e: Exception) {
                                    toast("Failed to enqueue: ${e.message ?: "Voice error"}")
                                }
                            }
                        }
                    },
                )
                HomeTab.TRACKS -> LibraryScreen(Modifier.padding(innerPadding))
                HomeTab.SETTINGS -> SettingsScreen(Modifier.padding(innerPadding))
            }
        }
    }
}

@Composable
private fun BroadcastContent(
    modifier: Modifier = Modifier,
    isBroadcastingNews: Boolean = false,
    onBroadcastNewsNow: ((String, String) -> Unit)? = null,
    onBroadcastChatNow: ((StationChatMessage) -> Unit)? = null,
) {
    val engine = remember { EngineRegistry.engine }
    val connState by engine.connectionState.collectAsState()
    val connMessage by engine.connectionMessage.collectAsState()
    val latencyMs by engine.connectionLatencyMs.collectAsState()
    val durationMs by engine.connectionDurationMs.collectAsState()
    val trackTitle by engine.currentTrackTitle.collectAsState()
    val trackArtist by engine.currentTrackArtist.collectAsState()
    val enginePosition by engine.musicPosition.collectAsState()
    val rjVoice by RjConfig.voiceIndexFlow.collectAsState()
    val rjSpeed by RjConfig.speedIndexFlow.collectAsState()
    val rjInterval by RjConfig.autoTimeIntervalFlow.collectAsState()
    val isTakeoverEnabled by RjConfig.isTakeoverEnabledFlow.collectAsState()
    val takeoverNewsInterval by RjConfig.takeoverNewsIntervalFlow.collectAsState()
    val takeoverTimeInterval by RjConfig.takeoverTimeIntervalFlow.collectAsState()
    val takeoverAutoReplay by RjConfig.autoReplayFlow.collectAsState()
    val chatMessages by ChatOnAirManager.messagesFlow.collectAsState()
    var isPlaying by remember { mutableStateOf(true) }
    var seek by remember { mutableFloatStateOf(0.35f) }
    var queueDialogFor by remember { mutableStateOf<Int?>(null) }
    var upNextFilter by rememberSaveable { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var timeChecking by remember { mutableStateOf(false) }
    var timeCheckProgressSec by remember { mutableIntStateOf(0) }
    var timeCheckError by remember { mutableStateOf<String?>(null) }
    var showFlashNewsDialog by rememberSaveable { mutableStateOf(false) }
    var newsGenerating by remember { mutableStateOf(false) }
    var newsHeadline by rememberSaveable { mutableStateOf(FlashNews.DEFAULT_TEST_HEADLINE) }
    var newsContent by rememberSaveable { mutableStateOf(FlashNews.DEFAULT_TEST_CONTENT) }
    var showStopConfirm by remember { mutableStateOf(false) }

    val context = LocalContext.current
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_LONG).show()

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    // Start or resume playing the currently selected track when going live
    fun playCurrentTrack() {
        val curIdx = if (Playlist.currentIndex in Playlist.items.indices) {
            Playlist.currentIndex
        } else if (Playlist.items.isNotEmpty()) {
            0
        } else {
            -1
        }
        if (curIdx >= 0) {
            isPlaying = true
            val currentItem = Playlist.items[curIdx]
            val currentPos = engine.musicPosition.value
            if (trackTitle.isBlank() || trackTitle != currentItem.title || currentPos <= 0.005f) {
                Playlist.play(curIdx, engine, context, startPercent = currentPos)
            } else {
                engine.setMusicPlaying(true)
            }
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (granted) {
            BroadcastForegroundService.start(context)
            engine.startBroadcast()
            playCurrentTrack()
        }
        else toast("Microphone permission is required to broadcast.")
    }

    // Validate real server config + mic permission before going live — no dummy start.
    fun goLive() {
        when {
            StreamConfig.host.isBlank() ->
                toast("Add your Icecast/Shoutcast server in Settings before going live.")
            StreamConfig.port <= 0 ->
                toast("Set a valid server port in Settings.")
            StreamConfig.mount.isBlank() ->
                toast("Set a mount point in Settings.")
            StreamConfig.password.isBlank() ->
                toast("Enter the source password in Settings.")
            !micGranted ->
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            else -> {
                BroadcastForegroundService.start(context)
                engine.startBroadcast()
                playCurrentTrack()
                AuthRepository.currentUser?.uid?.let { uid ->
                    scope.launch {
                        val tracks = Playlist.musicTracks.map {
                            mapOf("title" to it.title, "artist" to it.artist)
                        }
                        if (tracks.isNotEmpty()) {
                            StationManager.syncPlaylist(uid, tracks)
                        }
                    }
                }
            }
        }
    }

    // Keep the seek bar and Playlist state in sync with the engine's reported position.
    LaunchedEffect(enginePosition) {
        seek = enginePosition
        Playlist.currentPositionPercent = enginePosition
    }

    // Keep playing state in sync with Playlist state
    LaunchedEffect(isPlaying) {
        Playlist.isMusicPlaying = isPlaying
    }

    // Keep playing state in sync when tracks change
    LaunchedEffect(Playlist.currentIndex) {
        if (Playlist.currentIndex >= 0) isPlaying = true
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Keep live broadcast active when screen is locked or app is backgrounded
    LaunchedEffect(connState) {
        if (connState == 0 || connState == 1) {
            BroadcastForegroundService.start(context)
        } else if (connState == 2) {
            BroadcastForegroundService.stop(context)
        }
    }

    // Manage playback during live broadcast
    LaunchedEffect(connState) {
        if (connState == 1) {
            playCurrentTrack()
        }
    }

    LaunchedEffect(Unit) {
        RjScheduler.announcementEvents.collect { title ->
            toast("AI RJ auto-scheduled: $title")
        }
    }

    var user by remember { mutableStateOf(AuthRepository.currentUser) }
    DisposableEffect(Unit) {
        val listener = AuthRepository.addAuthStateListener { u ->
            user = u
        }
        onDispose { AuthRepository.removeAuthStateListener(listener) }
    }
    val stationUrl = remember(user?.uid) { user?.uid?.let { StationManager.stationUrl(it) } ?: "" }

    fun openPublicPage() {
        if (stationUrl.isNotBlank()) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(stationUrl))
                context.startActivity(intent)
            } catch (e: Exception) {
                toast("Could not open browser: ${e.message}")
            }
        } else {
            toast("Station URL is not ready yet.")
        }
    }

    var showPublicListeningPrompt by remember { mutableStateOf(false) }
    var publicListeningInput by remember { mutableStateOf("") }

    // Auto-register broadcaster's unique station (1 per user) on launch / sign-in
    LaunchedEffect(user?.uid) {
        val u = user ?: return@LaunchedEffect
        runCatching {
            // Load custom stream settings if previously saved in Cloud Firestore
            val userSettings = withTimeoutOrNull(5000) { UserStore.loadSettings(u.uid) }
            if (userSettings != null) {
                (userSettings["host"] as? String)?.takeIf { it.isNotBlank() }?.let { StreamConfig.host = it }
                (userSettings["port"] as? String)?.toIntOrNull()?.let { StreamConfig.port = it }
                (userSettings["mount"] as? String)?.takeIf { it.isNotBlank() }?.let { StreamConfig.mount = it }
                (userSettings["username"] as? String)?.takeIf { it.isNotBlank() }?.let { StreamConfig.user = it }
                (userSettings["password"] as? String)?.takeIf { it.isNotBlank() }?.let { StreamConfig.password = it }
                (userSettings["bitrate"] as? Number)?.let { StreamConfig.bitrate = intArrayOf(64, 128, 192, 320).getOrElse(it.toInt()) { 128 } }
                (userSettings["sampleRate"] as? Number)?.let { StreamConfig.sampleRate = if (it.toInt() == 1) 48000 else 44100 }
                (userSettings["channels"] as? Number)?.let { StreamConfig.channels = if (it.toInt() == 0) 1 else 2 }
                (userSettings["autoReconnect"] as? Boolean)?.let { StreamConfig.autoReconnect = it }
                (userSettings["rjVoice"] as? Number)?.let { RjConfig.voiceIndex = it.toInt() }
                (userSettings["rjSpeed"] as? Number)?.let { RjConfig.speedIndex = it.toInt() }
                (userSettings["autoTimeInterval"] as? Number)?.let { RjConfig.autoTimeIntervalIndex = it.toInt() }
            } else {
                // Keep Icecast details on Firestore if this broadcaster has not saved them yet
                UserStore.saveSettings(
                    u.uid,
                    mapOf(
                        "host" to StreamConfig.host,
                        "port" to StreamConfig.port.toString(),
                        "mount" to StreamConfig.mount,
                        "username" to StreamConfig.user,
                        "password" to StreamConfig.password,
                        "bitrate" to when (StreamConfig.bitrate) { 64 -> 0; 192 -> 2; 320 -> 3; else -> 1 },
                        "sampleRate" to (if (StreamConfig.sampleRate == 48000) 1 else 0),
                        "channels" to (if (StreamConfig.channels == 1) 0 else 1),
                        "autoReconnect" to StreamConfig.autoReconnect,
                        "publicListeningUrl" to StreamConfig.publicListeningUrl,
                        "rjVoice" to RjConfig.voiceIndex,
                        "rjSpeed" to RjConfig.speedIndex,
                        "autoTimeInterval" to RjConfig.autoTimeIntervalIndex,
                    )
                )
            }

            // Sync updated values to local SharedPreferences so they persist offline and across restarts
            val localState = SettingsStore.load(context)
            SettingsStore.save(
                context,
                localState.copy(
                    host = StreamConfig.host,
                    port = StreamConfig.port.toString(),
                    mount = StreamConfig.mount,
                    username = StreamConfig.user,
                    password = StreamConfig.password,
                    autoReconnect = StreamConfig.autoReconnect,
                    rjVoice = RjConfig.voiceIndex,
                    rjSpeed = RjConfig.speedIndex,
                    autoTimeInterval = RjConfig.autoTimeIntervalIndex,
                ),
            )

            val station = StationManager.getStation(u.uid)
            val stationDocUrl = (station?.get("publicListeningUrl") as? String)
                ?: (station?.get("streamUrl") as? String)
            val userSettingsUrl = (userSettings?.get("publicListeningUrl") as? String)
            val configUrl = StreamConfig.publicListeningUrl

            val existingPublicUrl = listOfNotNull(stationDocUrl, userSettingsUrl, configUrl)
                .firstOrNull { StationManager.isValidPublicStreamUrl(it) }

            if (existingPublicUrl.isNullOrBlank()) {
                publicListeningInput = ""
                showPublicListeningPrompt = true
            } else {
                StreamConfig.publicListeningUrl = existingPublicUrl
                val current = SettingsStore.load(context)
                if (current.publicListeningUrl != existingPublicUrl) {
                    SettingsStore.save(context, current.copy(publicListeningUrl = existingPublicUrl))
                }
            }

            val serverTypeNum = (userSettings?.get("serverType") as? Number)?.toInt() ?: 0
            val serverType = if (serverTypeNum == 1) "Shoutcast" else "Icecast 2"

            StationManager.registerOrUpdateStation(
                uid = u.uid,
                broadcasterName = u.displayName?.takeIf { it.isNotBlank() } ?: u.email?.substringBefore("@") ?: "Broadcaster",
                broadcasterEmail = u.email ?: "",
                host = StreamConfig.host,
                port = StreamConfig.port.toString(),
                serverType = serverType,
                mount = StreamConfig.mount,
                publicListeningUrl = existingPublicUrl ?: "",
                stationTitle = (station?.get("stationTitle") as? String) ?: (userSettings?.get("stationTitle") as? String),
                isLive = connState == 1,
                nowTrack = trackTitle,
                nowArtist = trackArtist,
                rjVoice = RjConfig.voiceIndex,
                rjSpeed = RjConfig.speedIndex,
            )
        }
    }

    // Sync live broadcast state and now playing metadata to broadcaster's public page
    LaunchedEffect(connState, trackTitle, trackArtist) {
        val u = user ?: return@LaunchedEffect
        runCatching {
            StationManager.updateLiveBroadcastState(
                uid = u.uid,
                isLive = connState == 1,
                nowTrack = trackTitle,
                nowArtist = trackArtist,
            )
        }
    }

    // Dialog prompting for Public Listening Stream URL if not set
    if (showPublicListeningPrompt) {
        user?.let { u ->
            AlertDialog(
                onDismissRequest = { showPublicListeningPrompt = false },
                icon = {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BrandBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_headphones), contentDescription = null, tint = BrandBlue, modifier = Modifier.size(22.dp))
                    }
                },
                title = {
                    Text("Set Public Listening URL", fontWeight = FontWeight.Bold)
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Listeners on your public web page need an audio stream URL to tune in live. Enter or confirm your public streaming URL:",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = publicListeningInput,
                            onValueChange = { publicListeningInput = it },
                            placeholder = { Text("e.g. http://stream.station.com:8000/live") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = BrandBlue,
                                unfocusedBorderColor = BorderLight,
                            ),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val trimmed = publicListeningInput.trim()
                            if (trimmed.isNotBlank()) {
                                scope.launch {
                                    StationManager.savePublicListeningUrl(u.uid, trimmed)
                                    UserStore.saveSettings(u.uid, mapOf("publicListeningUrl" to trimmed))
                                    StreamConfig.publicListeningUrl = trimmed
                                    val currentSettings = SettingsStore.load(context)
                                    SettingsStore.save(context, currentSettings.copy(publicListeningUrl = trimmed))
                                    toast("Public listening URL saved!")
                                }
                            }
                            showPublicListeningPrompt = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    ) {
                        Text("Save & Enable", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPublicListeningPrompt = false }) {
                        Text("Later", color = TextSecondary)
                    }
                },
            )
        }
    }

    if (showStopConfirm) {
        AlertDialog(
            onDismissRequest = { showStopConfirm = false },
            title = { Text("Stop broadcast?", fontWeight = FontWeight.SemiBold) },
            text = { Text("Your listeners will hear silence.") },
            confirmButton = {
                TextButton(onClick = {
                    showStopConfirm = false
                    engine.stopBroadcast()
                }) {
                    Text("Stop broadcast", color = DangerRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopConfirm = false }) {
                    Text("Keep broadcasting", color = TextSecondary)
                }
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- 1. Dashboard status card ---
        CardSurface {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                StatusPill(connState = connState)
                Spacer(Modifier.height(20.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Metric("RTT", if (connState == 1) "$latencyMs ms" else "—")
                    Metric("On air", if (connState != 2) formatElapsed((durationMs / 1000).toInt()) else "00:00:00")
                    Metric("Bitrate", "${StreamConfig.bitrate} kbps")
                }
                if (connState == 2 && connMessage.isNotBlank() &&
                    (connMessage.contains("error", true) || connMessage.contains("fail", true) ||
                        connMessage.contains("refused", true) || connMessage.contains("unreach", true))
                ) {
                    Spacer(Modifier.height(12.dp))
                    Text(connMessage, style = MaterialTheme.typography.bodySmall, color = DangerRed)
                }
                Spacer(Modifier.height(20.dp))

                if (connState == 2) {
                    Button(
                        onClick = { goLive() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                    ) {
                        Icon(painterResource(R.drawable.ic_broadcast), null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_go_live), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                } else if (connState == 0) {
                    // Connecting — disable the control while the broadcast is coming up
                    // so a second tap can't double-fire start or land on Stop.
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = BrandBlue, strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Connecting…", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = { showStopConfirm = true },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White),
                        ) {
                            Icon(painterResource(R.drawable.ic_stop), null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Stop", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { isPlaying = !isPlaying; engine.setMusicPlaying(isPlaying) },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderStrong),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceWhite, contentColor = TextPrimary),
                        ) {
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                                contentDescription = null,
                                tint = TextPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(if (isPlaying) "Pause" else "Resume", fontWeight = FontWeight.Medium)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = BorderLight)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Icecast", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    Text("128 kbps", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }
        }

        // --- AI Full Takeover Autonomous Banner ---
        if (isTakeoverEnabled) {
            CardSurface {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDCFCE7)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ai),
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "AI Takeover (Auto-Pilot)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFDCFCE7))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "AUTONOMOUS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D),
                                    fontSize = 9.sp,
                                )
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "News every ${takeoverNewsInterval}m · Time check every ${takeoverTimeInterval}m · Replay: ${if (takeoverAutoReplay) "ON" else "OFF"}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                        )
                    }
                }
            }
        }

        // --- Public Listener Station Page Card (Strictly 1 per broadcaster user) ---
        user?.let { u ->
            val stationUrl = remember(u.uid) { StationManager.stationUrl(u.uid) }
            CardSurface {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BrandBlue.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(painterResource(R.drawable.ic_broadcast), contentDescription = null, tint = BrandBlue, modifier = Modifier.size(22.dp))
                            }
                            Column {
                                Text(
                                    text = "Your Public Listener Page",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = "Role: Broadcaster · 1 Station",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (connState == 1) Color(0xFFDCFCE7) else SurfaceMuted)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (connState == 1) "LIVE ON WEB" else "OFFLINE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (connState == 1) Color(0xFF15803D) else TextMuted,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Your listeners tune in live, chat in real-time, and get AI RJ voice replies on your dedicated page:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SurfaceMuted)
                            .clickable { openPublicPage() }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stationUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = BrandBlue,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "↗ Open",
                            style = MaterialTheme.typography.labelSmall,
                            color = BrandBlue,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { openPublicPage() },
                            modifier = Modifier.weight(1.2f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                        ) {
                            Text("Open Page", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                val clip = ClipData.newPlainText("Public Station URL", stationUrl)
                                clipboard.setPrimaryClip(clip)
                                toast("Public listener link copied!")
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderStrong),
                        ) {
                            Text("Copy", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }

                        OutlinedButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, "Tune in to my live radio station!")
                                    putExtra(
                                        Intent.EXTRA_TEXT,
                                        "Tune in live to my radio station and chat with my AI Co-Host on Airwave Agents! $stationUrl",
                                    )
                                }
                                context.startActivity(Intent.createChooser(intent, "Share Public Station Link"))
                            },
                            modifier = Modifier.weight(1f).height(42.dp),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BorderStrong),
                        ) {
                            Text("Share", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // --- 2. Currently playing ---
        val isCurrentAi = Playlist.current?.isAi == true
        val currentAiBadge = if (isCurrentAi) {
            val curTitle = Playlist.current?.title.orEmpty()
            when {
                curTitle.contains("Flash News", ignoreCase = true) -> "FLASH NEWS ON-AIR"
                curTitle.contains("Time Check", ignoreCase = true) -> "TIME CHECK ON-AIR"
                curTitle.contains("Shoutout", ignoreCase = true) -> "LISTENER SHOUTOUT ON-AIR"
                else -> "AI CO-HOST ON-AIR"
            }
        } else null

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "CURRENTLY PLAYING",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextMuted,
                letterSpacing = 1.sp,
            )
            if (currentAiBadge != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(BrandBlue.copy(alpha = 0.12f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = currentAiBadge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlue,
                    )
                }
            }
        }
        CardSurface {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isCurrentAi) BrandBlue.copy(alpha = 0.1f) else SurfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (isCurrentAi) R.drawable.ic_ai else R.drawable.ic_tracks),
                        contentDescription = null,
                        tint = if (isCurrentAi) BrandBlue else TextMuted,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    val displayTitle = trackTitle.ifBlank { Playlist.current?.title ?: "No track selected" }
                    val displayArtist = trackArtist.ifBlank { Playlist.current?.artist ?: "—" }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (Playlist.current?.isAiMoved == true) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(BrandBlue.copy(alpha = 0.12f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = "AI MOVED",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = BrandBlue,
                                    fontSize = 10.sp,
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(displayTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary, maxLines = 1)
                    }
                    Text(displayArtist, style = MaterialTheme.typography.bodyMedium, color = TextSecondary, maxLines = 1)
                }
            }
            Spacer(Modifier.height(12.dp))
            Slider(
                value = seek,
                onValueChange = {
                    seek = it
                    engine.seek(it)
                    Playlist.currentPositionPercent = it
                },
                colors = SliderDefaults.colors(thumbColor = BrandBlue, activeTrackColor = BrandBlue, inactiveTrackColor = BorderLight),
            )
            val currentDurationMs = Playlist.current?.durationMs ?: 0L
            val elapsedMs = (seek * currentDurationMs).toLong()
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTrackMs(elapsedMs), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                Text(formatTrackMs(currentDurationMs), style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TransportIcon(R.drawable.ic_skip_previous, "Previous track") { Playlist.prev(engine, context) }
                Spacer(Modifier.width(24.dp))
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(BrandBlue)
                        .clickable {
                            val curIdx = if (Playlist.currentIndex in Playlist.items.indices) {
                                Playlist.currentIndex
                            } else if (Playlist.items.isNotEmpty()) {
                                0
                            } else {
                                -1
                            }
                            if ((!isPlaying || trackTitle.isBlank()) && curIdx >= 0) {
                                val currentItem = Playlist.items[curIdx]
                                val currentPos = if (trackTitle == currentItem.title) engine.musicPosition.value else 0f
                                Playlist.play(curIdx, engine, context, startPercent = currentPos)
                                isPlaying = true
                            } else {
                                isPlaying = !isPlaying
                                engine.setMusicPlaying(isPlaying)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(24.dp))
                TransportIcon(R.drawable.ic_skip_next, "Next track") { Playlist.next(engine, context) }
            }

            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        if (!timeChecking) {
                            val curDurMs = Playlist.current?.durationMs ?: 0L
                            val curElapsedMs = (seek * curDurMs).toLong()
                            val balanceMs = if (isPlaying && curDurMs > curElapsedMs) {
                                curDurMs - curElapsedMs
                            } else 0L
                            val balanceSec = balanceMs / 1000L
                            val minRequiredSec = TimeVoiceOver.getMinimumRequiredBalanceSec()

                            // Minimum seconds validation against voice creation progress time
                            if (isPlaying && curDurMs > 0 && balanceSec < minRequiredSec) {
                                val errorMsg = "Cannot create Time Check: Only ${balanceSec}s left in song. Minimum ${minRequiredSec}s required for AI voice creation."
                                timeCheckError = errorMsg
                                toast(errorMsg)
                                scope.launch {
                                    delay(5000L)
                                    if (timeCheckError == errorMsg) timeCheckError = null
                                }
                                return@OutlinedButton
                            }

                            timeChecking = true
                            timeCheckError = null
                            timeCheckProgressSec = 0
                            scope.launch {
                                val progressJob = launch {
                                    while (isActive) {
                                        delay(1000L)
                                        timeCheckProgressSec++
                                    }
                                }
                                try {
                                    val totalDelaySec = balanceSec + 20L
                                    val targetTimeMs = ((System.currentTimeMillis() + (totalDelaySec * 1000L) + 30_000L) / 60_000L) * 60_000L
                                    val timeLabel = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(targetTimeMs))

                                    val nextTrack = Playlist.items.getOrNull(Playlist.currentIndex + 1)
                                    val item = TimeVoiceOver.create(
                                        context = context,
                                        nowTrack = trackTitle,
                                        nowArtist = trackArtist,
                                        nextTrack = nextTrack?.title.orEmpty(),
                                        nextArtist = nextTrack?.artist.orEmpty(),
                                        targetTimeMs = targetTimeMs,
                                    )
                                    Playlist.enqueueNext(item)
                                    val statusMsg = if (balanceSec > 0) {
                                        "AI RJ Time check ($timeLabel) added to Up Next (song remaining: ${balanceSec}s + 20s relay)"
                                    } else {
                                        "AI RJ Time check ($timeLabel) added to Up Next (+20s relay delay)"
                                    }
                                    toast(statusMsg)
                                } catch (e: Exception) {
                                    val failMsg = "RJ Time check failed: ${e.message ?: "Voice service error"}"
                                    timeCheckError = failMsg
                                    toast(failMsg)
                                } finally {
                                    progressJob.cancel()
                                    timeChecking = false
                                }
                            }
                        }
                    },
                    enabled = !timeChecking && !newsGenerating,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (timeCheckError != null) DangerRed.copy(alpha = 0.8f) else BrandBlue.copy(alpha = 0.6f)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceWhite, contentColor = if (timeCheckError != null) DangerRed else BrandBlue),
                ) {
                    if (timeChecking) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = BrandBlue)
                        Spacer(Modifier.width(6.dp))
                        Text("Voicing (${timeCheckProgressSec}s)…", fontWeight = FontWeight.Medium, maxLines = 1)
                    } else {
                        Text("Time Check", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }

                Button(
                    onClick = { showFlashNewsDialog = true },
                    enabled = !timeChecking && !newsGenerating && !isBroadcastingNews,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                ) {
                    if (newsGenerating || isBroadcastingNews) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Voicing…", fontWeight = FontWeight.Medium, maxLines = 1)
                    } else {
                        Text("Flash News", fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }

            if (timeCheckError != null) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DangerRed.copy(alpha = 0.08f))
                        .border(1.dp, DangerRed.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_warning),
                        contentDescription = null,
                        tint = DangerRed,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = timeCheckError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "RJ: ${RjConfig.voiceDisplayName} · ${RjConfig.speedDisplayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                Text(
                    text = "Auto time check: ${RjConfig.intervalDisplayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (RjConfig.autoTimeIntervalIndex > 0) OnAirTeal else TextMuted,
                    fontWeight = if (RjConfig.autoTimeIntervalIndex > 0) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }

        // --- 3. Up next (real playlist queue) ---
        val startIdx = (Playlist.currentIndex + 1).coerceAtLeast(0)
        val allUpNext = Playlist.items.drop(startIdx).take(12)
        val filteredUpNext = remember(allUpNext.toList(), upNextFilter) {
            when (upNextFilter) {
                1 -> allUpNext.filter { !it.isAi }
                2 -> allUpNext.filter { it.isAi }
                else -> allUpNext
            }
        }
        if (allUpNext.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "UP NEXT",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted,
                    letterSpacing = 1.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (allUpNext.any { it.isAi }) {
                        Text(
                            text = "Clear AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = DangerRed,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable {
                                    Playlist.clearAiClips()
                                    toast("Cleared AI shoutouts from queue")
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    if (allUpNext.any { it.isAi } && allUpNext.any { !it.isAi }) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("All", "Tracks", "AI").forEachIndexed { idx, label ->
                                val isSelected = upNextFilter == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) BrandBlue else SurfaceMuted)
                                        .clickable { upNextFilter = idx }
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else TextSecondary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            CardSurface {
                if (filteredUpNext.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (upNextFilter == 1) "No music tracks queued up next" else "No AI clips queued up next",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted,
                        )
                    }
                } else {
                    filteredUpNext.forEachIndexed { offset, item ->
                        val realIdx = Playlist.items.indexOf(item)
                        QueueRow(
                            title = item.title,
                            artist = item.artist.ifBlank { "—" },
                            duration = formatTrackMs(item.durationMs),
                            isAi = item.isAi,
                            isAiMoved = item.isAiMoved,
                            canMoveUp = offset > 0,
                            canMoveDown = offset < filteredUpNext.lastIndex,
                            onMoveUp = { if (realIdx > 0) Playlist.move(realIdx, realIdx - 1) },
                            onMoveDown = { if (realIdx in 0 until Playlist.items.size - 1) Playlist.move(realIdx, realIdx + 1) },
                            onClick = { if (realIdx >= 0) queueDialogFor = realIdx },
                        )
                        if (offset < filteredUpNext.lastIndex) HorizontalDivider(color = BorderLight)
                    }
                }
            }
        }

        // --- 4. Live Listener Chats & On-Air Shoutouts ---
        Text(
            text = "LIVE LISTENER CHATS & ON-AIR SHOUTOUTS",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = TextMuted,
            letterSpacing = 1.sp,
        )
        CardSurface {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(BrandBlue.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_chat), contentDescription = null, tint = BrandBlue, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text(
                            text = "Listener Feed",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                        )
                        Text(
                            text = "Spoken by Gemini RJ at each song end",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (chatMessages.isNotEmpty()) BrandBlue.copy(alpha = 0.1f) else SurfaceMuted)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (chatMessages.isNotEmpty()) "${chatMessages.size} Messages" else "0 Messages",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (chatMessages.isNotEmpty()) BrandBlue else TextMuted,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (chatMessages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No live chats yet", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = TextMuted)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "When listeners chat on your public page, their message will appear here with location and air automatically after this song!",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            textAlign = TextAlign.Center,
                        )
                        if (stationUrl.isNotBlank()) {
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { openPublicPage() },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.5f)),
                            ) {
                                Text(
                                    text = "Open Listener Chat Page",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrandBlue,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            } else {
                chatMessages.take(10).forEachIndexed { idx, msg ->
                    ListenerChatRow(
                        msg = msg,
                        onBroadcastNow = { onBroadcastChatNow?.invoke(msg) },
                    )
                    if (idx < chatMessages.take(10).lastIndex) {
                        HorizontalDivider(color = BorderLight, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
        }
    }

    // Up Next item actions — Play now, Move Up, Move Down, Remove
    queueDialogFor?.let { idx ->
        val item = Playlist.items.getOrNull(idx)
        AlertDialog(
            onDismissRequest = { queueDialogFor = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item?.isAiMoved == true) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BrandBlue.copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "AI MOVED",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = BrandBlue,
                                fontSize = 10.sp,
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(item?.title ?: "Track", maxLines = 1)
                }
            },
            text = {
                Column {
                    Text(
                        text = "▶ Play now",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Playlist.play(idx, engine, context)
                                queueDialogFor = null
                            }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                    )
                    if (idx > 0) {
                        Text(
                            text = "↑ Move up in order",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Playlist.moveUp(idx)
                                    queueDialogFor = null
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                        )
                    }
                    if (idx < Playlist.items.size - 1) {
                        Text(
                            text = "↓ Move down in order",
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Playlist.moveDown(idx)
                                    queueDialogFor = null
                                }
                                .padding(vertical = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                        )
                    }
                    Text(
                        text = "✕ Remove from queue",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                Playlist.remove(idx)
                                queueDialogFor = null
                            }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = DangerRed,
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { queueDialogFor = null }) { Text("Close") }
            },
        )
    }

    if (showFlashNewsDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!newsGenerating) showFlashNewsDialog = false
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("AI Flash News", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Starts with breaking news flash music, delivers the news with live radio authority, and ends smoothly with an on-air handoff back to the music.",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                    )

                    OutlinedTextField(
                        value = newsHeadline,
                        onValueChange = { newsHeadline = it },
                        label = { Text("Headline") },
                        placeholder = { Text("e.g. Breaking news headline...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3,
                        enabled = !newsGenerating,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBlue,
                            unfocusedBorderColor = BorderLight,
                        ),
                    )

                    OutlinedTextField(
                        value = newsContent,
                        onValueChange = { newsContent = it },
                        label = { Text("News Story / Article") },
                        placeholder = { Text("Paste article text or bullet points here...") },
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                        enabled = !newsGenerating,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = BrandBlue,
                            unfocusedBorderColor = BorderLight,
                        ),
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = {
                                newsHeadline = FlashNews.DEFAULT_TEST_HEADLINE
                                newsContent = FlashNews.DEFAULT_TEST_CONTENT
                            },
                            enabled = !newsGenerating,
                        ) {
                            Text("Reset to Test Story", style = MaterialTheme.typography.labelSmall, color = BrandBlue)
                        }

                        TextButton(
                            onClick = {
                                newsHeadline = ""
                                newsContent = ""
                            },
                            enabled = !newsGenerating,
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall, color = TextMuted)
                        }
                    }

                    if (newsGenerating) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(SurfaceMuted, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = BrandBlue)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                "Gemini is writing and voicing news bulletin…",
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandBlue,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            if (!newsGenerating) {
                                if (newsHeadline.isBlank()) {
                                    toast("Please enter a headline")
                                    return@TextButton
                                }
                                if (onBroadcastNewsNow != null) {
                                    onBroadcastNewsNow(newsHeadline, newsContent)
                                    showFlashNewsDialog = false
                                } else {
                                    newsGenerating = true
                                    scope.launch(Dispatchers.Main) {
                                        withContext(NonCancellable) {
                                            try {
                                                val item = FlashNews.create(
                                                    context = context,
                                                    headline = newsHeadline,
                                                    content = newsContent,
                                                )
                                                val targetIdx = (Playlist.currentIndex + 1).coerceAtLeast(0)
                                                Playlist.insert(targetIdx, item)
                                                Playlist.play(targetIdx, engine, context)
                                                toast("Broadcasting Flash News now!")
                                                showFlashNewsDialog = false
                                            } catch (e: Exception) {
                                                toast("Flash News failed: ${e.message ?: "Voice service error"}")
                                            } finally {
                                                newsGenerating = false
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !newsGenerating && newsHeadline.isNotBlank(),
                    ) {
                        if (newsGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = BrandBlue)
                            Spacer(Modifier.width(6.dp))
                            Text("Voicing…", color = BrandBlue, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Play Now", color = OnAirTeal, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Button(
                        onClick = {
                            if (!newsGenerating) {
                                if (newsHeadline.isBlank()) {
                                    toast("Please enter a headline")
                                    return@Button
                                }
                                newsGenerating = true
                                scope.launch {
                                    try {
                                        val item = FlashNews.create(
                                            context = context,
                                            headline = newsHeadline,
                                            content = newsContent,
                                        )
                                        Playlist.enqueueNext(item)
                                        toast("Flash News enqueued to Up Next!")
                                        showFlashNewsDialog = false
                                    } catch (e: Exception) {
                                        toast("Flash News failed: ${e.message ?: "Voice service error"}")
                                    } finally {
                                        newsGenerating = false
                                    }
                                }
                            }
                        },
                        enabled = !newsGenerating && newsHeadline.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                    ) {
                        Text("Add to Up Next")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showFlashNewsDialog = false },
                    enabled = !newsGenerating,
                ) {
                    Text("Close")
                }
            },
        )
    }
}

private fun formatTrackMs(ms: Long): String {
    if (ms <= 0L) return "--:--"
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

@Composable
private fun QueueRow(
    title: String,
    artist: String,
    duration: String,
    isAi: Boolean = false,
    isAiMoved: Boolean = false,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onClick: () -> Unit,
) {
    val (aiBadgeLabel, aiBadgeColor) = when {
        isAi && title.contains("Flash News", ignoreCase = true) -> "NEWS" to DangerRed
        isAi && title.contains("Time Check", ignoreCase = true) -> "TIME" to BrandBlue
        isAi && (title.contains("Song Request", ignoreCase = true) || title.contains("Request", ignoreCase = true)) -> "REQUEST" to OnAirTeal
        isAi && title.contains("Shoutout", ignoreCase = true) -> "CHAT" to OnAirTeal
        isAi -> "AI" to BrandBlue
        isAiMoved -> "AI MOVED" to BrandBlue
        else -> "" to Color.Transparent
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f).clickable(onClick = onClick),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isAi || isAiMoved) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(aiBadgeColor.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = aiBadgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = aiBadgeColor,
                            fontSize = 10.sp,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = artist,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(duration, style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(Modifier.width(8.dp))

        // Reordering arrows: Move Up & Move Down
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canMoveUp, onClick = onMoveUp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_up),
                    contentDescription = "Move Up",
                    tint = if (canMoveUp) TextPrimary else TextMuted.copy(alpha = 0.25f),
                    modifier = Modifier.size(20.dp),
                )
            }
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(enabled = canMoveDown, onClick = onMoveDown),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_down),
                    contentDescription = "Move Down",
                    tint = if (canMoveDown) TextPrimary else TextMuted.copy(alpha = 0.25f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun ListenerChatRow(
    msg: StationChatMessage,
    onBroadcastNow: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Row with User Name, Location Pill, and Status Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Text(
                    text = msg.userName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val country = msg.userCountry.ifBlank { "Global" }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFF1F5F9))
                        .border(1.dp, Color(0xFFE2E8F0), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "$country",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondary,
                        maxLines = 1,
                    )
                }
            }

            // Status Badge
            val (badgeBg, badgeTextColor, badgeLabel) = when {
                msg.isPlayingOnAir -> Triple(Color(0xFFDCFCE7), Color(0xFF15803D), "ON AIR NOW")
                msg.isPlayedOnAir -> Triple(Color(0xFFF1F5F9), Color(0xFF64748B), "Spoken On-Air")
                msg.isQueuedOnAir -> Triple(Color(0xFFFEF3C7), Color(0xFFB45309), "Queued (Song End)")
                msg.status == "replied" -> Triple(Color(0xFFE0E7FF), BrandBlue, "Ready")
                msg.status == "processing" -> Triple(Color(0xFFF3E8FF), Color(0xFF7E22CE), "AI Voicing…")
                else -> Triple(Color(0xFFF1F5F9), TextMuted, "Received")
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(badgeBg)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeTextColor,
                )
            }
        }

        // Listener chat text in bubble
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(SurfaceMuted)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                text = "\"${msg.text}\"",
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
            )
        }

        // Spoken RJ reply quote if available
        if (msg.replyText.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(BrandBlue.copy(alpha = 0.06f))
                    .border(1.dp, BrandBlue.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        painter = painterResource(R.drawable.ic_mic),
                        contentDescription = null,
                        tint = BrandBlue,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "AI RJ: \"${msg.replyText}\"",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = BrandBlue,
                    )
                }
            }
        }

        // Action: Broadcast Now button if audio ready & not already playing/played
        if (msg.replyAudioBase64.isNotBlank() && !msg.isPlayingOnAir && !msg.isPlayedOnAir) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (msg.isQueuedOnAir) "Auto-plays when song ends" else "Audio reply synthesized",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
                TextButton(
                    onClick = onBroadcastNow,
                ) {
                    Text(
                        text = "Play On-Air Now",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = BrandBlue,
                    )
                }
            }
        }
    }
}

@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    // Delegates to the shared design-system card instead of re-implementing it.
    SectionCard { content() }
}

@Composable
private fun StatusPill(connState: Int) {
    val (color, label) = when {
        connState == 1 -> OnAirTeal to stringResource(R.string.home_status_live)
        connState == 0 -> WarnAmber to "CONNECTING…"
        else -> TextMuted to stringResource(R.string.home_status_offline)
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = color)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.bodySmall, color = TextMuted)
    }
}

@Composable
private fun TransportIcon(resId: Int, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(resId),
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun RowScope.NavItem(
    selected: Boolean,
    labelRes: Int,
    iconRes: Int,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.size(22.dp)) },
        label = { Text(stringResource(labelRes)) },
        colors = navItemColors(),
    )
}

@Composable
private fun navItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = Color.White,
    selectedTextColor = BrandBlue,
    indicatorColor = BrandBlue,
    unselectedIconColor = TextMuted,
    unselectedTextColor = TextMuted,
)

private fun formatElapsed(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true, showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    AirWaveTheme {
        HomeScreen()
    }
}
