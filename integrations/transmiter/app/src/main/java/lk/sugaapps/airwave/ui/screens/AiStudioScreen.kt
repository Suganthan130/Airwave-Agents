package lk.sugaapps.airwave.ui.screens

import android.content.Context
import android.media.MediaPlayer
import android.widget.Toast
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
import kotlinx.coroutines.launch
import lk.sugaapps.airwave.R
import lk.sugaapps.airwave.data.FlashNews
import lk.sugaapps.airwave.data.HiruNewsItem
import lk.sugaapps.airwave.data.HiruNewsManager
import lk.sugaapps.airwave.data.AuthRepository
import lk.sugaapps.airwave.data.RjConfig
import lk.sugaapps.airwave.data.StationManager
import lk.sugaapps.airwave.data.SettingsStore
import lk.sugaapps.airwave.data.TimeVoiceOver
import lk.sugaapps.airwave.ui.components.SegmentedPills
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
 * - Lists real-time Hiru Flash News fetched directly on Android.
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

    val newsList by HiruNewsManager.newsItems.collectAsState()
    val isRefreshing by HiruNewsManager.isRefreshing.collectAsState()
    val currentVoice by RjConfig.voiceIndexFlow.collectAsState()
    val currentSpeed by RjConfig.speedIndexFlow.collectAsState()

    var generatingId by remember { mutableStateOf<String?>(null) }
    var broadcastingId by remember { mutableStateOf<String?>(null) }
    var enqueuingId by remember { mutableStateOf<String?>(null) }
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var previewingId by remember { mutableStateOf<String?>(null) }
    var timeChecking by remember { mutableStateOf(false) }

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

    fun previewNews(item: HiruNewsItem) {
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

    fun playNewsNow(item: HiruNewsItem) {
        if (broadcastingId != null || generatingId != null) return
        stopPreview()
        broadcastingId = item.id
        HiruNewsManager.markSeen(context, item.id)
        onBroadcastNewsNow(item.title, item.description.ifBlank { item.title })
    }

    fun queueNewsNext(item: HiruNewsItem) {
        if (enqueuingId != null || generatingId != null) return
        enqueuingId = item.id
        HiruNewsManager.markSeen(context, item.id)
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
                                timeChecking = true
                                scope.launch {
                                    try {
                                        val targetTimeMs = Playlist.calculateProjectedTimeCheckMs()
                                        val timeLabel = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(targetTimeMs))
                                        val balanceSec = Playlist.getSongBalanceTimeMs() / 1000L
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
                                        toast("Time check failed: ${e.message ?: "Service error"}")
                                    } finally {
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
                        border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SurfaceWhite,
                            contentColor = BrandBlue,
                        ),
                    ) {
                        if (timeChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = BrandBlue,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Generating RJ Time Check…", fontWeight = FontWeight.Medium)
                        } else {
                            Text("🎙️ Announce Time Check (AI RJ)", fontWeight = FontWeight.SemiBold)
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
                            text = "Hiru Watcher: Active (60s check)",
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

        // --- 2. Live Hiru Flash News Header ---
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
                        text = "HIRU FLASH NEWS TICKER",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted,
                        letterSpacing = 1.sp,
                    )
                }

                TextButton(
                    onClick = {
                        scope.launch {
                            HiruNewsManager.refresh(context, notifyNew = false)
                            toast("Hiru News ticker refreshed")
                        }
                    },
                    enabled = !isRefreshing,
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = BrandBlue)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Refresh Ticker", style = MaterialTheme.typography.labelSmall, color = BrandBlue)
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
                            Text("Connecting directly to Hiru News ticker…", color = TextSecondary)
                        } else {
                            Text("No flash news loaded yet.", color = TextSecondary)
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { scope.launch { HiruNewsManager.refresh(context) } },
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                            ) {
                                Text("Fetch Hiru Flash News")
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
    item: HiruNewsItem,
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
                                Text("🚨 NEW", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            text = "Hiru #${item.id}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted,
                        )
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
                            text = if (isPreviewing) "⏹ Stop" else "🎧 Listen",
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
                        Text("▶ Play Now", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardSurface(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(3.dp, shape, clip = false, ambientColor = Color(0x120F172A), spotColor = Color(0x120F172A))
            .clip(shape)
            .background(SurfaceWhite)
            .border(1.dp, BorderLight, shape)
            .padding(16.dp),
    ) { content() }
}

