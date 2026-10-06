package com.crazestudio.anisteam

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import kotlinx.coroutines.ensureActive
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
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebChromeClient
import android.webkit.WebViewClient
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import android.content.Context
import android.util.Base64

private const val SOURCE = "https://animesalt.cx"

data class Anime(val id:Int,val title:String,val image:String,val score:String,val episodes:String,val type:String,val synopsis:String="",val sourceId:String="animesalt",val seasons:Int=1,val slug:String="")
data class AnimeSource(val id:String,val name:String,val baseUrl:String,val enabled:Boolean=true)
data class PlayerServer(val name:String,val url:String)
data class Episode(val number:Int,val title:String,val aired:String,val url:String="",val playerUrl:String="",val servers:List<PlayerServer> = emptyList(),val animeSlug:String="")

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } }
}

private val SOURCES = listOf(AnimeSource("animesalt","AnimeSalt","https://animesalt.cx"))

private val colors = darkColorScheme(background=Color(0xFF07070A),surface=Color(0xFF111116),primary=Color(0xFF9B6CFF),secondary=Color(0xFFC7B5FF))

@Composable fun App() {
    MaterialTheme(colorScheme=colors) {
        var selected by remember { mutableStateOf<String?>(null) }
        var showSources by remember { mutableStateOf(false) }
        if (selected == null) Home(onOpen = { slug -> selected=slug }, onSources = { showSources=true }) else Details(selected!!){ selected=null }
        if (showSources) SourceDialog(onDismiss = { showSources=false })
    }
}

@Composable private fun Home(onOpen:(String)->Unit, onSources:()->Unit) {
    var q by remember { mutableStateOf("") }; var submitted by remember { mutableStateOf("") }
    val context=LocalContext.current
    var data by remember { mutableStateOf<List<Anime>>(emptyList()) }; val continueWatching=remember{loadContinueWatching(context)}; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(submitted) { loading=true;error=null;try{data=fetchAnime(submitted)}catch(e:Exception){error=e.message?:"Unable to load anime."}finally{loading=false} }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.padding(18.dp)) {
            if(continueWatching!=null && continueWatching.animeSlug.isNotBlank()){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFF17171D)).clickable{onOpen(continueWatching.animeSlug)}.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Continue Watching",color=Color.White,fontWeight=FontWeight.Bold);Text("Episode ${continueWatching.number} • ${continueWatching.title}",color=Color.Gray,fontSize=12.sp)};Text("▶",color=colors.primary,fontSize=20.sp)}}
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment=Alignment.CenterVertically){Text("AniSteam",color=Color.White,fontSize=27.sp,fontWeight=FontWeight.ExtraBold);Spacer(Modifier.weight(1f));TextButton(onClick=onSources){Text("SOURCES",color=colors.primary,fontSize=11.sp,fontWeight=FontWeight.Bold)}}
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(value=q,onValueChange={q=it},modifier=Modifier.fillMaxWidth(),singleLine=true,placeholder={Text("Search anime...",color=Color.Gray)},trailingIcon={if(q.isNotBlank())TextButton({submitted=q.trim()}){Text("Search")}})
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){listOf("Popular","Latest","Movies","TV").forEach{Text(it,color=Color(0xFFC9C9D1),modifier=Modifier.clip(RoundedCornerShape(50)).background(Color(0xFF17171D)).padding(12.dp,8.dp),fontSize=12.sp)}}
        }
        when {
            loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            error!=null->Box(Modifier.fillMaxSize().padding(24.dp),contentAlignment=Alignment.Center){Text("Unable to load catalog.\n"+error!!,color=Color.LightGray)}
            else->LazyVerticalGrid(columns=GridCells.Fixed(2),contentPadding=PaddingValues(14.dp),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
                items(data){item->Column(Modifier.clickable{onOpen(item.slug)}){Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF17171D))){
                    AsyncImage(item.image,item.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    Text("★ "+item.score,color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold,modifier=Modifier.align(Alignment.TopStart).padding(8.dp).background(Color(0xCC0B0B0F),RoundedCornerShape(7.dp)).padding(7.dp,4.dp))
                };Spacer(Modifier.height(7.dp));Text(item.title,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=2);Text(item.type+" • "+item.episodes+" eps",color=Color.Gray,fontSize=11.sp)}}
            }
        }
    }
}

@Composable private fun Details(slug:String,onBack:()->Unit) {
    val context=LocalContext.current
    var anime by remember { mutableStateOf<Anime?>(null) };var eps by remember { mutableStateOf<List<Episode>>(emptyList()) };var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf<String?>(null)};var chosen by remember{mutableStateOf<Episode?>(null)};var season by remember{mutableIntStateOf(1)};var playingEpisode by remember{mutableStateOf<Episode?>(null)};var playerLoading by remember{mutableStateOf(false)};var playerError by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(slug){
        loading=true;error=null;eps=emptyList();season=1
        try{anime=fetchDetailsBySlug(slug)}catch(e:Exception){if(e !is kotlinx.coroutines.CancellationException) error=e.message?:"Unable to load details."}finally{loading=false}
    }
    LaunchedEffect(slug,anime,season){
        if(anime==null)return@LaunchedEffect
        loading=true;error=null
        try{eps=fetchEpisodesBySlug(slug,season)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=e.message?:"Unable to load episodes."}finally{loading=false}
    }
    LaunchedEffect(playingEpisode?.url){
        val ep=playingEpisode ?: return@LaunchedEffect
        playerLoading=true; playerError=null
        try {
            val servers=withContext(Dispatchers.IO){fetchPlayerServers(ep.url)}
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if(servers.isEmpty()) playerError="No public player was found for this episode. Try again."
            else { val ready=ep.copy(servers=servers); playingEpisode=ready; saveContinueWatching(context,ready) }
        } catch(e:kotlinx.coroutines.CancellationException){ throw e }
        catch(e:Exception){ playerError="Could not load this episode player: ${e.message ?: "network error"}" }
        finally { playerLoading=false }
    }
    if(playingEpisode!=null){BackHandler(enabled=true){playingEpisode=null};if(playerLoading){Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){CircularProgressIndicator()}}else if(playerError!=null){Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("Player unavailable",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Text(playerError!!,color=Color.Gray);Spacer(Modifier.height(18.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){TextButton(onClick={playerError=null;playingEpisode=playingEpisode?.copy()}){Text("Retry")};TextButton(onClick={playingEpisode=null;playerError=null}){Text("Back")}}}}}else{SourcePlayer(playingEpisode!!,eps,onBack={playingEpisode=null},onEpisode={playingEpisode=it})};return}
    chosen?.let{ep->AlertDialog(onDismissRequest={chosen=null},title={Text("Episode "+ep.number)},text={Text("Open this episode in AniSteam Player. Playback uses a publicly exposed media URL when AnimeSalt provides one.")},confirmButton={TextButton(onClick={chosen=null;playingEpisode=ep}){Text("Play")}},dismissButton={TextButton(onClick={chosen=null}){Text("Cancel")}})}
    Column(Modifier.fillMaxSize().background(colors.background)){
        TextButton(onClick=onBack,modifier=Modifier.padding(8.dp)){Text("‹ Back")}
        when{
            loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            error!=null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(error!!,color=Color.LightGray)}
            anime!=null->LazyColumn(contentPadding=PaddingValues(bottom=24.dp)){
                item{
                    Row(Modifier.padding(18.dp)){AsyncImage(anime!!.image,anime!!.title,Modifier.width(135.dp).height(195.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop);Spacer(Modifier.width(16.dp));Column{Text(anime!!.title,color=Color.White,fontSize=21.sp,fontWeight=FontWeight.Bold);Text(anime!!.type+" • "+anime!!.episodes+" eps",color=Color.Gray);Text("★ "+anime!!.score,color=colors.secondary)}}
                    Text("Synopsis",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=18.dp));Text(anime!!.synopsis.ifBlank{"No synopsis available."},color=Color(0xFFB8B8C0),modifier=Modifier.padding(18.dp));Text("Episodes",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(18.dp,8.dp));if(anime!!.seasons>1){Row(Modifier.padding(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..anime!!.seasons).forEach{s->TextButton(onClick={season=s},enabled=season!=s){Text("Season $s")}}};}
                }
                items(eps){ep->Row(Modifier.padding(horizontal=18.dp,vertical=5.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF111116)).clickable{chosen=ep}.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(ep.number.toString(),color=colors.secondary,fontWeight=FontWeight.Bold,modifier=Modifier.width(42.dp));Column(Modifier.weight(1f)){Text(if(ep.title.isBlank())"Episode "+ep.number else ep.title,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=2);if(ep.aired.isNotBlank())Text(ep.aired,color=Color.Gray,fontSize=11.sp)};Text("▶",color=colors.primary)}}
            }
        }
    }
}


private fun saveContinueWatching(context:Context,ep:Episode){context.getSharedPreferences("anisteam_watch",Context.MODE_PRIVATE).edit().putInt("number",ep.number).putString("title",ep.title).putString("url",ep.url).putString("slug",ep.animeSlug).apply()}
private fun loadContinueWatching(context:Context):Episode?{val p=context.getSharedPreferences("anisteam_watch",Context.MODE_PRIVATE);val u=p.getString("url",null)?:return null;return Episode(p.getInt("number",1),p.getString("title","Episode")?:"Episode","",u,animeSlug=p.getString("slug","")?:"")}
private const val BASE_URL = "https://animesalt.cx"

@Composable private fun SourceDialog(onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Sources")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SOURCES.forEach { source ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF17171D)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(source.name,color=Color.White,fontWeight=FontWeight.Bold);Text(source.baseUrl,color=Color.Gray,fontSize=11.sp)};Text(if(source.enabled)"ON" else "OFF",color=colors.secondary,fontWeight=FontWeight.Bold)}
    }}},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}

private suspend fun fetchAnime(q:String)=withContext(Dispatchers.IO){
    if(q.isBlank()) return@withContext fetchHome()
    val encoded=URLEncoder.encode(q,"UTF-8")
    val doc=getHtml("$BASE_URL/?s=$encoded")
    extractSeries(doc).take(50)
}

private suspend fun fetchHome()=withContext(Dispatchers.IO){
    val doc=getHtml(BASE_URL)
    extractSeries(doc).distinctBy { it.synopsis }.take(50)
}

private fun extractSeries(doc:org.jsoup.nodes.Document):List<Anime>{
    return doc.select("a[href]").mapNotNull { a ->
        val href=a.absUrl("href").ifBlank { a.attr("href") }
        val marker="/series/"
        val index=href.indexOf(marker)
        if(index<0) return@mapNotNull null
        val slug=href.substring(index+marker.length).trim('/').substringBefore('?').substringBefore('#')
        if(slug.isBlank()) return@mapNotNull null
        val container=a.closest("li, article, .item, .row, .item-list")
        val title=(a.selectFirst("h2,h3,h4,.entry-title,.title")?.text() ?: container?.selectFirst("h2,h3,h4,.entry-title,.title")?.text() ?: a.text()).trim()
        if(title.isBlank()) return@mapNotNull null
        val image=posterFrom(a.selectFirst("img") ?: container?.selectFirst("img"))
        Anime(slug.hashCode(),title,image,"—","?","Anime",slug,"animesalt",1,slug)
    }.distinctBy{it.synopsis}
}


private suspend fun fetchDetailsBySlug(slug:String)=withContext(Dispatchers.IO){
    val doc=getHtml("$BASE_URL/series/$slug/")
    val title=doc.selectFirst(".fg1 .entry-title, h1.entry-title, h1")?.text()?.trim().orEmpty()
    if(title.isBlank()) throw IllegalStateException("Anime not found")
    val description=doc.selectFirst(".description, .desc, [class*=description]")
    val overview=description?.selectFirst("p")?.text()?.trim().orEmpty()
    val language=description?.select("p")?.firstOrNull { it.text().contains("Language:") }?.text()?.substringAfter("Language:")?.trim().orEmpty()
    val quality=description?.select("p")?.firstOrNull { it.text().contains("Quality:") }?.text()?.substringAfter("Quality:")?.trim().orEmpty()
    val genres=doc.select(".genres a, [class*=genre] a").map { it.text().trim() }.filter { it.isNotBlank() }
    val year=doc.selectFirst(".year")?.text()?.trim().orEmpty()
    val seasonCount=Regex("(\\d+)\\s+Seasons?",RegexOption.IGNORE_CASE).find(doc.text())?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
    val episodes=Regex("(\\d+)\\s+Episodes?",RegexOption.IGNORE_CASE).find(doc.text())?.groupValues?.getOrNull(1) ?: "?"
    val rating=doc.selectFirst(".vote-cn .vote")?.text()?.replace("TMDB","")?.trim().orEmpty()
    val poster=posterFrom(doc.selectFirst(".post-thumbnail img, .poster img, img[src*=tmdb], img[data-src*=tmdb]"))
    Anime(slug.hashCode(),title,poster,rating,episodes,"Anime",slug,"animesalt",seasonCount,slug)
}

private suspend fun fetchEpisodesBySlug(slug:String,season:Int=1)=withContext(Dispatchers.IO){
    val pageUrl="$BASE_URL/episode/$slug-${season}x1/"
    val doc=getHtml(pageUrl)
    val playerUrl=doc.selectFirst("iframe[src]")?.absUrl("src").orEmpty()
    val nodes=doc.select("#episode_by_temp li, a[href*=/episode/]")
    nodes.mapNotNull { el ->
        val text=el.text().trim()
        val raw=el.selectFirst(".num-epi")?.text()?.trim() ?: Regex("${season}x(\\d+)").find(text)?.value.orEmpty()
        val ep=Regex("${season}x(\\d+)").find(raw)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: el.selectFirst(".num-epi")?.text()?.filter{it.isDigit()}?.toIntOrNull() ?: return@mapNotNull null
        val title=(el.selectFirst(".entry-title,h2,h3")?.text() ?: text.replace(Regex("${season}x\\d+"),"").trim()).trim()
        Episode(ep,title,el.selectFirst("img")?.attr("alt").orEmpty(),el.absUrl("href").ifBlank { el.selectFirst("a[href]")?.absUrl("href").orEmpty() },animeSlug=slug)
    }.distinctBy{it.number}.sortedBy{it.number}
}

@Composable private fun SourcePlayer(episode:Episode,episodes:List<Episode>,onBack:()->Unit,onEpisode:(Episode)->Unit){
    val context=LocalContext.current; val activity=context as? ComponentActivity
    val direct=episode.servers.firstOrNull{isDirectMediaUrl(it.url)}
    var selectedServer by remember(episode.url){mutableStateOf(episode.servers.firstOrNull())}
    var fullscreen by remember{mutableStateOf(false)}
    val index=episodes.indexOfFirst{it.number==episode.number}; val previous=episodes.getOrNull(index-1); val next=episodes.getOrNull(index+1)
    BackHandler{if(fullscreen){fullscreen=false;activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED}else onBack()}
    if(selectedServer==null){Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("No public player found",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold);Text("This episode exists, but AnimeSalt did not expose a playable public source.",color=Color.Gray,modifier=Modifier.padding(18.dp));TextButton(onClick=onBack){Text("Back")}}};return}
    if(direct!=null && selectedServer?.url==direct.url){
        val player=remember(direct.url){ExoPlayer.Builder(context).build().apply{setMediaItem(MediaItem.fromUri(direct.url));prepare();seekTo(loadWatchPosition(context,episode.url));playWhenReady=true}}
        DisposableEffect(player){onDispose{saveWatchPosition(context,episode.url,player.currentPosition);player.release()}}
        Column(Modifier.fillMaxSize().background(Color.Black)){PlayerHeader(episode,onBack,{fullscreen=!fullscreen;activity?.requestedOrientation=if(fullscreen)ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED});AndroidView(factory={ctx->PlayerView(ctx).apply{useController=true;setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)}},update={it.player=player},modifier=Modifier.fillMaxWidth().weight(1f));ServerPicker(episode.servers,selectedServer!!){selectedServer=it};EpisodeNav(previous,next,onEpisode,episode)}
    }else{
        val server=selectedServer!!
        Column(Modifier.fillMaxSize().background(Color.Black)){
            AndroidView(factory={ctx->android.webkit.WebView(ctx).apply{settings.javaScriptEnabled=true;settings.domStorageEnabled=true;settings.mediaPlaybackRequiresUserGesture=false;webViewClient=WebViewClient();webChromeClient=WebChromeClient();setBackgroundColor(android.graphics.Color.BLACK);loadUrl(server.url,mapOf("Referer" to BASE_URL+"/"))}},update={if(it.url!=server.url)it.loadUrl(server.url,mapOf("Referer" to BASE_URL+"/"))},modifier=Modifier.fillMaxWidth().weight(1f))
            Column(Modifier.fillMaxWidth().background(Color(0xFF0D0D12))){PlayerHeader(episode,onBack,{fullscreen=!fullscreen;activity?.requestedOrientation=if(fullscreen)ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED});ServerPicker(episode.servers,selectedServer!!){selectedServer=it};EpisodeNav(previous,next,onEpisode,episode)}
        }
    }
@Composable private fun PlayerHeader(episode:Episode,onBack:()->Unit,onFullscreen:()->Unit){Row(Modifier.fillMaxWidth().background(Color(0xFF0D0D12)).padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("‹ Back",color=Color.White)};Column(Modifier.weight(1f)){Text("AniSteam Player",color=Color.White,fontWeight=FontWeight.Bold);Text("Episode ${episode.number} • ${episode.title.ifBlank{"AnimeSalt"}}",color=Color.Gray,fontSize=11.sp,maxLines=1)};TextButton(onClick=onFullscreen){Text("FULL",color=Color.White,fontSize=11.sp)}}}
@Composable private fun ServerPicker(servers:List<PlayerServer>,selected:PlayerServer,onSelect:(PlayerServer)->Unit){if(servers.size>1)Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=7.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){servers.forEachIndexed{index,server->Text(server.name.ifBlank{"Server ${index+1}"},modifier=Modifier.clip(RoundedCornerShape(20.dp)).background(if(server.url==selected.url)colors.primary else Color(0xFF202027)).clickable{onSelect(server)}.padding(horizontal=14.dp,vertical=8.dp),color=Color.White,fontSize=12.sp,fontWeight=FontWeight.SemiBold)}}}
@Composable private fun EpisodeNav(previous:Episode?,next:Episode?,onEpisode:(Episode)->Unit,episode:Episode){Row(Modifier.fillMaxWidth().padding(7.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){TextButton(onClick={previous?.let(onEpisode)},enabled=previous!=null){Text("Previous")};Text("EP ${episode.number}",color=Color.White,fontWeight=FontWeight.Bold);TextButton(onClick={next?.let(onEpisode)},enabled=next!=null){Text("Next")}}}
private fun loadWatchPosition(context:Context,url:String):Long=context.getSharedPreferences("anisteam_watch",Context.MODE_PRIVATE).getLong("pos_"+url.hashCode(),0L)
private fun saveWatchPosition(context:Context,url:String,position:Long){if(url.isNotBlank())context.getSharedPreferences("anisteam_watch",Context.MODE_PRIVATE).edit().putLong("pos_"+url.hashCode(),position.coerceAtLeast(0L)).apply()}
private fun isDirectMediaUrl(url:String):Boolean{
    val u=url.substringBefore('?').lowercase()
    return u.endsWith(".m3u8")||u.endsWith(".mp4")||u.endsWith(".webm")||u.endsWith(".mpd")
}
private fun fetchPlayerServers(url:String):List<PlayerServer>{
    val page=getHtml(url)
    val result=mutableListOf<PlayerServer>()

    fun add(name:String,target:String){
        if(target.isBlank()) return
        val cleaned=target.replace("\\u0026","&").replace("\\/","/").replace("&amp;","&").trim().trim('\'', '"')
        val absolute=try{URL(url).let{base->URL(base,cleaned).toString()}}catch(_:Exception){cleaned}
        if(!absolute.startsWith("http",true)) return
        if(result.none{it.url==absolute}) result.add(PlayerServer(name,absolute))
    }

    page.select("iframe[src], iframe[data-src]").forEach { iframe ->
        val target=iframe.attr("data-src").ifBlank { iframe.attr("src") }
        if(target.isBlank()) return@forEach
        val data=try{Uri.parse(target).getQueryParameter("data")}catch(_:Exception){null}
        if(!data.isNullOrBlank()){
            try{
                val decoded=Base64.decode(data,Base64.DEFAULT).toString(Charsets.UTF_8)
                val entries=JSONArray(decoded)
                for(i in 0 until entries.length()){
                    val item=entries.optJSONObject(i) ?: continue
                    val language=item.optString("language").trim()
                    val player=item.optString("link").trim()
                    if(player.isNotBlank()) add(if(language.isNotBlank()) language else "Server "+(i+1),player)
                }
            }catch(_:Exception){ add("Embedded",target) }
        }else{
            val lower=target.lowercase()
            add(when{
                lower.contains("mystream")->"MyStream"
                lower.contains("abyss")->"Abyss"
                isDirectMediaUrl(target)->"Direct"
                else->"Embedded"
            },target)
        }
    }

    page.select("video[src], video source[src], source[src]").forEach { el ->
        val target=el.absUrl("src").ifBlank{el.attr("src")}
        if(target.isNotBlank()) add(if(isDirectMediaUrl(target)) "Direct" else "Embedded",target)
    }

    page.select("[data-src],[data-url],[data-embed],[data-player],[onclick]").forEach { el ->
        val context=el.outerHtml()
        val label=el.text().trim()
        val name=when{
            label.contains("mystream",true)||context.contains("mystream",true)->"MyStream"
            label.contains("abyss",true)||context.contains("abyss",true)->"Abyss"
            else->"Embedded"
        }
        listOf(el.attr("data-src"),el.attr("data-url"),el.attr("data-embed"),el.attr("data-player"),el.attr("onclick")).forEach { value ->
            Regex("""https?://[^\\s"'<>\\\\]+""").findAll(value).forEach { match -> add(name,match.value.trimEnd(')', ';', ',')) }
        }
    }
    return result.distinctBy{it.url}
}
private fun getHtml(url:String):org.jsoup.nodes.Document{
    return org.jsoup.Jsoup.connect(url)
        .userAgent("Mozilla/5.0 (Android) AppleWebKit/537.36 Chrome/121 Mobile Safari/537.36")
        .header("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .header("Accept-Language","en-US,en;q=0.9")
        .referrer("$BASE_URL/")
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
