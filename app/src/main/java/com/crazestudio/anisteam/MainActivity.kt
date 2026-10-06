package com.crazestudio.anisteam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class AnimeCard(val title: String, val image: String, val score: String, val episodes: String, val type: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AniSteamApp() }
    }
}

private val AniSteamColors = darkColorScheme(
    background = Color(0xFF07070A),
    surface = Color(0xFF111116),
    primary = Color(0xFF9B6CFF),
    secondary = Color(0xFFC7B5FF)
)

@Composable
fun AniSteamApp() {
    MaterialTheme(colorScheme = AniSteamColors) {
        Surface(Modifier.fillMaxSize()) { AnimeHome() }
    }
}

@Composable
private fun AnimeHome() {
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var anime by remember { mutableStateOf<List<AnimeCard>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun loadAnime(search: String) {
        loading = true
        error = null
        try { anime = fetchAnime(search) }
        catch (_: Exception) { error = "Couldn't load anime. Check your connection and try again." }
        finally { loading = false }
    }

    LaunchedEffect(submittedQuery) { loadAnime(submittedQuery) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AniSteam", color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text("DISCOVER", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search anime...", color = Color(0xFF77777F)) },
                leadingIcon = { Text("⌕", color = MaterialTheme.colorScheme.primary, fontSize = 25.sp) },
                trailingIcon = {
                    if (query.isNotBlank()) TextButton(onClick = { submittedQuery = query.trim() }) { Text("Search") }
                },
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("Popular"); FilterChip("Latest"); FilterChip("Movies"); FilterChip("TV")
            }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(error!!, color = Color(0xFFB8B8C0))
            }
            else -> {
                Text(
                    if (submittedQuery.isBlank()) "Popular Anime" else "Search results",
                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) { items(anime) { AnimeCardView(it) } }
            }
        }
    }
}

@Composable
private fun FilterChip(label: String) {
    Box(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF17171D)).padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(label, color = Color(0xFFC9C9D1), fontSize = 12.sp)
    }
}

@Composable
private fun AnimeCardView(item: AnimeCard) {
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF17171D))) {
            AsyncImage(model = item.image, contentDescription = item.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(Modifier.align(Alignment.TopStart).padding(7.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xCC0B0B0F)).padding(horizontal = 7.dp, vertical = 4.dp)) {
                Text("★ " + item.score, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(item.title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, maxLines = 2)
        Spacer(Modifier.height(3.dp))
        Text(item.type + " • " + item.episodes + " eps", color = Color(0xFF8F8F99), fontSize = 11.sp)
    }
}

private suspend fun fetchAnime(query: String): List<AnimeCard> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val endpoint = if (query.isBlank()) "https://api.tenrai.org/v1/top/anime?limit=12"
    else "https://api.tenrai.org/v1/anime?q=" + encoded + "&limit=12"
    val connection = URL(endpoint).openConnection() as HttpURLConnection
    connection.requestMethod = "GET"
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    connection.setRequestProperty("Accept", "application/json")
    try {
        if (connection.responseCode !in 200..299) throw IllegalStateException("HTTP " + connection.responseCode)
        val json = connection.inputStream.bufferedReader().use { it.readText() }
        val data = JSONArray(JSONObject(json).getJSONArray("data").toString())
        buildList {
            for (i in 0 until data.length()) {
                val item = data.getJSONObject(i)
                val jpg = item.optJSONObject("images")?.optJSONObject("jpg") ?: continue
                val image = jpg.optString("large_image_url").ifBlank { jpg.optString("image_url") }
                if (image.isBlank()) continue
                add(AnimeCard(
                    title = item.optString("title", "Unknown title"),
                    image = image,
                    score = if (item.isNull("score")) "—" else item.optString("score"),
                    episodes = if (item.isNull("episodes")) "?" else item.optString("episodes"),
                    type = item.optString("type", "Anime")
                ))
            }
        }
    } finally { connection.disconnect() }
}
