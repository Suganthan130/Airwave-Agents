package lk.sugaapps.airwave.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import lk.sugaapps.airwave.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.text.style.TextOverflow
import lk.sugaapps.airwave.data.AuthRepository
import lk.sugaapps.airwave.data.SettingsStore
import lk.sugaapps.airwave.data.StationManager
import lk.sugaapps.airwave.data.UserStore
import lk.sugaapps.airwave.engine.StreamConfig
import lk.sugaapps.airwave.ui.components.LabeledTextField
import lk.sugaapps.airwave.ui.components.SectionCard
import lk.sugaapps.airwave.ui.components.SegmentedPills
import lk.sugaapps.airwave.ui.components.SwitchRow
import lk.sugaapps.airwave.ui.theme.BorderStrong
import lk.sugaapps.airwave.ui.theme.BrandBlue
import lk.sugaapps.airwave.ui.theme.DangerRed
import lk.sugaapps.airwave.ui.theme.SurfaceMuted
import lk.sugaapps.airwave.ui.theme.TextMuted
import lk.sugaapps.airwave.ui.theme.TextPrimary
import lk.sugaapps.airwave.ui.theme.TextSecondary

/**
 * Settings: real Google account + stream config persisted to Firestore.
 * Stream config values are plain inputs — the real broadcast engine stays private.
 */
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val user = AuthRepository.currentUser
    val userName = user?.displayName?.takeIf { it.isNotBlank() }
        ?: user?.email?.substringBefore("@") ?: "Signed-in user"
    val userEmail = user?.email ?: "—"

    val initialSettings = remember { SettingsStore.load(context) }
    var serverType by rememberSaveable { mutableIntStateOf(initialSettings.serverType) }
    var host by rememberSaveable { mutableStateOf(initialSettings.host) }
    var port by rememberSaveable { mutableStateOf(initialSettings.port) }
    var mount by rememberSaveable { mutableStateOf(initialSettings.mount) }
    var username by rememberSaveable { mutableStateOf(initialSettings.username) }
    var password by rememberSaveable { mutableStateOf(initialSettings.password) }
    var bitrate by rememberSaveable { mutableIntStateOf(initialSettings.bitrate) }
    var sampleRate by rememberSaveable { mutableIntStateOf(initialSettings.sampleRate) }
    var channels by rememberSaveable { mutableIntStateOf(initialSettings.channels) }
    var keepScreenOn by rememberSaveable { mutableStateOf(initialSettings.keepScreenOn) }
    var autoReconnect by rememberSaveable { mutableStateOf(initialSettings.autoReconnect) }
    var lowLatency by rememberSaveable { mutableStateOf(initialSettings.lowLatency) }
    var rjVoice by rememberSaveable { mutableIntStateOf(initialSettings.rjVoice) }
    var rjSpeed by rememberSaveable { mutableIntStateOf(initialSettings.rjSpeed) }
    var autoTimeInterval by rememberSaveable { mutableIntStateOf(initialSettings.autoTimeInterval) }
    var stationTitle by rememberSaveable { mutableStateOf(initialSettings.stationTitle.ifBlank { "$userName's Live Radio" }) }
    var publicListeningUrl by rememberSaveable { mutableStateOf(initialSettings.publicListeningUrl) }
    var saving by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    val currentRjVoice by lk.sugaapps.airwave.data.RjConfig.voiceIndexFlow.collectAsState()
    val currentRjSpeed by lk.sugaapps.airwave.data.RjConfig.speedIndexFlow.collectAsState()
    val currentRjInterval by lk.sugaapps.airwave.data.RjConfig.autoTimeIntervalFlow.collectAsState()

    LaunchedEffect(currentRjVoice, currentRjSpeed, currentRjInterval) {
        if (rjVoice != currentRjVoice) rjVoice = currentRjVoice
        if (rjSpeed != currentRjSpeed) rjSpeed = currentRjSpeed
        if (autoTimeInterval != currentRjInterval) autoTimeInterval = currentRjInterval
    }

    // Persist all settings immediately to SharedPreferences on every change so they never reset on navigation or reopen
    LaunchedEffect(
        serverType, host, port, mount, username, password,
        bitrate, sampleRate, channels, keepScreenOn, autoReconnect,
        lowLatency, rjVoice, rjSpeed, autoTimeInterval, stationTitle, publicListeningUrl
    ) {
        val state = SettingsStore.State(
            serverType = serverType,
            host = host,
            port = port,
            mount = mount,
            username = username,
            password = password,
            bitrate = bitrate,
            sampleRate = sampleRate,
            channels = channels,
            keepScreenOn = keepScreenOn,
            autoReconnect = autoReconnect,
            lowLatency = lowLatency,
            rjVoice = rjVoice,
            rjSpeed = rjSpeed,
            autoTimeInterval = autoTimeInterval,
            stationTitle = stationTitle,
            publicListeningUrl = publicListeningUrl,
            takeoverEnabled = lk.sugaapps.airwave.data.RjConfig.isTakeoverEnabled,
            takeoverNewsInterval = lk.sugaapps.airwave.data.RjConfig.takeoverNewsIntervalMinutes,
            takeoverTimeInterval = lk.sugaapps.airwave.data.RjConfig.takeoverTimeIntervalMinutes,
            takeoverAutoReplay = lk.sugaapps.airwave.data.RjConfig.autoReplay,
            takeoverNewsMode = lk.sugaapps.airwave.data.RjConfig.takeoverNewsMode,
        )
        SettingsStore.save(context, state)
        SettingsStore.applyToEngine(state)
    }

    // Sync remote Firestore settings if available (keeps SharedPreferences up to date)
    LaunchedEffect(user?.uid) {
        val uid = user?.uid ?: return@LaunchedEffect
        runCatching { withTimeoutOrNull(8000) { UserStore.loadSettings(uid) } }.getOrNull()?.let { s ->
            (s["serverType"] as? Number)?.let { serverType = it.toInt() }
            (s["host"] as? String)?.takeIf { it.isNotBlank() }?.let { host = it }
            (s["port"] as? String)?.takeIf { it.isNotBlank() }?.let { port = it }
            (s["mount"] as? String)?.takeIf { it.isNotBlank() }?.let { mount = it }
            (s["username"] as? String)?.takeIf { it.isNotBlank() }?.let { username = it }
            (s["password"] as? String)?.takeIf { it.isNotBlank() }?.let { password = it }
            (s["bitrate"] as? Number)?.let { bitrate = it.toInt() }
            (s["sampleRate"] as? Number)?.let { sampleRate = it.toInt() }
            (s["channels"] as? Number)?.let { channels = it.toInt() }
            (s["keepScreenOn"] as? Boolean)?.let { keepScreenOn = it }
            (s["autoReconnect"] as? Boolean)?.let { autoReconnect = it }
            (s["lowLatency"] as? Boolean)?.let { lowLatency = it }
            (s["stationTitle"] as? String)?.takeIf { it.isNotBlank() }?.let { stationTitle = it }
            (s["publicListeningUrl"] as? String)?.takeIf { StationManager.isValidPublicStreamUrl(it) }?.let { publicListeningUrl = it }
            (s["rjVoice"] as? Number)?.let { rjVoice = it.toInt() }
            (s["rjSpeed"] as? Number)?.let { rjSpeed = it.toInt() }
            (s["autoTimeInterval"] as? Number)?.let { autoTimeInterval = it.toInt() }
        }
        // Also check StationManager document for publicListeningUrl if empty
        runCatching {
            val station = StationManager.getStation(uid)
            val stream = (station?.get("publicListeningUrl") as? String)
                ?: (station?.get("streamUrl") as? String)
            if (!stream.isNullOrBlank() && StationManager.isValidPublicStreamUrl(stream) && publicListeningUrl.isBlank()) {
                publicListeningUrl = stream
            }
            val title = station?.get("stationTitle") as? String
            if (!title.isNullOrBlank() && (stationTitle.isBlank() || stationTitle.endsWith("'s Live Radio"))) {
                stationTitle = title
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Account
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(48.dp).clip(CircleShape).background(BrandBlue),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = userName.firstOrNull()?.uppercase() ?: "A",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(userName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                    Text(userEmail, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { showSignOutDialog = true }, shape = RoundedCornerShape(8.dp)) {
                Text("Sign out", color = DangerRed, fontWeight = FontWeight.Medium)
            }
        }

        // Public Listener Station (Strictly 1 per broadcaster user)
        user?.let { u ->
            val stationUrl = remember(u.uid) { StationManager.stationUrl(u.uid) }
            SectionCard(title = "Public listener station (1 per user)") {
                Text(
                    text = "Every broadcaster user has strictly one public listener station page where listeners tune in, participate in chat, and get AI RJ voice replies.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                LabeledTextField("Station title", stationTitle, { stationTitle = it }, placeholder = "e.g. Jason's Live Radio")
                Spacer(Modifier.height(12.dp))
                val candidateStream = if (host.isNotBlank() && port.isNotBlank()) "http://$host:$port$mount" else ""
                LabeledTextField(
                    label = "Public listening stream URL",
                    value = publicListeningUrl,
                    onValueChange = { publicListeningUrl = it; urlError = null },
                    placeholder = if (candidateStream.isNotBlank()) candidateStream else "http://stream.station.com:80/live",
                )
                if (urlError != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = urlError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = DangerRed,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Direct audio stream URL that web listeners tune into. If blank, defaults to http://$host:$port$mount.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
                Spacer(Modifier.height(12.dp))

                Text("Public station link", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceMuted)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = stationUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = BrandBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Station URL", stationUrl)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Public listener link copied!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, BorderStrong),
                    ) {
                        Icon(painterResource(R.drawable.ic_copy), contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Copy Link", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }

                    Button(
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
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue),
                    ) {
                        Icon(painterResource(R.drawable.ic_share), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Share Link", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }
                }
            }
        }

        // Server
        SectionCard(title = "Stream server") {
            Text("Server type", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            SegmentedPills(listOf("Icecast", "Shoutcast"), serverType) { serverType = it }
            Spacer(Modifier.height(16.dp))
            LabeledTextField("Host", host, { host = it }, placeholder = "stream.station.com")
            Spacer(Modifier.height(12.dp))
            LabeledTextField("Port", port, { port = it }, placeholder = "8000", keyboardType = KeyboardType.Number)
        }

        // Authentication
        SectionCard(title = "Authentication") {
            LabeledTextField("Mount point", mount, { mount = it }, placeholder = "/live")
            Spacer(Modifier.height(12.dp))
            LabeledTextField("Username", username, { username = it })
            Spacer(Modifier.height(12.dp))
            LabeledTextField("Password", password, { password = it }, isPassword = true)
        }

        // Audio quality
        SectionCard(title = "Audio quality") {
            Text("Bitrate (kbps)", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            SegmentedPills(listOf("64", "128", "192", "320"), bitrate) { bitrate = it }
            Spacer(Modifier.height(16.dp))
            Text("Sample rate (kHz)", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            SegmentedPills(listOf("44.1", "48"), sampleRate) { sampleRate = it }
            Spacer(Modifier.height(16.dp))
            Text("Channels", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            SegmentedPills(listOf("Mono", "Stereo"), channels) { channels = it }
        }

        // Preferences
        SectionCard(title = "Preferences") {
            SwitchRow("Keep screen on", keepScreenOn, { keepScreenOn = it }, subtitle = "Prevent sleep while broadcasting")
            SwitchRow("Auto-reconnect", autoReconnect, { autoReconnect = it }, subtitle = "Rejoin the stream after a drop")
            SwitchRow("Low-latency monitoring", lowLatency, { lowLatency = it })
        }

        // AI Radio Jockey (RJ) & Time Announcements
        SectionCard(title = "AI Radio Jockey (RJ)") {
            Text("Voice personality", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            SegmentedPills(listOf("Chirp-HD Host", "Studio Pro", "Tamil Host"), rjVoice) {
                rjVoice = it
                lk.sugaapps.airwave.data.RjConfig.voiceIndex = it
                val uid = user?.uid
                if (uid != null) {
                    scope.launch {
                        runCatching {
                            StationManager.updateAiVoiceSettings(uid, it, rjSpeed)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Voice speech speed", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            SegmentedPills(listOf("Relaxed (0.9x)", "Normal (1.0x)", "Fast (1.1x)"), rjSpeed) {
                rjSpeed = it
                lk.sugaapps.airwave.data.RjConfig.speedIndex = it
                val uid = user?.uid
                if (uid != null) {
                    scope.launch {
                        runCatching {
                            StationManager.updateAiVoiceSettings(uid, rjVoice, it)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Auto on-air time check (while live)", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
            Spacer(Modifier.height(8.dp))
            SegmentedPills(listOf("Off", "15 min", "30 min", "1 hour"), autoTimeInterval) {
                autoTimeInterval = it
                lk.sugaapps.airwave.data.RjConfig.autoTimeIntervalIndex = it
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (autoTimeInterval == 0) "Automatic time checks disabled. Use the console button anytime."
                else "AI RJ will automatically announce the time ${lk.sugaapps.airwave.data.RjConfig.intervalDisplayName.lowercase()} while live.",
                style = MaterialTheme.typography.bodySmall,
                color = TextMuted,
            )
        }

        // Save to cloud
        Button(
            onClick = {
                val uid = user?.uid
                if (uid == null) {
                    Toast.makeText(context, "Not signed in", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                // Validate the public stream URL before persisting — never discard a typed
                // value while reporting "saved" (listeners would silently get a dead page).
                val trimmedUrl = publicListeningUrl.trim()
                if (trimmedUrl.isNotEmpty() && !StationManager.isValidPublicStreamUrl(trimmedUrl)) {
                    urlError = "Enter a valid public stream URL (http:// or https://) before saving."
                    Toast.makeText(context, "Fix the stream URL before saving", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                urlError = null
                // Apply to the live engine config so "Go live" uses these settings.
                StreamConfig.host = host.trim()
                StreamConfig.port = port.toIntOrNull() ?: 8000
                StreamConfig.mount = mount.trim()
                StreamConfig.user = username.trim()
                StreamConfig.password = password
                StreamConfig.bitrate = intArrayOf(64, 128, 192, 320).getOrElse(bitrate) { 128 }
                StreamConfig.sampleRate = if (sampleRate == 1) 48000 else 44100
                StreamConfig.channels = if (channels == 0) 1 else 2
                StreamConfig.autoReconnect = autoReconnect

                // Apply RJ preferences immediately
                lk.sugaapps.airwave.data.RjConfig.voiceIndex = rjVoice
                lk.sugaapps.airwave.data.RjConfig.speedIndex = rjSpeed
                lk.sugaapps.airwave.data.RjConfig.autoTimeIntervalIndex = autoTimeInterval

                saving = true
                scope.launch {
                    val result = runCatching {
                        val effectiveStream = publicListeningUrl.trim().takeIf { StationManager.isValidPublicStreamUrl(it) }
                            ?: StreamConfig.publicListeningUrl.takeIf { StationManager.isValidPublicStreamUrl(it) }
                            ?: ""
                        val finalState = SettingsStore.State(
                            serverType = serverType,
                            host = host.trim(),
                            port = port.trim(),
                            mount = mount.trim(),
                            username = username.trim(),
                            password = password,
                            bitrate = bitrate,
                            sampleRate = sampleRate,
                            channels = channels,
                            keepScreenOn = keepScreenOn,
                            autoReconnect = autoReconnect,
                            lowLatency = lowLatency,
                            rjVoice = rjVoice,
                            rjSpeed = rjSpeed,
                            autoTimeInterval = autoTimeInterval,
                            stationTitle = stationTitle,
                            publicListeningUrl = effectiveStream,
                            takeoverEnabled = lk.sugaapps.airwave.data.RjConfig.isTakeoverEnabled,
                            takeoverNewsInterval = lk.sugaapps.airwave.data.RjConfig.takeoverNewsIntervalMinutes,
                            takeoverTimeInterval = lk.sugaapps.airwave.data.RjConfig.takeoverTimeIntervalMinutes,
                            takeoverAutoReplay = lk.sugaapps.airwave.data.RjConfig.autoReplay,
                            takeoverNewsMode = lk.sugaapps.airwave.data.RjConfig.takeoverNewsMode,
                        )
                        SettingsStore.save(context, finalState)
                        SettingsStore.applyToEngine(finalState)

                        withTimeoutOrNull(15_000) {
                            UserStore.saveSettings(
                                uid,
                                mapOf(
                                    "serverType" to serverType,
                                    "host" to host,
                                    "port" to port,
                                    "mount" to mount,
                                    "username" to username,
                                    "password" to password,
                                    "bitrate" to bitrate,
                                    "sampleRate" to sampleRate,
                                    "channels" to channels,
                                    "keepScreenOn" to keepScreenOn,
                                    "autoReconnect" to autoReconnect,
                                    "lowLatency" to lowLatency,
                                    "stationTitle" to stationTitle,
                                    "publicListeningUrl" to effectiveStream,
                                    "rjVoice" to rjVoice,
                                    "rjSpeed" to rjSpeed,
                                    "autoTimeInterval" to autoTimeInterval,
                                ),
                            )
                            // Sync broadcaster station in Firestore (strictly 1 station per user)
                            StationManager.registerOrUpdateStation(
                                uid = uid,
                                broadcasterName = userName,
                                broadcasterEmail = userEmail,
                                host = host.trim(),
                                port = port.trim(),
                                serverType = if (serverType == 0) "Icecast 2" else "Shoutcast",
                                mount = mount.trim(),
                                publicListeningUrl = effectiveStream,
                                stationTitle = stationTitle,
                                rjVoice = rjVoice,
                                rjSpeed = rjSpeed,
                            )
                        }
                    }
                    saving = false
                    val msg = when {
                        result.isFailure -> "Save failed: ${result.exceptionOrNull()?.message}"
                        result.getOrNull() == null -> "Save timed out. Check your connection."
                        else -> "Settings saved"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            },
            enabled = !saving,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
        ) {
            Text(if (saving) "Saving…" else "Save to cloud", fontWeight = FontWeight.SemiBold)
        }

        // About
        SectionCard(title = "About") {
            Text("Airwave Agents — Transmitter", style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            Text("Version 1.0 (preview)", style = MaterialTheme.typography.bodySmall, color = TextMuted)
        }
    }

    // --- Confirm sign out (ends the broadcast if live) ---
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("If you're broadcasting, signing out stops the stream and your listeners will hear silence.") },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    AuthRepository.signOut()
                }) {
                    Text("Sign out", color = DangerRed, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Stay signed in") }
            },
        )
    }
}
