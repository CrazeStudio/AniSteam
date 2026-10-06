package com.crazestudio.anisteam

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AniSteamApp() }
    }
}

@Composable
fun AniSteamApp() {
    MaterialTheme { Surface { HomeScreen() } }
}

@Composable
fun HomeScreen() {
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("AniSteam", style = MaterialTheme.typography.headlineLarge)
        Text("Anime player foundation", style = MaterialTheme.typography.bodyLarge)
    }
}
