package com.crazestudio.anisteam

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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

private const val API = "https://api.tenrai.org/v1"
private const val SOURCE = "https://animesalt.cx"

data class Anime(val id:Int,val title:String,val image:String,val score:String,val episodes:String,val type:String,val synopsis:String="")
data class Episode(val number:Int,val title:String,val aired:String)

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } }
}

private val colors = darkColorScheme(background=Color(0xFF07070A),surface=Color(0xFF111116),primary=Color(0xFF9B6CFF),secondary=Color(0xFFC7B5FF))

@Composable fun App() {
    MaterialTheme(colorScheme=colors) {
        var selected by remember { mutableStateOf<String?>(null) }
        if (selected == null) Home { slug -> selected=slug } else Details(selected!!){ selected=null }
    }
}

@Composable private fun Home(onOpen:(String)->Unit) {
    var q by remember { mutableStateOf("") }; var submitted by remember { mutableStateOf("") }
    var data by remember { mutableStateOf<List<Anime>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(submitted) { loading=true;error=null;try{data=fetchAnime(submitted)}catch(e:Exception){error=e.message?:"Unable to load anime."}finally{loading=false} }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically){Text("AniSteam",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.ExtraBold);Spacer(Modifier.weight(1f));Text("DISCOVER",color=colors.primary,fontSize=12.sp,fontWeight=FontWeight.Bold)}
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value=q,onValueChange={q=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search anime...",color=Color.Gray)},trailingIcon={if(q.isNotBlank())TextButton({submitted=q.trim()}){Text("Search")}})
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Popular","Latest","Movies","TV").forEach{Text(it,color=Color(0xFFC9C9D1),modifier=Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF17171D)).padding(12.dp,8.dp),fontSize=12.sp)}}
        }
        when {
            loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            error!=null->Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center){Text("Unable to load catalog.\n"+error!!,color=Color.LightGray)}
            else->LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                items(data){item->Column(Modifier.clickable{onOpen(item.synopsis)}){Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF17171D))){
                    AsyncImage(item.image,item.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    Text("★ "+item.score,color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0xCC0B0B0F),RoundedCornerShape(7.dp)).padding(7.dp,4.dp))
                };Spacer(Modifier.height(7.dp));Text(item.title,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=2);Text(item.type+" • "+item.episodes+" eps",color=Color.Gray,fontSize=11.sp)}}
            }
        }
    }
}

@Composable private fun Details(slug:String,onBack:()->Unit) {
    val context=LocalContext.current
    var anime by remember { mutableStateOf<Anime?>(null) };var eps by remember { mutableStateOf<List<Episode>>(emptyList()) };var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf<String?>(null)};var chosen by remember{mutableStateOf<Episode?>(null)}
    LaunchedEffect(slug){loading=true;try{anime=fetchDetailsBySlug(slug);eps=fetchEpisodesBySlug(slug)}catch(e:Exception){error=e.message?:"Unable to load details."}finally{loading=false}}
    chosen?.let{ep->AlertDialog(onDismissRequest={chosen=null},title={Text("Episode "+ep.number)},text={Text("No authorized in-app playback URL is configured yet. Open the configured AnimeSalt.cx source website to continue.")},confirmButton={TextButton({chosen=null;context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("$BASE_URL/episode/$slug-1x${ep.number}/")))}){Text("Open source")}},dismissButton={TextButton({chosen=null}){Text("Cancel")}})}
    Column(Modifier.fillMaxSize().background(colors.background)){
        TextButton(onClick=onBack,modifier=Modifier.padding(8.dp)){Text("‹ Back")}
        when{
            loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            error!=null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(error!!,color=Color.LightGray)}
            anime!=null->LazyColumn(contentPadding=PaddingValues(bottom=24.dp)){
                item{
                    Row(Modifier.padding(18.dp)){AsyncImage(anime!!.image,anime!!.title,Modifier.width(135.dp).height(195.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop);Spacer(Modifier.width(16.dp));Column{Text(anime!!.title,color=Color.White,fontSize=21.sp,fontWeight=FontWeight.Bold);Text(anime!!.type+" • "+anime!!.episodes+" eps",color=Color.Gray);Text("★ "+anime!!.score,color=colors.secondary)}}
                    Text("Synopsis",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=18.dp));Text(anime!!.synopsis.ifBlank{"No synopsis available."},color=Color(0xFFB8B8C0),modifier=Modifier.padding(18.dp));Text("Episodes",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(18.dp,8.dp))
                }
                items(eps){ep->Row(Modifier.padding(horizontal=18.dp,vertical=5.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF111116)).clickable{chosen=ep}.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(ep.number.toString(),color=colors.secondary,fontWeight=FontWeight.Bold,modifier=Modifier.width(42.dp));Column(Modifier.weight(1f)){Text(if(ep.title.isBlank())"Episode "+ep.number else ep.title,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=2);if(ep.aired.isNotBlank())Text(ep.aired,color=Color.Gray,fontSize=11.sp)};Text("▶",color=colors.primary)}}
            }
        }
    }
}

private const val BASE_URL = "https://animesalt.cx"

private suspend fun fetchAnime(q:String)=withContext(Dispatchers.IO){
    if(q.isBlank()) return@withContext fetchHome()
    val encoded=URLEncoder.encode(q,"UTF-8")
    val doc=getHtml("$BASE_URL/?s=$encoded")
    doc.select("#aa-movies li.series").mapNotNull { el ->
        val link=el.selectFirst("a.lnk-blk")?.attr("href") ?: return@mapNotNull null
        val id=link.trimEnd('/').substringAfterLast("/series/")
        val title=el.selectFirst("h2.entry-title")?.text()?.trim().orEmpty()
        val poster=posterFrom(el.selectFirst("img"))
        if(id.isBlank()||title.isBlank()) null else Anime(id.hashCode(),title,poster,"—","?","Anime",id)
    }
}

private suspend fun fetchHome()=withContext(Dispatchers.IO){
    val doc=getHtml(BASE_URL)
    val items=doc.select("#aa-movies li.series, #aa-movies li.movie").mapNotNull { el ->
        val link=el.selectFirst("a.lnk-blk")?.attr("href") ?: return@mapNotNull null
        val id=link.trimEnd('/').substringAfterLast("/series/").substringAfterLast("/movies/")
        val title=el.selectFirst("h2.entry-title")?.text()?.trim().orEmpty()
        val poster=posterFrom(el.selectFirst("img"))
        if(id.isBlank()||title.isBlank()) null else Anime(id.hashCode(),title,poster,"—","?","Anime",id)
    }
    items.distinctBy { it.synopsis }.take(30)
}

private suspend fun fetchDetails(id:Int)=withContext(Dispatchers.IO){
    throw IllegalStateException("Direct source IDs are loaded from search results; open an anime from Search.")
}

private suspend fun fetchEpisodes(id:Int)=withContext(Dispatchers.IO){
    emptyList<Episode>()
}

private suspend fun fetchDetailsBySlug(slug:String)=withContext(Dispatchers.IO){
    val doc=getHtml("$BASE_URL/series/$slug/")
    val title=doc.selectFirst(".fg1 .entry-title")?.text()?.trim().orEmpty()
    if(title.isBlank()) throw IllegalStateException("Anime not found")
    val description=doc.selectFirst(".description")
    val overview=description?.selectFirst("p")?.text()?.trim().orEmpty()
    val language=description?.select("p")?.firstOrNull { it.text().contains("Language:") }?.text()?.substringAfter("Language:")?.trim().orEmpty()
    val quality=description?.select("p")?.firstOrNull { it.text().contains("Quality:") }?.text()?.substringAfter("Quality:")?.trim().orEmpty()
    val genres=doc.select(".genres a").map { it.text().trim() }.filter { it.isNotBlank() }
    val year=doc.selectFirst(".year")?.text()?.trim().orEmpty()
    val seasons=doc.selectFirst(".seasons")?.text()?.replace("Seasons","",ignoreCase=true)?.trim().orEmpty()
    val episodes=doc.selectFirst(".entry-meta .episodes")?.text()?.replace("Episodes","",ignoreCase=true)?.trim().orEmpty()
    val rating=doc.selectFirst(".vote-cn .vote")?.text()?.replace("TMDB","")?.trim().orEmpty()
    val poster=posterFrom(doc.selectFirst(".post-thumbnail img"))
    Anime(slug.hashCode(),title,poster,rating,episodes,"Anime",slug)
}

private suspend fun fetchEpisodesBySlug(slug:String,season:Int=1)=withContext(Dispatchers.IO){
    val doc=getHtml("$BASE_URL/episode/$slug-${season}x1/")
    doc.select("#episode_by_temp li").mapNotNull { el ->
        val raw=el.selectFirst(".num-epi")?.text()?.trim().orEmpty()
        val parts=raw.split("x")
        val ep=parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
        Episode(ep,el.selectFirst(".entry-title")?.text()?.trim().orEmpty(),el.selectFirst("img")?.attr("alt").orEmpty())
    }
}

private fun getHtml(url:String):org.jsoup.nodes.Document{
    return org.jsoup.Jsoup.connect(url)
        .userAgent("Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/121 Mobile Safari/537.36")
        .header("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .header("Accept-Language","en-US,en;q=0.9")
        .referrer("https://toonstream.dad/")
        .timeout(15000)
        .get()
}

private fun posterFrom(img:org.jsoup.nodes.Element?):String{
    if(img==null) return ""
    var value=img.attr("data-src").ifBlank { img.attr("data-lazy-src") }
        .ifBlank { img.attr("data-original") }.ifBlank { img.attr("src") }
    if(value.startsWith("//")) value="https:$value"
    return if(value.startsWith("data:image")) "" else value
}
