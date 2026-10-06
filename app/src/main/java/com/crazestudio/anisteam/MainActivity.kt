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
data class Episode(val number:Int,val title:String,val aired:String,val url:String="",val servers:List<PlayerServer> = emptyList(),val animeSlug:String="")

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } }
}

private val SOURCES = listOf(AnimeSource("animesalt","AnimeSalt","https://animesalt.cx"))

private val colors = darkColorScheme(background=Color(0xFF050509),surface=Color(0xFF101018),surfaceVariant=Color(0xFF191923),primary=Color(0xFFA56CFF),secondary=Color(0xFFE0D3FF))

@Composable fun App() {
    MaterialTheme(colorScheme=colors) {
        var selected by remember { mutableStateOf<String?>(null) }
        var showSources by remember { mutableStateOf(false) }
        if (selected == null) Home(onOpen = { slug -> selected=slug }, onSources = { showSources=true }) else Details(selected!!){ selected=null }
        if (showSources) SourceDialog(onDismiss = { showSources=false })
    }
}

@Composable private fun Home(onOpen:(String)->Unit,onSources:()->Unit) {
    var q by remember{mutableStateOf("")}
    var submitted by remember{mutableStateOf("")}
    val context=LocalContext.current
    var data by remember{mutableStateOf<List<Anime>>(emptyList())}
    val continueWatching=remember{loadContinueWatching(context)}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(submitted){
        loading=true;error=null
        try{data=fetchAnime(submitted)}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error=e.message?:"Unable to load anime."}
        finally{loading=false}
    }
    val featured=data.firstOrNull()
    LazyColumn(Modifier.fillMaxSize().background(colors.background),contentPadding=PaddingValues(bottom=90.dp)){
        item{
            Column(Modifier.padding(18.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("ANISTEAM",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.8.sp)
                        Text("Watch what you love.",color=Color(0xFF8E8E9A),fontSize=12.sp)
                    }
                    TextButton(onClick=onSources){Text("SOURCE",color=colors.primary,fontSize=11.sp,fontWeight=FontWeight.Bold)}
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(value=q,onValueChange={q=it},modifier=Modifier.fillMaxWidth(),singleLine=true,shape=RoundedCornerShape(18.dp),placeholder={Text("Search anime...",color=Color(0xFF777781))},trailingIcon={
                    if(q.isNotBlank())TextButton(onClick={submitted=q.trim()}){Text("SEARCH",color=colors.primary,fontSize=11.sp,fontWeight=FontWeight.Bold)}
                })
            }
        }
        if(!loading&&error==null&&featured!=null&&submitted.isBlank()){
            item{
                Column(Modifier.padding(horizontal=18.dp)){
                    Text("FEATURED",color=colors.primary,fontSize=11.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp)
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(310.dp).clip(RoundedCornerShape(24.dp)).clickable{onOpen(featured.slug)}){
                        AsyncImage(featured.image,featured.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        Box(Modifier.fillMaxSize().background(Color(0x88000000)))
                        Column(Modifier.align(Alignment.BottomStart).padding(20.dp)){
                            Text(featured.title,color=Color.White,fontSize=26.sp,fontWeight=FontWeight.ExtraBold,maxLines=2)
                            Text(featured.type+" • "+featured.episodes+" eps • ★ "+featured.score,color=Color(0xFFE5E5EA),fontSize=12.sp)
                        }
                    }
                }
            }
        }
        if(continueWatching!=null&&continueWatching.animeSlug.isNotBlank()&&submitted.isBlank()){
            item{
                Column(Modifier.padding(18.dp)){
                    Text("CONTINUE WATCHING",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color(0xFF14141B)).clickable{onOpen(continueWatching.animeSlug)}.padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                        Text("▶",color=colors.primary,fontSize=20.sp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)){
                            Text("Episode "+continueWatching.number,color=Color.White,fontWeight=FontWeight.Bold)
                            Text(continueWatching.title.ifBlank{"Continue watching"},color=Color(0xFF92929D),fontSize=11.sp,maxLines=1)
                        }
                        Text("›",color=Color.White,fontSize=25.sp)
                    }
                }
            }
        }
        item{
            Text(if(submitted.isBlank())"DISCOVER" else "SEARCH RESULTS",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=18.dp,vertical=6.dp))
        }
        when{
            loading->item{Box(Modifier.fillMaxWidth().height(260.dp),contentAlignment=Alignment.Center){CircularProgressIndicator(color=colors.primary)}}
            error!=null->item{Box(Modifier.fillMaxWidth().padding(28.dp),contentAlignment=Alignment.Center){Text(error!!,color=Color.LightGray)}}
            data.isEmpty()->item{Box(Modifier.fillMaxWidth().height(220.dp),contentAlignment=Alignment.Center){Text("No anime found.",color=Color(0xFF8C8C96))}}
            else->{
                val visible=data.drop(if(submitted.isBlank())1 else 0)
                items(visible){anime->
                    Row(Modifier.padding(horizontal=18.dp,vertical=5.dp).fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(Color(0xFF121218)).clickable{onOpen(anime.slug)}.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                        AsyncImage(anime.image,anime.title,Modifier.size(76.dp,100.dp).clip(RoundedCornerShape(11.dp)),contentScale=ContentScale.Crop)
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)){
                            Text(anime.title,color=Color.White,fontWeight=FontWeight.Bold,fontSize=15.sp,maxLines=2)
                            Text(anime.type+" • "+anime.episodes+" eps",color=Color(0xFF858590),fontSize=11.sp)
                            Text("★ "+anime.score,color=colors.secondary,fontSize=11.sp,fontWeight=FontWeight.Bold)
                        }
                        Text("›",color=Color(0xFF777781),fontSize=25.sp)
                    }
                }
            }
        }
    }
}

@Composable private fun Details(slug:String,onBack:()->Unit) {
    val context=LocalContext.current; var anime by remember{mutableStateOf<Anime?>(null)};var eps by remember{mutableStateOf<List<Episode>>(emptyList())};var loading by remember{mutableStateOf(true)};var error by remember{mutableStateOf<String?>(null)};var chosen by remember{mutableStateOf<Episode?>(null)};var season by remember{mutableIntStateOf(1)};var playingEpisode by remember{mutableStateOf<Episode?>(null)};var playerLoading by remember{mutableStateOf(false)};var playerError by remember{mutableStateOf<String?>(null)}
    LaunchedEffect(slug){loading=true;error=null;eps=emptyList();season=1;try{anime=fetchDetailsBySlug(slug)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=e.message?:"Unable to load details."}finally{loading=false}}
    LaunchedEffect(slug,anime,season){if(anime==null)return@LaunchedEffect;loading=true;error=null;try{eps=fetchEpisodesBySlug(slug,season)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=e.message?:"Unable to load episodes."}finally{loading=false}}
    LaunchedEffect(playingEpisode?.url){val ep=playingEpisode?:return@LaunchedEffect;playerLoading=true;playerError=null;try{val servers=withContext(Dispatchers.IO){fetchPlayerServers(ep.url)};kotlinx.coroutines.currentCoroutineContext().ensureActive();if(servers.isEmpty())playerError="No public player was found for this episode. Try again." else{val ready=ep.copy(servers=servers);playingEpisode=ready;saveContinueWatching(context,ready)}}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){playerError="Could not load this episode player: "+(e.message?:"network error")}finally{playerLoading=false}}
    if(playingEpisode!=null){BackHandler(enabled=true){playingEpisode=null};if(playerLoading)Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){CircularProgressIndicator(color=colors.primary)}else if(playerError!=null)Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text("Player unavailable",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(10.dp));Text(playerError!!,color=Color.Gray);Spacer(Modifier.height(18.dp));Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){TextButton(onClick={playerError=null;playingEpisode=playingEpisode?.copy()}){Text("Retry")};TextButton(onClick={playingEpisode=null;playerError=null}){Text("Back")}}}}else SourcePlayer(playingEpisode!!,eps,onBack={playingEpisode=null},onEpisode={playingEpisode=it});return}
    chosen?.let{ep->AlertDialog(onDismissRequest={chosen=null},title={Text("Episode "+ep.number)},text={Text("Open the real AnimeSalt server page for this episode. AniSteam embeds the server's own JWPlayer.")},confirmButton={TextButton(onClick={chosen=null;playingEpisode=ep}){Text("Open Player")}},dismissButton={TextButton(onClick={chosen=null}){Text("Cancel")}})}
    Column(Modifier.fillMaxSize().background(colors.background)){
        Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("‹  BACK",color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold)};Spacer(Modifier.weight(1f));Text("ANIME",color=colors.primary,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)}
        when{
            loading->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(color=colors.primary)}
            error!=null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(error!!,color=Color.LightGray)}
            anime!=null->LazyColumn(contentPadding=PaddingValues(bottom=30.dp)){
                item{Column(Modifier.padding(horizontal=18.dp)){Box(Modifier.fillMaxWidth().height(350.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF17171D))){AsyncImage(anime!!.image,anime!!.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop);Box(Modifier.fillMaxSize().background(Color(0x99000000)));Column(Modifier.align(Alignment.BottomStart).padding(20.dp)){Text(anime!!.title,color=Color.White,fontSize=28.sp,fontWeight=FontWeight.ExtraBold,maxLines=3);Spacer(Modifier.height(6.dp));Text(anime!!.type+" • "+anime!!.episodes+" eps • ★ "+anime!!.score,color=Color(0xFFE6E6EC),fontSize=12.sp)}};Spacer(Modifier.height(18.dp));Text("OVERVIEW",color=colors.primary,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.7.sp);Spacer(Modifier.height(7.dp));Text(anime!!.synopsis.ifBlank{"No synopsis available."},color=Color(0xFFB8B8C0),fontSize=13.sp,lineHeight=19.sp);Spacer(Modifier.height(20.dp));Row(verticalAlignment=Alignment.CenterVertically){Text("EPISODES",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Text(eps.size.toString()+" loaded",color=Color(0xFF777781),fontSize=11.sp)};Spacer(Modifier.height(10.dp));if(anime!!.seasons>1){Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFF121218)).padding(5.dp),horizontalArrangement=Arrangement.spacedBy(5.dp)){(1..anime!!.seasons).forEach{s->Text("S"+s,color=if(season==s)Color.White else Color(0xFF777781),fontWeight=FontWeight.Bold,fontSize=12.sp,modifier=Modifier.clip(RoundedCornerShape(11.dp)).background(if(season==s)colors.primary else Color.Transparent).clickable{season=s}.padding(horizontal=15.dp,vertical=9.dp))}};Spacer(Modifier.height(12.dp))}}}
                items(eps){ep->Row(Modifier.padding(horizontal=18.dp,vertical=5.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF121218)).clickable{chosen=ep}.padding(13.dp),verticalAlignment=Alignment.CenterVertically){Text(ep.number.toString().padStart(2,48.toChar()),color=colors.secondary,fontWeight=FontWeight.ExtraBold,fontSize=14.sp,modifier=Modifier.width(42.dp));Column(Modifier.weight(1f)){Text(if(ep.title.isBlank())"Episode "+ep.number else ep.title,color=Color.White,fontWeight=FontWeight.SemiBold,maxLines=2);if(ep.aired.isNotBlank())Text(ep.aired,color=Color(0xFF777781),fontSize=10.sp)};Text("▶",color=colors.primary,fontSize=13.sp)}}
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
    val seriesUrl="$BASE_URL/series/$slug/"
    val seriesDoc=getHtml(seriesUrl)
    fun seasonDocument(targetSeason:Int):org.jsoup.nodes.Document{
        if(targetSeason==1) return seriesDoc
        val button=seriesDoc.selectFirst(".season-btn[data-season=\"$targetSeason\"][data-post]")
        val postId=button?.attr("data-post").orEmpty()
        if(postId.isBlank()) return org.jsoup.Jsoup.parse("")
        return getHtml("$BASE_URL/wp-admin/admin-ajax.php?action=action_select_season&season=$targetSeason&post=$postId")
    }
    val currentDoc=seasonDocument(season)
    val previousSeasonCount=(1 until season).sumOf { previousSeason ->
        parseEpisodeList(seasonDocument(previousSeason),slug,previousSeason).size
    }
    parseEpisodeList(currentDoc,slug,season,previousSeasonCount)
}
private fun parseEpisodeList(doc:org.jsoup.nodes.Document,slug:String,season:Int,episodeOffset:Int=0):List<Episode>{
    val nodes=doc.select("#episode_by_temp li, #episode_by_temp_non_dubbed li, a[href*=/episode/]")
    return nodes.mapNotNull { el ->
        val link=if(el.tagName()=="a" && el.attr("href").contains("/episode/")){
            el
        }else{
            el.selectFirst("a[href*=/episode/], a.lnk-blk")
        } ?: return@mapNotNull null
        val href=link.absUrl("href").ifBlank{link.attr("href")}.trim()
        if(href.isBlank()) return@mapNotNull null
        val path=try{Uri.parse(href).path.orEmpty()}catch(_:Exception){href}
        val seasonEpisode=Regex("/episode/[^/]+-(\\d+)x(\\d+)/?",RegexOption.IGNORE_CASE).find(path)
        val urlSeason=seasonEpisode?.groupValues?.getOrNull(1)?.toIntOrNull()
        val seasonEpisodeNumber=seasonEpisode?.groupValues?.getOrNull(2)?.toIntOrNull() ?: return@mapNotNull null
        if(urlSeason!=season) return@mapNotNull null
        val title=(el.selectFirst(".entry-title,h2,h3")?.text()
            ?: el.text().replace(Regex("\\b" + season + "x\\d+\\b"),"").trim()).trim()
        Episode(seasonEpisodeNumber+episodeOffset,title,el.selectFirst("img")?.attr("alt").orEmpty(),href,animeSlug=slug)
    }.distinctBy{it.url}.sortedBy{it.number}
}

@Composable private fun SourcePlayer(episode:Episode,episodes:List<Episode>,onBack:()->Unit,onEpisode:(Episode)->Unit){
    val context=LocalContext.current
    val activity=context as? ComponentActivity
    var selectedServer by remember(episode.url){mutableStateOf(episode.servers.firstOrNull())}
    var fullscreen by remember{mutableStateOf(false)}
    val index=episodes.indexOfFirst{it.number==episode.number}
    val previous=episodes.getOrNull(index-1)
    val next=episodes.getOrNull(index+1)

    BackHandler{
        if(fullscreen){
            fullscreen=false
            activity?.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }else onBack()
    }

    val server=selectedServer
    if(server==null){
        Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){
            Column(horizontalAlignment=Alignment.CenterHorizontally){
                Text("No public player found",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.Bold)
                Text("This episode exists, but AnimeSalt did not expose a playable server page.",color=Color.Gray,modifier=Modifier.padding(18.dp))
                TextButton(onClick=onBack){Text("Back")}
            }
        }
        return
    }

    Column(Modifier.fillMaxSize().background(Color.Black)){
        AndroidView(
            factory={ctx->
                android.webkit.WebView(ctx).apply{
                    settings.javaScriptEnabled=true
                    settings.domStorageEnabled=true
                    settings.mediaPlaybackRequiresUserGesture=false
                    settings.loadsImagesAutomatically=true
                    settings.javaScriptCanOpenWindowsAutomatically=true
                    settings.setSupportMultipleWindows(false)
                    webViewClient=WebViewClient()
                    webChromeClient=WebChromeClient()
                    setBackgroundColor(android.graphics.Color.BLACK)
                    loadUrl(server.url,mapOf("Referer" to BASE_URL+"/"))
                }
            },
            update={webView->
                if(webView.url!=server.url){
                    webView.loadUrl(server.url,mapOf("Referer" to BASE_URL+"/"))
                }
            },
            modifier=Modifier.fillMaxWidth().weight(1f)
        )
        Column(Modifier.fillMaxWidth().background(Color(0xFF0D0D12))){
            PlayerHeader(
                episode,
                onBack,
                {
                    fullscreen=!fullscreen
                    activity?.requestedOrientation=
                        if(fullscreen) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            )
            ServerPicker(episode.servers,server){selectedServer=it}
            EpisodeNav(previous,next,onEpisode,episode)
        }
    }
}

@Composable private fun PlayerHeader(episode:Episode,onBack:()->Unit,onFullscreen:()->Unit){Row(Modifier.fillMaxWidth().background(Color(0xFF0D0D12)).padding(horizontal=8.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically){TextButton(onClick=onBack){Text("‹ Back",color=Color.White)};Column(Modifier.weight(1f)){Text("AniSteam Player",color=Color.White,fontWeight=FontWeight.Bold);Text("Episode ${episode.number} • ${episode.title.ifBlank{"AnimeSalt"}}",color=Color.Gray,fontSize=11.sp,maxLines=1)};TextButton(onClick=onFullscreen){Text("FULL",color=Color.White,fontSize=11.sp)}}}
@Composable private fun ServerPicker(servers:List<PlayerServer>,selected:PlayerServer,onSelect:(PlayerServer)->Unit){if(servers.size>1)Row(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=7.dp),horizontalArrangement=Arrangement.spacedBy(7.dp)){servers.forEachIndexed{index,server->Text(server.name.ifBlank{"Server ${index+1}"},modifier=Modifier.clip(RoundedCornerShape(20.dp)).background(if(server.url==selected.url)colors.primary else Color(0xFF202027)).clickable{onSelect(server)}.padding(horizontal=14.dp,vertical=8.dp),color=Color.White,fontSize=12.sp,fontWeight=FontWeight.SemiBold)}}}
@Composable private fun EpisodeNav(previous:Episode?,next:Episode?,onEpisode:(Episode)->Unit,episode:Episode){Row(Modifier.fillMaxWidth().padding(7.dp),horizontalArrangement=Arrangement.SpaceEvenly,verticalAlignment=Alignment.CenterVertically){TextButton(onClick={previous?.let(onEpisode)},enabled=previous!=null){Text("Previous")};Text("EP ${episode.number}",color=Color.White,fontWeight=FontWeight.Bold);TextButton(onClick={next?.let(onEpisode)},enabled=next!=null){Text("Next")}}}
private fun fetchPlayerServers(url:String):List<PlayerServer>{
    // AniSteam embeds AnimeSalt's real server/player pages only.
    // It never extracts a raw media URL or substitutes its own player.
    val page=getHtml(url)
    val result=mutableListOf<PlayerServer>()

    fun add(name:String,target:String){
        if(target.isBlank()) return
        val cleaned=target.replace("\\u0026","&").replace("\\/","/").replace("&amp;","&").trim().trim('\'','"')
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
            }catch(_:Exception){
                add("Embedded",target)
            }
        }else{
            add("Embedded",target)
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
