package com.ayan.violetstorm

import android.content.ComponentName
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private val Black = Color(0xFF030108)
private val Violet = Color(0xFF792BFF)
private val Glow = Color(0xFFB98AFF)
private val Muted = Color(0xFFC7B8EF)
private val Panel = Color(0xFF100A23)

private data class Track(val uri: String, val title: String, val artist: String, val album: String) {
    fun json() = JSONObject().put("uri", uri).put("title", title).put("artist", artist).put("album", album)
    companion object {
        fun parse(o: JSONObject) = Track(o.optString("uri"), o.optString("title", "Unknown track"), o.optString("artist", "Unknown artist"), o.optString("album", "Unknown album"))
    }
}

class MainActivity : ComponentActivity() {
    private var controller: MediaController? = null
    private var future: ListenableFuture<MediaController>? = null
    private val prefs by lazy { getSharedPreferences("violet_storm", MODE_PRIVATE) }
    private var audioEq: Equalizer? = null

    private fun tracks(): List<Track> = try {
        val a = JSONArray(prefs.getString("tracks", "[]"))
        (0 until a.length()).map { Track.parse(a.getJSONObject(it)) }
    } catch (_: Exception) { emptyList() }

    private fun saveTracks(list: List<Track>) {
        val a = JSONArray()
        list.forEach { a.put(it.json()) }
        prefs.edit().putString("tracks", a.toString()).apply()
    }

    private fun metadata(uri: Uri): Track {
        var title = uri.lastPathSegment?.substringAfterLast('/') ?: "Local track"
        var artist = "Unknown artist"
        var album = "Unknown album"
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(this, uri)
            title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: title
            artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: artist
            album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: album
        } catch (_: Exception) {
        } finally { try { r.release() } catch (_: Exception) {} }
        return Track(uri.toString(), title, artist, album)
    }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) {
            val list = tracks().toMutableList()
            uris.forEach { uri ->
                try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) {}
                val t = metadata(uri)
                if (list.none { it.uri == t.uri }) list.add(t)
            }
            saveTracks(list)
            play(list, 0)
            render()
        }
    }

    private fun play(list: List<Track>, index: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { t ->
            MediaItem.Builder().setUri(Uri.parse(t.uri)).setMediaMetadata(
                MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build()
            ).build()
        }, index.coerceIn(0, list.lastIndex), 0L)
        c.prepare()
        c.play()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        future = MediaController.Builder(this, token).buildAsync()
        future?.addListener({ try { controller = future?.get(); render() } catch (_: Exception) { render() } }, mainExecutor)
        render()
    }

    private fun render() {
        setContent {
            val player = controller
            var page by remember { mutableStateOf("Player") }
            var library by remember { mutableStateOf(tracks()) }
            var favorites by remember { mutableStateOf(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()) }
            var playlist by remember { mutableStateOf(prefs.getStringSet("playlist", emptySet())?.toSet() ?: emptySet()) }
            var search by remember { mutableStateOf("") }
            var playing by remember { mutableStateOf(player?.isPlaying == true) }
            var title by remember { mutableStateOf("Choose an MP3") }
            var artist by remember { mutableStateOf("Violet Storm") }
            var pos by remember { mutableFloatStateOf(0f) }
            var dur by remember { mutableFloatStateOf(1f) }
            var vol by remember { mutableFloatStateOf(player?.volume ?: .7f) }
            var shuffle by remember { mutableStateOf(player?.shuffleModeEnabled == true) }
            var repeat by remember { mutableStateOf(player?.repeatMode == Player.REPEAT_MODE_ALL) }
            var eqValues by remember { mutableStateOf(listOf(0f,0f,0f,0f,0f)) }
            var eqMessage by remember { mutableStateOf("Start playback to enable audio EQ") }

            LaunchedEffect(player) {
                while (true) {
                    player?.let {
                        title = it.currentMediaItem?.mediaMetadata?.title?.toString() ?: title
                        artist = it.currentMediaItem?.mediaMetadata?.artist?.toString() ?: artist
                        playing = it.isPlaying
                        dur = it.duration.takeIf { d -> d > 0 }?.toFloat() ?: 1f
                        pos = it.currentPosition.toFloat().coerceAtLeast(0f)
                        vol = it.volume
                    }
                    delay(350)
                }
            }
            LaunchedEffect(player?.audioSessionId) {
                val id = player?.audioSessionId ?: 0
                if (id > 0) try {
                    audioEq?.release()
                    audioEq = Equalizer(0, id).apply { enabled = true }
                    eqMessage = "Audio equalizer connected"
                } catch (_: Exception) { eqMessage = "Equalizer not supported on this watch" }
            }

            val current = library.firstOrNull { it.title == title }
            val visible = library.filter {
                val q = search.trim().lowercase(Locale.ROOT)
                q.isEmpty() || it.title.lowercase(Locale.ROOT).contains(q) || it.artist.lowercase(Locale.ROOT).contains(q) || it.album.lowercase(Locale.ROOT).contains(q)
            }

            Box(Modifier.fillMaxSize().background(Black).pointerInput(Unit) {
                var dy = 0f
                detectVerticalDragGestures(onVerticalDrag = { _, v -> dy += v }, onDragEnd = {
                    if (dy > 35) page = "Menu" else if (dy < -35) page = "Player"
                    dy = 0f
                })
            }) {
                StormBackground()
                Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("10:25", color = Color.White, fontSize = 12.sp)
                    when (page) {
                        "Player" -> {
                            Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(artist, color = Muted, fontSize = 11.sp, maxLines = 1)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
                                AlbumTile()
                                Bars(Modifier.width(108.dp))
                                ButtonCircle(if (current != null && favorites.contains(current.uri)) "♥" else "♡", 39) {
                                    current?.let { favorites = if (favorites.contains(it.uri)) favorites - it.uri else favorites + it.uri; prefs.edit().putStringSet("favorites", favorites).apply() }
                                }
                            }
                            Slider(value = (pos / dur).coerceIn(0f,1f), onValueChange = { pos = it * dur }, onValueChangeFinished = { player?.seekTo(pos.toLong()) })
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(clock(pos), color = Muted, fontSize = 9.sp)
                                Text(clock(dur), color = Muted, fontSize = 9.sp)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                ButtonCircle("◀|", 39) { player?.seekToPreviousMediaItem() }
                                ButtonCircle(if (playing) "Ⅱ" else "▶", 53) { player?.let { if (it.isPlaying) it.pause() else it.play() } }
                                ButtonCircle("|▶", 39) { player?.seekToNextMediaItem() }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ButtonCircle("−", 34) { vol = (vol - .1f).coerceAtLeast(0f); player?.volume = vol }
                                VolumeDial(vol)
                                ButtonCircle("+", 34) { vol = (vol + .1f).coerceAtMost(1f); player?.volume = vol }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ButtonCircle(if (shuffle) "SH" else "⇄", 32) { shuffle = !shuffle; player?.shuffleModeEnabled = shuffle }
                                ButtonCircle("BT", 32) { try { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) {} }
                                ButtonCircle(if (repeat) "↻" else "↺", 32) { repeat = !repeat; player?.repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF }
                            }
                            Text("SWIPE DOWN FOR MUSIC MENU", color = Muted, fontSize = 8.sp)
                        }
                        "Menu" -> {
                            Header("♫ MUSIC MENU") { page = "Player" }
                            MenuRow("Library", "All songs stored on your watch") { page = "Library" }
                            MenuRow("Albums and folders", "Browse albums and artists") { page = "Albums" }
                            MenuRow("Favourite tracks and playlists", "Your saved music") { page = "Favourites" }
                            MenuRow("Equalizer and settings", "Five-band audio controls") { page = "Equalizer" }
                            MenuRow("Bluetooth output", "Open watch Bluetooth settings") { try { startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) } catch (_: Exception) {} }
                        }
                        "Library" -> {
                            Header("LIBRARY") { page = "Menu" }
                            Search(search) { search = it }
                            ButtonCircle("＋ ADD SONGS", 44) { picker.launch(arrayOf("audio/*")) }
                            Text("\${library.size} saved tracks", color = Muted, fontSize = 9.sp)
                            TrackRows(visible, favorites, { t -> play(library, library.indexOf(t)) }, { t ->
                                favorites = if (favorites.contains(t.uri)) favorites - t.uri else favorites + t.uri
                                prefs.edit().putStringSet("favorites", favorites).apply()
                            })
                        }
                        "Albums" -> {
                            Header("ALBUMS & ARTISTS") { page = "Menu" }
                            Search(search) { search = it }
                            val groups = visible.groupBy { it.album }
                            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                                groups.forEach { (album, songs) -> item { MenuRow(album, "\${songs.size} tracks") { play(songs, 0) } } }
                                if (groups.isEmpty()) item { Text("Add songs from Library first.", color = Muted, fontSize = 10.sp) }
                            }
                        }
                        "Favourites" -> {
                            Header("FAVOURITES & PLAYLIST") { page = "Menu" }
                            Text("FAVOURITES", color = Glow, fontSize = 10.sp)
                            val fav = library.filter { favorites.contains(it.uri) }
                            TrackRows(fav, favorites, { t -> play(fav, fav.indexOf(t)) }, { t -> favorites = favorites - t.uri; prefs.edit().putStringSet("favorites", favorites).apply() })
                            ButtonCircle("＋ ADD CURRENT TO PLAYLIST", 40) {
                                current?.let { playlist = playlist + it.uri; prefs.edit().putStringSet("playlist", playlist).apply() }
                            }
                            Text("MY PLAYLIST • \${playlist.size} tracks", color = Glow, fontSize = 10.sp)
                            val saved = library.filter { playlist.contains(it.uri) }
                            TrackRows(saved, favorites, { t -> play(saved, saved.indexOf(t)) }, { t -> playlist = playlist - t.uri; prefs.edit().putStringSet("playlist", playlist).apply() })
                        }
                        "Equalizer" -> {
                            Header("EQUALIZER & SETTINGS") { page = "Menu" }
                            Text(eqMessage, color = Muted, fontSize = 9.sp, textAlign = TextAlign.Center)
                            listOf("60 Hz","230 Hz","910 Hz","3.6 kHz","14 kHz").forEachIndexed { i, label ->
                                Column(Modifier.fillMaxWidth()) {
                                    Text("\$label  \${eqValues[i].toInt()} dB", color = Color.White, fontSize = 9.sp)
                                    Slider(value = eqValues[i], valueRange = -12f..12f, onValueChange = { v ->
                                        eqValues = eqValues.toMutableList().also { it[i] = v }
                                        try {
                                            audioEq?.let { e ->
                                                val band = i.coerceAtMost(e.numberOfBands - 1).toShort()
                                                val range = e.bandLevelRange
                                                e.setBandLevel(band, (v * 100).toInt().coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                                            }
                                        } catch (_: Exception) { eqMessage = "Audio effect unavailable" }
                                    })
                                }
                            }
                            ButtonCircle("RESET EQ", 38) {
                                eqValues = listOf(0f,0f,0f,0f,0f)
                                try { audioEq?.let { e -> for (i in 0 until e.numberOfBands) e.setBandLevel(i.toShort(), 0) } } catch (_: Exception) {}
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        try { audioEq?.release() } catch (_: Exception) {}
        audioEq = null
        future?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }
}

@Composable
private fun Header(title: String, back: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", color = Glow, fontSize = 25.sp, modifier = Modifier.clickable { back() }.padding(horizontal = 6.dp))
        Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Search(value: String, change: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(20.dp)).background(Panel).border(1.dp, Violet, RoundedCornerShape(20.dp)).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("⌕ ", color = Glow)
        BasicTextField(value, change, singleLine = true, textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 11.sp), decorationBox = { inner ->
            Box { if (value.isEmpty()) Text("Search songs, artists, albums", color = Muted, fontSize = 10.sp); inner() }
        })
    }
}

@Composable
private fun TrackRows(list: List<Track>, favorites: Set<String>, play: (Track) -> Unit, favorite: (Track) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
        items(list, key = { it.uri }) { t ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(15.dp)).background(Panel).border(1.dp, Color(0xFF39206A), RoundedCornerShape(15.dp)).clickable { play(t) }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumTile(31)
                Spacer(Modifier.width(7.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.title, color = Color.White, fontSize = 10.sp, maxLines = 1)
                    Text("\${t.artist} • \${t.album}", color = Muted, fontSize = 8.sp, maxLines = 1)
                }
                Text(if (favorites.contains(t.uri)) "♥" else "♡", color = Glow, modifier = Modifier.clickable { favorite(t) }.padding(4.dp))
            }
        }
        if (list.isEmpty()) item { Text("No tracks found. Add songs in Library.", color = Muted, fontSize = 10.sp) }
    }
}

@Composable
private fun MenuRow(title: String, subtitle: String, action: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(19.dp)).background(Panel).border(1.dp, Color(0xFF48208A), RoundedCornerShape(19.dp)).clickable { action() }.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("✦", color = Glow, fontSize = 17.sp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 8.sp)
        }
        Text("›", color = Glow, fontSize = 18.sp)
    }
}

@Composable
private fun AlbumTile(size: Int = 57) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(11.dp)).background(Brush.linearGradient(listOf(Color(0xFF4A12B9), Black, Color(0xFF25065C)))).border(1.dp, Violet, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
        Text("VS", color = Color.White, fontWeight = FontWeight.Black, fontSize = (size * .32f).sp)
    }
}

@Composable
private fun VolumeDial(volume: Float) {
    Box(Modifier.size(62.dp).padding(3.dp).border(2.dp, Violet, CircleShape).padding(4.dp).border(2.dp, Glow, CircleShape), contentAlignment = Alignment.Center) {
        Text("◖))\n\${(volume * 100).toInt()}%", color = Color.White, fontSize = 9.sp, textAlign = TextAlign.Center, lineHeight = 11.sp)
    }
}

@Composable
private fun ButtonCircle(label: String, size: Int, action: () -> Unit) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF080415)).border(1.5.dp, Violet, CircleShape).clickable { action() }, contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, fontSize = if (size > 45) 19.sp else 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    }
}

@Composable
private fun StormBackground() {
    val transition = rememberInfiniteTransition(label = "storm")
    val pulse by transition.animateFloat(.35f, .95f, infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Canvas(Modifier.fillMaxSize()) {
        val c = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        val r = minOf(size.width, size.height) * .48f
        drawCircle(Brush.radialGradient(listOf(Color(0xFF310078).copy(alpha = .4f * pulse), Color.Transparent), c, r), r, c)
        for (i in 0..31) {
            val a = i * (Math.PI * 2 / 32)
            val s = androidx.compose.ui.geometry.Offset(c.x + kotlin.math.cos(a).toFloat() * r * .78f, c.y + kotlin.math.sin(a).toFloat() * r * .78f)
            val e = androidx.compose.ui.geometry.Offset(c.x + kotlin.math.cos(a).toFloat() * r, c.y + kotlin.math.sin(a).toFloat() * r)
            drawLine(Violet.copy(alpha = .25f + .65f * pulse), s, e, strokeWidth = 1.4f)
        }
        drawCircle(Color(0xFF6E24FF).copy(alpha = .85f * pulse), r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.4f))
    }
}

@Composable
private fun Bars(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "bars")
    val heights = (0..12).map { i ->
        val h by transition.animateFloat(.18f + (i % 4) * .08f, .35f + (i % 5) * .13f, infiniteRepeatable(tween(300 + i * 41), RepeatMode.Reverse), label = "bar\$i")
        h
    }
    Row(modifier.height(62.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEach { h -> Box(Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Glow, Violet, Color(0xFF2B00A0))))) }
    }
}

private fun clock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(s / 60, s % 60)
}
