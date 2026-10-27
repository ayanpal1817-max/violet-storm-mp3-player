package com.ayan.violetstorm

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.lifecycle.lifecycleScope
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutionException

private val VoidBlack = Color(0xFF030107)
private val Violet = Color(0xFF7C2CFF)
private val Electric = Color(0xFFB58AFF)
private val Muted = Color(0xFFB9A8E9)

class MainActivity : ComponentActivity() {
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val picker = registerForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (!uris.isNullOrEmpty()) {
            uris.forEach { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                catch (_: SecurityException) { }
            }
            val c = controller ?: return@registerForActivityResult
            c.setMediaItems(uris.map { uri ->
                MediaItem.Builder().setUri(uri).setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(uri.lastPathSegment?.substringAfterLast('/') ?: "Local track")
                        .build()
                ).build()
            })
            c.prepare()
            c.play()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                render()
            } catch (_: Exception) { render() }
        }, mainExecutor)
        render()
    }

    private fun render() {
        setContent {
            val player = controller
            var page by remember { mutableStateOf("Player") }
            var menu by remember { mutableStateOf(false) }
            var currentTitle by remember { mutableStateOf("Choose an MP3") }
            var playing by remember { mutableStateOf(player?.isPlaying == true) }
            var position by remember { mutableFloatStateOf(0f) }
            var duration by remember { mutableFloatStateOf(1f) }
            var volume by remember { mutableFloatStateOf(0.7f) }
            var search by remember { mutableStateOf("") }
            var favorites by remember { mutableStateOf(setOf<String>()) }
            var shuffle by remember { mutableStateOf(false) }
            var repeat by remember { mutableStateOf(false) }
            var eq by remember { mutableStateOf(listOf(0.5f,0.5f,0.5f,0.5f,0.5f)) }

            LaunchedEffect(player) {
                while (true) {
                    player?.let {
                        currentTitle = it.currentMediaItem?.mediaMetadata?.title?.toString() ?: currentTitle
                        playing = it.isPlaying
                        duration = it.duration.takeIf { d -> d > 0 }?.toFloat() ?: 1f
                        position = it.currentPosition.toFloat().coerceAtLeast(0f)
                    }
                    delay(400)
                }
            }

            Box(
                Modifier.fillMaxSize().background(VoidBlack)
                    .pointerInput(Unit) {
                        var drag = 0f
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, amount -> drag += amount },
                            onDragEnd = { if (drag > 35) menu = true else if (drag < -35) menu = false; drag = 0f }
                        )
                    }
            ) {
                StormBackground()
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 9.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    Text("10:25", color = Color.White, fontSize = 12.sp)
                    if (page == "Player" && !menu) {
                        Text(currentTitle, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1)
                        Text("LOCAL MUSIC  •  ♫", color = Muted, fontSize = 11.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            Box(Modifier.size(58.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(Violet, VoidBlack, Color(0xFF34106D)))).border(1.dp, Violet, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                                Text("VS", color = Color.White, fontWeight = FontWeight.Black, fontSize = 23.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            EqualizerBars()
                            Text(if (favorites.contains(currentTitle)) "♥" else "♡", color = Electric, fontSize = 27.sp, modifier = Modifier.clickable {
                                favorites = if (favorites.contains(currentTitle)) favorites - currentTitle else favorites + currentTitle
                            }.padding(3.dp))
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Slider(value = (position / duration).coerceIn(0f,1f), onValueChange = { position = it * duration }, onValueChangeFinished = { player?.seekTo(position.toLong()) }, modifier = Modifier.fillMaxWidth())
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(time(position.toLong()), color = Muted, fontSize = 10.sp)
                                Text(time(duration.toLong()), color = Muted, fontSize = 10.sp)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            NeonButton("◀|", 40) { player?.seekToPreviousMediaItem() }
                            NeonButton(if (playing) "Ⅱ" else "▶", 54) { player?.let { if (it.isPlaying) it.pause() else it.play() } }
                            NeonButton("|▶", 40) { player?.seekToNextMediaItem() }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                            NeonButton("−", 34) { volume = (volume - .1f).coerceAtLeast(0f); player?.volume = volume }
                            Spacer(Modifier.width(5.dp))
                            NeonButton("◖))", 58) { player?.volume = volume; volume = (volume + .05f).coerceAtMost(1f) }
                            Spacer(Modifier.width(5.dp))
                            NeonButton("+", 34) { volume = (volume + .1f).coerceAtMost(1f); player?.volume = volume }
                        }
                        Text("SWIPE DOWN FOR MUSIC MENU", color = Muted, fontSize = 8.sp)
                    } else if (menu) {
                        Text("♫  MUSIC MENU", color = Electric, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        listOf("Library", "Albums and folders", "Favourite tracks and playlists", "Equalizer and settings", "Back to player").forEach { item ->
                            MenuRow(item) {
                                when (item) {
                                    "Back to player" -> { menu = false; page = "Player" }
                                    "Library" -> { page = "Library"; menu = false }
                                    "Albums and folders" -> { page = "Albums"; menu = false }
                                    "Favourite tracks and playlists" -> { page = "Favourites"; menu = false }
                                    else -> { page = "Equalizer"; menu = false }
                                }
                            }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("‹", color = Electric, fontSize = 27.sp, modifier = Modifier.clickable { page = "Player" }.padding(4.dp))
                            Text(page, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        when (page) {
                            "Library" -> {
                                Text("Pick local MP3 files for offline playback", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                                NeonButton("＋ Add songs", 45) { picker.launch(arrayOf("audio/mpeg", "audio/*")) }
                                NeonButton("Search / add more", 40) { picker.launch(arrayOf("audio/*")) }
                            }
                            "Albums" -> {
                                Text("Albums & folders", color = Electric, fontSize = 14.sp)
                                Text("Use Add songs to select tracks from watch storage.", color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                                NeonButton("＋ Browse audio", 42) { picker.launch(arrayOf("audio/*")) }
                            }
                            "Favourites" -> {
                                Text("Favourite tracks", color = Electric, fontSize = 14.sp)
                                Text(if (favorites.isEmpty()) "Tap ♡ on the player to favourite the current track." else favorites.joinToString("\n"), color = Muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                            else -> {
                                Text("5-band equalizer (UI preview)", color = Muted, fontSize = 11.sp)
                                listOf("60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz").forEachIndexed { i, label ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label, color = Muted, fontSize = 9.sp, modifier = Modifier.width(48.dp))
                                        Slider(value = eq[i], onValueChange = { v -> eq = eq.toMutableList().also { it[i] = v } })
                                    }
                                }
                                Text("Equalizer controls are visual in this starter; audio effect wiring remains to be implemented.", color = Muted, fontSize = 9.sp, textAlign = TextAlign.Center)
                                NeonButton(if (shuffle) "Shuffle: ON" else "Shuffle: OFF", 36) { shuffle = !shuffle; player?.shuffleModeEnabled = shuffle }
                                NeonButton(if (repeat) "Repeat: ON" else "Repeat: OFF", 36) { repeat = !repeat; player?.repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }
}

@Composable
private fun StormBackground() {
    val transition = rememberInfiniteTransition(label = "storm")
    val pulse by transition.animateFloat(initialValue = 0.35f, targetValue = 0.95f, animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2, size.height / 2)
        val radius = minOf(size.width, size.height) * .48f
        drawCircle(Brush.radialGradient(listOf(Color(0xFF21004D).copy(alpha = .32f * pulse), Color.Transparent), center, radius), radius = radius)
        for (i in 0..23) {
            val angle = i * (Math.PI * 2 / 24)
            val start = Offset(center.x + kotlin.math.cos(angle).toFloat() * radius * .76f, center.y + kotlin.math.sin(angle).toFloat() * radius * .76f)
            val end = Offset(center.x + kotlin.math.cos(angle).toFloat() * radius, center.y + kotlin.math.sin(angle).toFloat() * radius)
            drawLine(Violet.copy(alpha = .25f + .65f * pulse), start, end, strokeWidth = 1.4f)
        }
        drawCircle(Color(0xFF6E24FF).copy(alpha = .75f * pulse), radius = radius, center = center, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.3f))
    }
}

@Composable
private fun EqualizerBars() {
    val transition = rememberInfiniteTransition(label = "eq")
    val heights = (0..14).map { i ->
        val h by transition.animateFloat(.18f + (i % 4) * .08f, .35f + (i % 5) * .13f, infiniteRepeatable(tween(300 + i * 41), RepeatMode.Reverse), label = "bar$i")
        h
    }
    Row(Modifier.height(65.dp).width(116.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEach { h ->
            Box(Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Electric, Violet, Color(0xFF2B00A0)))))
        }
    }
}

@Composable
private fun NeonButton(label: String, size: Int, action: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF080415)).border(1.5.dp, Violet, CircleShape).clickable { action() }, contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = if (size > 45) 21.sp else 15.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MenuRow(label: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF100A24)).border(1.dp, Color(0xFF48208A), RoundedCornerShape(24.dp)).clickable { action() }.padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✦", color = Electric, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, fontSize = 11.sp)
        Spacer(Modifier.weight(1f))
        Text("›", color = Electric, fontSize = 20.sp)
    }
}

private fun time(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}
