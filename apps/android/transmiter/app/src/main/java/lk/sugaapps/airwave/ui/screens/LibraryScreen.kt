package lk.sugaapps.airwave.ui.screens

import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import lk.sugaapps.airwave.R
import lk.sugaapps.airwave.engine.EngineRegistry
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

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

enum class LibraryTab(val title: String) {
    TRACKS("Music Tracks"),
    AI("AI Generated"),
    ALL("All")
}

/** Track library / playlist — real device audio, played through the engine. */
@Composable
fun LibraryScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { EngineRegistry.engine }
    var query by remember { mutableStateOf("") }
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.TRACKS) }
    var optionsFor by remember { mutableStateOf<Int?>(null) }
    var confirmUpNext by remember { mutableStateOf<Int?>(null) }
    var showClearAiDialog by remember { mutableStateOf(false) }
    var showClearTestTracksDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Playlist.initialize(context)
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            scope.launch {
                val item = withContext(Dispatchers.IO) { readTrack(context, uri) }
                Playlist.add(item)
            }
        }
    }

    val tracksCount = remember(Playlist.items.toList()) { Playlist.items.count { !it.isAi } }
    val aiCount = remember(Playlist.items.toList()) { Playlist.items.count { it.isAi } }
    val totalCount = Playlist.items.size

    val tabFiltered = remember(selectedTab, Playlist.items.toList()) {
        when (selectedTab) {
            LibraryTab.TRACKS -> Playlist.items.filter { !it.isAi }
            LibraryTab.AI -> Playlist.items.filter { it.isAi }
            LibraryTab.ALL -> Playlist.items.toList()
        }
    }

    val filtered = remember(tabFiltered, query) {
        if (query.isBlank()) tabFiltered
        else tabFiltered.filter { it.title.contains(query, true) || it.artist.contains(query, true) }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Library", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                Text(
                    text = when (selectedTab) {
                        LibraryTab.TRACKS -> "$tracksCount music tracks from device"
                        LibraryTab.AI -> "$aiCount AI generated clips"
                        LibraryTab.ALL -> "$totalCount total library items"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted,
                )
            }
            if (selectedTab != LibraryTab.AI) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    val hasTestTracks = remember(Playlist.items.toList()) {
                        Playlist.items.any { !it.isAi && (it.artist == "Royalty Free" || it.uri.toString().contains("/tracks/")) }
                    }
                    if (hasTestTracks) {
                        OutlinedButton(
                            onClick = { showClearTestTracksDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, TextMuted.copy(alpha = 0.4f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceWhite, contentColor = TextSecondary),
                        ) {
                            Text("Remove test songs", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    val count = Playlist.loadDefaultTracksFromAssets(context, force = false)
                                    withContext(Dispatchers.Main) {
                                        if (count > 0) {
                                            Toast.makeText(context, "Added $count test songs to Library", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Test songs already loaded", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, BrandBlue.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = SurfaceWhite, contentColor = BrandBlue),
                        ) {
                            Text("+ Test songs", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Button(
                        onClick = { picker.launch(arrayOf("audio/*")) },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                    ) {
                        Text("Add tracks", style = MaterialTheme.typography.labelMedium)
                    }
                }
            } else if (aiCount > 0) {
                OutlinedButton(
                    onClick = { showClearAiDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DangerRed.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DangerRed),
                ) {
                    Text("Clear AI Clips", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // --- Filter Tabs: [Tracks] [AI Content] [All] ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceMuted)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            LibraryTab.values().forEach { tabOption ->
                val count = when (tabOption) {
                    LibraryTab.TRACKS -> tracksCount
                    LibraryTab.AI -> aiCount
                    LibraryTab.ALL -> totalCount
                }
                val isSelected = selectedTab == tabOption
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) SurfaceWhite else Color.Transparent)
                        .then(
                            if (isSelected) Modifier.border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                            else Modifier
                        )
                        .clickable { selectedTab = tabOption }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = when (tabOption) {
                                LibraryTab.TRACKS -> "Tracks"
                                LibraryTab.AI -> "AI Content"
                                LibraryTab.ALL -> "All"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) BrandBlue else TextSecondary,
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) BrandBlue.copy(alpha = 0.12f) else BorderLight)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(
                                text = "$count",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) BrandBlue else TextMuted,
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = {
                Text(
                    text = when (selectedTab) {
                        LibraryTab.TRACKS -> "Search music tracks…"
                        LibraryTab.AI -> "Search AI clips…"
                        LibraryTab.ALL -> "Search all library items…"
                    },
                    color = TextMuted,
                )
            },
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = BrandBlue,
                unfocusedBorderColor = BorderStrong,
                focusedContainerColor = SurfaceWhite,
                unfocusedContainerColor = SurfaceWhite,
                cursorColor = BrandBlue,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
            ),
        )
        Spacer(Modifier.height(12.dp))

        if (tabFiltered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(24.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(SurfaceMuted),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(if (selectedTab == LibraryTab.AI) R.drawable.ic_ai else R.drawable.ic_tracks),
                            contentDescription = null,
                            tint = TextMuted,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                    Text(
                        text = when (selectedTab) {
                            LibraryTab.TRACKS -> "No music tracks yet"
                            LibraryTab.AI -> "No AI generated clips yet"
                            LibraryTab.ALL -> "No tracks in library yet"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary,
                    )
                    Text(
                        text = when (selectedTab) {
                            LibraryTab.TRACKS -> "Tap \"Add tracks\" to choose songs from your device."
                            LibraryTab.AI -> "When flash news, time voiceovers, or listener shoutouts are generated, they will be listed here separately."
                            LibraryTab.ALL -> "Add music tracks or generate AI clips to see them here."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    if (selectedTab == LibraryTab.TRACKS || selectedTab == LibraryTab.ALL) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        val count = Playlist.loadDefaultTracksFromAssets(context, force = false)
                                        withContext(Dispatchers.Main) {
                                            if (count > 0) {
                                                Toast.makeText(context, "Loaded $count test songs to Library", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Test songs already loaded", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = BrandBlue, contentColor = Color.White),
                            ) {
                                Text("Load Test Songs")
                            }
                            OutlinedButton(
                                onClick = { picker.launch(arrayOf("audio/*")) },
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Pick Songs")
                            }
                        }
                    }
                }
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No matches for \"$query\"", style = MaterialTheme.typography.bodyMedium, color = TextMuted)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(filtered) { _, track ->
                    val realIndex = Playlist.items.indexOf(track)
                    TrackRow(
                        track = track,
                        isCurrent = realIndex == Playlist.currentIndex,
                        onClick = {
                            if (realIndex >= 0) {
                                Playlist.play(realIndex, engine, context)
                            }
                        },
                        onMore = {
                            if (realIndex >= 0) {
                                optionsFor = realIndex
                            }
                        },
                    )
                }
            }
        }
    }

    // --- Overflow actions dialog ---
    optionsFor?.let { idx ->
        val track = Playlist.items.getOrNull(idx)
        AlertDialog(
            onDismissRequest = { optionsFor = null },
            title = { Text(track?.title ?: "Track", maxLines = 1) },
            text = {
                Column {
                    DialogAction("Play now") { Playlist.play(idx, engine, context); optionsFor = null }
                    DialogAction("Add to Up Next") { optionsFor = null; confirmUpNext = idx }
                    DialogAction("Remove from library", DangerRed) { Playlist.remove(idx); optionsFor = null }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { optionsFor = null }) { Text("Close") } },
        )
    }

    // --- Confirm: Add to Up Next ---
    confirmUpNext?.let { idx ->
        val track = Playlist.items.getOrNull(idx)
        AlertDialog(
            onDismissRequest = { confirmUpNext = null },
            title = { Text("Add to Up Next") },
            text = { Text("Add \"${track?.title ?: "this track"}\" to the Up Next queue?") },
            confirmButton = {
                Button(onClick = {
                    Playlist.playNext(idx)
                    Toast.makeText(context, "Added to Up Next", Toast.LENGTH_SHORT).show()
                    confirmUpNext = null
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { confirmUpNext = null }) { Text("Cancel") } },
        )
    }

    // --- Confirm: Clear AI Clips ---
    if (showClearAiDialog) {
        AlertDialog(
            onDismissRequest = { showClearAiDialog = false },
            title = { Text("Clear AI Clips") },
            text = { Text("Remove all $aiCount AI generated clips from the library? Your music tracks will not be affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        Playlist.clearAiClips()
                        showClearAiDialog = false
                        Toast.makeText(context, "Cleared AI clips", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White),
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAiDialog = false }) { Text("Cancel") }
            },
        )
    }

    // --- Confirm: Clear Test Songs ---
    if (showClearTestTracksDialog) {
        AlertDialog(
            onDismissRequest = { showClearTestTracksDialog = false },
            title = { Text("Remove Test Songs") },
            text = { Text("Remove all pre-loaded test songs from the library? Your custom songs and AI clips will not be affected.") },
            confirmButton = {
                Button(
                    onClick = {
                        val count = Playlist.removeTestTracks()
                        showClearTestTracksDialog = false
                        Toast.makeText(context, "Removed $count test songs", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DangerRed, contentColor = Color.White),
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearTestTracksDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DialogAction(label: String, color: Color = TextPrimary, onClick: () -> Unit) {
    Text(
        text = label,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        color = color,
        style = MaterialTheme.typography.bodyLarge,
    )
}

@Composable
private fun TrackRow(
    track: Playlist.Item,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onMore: () -> Unit,
) {
    val isAi = track.isAi
    val isAiMoved = track.isAiMoved
    val (aiBadgeLabel, aiBadgeColor) = when {
        isAi && track.title.contains("Flash News", ignoreCase = true) -> "NEWS" to DangerRed
        isAi && track.title.contains("Time Check", ignoreCase = true) -> "TIME" to BrandBlue
        isAi && (track.title.contains("Song Request", ignoreCase = true) || track.title.contains("Request", ignoreCase = true)) -> "REQUEST" to OnAirTeal
        isAi && track.title.contains("Shoutout", ignoreCase = true) -> "CHAT" to OnAirTeal
        isAi -> "AI" to BrandBlue
        isAiMoved -> "AI MOVED" to BrandBlue
        else -> "" to Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceWhite)
            .border(1.dp, if (isCurrent) OnAirTeal else BorderLight, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isAi) aiBadgeColor.copy(alpha = 0.1f) else SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (isAi) R.drawable.ic_ai else R.drawable.ic_tracks),
                contentDescription = null,
                tint = if (isCurrent) OnAirTeal else if (isAi) aiBadgeColor else TextMuted,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(OnAirTeal.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = OnAirTeal,
                            fontSize = 10.sp,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
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
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(formatMs(track.durationMs), style = MaterialTheme.typography.bodySmall, color = TextMuted)
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_more),
                contentDescription = "More options",
                tint = TextSecondary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private fun readTrack(context: android.content.Context, uri: Uri): Playlist.Item {
    val fallback = displayName(context, uri) ?: "Unknown track"
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, uri)
        val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.ifBlank { null } ?: fallback
        val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.ifBlank { null } ?: "Unknown artist"
        val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        Playlist.Item(uri, title, artist, dur)
    } catch (e: Exception) {
        Playlist.Item(uri, fallback, "Unknown artist", 0L)
    } finally {
        runCatching { retriever.release() }
    }
}

private fun displayName(context: android.content.Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0).substringBeforeLast('.') else null
        }
    }.getOrNull()

private fun formatMs(ms: Long): String {
    if (ms <= 0L) return "--:--"
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
