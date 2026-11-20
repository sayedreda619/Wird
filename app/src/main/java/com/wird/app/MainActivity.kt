package com.wird.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

data class Surah(val n: Int, val name: String, val ayahs: Int)

val reciters = linkedMapOf(
    "مشاري العفاسي" to "https://server8.mp3quran.net/afs",
    "عبد الباسط عبد الصمد" to "https://server7.mp3quran.net/basit",
    "محمود خليل الحصري" to "https://server13.mp3quran.net/husr",
    "ماهر المعيقلي" to "https://server12.mp3quran.net/maher",
    "محمد صديق المنشاوي" to "https://server10.mp3quran.net/minsh"
)

fun ar(n: Int): String = n.toString().map { '٠' + (it - '0') }.joinToString("")

suspend fun fetch(u: String): String = withContext(Dispatchers.IO) { URL(u).readText() }

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    App(player)
                }
            }
        }
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(player: ExoPlayer) {
    var surahs by remember { mutableStateOf<List<Surah>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<Surah?>(null) }
    var reciter by remember { mutableStateOf(reciters.keys.first()) }
    var playing by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(retry) {
        try {
            val a = JSONObject(fetch("https://api.alquran.cloud/v1/surah")).getJSONArray("data")
            surahs = (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Surah(o.getInt("number"), o.getString("name"), o.getInt("numberOfAyahs"))
            }
            err = null
        } catch (e: Exception) {
            err = "تعذّر التحميل. تأكد من الإنترنت."
        }
    }

    fun play(n: Int) {
        val url = "${reciters[reciter]}/${n.toString().padStart(3, '0')}.mp3"
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        playing = n
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(open?.name ?: "ورد") },
                navigationIcon = { if (open != null) TextButton(onClick = { open = null }) { Text("رجوع") } }
            )
        },
        bottomBar = {
            val p = playing
            if (p != null) BottomAppBar {
                Text(surahs.getOrNull(p - 1)?.name ?: "", modifier = Modifier.padding(horizontal = 8.dp))
                TextButton(onClick = { player.pause() }) { Text("إيقاف") }
                TextButton(onClick = { player.play() }) { Text("استئناف") }
                TextButton(onClick = { if (p < 114) play(p + 1) }) { Text("التالية") }
                TextButton(onClick = {
                    val k = reciters.keys.toList()
                    reciter = k[(k.indexOf(reciter) + 1) % k.size]
                    play(p)
                }) { Text(reciter) }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val s = open
            if (s != null) {
                Reader(s) { play(s.n) }
            } else if (err != null) {
                Column(Modifier.padding(24.dp)) {
                    Text(err!!)
                    Button(onClick = { retry++ }) { Text("إعادة المحاولة") }
                }
            } else {
                LazyColumn {
                    items(surahs) { x ->
                        Row(Modifier.fillMaxWidth().clickable { open = x }.padding(16.dp)) {
                            Text(ar(x.n), modifier = Modifier.width(48.dp))
                            Text(x.name, fontSize = 20.sp, modifier = Modifier.weight(1f))
                            Text("${ar(x.ayahs)} آية")
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
fun Reader(s: Surah, onPlay: () -> Unit) {
    var text by remember(s.n) { mutableStateOf<String?>(null) }
    LaunchedEffect(s.n) {
        text = try {
            val a = JSONObject(fetch("https://api.alquran.cloud/v1/surah/${s.n}/quran-uthmani"))
                .getJSONObject("data").getJSONArray("ayahs")
            buildString {
                for (i in 0 until a.length()) {
                    var t = a.getJSONObject(i).getString("text")
                    if (i == 0 && s.n != 1 && s.n != 9) t = t.split(" ").drop(4).joinToString(" ")
                    append(t).append(" ﴿").append(ar(i + 1)).append("﴾ ")
                }
            }
        } catch (e: Exception) {
            "تعذّر تحميل السورة. تأكد من الإنترنت."
        }
    }
    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Button(onClick = onPlay) { Text("استماع") }
        Spacer(Modifier.height(12.dp))
        Text(text ?: "جارٍ التحميل…", fontSize = 26.sp, lineHeight = 48.sp, textAlign = TextAlign.Justify)
    }
}
