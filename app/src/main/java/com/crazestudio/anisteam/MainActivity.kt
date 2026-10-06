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
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val SOURCE = "https://animesalt.cx"

data class Anime(val id:Int,val title:String,val image:String,val score:String,val episodes:String,val type:String,val synopsis:String="",val sourceId:String="animesalt",val seasons:Int=1,val slug:String="")
data class AnimeSource(val id:String,val name:String,val baseUrl:String,val enabled:Boolean=true)
data class Episode(val number:Int,val title:String,val aired:String,val url:String="",val playerUrl:String="")

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
    var data by remember { mutableStateOf<List<Anime>>(emptyList()) }; var loading by remember { mutableStateOf(true) }; var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(submitted) { loading=true;error=null;try{data=fetchAnime(submitted)}catch(e:Exception){error=e.message?:"Unable to load anime."}finally{loading=false} }
    Column(Modifier.fillMaxSize().background(colors.background)) {
        Column(Modifier.padding(18.dp)) {
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
    LaunchedEffect(slug){loading=true;try{anime=fetchDetailsBySlug(slug);season=1;eps=fetchEpisodesBySlug(slug,1)}catch(e:Exception){error=e.message?:"Unable to load details."}finally{loading=false}}\n    LaunchedEffect(playingEpisode){ val ep=playingEpisode ?: return@LaunchedEffect; playerLoading=true; playerError=null; val ok=withContext(Dispatchers.IO){checkEpisodeAvailable(ep.url)}; if(!ok) playerError="AnimeSalt did not expose a playable iframe for this episode."; playerLoading=false }
    if(playingEpisode!=null){if(playerLoading){Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){CircularProgressIndicator()}}else if(playerError!=null){Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("Anime not available in our server",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Text(playerError!!,color=Color.Gray);Spacer(Modifier.height(18.dp));TextButton(onClick={playingEpisode=null;playerError=null}){Text("Back")}}}}else{SourcePlayer(playingEpisode!!.url){playingEpisode=null}};return}
    chosen?.let{ep->AlertDialog(onDismissRequest={chosen=null},title={Text("Episode "+ep.number)},text={Text("Check AnimeSalt and open its real embedded player.")},confirmButton={TextButton(onClick={chosen=null;playingEpisode=ep}){Text("Play")}},dismissButton={TextButton(onClick={chosen=null}){Text("Cancel")}})}
    Column(Modifier.fillMaxSize().background(colors.background)){
        TextButton(onClick=onBack,modifier=Modifier.padding(8.dp)){Text("‹ Back")}
        when{
            loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            error!=null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(error!!,color=Color.LightGray)}
            anime!=null->LazyColumn(contentPadding=PaddingValues(bottom=24.dp)){
                item{
                    Row(Modifier.padding(18.dp)){AsyncImage(anime!!.image,anime!!.title,Modifier.width(135.dp).height(195.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop);Spacer(Modifier.width(16.dp));Column{Text(anime!!.title,color=Color.White,fontSize=21.sp,fontWeight=FontWeight.Bold);Text(anime!!.type+" • "+anime!!.episodes+" eps",color=Color.Gray);Text("★ "+anime!!.score,color=colors.secondary)}}
                    Text("Synopsis",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=18.dp));Text(anime!!.synopsis.ifBlank{"No synopsis available."},color=Color(0xFFB8B8C0),modifier=Modifier.padding(18.dp));Text("Episodes",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(18.dp,8.dp));if(anime!!.seasons>1){Row(Modifier.padding(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){(1..anime!!.seasons).forEach{s->TextButton(onClick={season=s;loading=true;error=null},enabled=season!=s){Text("Season $s")}}};LaunchedEffect(season){try{loading=true;error=null;eps=fetchEpisodesBySlug(slug,season)}catch(e:Exception){error=e.message ?: "Unable to load episodes."}finally{loading=false}}}
                }
                items(eps){ep->Row(Modifier.padding(horizontal=18.dp,vertical=5.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF111116)).clickable{chosen=ep}.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Text(ep.number.toString(),color=colors.secondary,fontWeight=FontWeight.Bold,modifier=Modifier.width(42.dp));Column(Modifier.weight(1f)){Text(if(ep.title.isBlank())"Episode "+ep.number else ep.title,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=2);if(ep.aired.isNotBlank())Text(ep.aired,color=Color.Gray,fontSize=11.sp)};Text("▶",color=colors.primary)}}
            }
        }
    }
}

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
        Episode(ep,title,el.selectFirst("img")?.attr("alt").orEmpty(),el.absUrl("href").ifBlank { el.selectFirst("a[href]")?.absUrl("href").orEmpty() })
    }.distinctBy{it.number}.sortedBy{it.number}
}

private fun checkEpisodeAvailable(url:String):Boolean{ return try{ getHtml(url).selectFirst("iframe[src]") != null }catch(_:Exception){ false } }\n\n@Composable private fun SourcePlayer(url:String,onBack:()->Unit){
    var failed by remember(url){mutableStateOf<String?>(null)}
    Column(Modifier.fillMaxSize().background(Color.Black)){
        Row(Modifier.fillMaxWidth().padding(6.dp),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick=onBack){Text("‹ Back",color=Color.White)}
            Text("AnimeSalt Player",color=Color.White,fontWeight=FontWeight.Bold)
        }
        if(failed!=null){
            Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                Text("Player failed to load",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(failed!!,color=Color.LightGray)
                Spacer(Modifier.height(16.dp))
                TextButton(onClick={failed=null}){Text("Retry")}
            }
        }else{
            AndroidView(factory={context->
                android.webkit.WebView(context).apply{
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled=true
                    settings.domStorageEnabled=true
                    settings.databaseEnabled=true
                    settings.mediaPlaybackRequiresUserGesture=false
                    settings.loadsImagesAutomatically=true
                    settings.allowFileAccess=true
                    settings.allowContentAccess=true
                    settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this,true)
                    webViewClient=object:android.webkit.WebViewClient(){
                        override fun onReceivedError(view:android.webkit.WebView,request:android.webkit.WebResourceRequest,error:android.webkit.WebResourceError){
                            if(request.isForMainFrame) failed=error.description?.toString() ?: "Network error"
                        }
                    }
                    webChromeClient=android.webkit.WebChromeClient()
                    loadUrl(url,mapOf("Referer" to "$BASE_URL/"))
                }
            },modifier=Modifier.fillMaxSize())
        }
    }
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
