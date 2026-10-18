package lk.sugaapps.airwave.ui.screens

import android.content.Context
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import lk.sugaapps.airwave.R
import lk.sugaapps.airwave.data.FlashNews
import lk.sugaapps.airwave.data.NewsItem
import lk.sugaapps.airwave.data.NewsManager
import lk.sugaapps.airwave.data.AuthRepository
import lk.sugaapps.airwave.data.RjConfig
import lk.sugaapps.airwave.data.StationManager
import lk.sugaapps.airwave.data.SettingsStore
import lk.sugaapps.airwave.data.RjScheduler
import lk.sugaapps.airwave.data.TimeVoiceOver
import lk.sugaapps.airwave.ui.components.SectionCard
import lk.sugaapps.airwave.ui.components.SegmentedPills
import lk.sugaapps.airwave.ui.components.SwitchRow
import androidx.compose.foundation.border
import androidx.compose.ui.draw.shadow
import lk.sugaapps.airwave.engine.Playlist
import lk.sugaapps.airwave.ui.theme.BorderLight
import lk.sugaapps.airwave.ui.theme.BorderStrong
import lk.sugaapps.airwave.ui.theme.BrandBlue
import lk.sugaapps.airwave.ui.theme.DangerRed
import lk.sugaapps.airwave.ui.theme.OnAirTeal
import lk.sugaapps.airwave.ui.theme.SurfaceMuted
import lk.sugaapps.airwave.ui.theme.SurfaceWhite
import lk.sugaapps.airwave.ui.theme.TextMuted
import lk.sugaapps.airwave.ui.theme.TextPrimary
import lk.sugaapps.airwave.ui.theme.TextSecondary

/**
 * AI Co-Host & News Studio screen:
 * - Lists real-time curated news synchronized from Cloud Firestore.
 * - Allows listening / previewing the generated flash news audio.
 * - Broadcast Now with music pause & automatic resume on end.
 * - AI Host voice and speed controls.
 */
@Composable
fun AiStudioScreen(
    modifier: Modifier = Modifier,
    onBroadcastNewsNow: (headline: String, content: String) -> Unit,
    onEnqueueNews: (headline: String, content: String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    val newsList by NewsManager.newsItems.collectAsState()
    val isRefreshing by NewsManager.isRefreshing.collectAsState()
    val currentVoice by RjConfig.voiceIndexFlow.collectAsState()
    val currentSpeed by RjConfig.speedIndexFlow.collectAsState()
    val isTakeoverEnabled by RjConfig.isTakeoverEnabledFlow.collectAsState()
    val takeoverNewsInterval by RjConfig.takeoverNewsIntervalFlow.collectAsState()
    val takeoverTimeInterval by RjConfig.takeoverTimeIntervalFlow.collectAsState()
    val takeoverAutoReplay by RjConfig.autoReplayFlow.collectAsState()
    val takeoverNewsMode by RjConfig.takeoverNewsModeFlow.collectAsState()

    var generatingId by remember { mutableStateOf<String?>(null) }
    var broadcastingId by remember { mutableStateOf<String?>(null) }
    var enqueuingId by remember { mutableStateOf<String?>(null) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewingId by remember { mutableStateOf<String?>(null) }
    var timeChecking by remember { mutableStateOf(false) }
    var timeCheckProgressSec by remember { mutableIntStateOf(0) }
    var timeCheckError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            previewPlayer?.release()
            previewPlayer = null
        }
    }

    fun stopPreview() {
        previewPlayer?.stop()
        previewPlayer?.release()
        previewPlayer = null
        previewingId = null
    }

    fun previewNews(item: NewsItem) {
        if (previewingId == item.id) {
            stopPreview()
            return
        }
        stopPreview()
        generatingId = item.id
        scope.launch {
            try {
                val playlistItem = FlashNews.create(
                    context = context,
                    headline = item.title,
                    content = item.description.ifBlank { item.title },
                )
                val mp = MediaPlayer().apply {
                    setDataSource(context, playlistItem.uri)
                    prepare()
                    setOnCompletionListener {
                        stopPreview()
                    }
                    start()
                }
                previewPlayer = mp
                previewingId = item.id
                toast("Listening to AI preview...")
            } catch (e: Exception) {
                toast("Preview failed: ${e.message ?: "Voice service error"}")
            } finally {
                generatingId = null
            }
        }
    }

    fun playNewsNow(item: NewsItem) {
        if (broadcastingId != null || generatingId != null) return
        stopPreview()
        broadcastingId = item.id
        NewsManager.markBroadcast(context, item.id)
        onBroadcastNewsNow(item.title, item.description.ifBlank { item.title })
        // Broadcast is fire-and-forget; clear the busy state after a bounded floor
        // so the button always recovers (mirrors queueNewsNext's enqueuingId reset).
        scope.launch {
            kotlinx.coroutines.delay(4000)
            if (broadcastingId == item.id) broadcastingId = null
        }
    }

    fun queueNewsNext(item: NewsItem) {
        if (enqueuingId != null || generatingId != null) return
        enqueuingId = item.id
        NewsManager.markBroadcast(context, item.id)
        onEnqueueNews(item.title, item.description.ifBlank { item.title })
        scope.launch {
            kotlinx.coroutines.delay(1200)
            enqueuingId = null
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // --- 0. AI Full Takeover (Auto-Pilot) Card ---
        item {
            CardSurface {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header: Icon + Title + Status Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(if (isTakeoverEnabled) Color(0xFFDCFCE7) else BrandBlue.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_ai),
                                    contentDescription = null,
                                    tint = if (isTakeoverEnabled) Color(0xFF15803D) else BrandBlue,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AI Full Takeover (Auto-Pilot)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = if (isTakeoverEnabled) "Autonomous on-air broadcast active" else "Automate news, time checks & loop",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isTakeoverEnabled) Color(0xFF15803D) else TextSecondary,
                                    maxLines = 1,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isTakeoverEnabled) Color(0xFFDCFCE7) else SurfaceMuted)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = if (isTakeoverEnabled) "ACTIVE" else "MANUAL",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTakeoverEnabled) Color(0xFF15803D) else TextMuted,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    SwitchRow(
                        title = "Enable AI Full Takeover",
                        subtitle = "AI autonomously voices news, announces RJ time, and loops playlist",
                        checked = isTakeoverEnabled,
                        onCheckedChange = { enabled ->
                            RjConfig.isTakeoverEnabled = enabled
                            val current = SettingsStore.load(context)
                            SettingsStore.save(context, current.copy(takeoverEnabled = enabled))
                            RjScheduler.resetTakeoverTimers()
                            if (enabled) {
                                toast("AI Full Takeover enabled: 24/7 autonomous broadcast")
                            } else {
                                toast("AI Full Takeover disabled")
                            }
                        }
                    )

                    AnimatedVisibility(
                        visible = isTakeoverEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut(),
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = BorderLight)
                            Spacer(Modifier.height(10.dp))

                            // News Bulletin Interval
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "News bulletin interval",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = "Every ${takeoverNewsInterval} min",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandBlue,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            IntervalPills(
                                options = RjConfig.NEWS_INTERVAL_OPTIONS,
                                selectedMinutes = takeoverNewsInterval,
                                onSelectMinutes = { mins ->
                                    RjConfig.takeoverNewsIntervalMinutes = mins
                                    val current = SettingsStore.load(context)
                                    SettingsStore.save(context, current.copy(takeoverNewsInterval = mins))
                                    RjScheduler.resetTakeoverTimers()
                                    toast("News interval set to $mins min")
                                }
                            )

                            Spacer(Modifier.height(12.dp))

                            // Time Check Announcement Interval
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "RJ time check interval",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = "Every ${takeoverTimeInterval} min",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = BrandBlue,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            IntervalPills(
                                options = RjConfig.TIME_INTERVAL_OPTIONS,
                                selectedMinutes = takeoverTimeInterval,
                                onSelectMinutes = { mins ->
                                    RjConfig.takeoverTimeIntervalMinutes = mins
                                    val current = SettingsStore.load(context)
                                    SettingsStore.save(context, current.copy(takeoverTimeInterval = mins))
                                    RjScheduler.resetTakeoverTimers()
                                    toast("Time check interval set to $mins min")
                                }
                            )

                            Spacer(Modifier.height(12.dp))

                            // News Delivery Style
                            Text(
                                text = "News delivery style",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary,
                            )
                            Spacer(Modifier.height(6.dp))
                            SegmentedPills(
                                options = listOf("Play Now (Interrupt)", "Add to Up Next"),
                                selectedIndex = takeoverNewsMode,
                            ) { mode ->
                                RjConfig.takeoverNewsMode = mode
                                val current = SettingsStore.load(context)
                                SettingsStore.save(context, current.copy(takeoverNewsMode = mode))
                            }

                            Spacer(Modifier.height(10.dp))
                            HorizontalDivider(color = BorderLight)
                            Spacer(Modifier.height(10.dp))

                            // Auto-Replay Playlist Switch
                            SwitchRow(
                                title = "Continuous Playlist Replay",
                                subtitle = "Auto-repeat playlist from start when it ends (prevents dead air)",
                                checked = takeoverAutoReplay,
                                onCheckedChange = { replay ->
                                    RjConfig.autoReplay = replay
                                    val current = SettingsStore.load(context)
                                    SettingsStore.save(context, current.copy(takeoverAutoReplay = replay))
                                    toast(if (replay) "Playlist auto-replay enabled" else "Auto-replay disabled")
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- 1. AI Co-Host Status Card ---
        item {
            CardSurface {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header: Icon + Title + Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(BrandBlue.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_ai),
                                    contentDescription = null,
                                    tint = BrandBlue,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "AI Radio Co-Host",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                )
                                Text(
                                    text = "Gemini 2.5 Flash · ${RjConfig.voiceDisplayName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary,
                                    maxLines = 1,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(SurfaceMuted)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "${newsList.size} stories",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    Text("Voice personality", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    SegmentedPills(listOf("Chirp-HD Host", "Studio Pro", "Tamil Host"), currentVoice) {
                        RjConfig.voiceIndex = it
                        val current = SettingsStore.load(context)
                        SettingsStore.save(context, current.copy(rjVoice = it))
                        val uid = AuthRepository.currentUser?.uid
                        if (uid != null) {
                            scope.launch {
                                runCatching {
                                    StationManager.updateAiVoiceSettings(uid, it, RjConfig.speedIndex)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("Speech speed", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(Modifier.height(6.dp))
                    SegmentedPills(listOf("Relaxed (0.9x)", "Normal (1.0x)", "Fast (1.1x)"), currentSpeed) {
                        RjConfig.speedIndex = it
                        val current = SettingsStore.load(context)
                        SettingsStore.save(context, current.copy(rjSpeed = it))
                        val uid = AuthRepository.currentUser?.uid
                        if (uid != null) {
                            scope.launch {
                                runCatching {
                                    StationManager.updateAiVoiceSettings(uid, RjConfig.voiceIndex, it)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Dedicated Time Check button: full width, clean padding, never collapses
                    OutlinedButton(
                        onClick = {
                            if (!timeChecking) {
                                val curDurMs = Playlist.current?.durationMs ?: 0L
                                val balanceSec = Playlist.getSongBalanceTimeMs() / 1000L
                                val minRequiredSec = TimeVoiceOver.getMinimumRequiredBalanceSec()

                                // Minimum seconds validation against voice creation progress time
                                if (Playlist.isMusicPlaying && curDurMs > 0 && balanceSec < minRequiredSec) {
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
                                        val targetTimeMs = Playlist.calculateProjectedTimeCheckMs()
                                        val timeLabel = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(targetTimeMs))
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
                                        val statusMsg = if (balanceSec > 0) {
                                            "AI Time Check ($timeLabel) added to Up Next (song: ${balanceSec}s + 20s relay)"
                                        } else {
                                            "AI Time Check ($timeLabel) added to Up Next (+20s relay delay)"
                                        }
                                        toast(statusMsg)
                                    } catch (e: Exception) {
                                        val failMsg = "Time check failed: ${e.message ?: "Service error"}"
                                        timeCheckError = failMsg
                                        toast(failMsg)
                                    } finally {
                                        progressJob.cancel()
                                        timeChecking = false
                                    }
                                }
                            }
                        },
                        enabled = !timeChecking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (timeCheckError != null) DangerRed.copy(alpha = 0.8f) else BrandBlue.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SurfaceWhite,
                            contentColor = if (timeCheckError != null) DangerRed else BrandBlue,
                        ),
                    ) {
                        if (timeChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = BrandBlue,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Generating RJ Time Check (${timeCheckProgressSec}s)…", fontWeight = FontWeight.Medium)
                        } else {
                            Text("Announce Time Check (AI RJ)", fontWeight = FontWeight.SemiBold)
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

                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = BorderLight)
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "News Feed: Live · Hiru News",
                            style = MaterialTheme.typography.labelSmall,
                            color = OnAirTeal,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = "Speed: ${RjConfig.speedDisplayName}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
                    }
                }
            }
        }

        // --- 2. Live News Feed Header ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(DangerRed)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "AIRWAVE BREAKING NEWS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp,
                    )
                }

                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                NewsManager.refresh(context)
                                toast("News feed refreshed from Hiru News")
                            } catch (e: Exception) {
                                toast("Refresh failed: ${e.message ?: "Network error"}")
                            }
                        }
                    },
                    enabled = !isRefreshing,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = BrandBlue)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Refresh Feed", style = MaterialTheme.typography.labelSmall, color = BrandBlue)
                }
            }
        }

        // --- 3. Empty State or List of News Items ---
        if (newsList.isEmpty()) {
            item {
                CardSurface {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = BrandBlue)
                            Spacer(Modifier.height(12.dp))
                            Text("Fetching the latest Hiru News…", color = TextSecondary)
                        } else {
                            Text("No news loaded yet.", color = TextSecondary)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { scope.launch { NewsManager.refresh(context) } },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            ) {
                                Text("Fetch News Feed")
                            }
                        }
                    }
                }
            }
        } else {
            items(newsList, key = { it.id }) { item ->
                NewsItemCard(
                    item = item,
                    isGenerating = generatingId == item.id,
                    isPreviewing = previewingId == item.id,
                    isBroadcasting = broadcastingId == item.id,
                    isEnqueuing = enqueuingId == item.id,
                    onPreview = { previewNews(item) },
                    onBroadcastNow = { playNewsNow(item) },
                    onEnqueue = { queueNewsNext(item) },
                )
            }
        }

        item {
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun NewsItemCard(
    item: NewsItem,
    isGenerating: Boolean,
    isPreviewing: Boolean,
    isBroadcasting: Boolean,
    isEnqueuing: Boolean,
    onPreview: () -> Unit,
    onBroadcastNow: () -> Unit,
    onEnqueue: () -> Unit,
) {
    CardSurface {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.isNew) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(DangerRed)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("NEW", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(BrandBlue.copy(alpha = 0.1f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = item.category.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = BrandBlue,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        if (item.published.isNotBlank()) {
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = item.published,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                    )
                }
            }

            if (item.description.isNotBlank() && item.description != item.title) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = item.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 3,
                )
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = BorderLight)
            Spacer(Modifier.height(8.dp))

            // Action buttons: Listen / Preview, Add to Up Next, Broadcast Now
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val isBusy = isGenerating || isBroadcasting || isEnqueuing

                // 1. Listen / Preview
                OutlinedButton(
                    onClick = onPreview,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, if (isPreviewing) BrandBlue else BorderLight),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isPreviewing) SurfaceMuted else SurfaceWhite,
                        contentColor = if (isPreviewing) BrandBlue else TextPrimary,
                    ),
                ) {
                    if (isGenerating && isPreviewing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = BrandBlue)
                    } else {
                        Text(
                            text = if (isPreviewing) "Stop" else "Listen",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }

                // 2. Add to Up Next
                OutlinedButton(
                    onClick = onEnqueue,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f).height(40.dp),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, BorderStrong),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceWhite, contentColor = TextPrimary),
                ) {
                    if (isEnqueuing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = BrandBlue)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isEnqueuing) "Adding…" else "+ Up Next",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }

                // 3. Play Now (Interrupt & Resume)
                Button(
                    onClick = onBroadcastNow,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1.3f).height(40.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                ) {
                    if (isBroadcasting) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("Voicing…", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    } else {
                        Text("Play Now", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    // Delegates to the shared SectionCard so card styling lives in one place.
    SectionCard { content() }
}

@Composable
private fun IntervalPills(
    options: List<Int>,
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(SurfaceMuted)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { minutes ->
            val selected = minutes == selectedMinutes
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (selected) BrandBlue else Color.Transparent)
                    .clickable { onSelectMinutes(minutes) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${minutes}m",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) Color.White else TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

