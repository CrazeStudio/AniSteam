package com.crazestudio.anisteam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.crazestudio.anisteam.data.Anime
import com.crazestudio.anisteam.data.AnimeRepository
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AniSteamApp() }
    }
}

private val Bg = Color(0xFF07070A)
private val Card = Color(0xFF13131A)
private val Accent = Color(0xFF8B5CF6)

@Composable
fun AniSteamApp() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize(), color = Bg) { Home() }
    }
}

@Composable
private fun Home() {
    val repo = remember { AnimeRepository() }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var isSearch by remember { mutableStateOf(false) }

    fun loadTrending() {
        loading = true
        isSearch = false
        scope.launch { runCatching { repo.trending() }.onSuccess { results = it }; loading = false }
    }

    fun doSearch() {
        if (query.isBlank()) { loadTrending(); return }
        loading = true
        isSearch = true
        scope.launch { runCatching { repo.search(query.trim()) }.onSuccess { results = it }; loading = false }
    }

    LaunchedEffect(Unit) { loadTrending() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 28.dp)
    ) {
        item {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("AniSteam", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { doSearch() }) {
                        Icon(Icons.Default.Search, "Search", tint = Color.White)
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Card),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, null, tint = Color(0xFF8E8E99), modifier = Modifier.padding(start = 14.dp))
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("Search anime...", color = Color(0xFF777781)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = Accent
                        )
                    )
                    IconButton(onClick = { doSearch() }) {
                        Icon(Icons.Default.Tune, "Search", tint = Color(0xFFBCA9FF))
                    }
                }

                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("All", "Trending", "Popular", "Movies").forEachIndexed { i, label ->
                        Text(
                            label,
                            color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(if (i == 0) Accent else Card)
                                .clickable { if (i == 1 || i == 0) loadTrending() }
                                .padding(horizontal = 15.dp, vertical = 9.dp),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }

        item {
            Text(
                if (isSearch) "Search results" else "Trending anime",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
            )
        }

        if (loading) {
            item {
                Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Accent)
                }
            }
        } else {
            itemsIndexed(results.chunked(3)) { _, row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { AnimeCard(it, Modifier.weight(1f)) }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun AnimeCard(anime: Anime, modifier: Modifier) {
    Column(modifier) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(.68f).clip(RoundedCornerShape(12.dp)).background(Card)
        ) {
            AsyncImage(
                model = anime.imageUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier.fillMaxWidth().height(65.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD07070A))))
            )
            anime.score?.let {
                Text(
                    "★ " + String.format("%.1f", it),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(7.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color(0xB0000000)).padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            anime.title,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            (anime.episodes ?: "—").toString() + " episodes",
            color = Color(0xFF85858F),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
