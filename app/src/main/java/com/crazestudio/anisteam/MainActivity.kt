package com.crazestudio.anisteam

import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Brush
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

private const val SOURCE = "https://animesalt.me"

data class Anime(val id:Int,val title:String,val image:String,val score:String,val episodes:String,val type:String,val synopsis:String="",val sourceId:String="animesalt",val seasons:Int=1,val slug:String="",val kind:String="series")
data class AnimeSource(val id:String,val name:String,val baseUrl:String,val enabled:Boolean=true)
data class PlayerServer(val name:String,val url:String)
data class Episode(val number:Int,val title:String,val aired:String,val url:String="",val servers:List<PlayerServer> = emptyList(),val animeSlug:String="")

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } }
}

private val SOURCES = listOf(AnimeSource("animesalt","AnimeSalt","https://animesalt.me"))

private val colors = darkColorScheme(background=Color(0xFF08080B),surface=Color(0xFF111116),surfaceVariant=Color(0xFF1A1A21),primary=Color(0xFFFF7A18),secondary=Color(0xFFFFD2B0))

@Composable fun App() {
    MaterialTheme(colorScheme=colors) {
        var selected by remember { mutableStateOf<String?>(null) }
        var showSources by remember { mutableStateOf(false) }

        AnimatedContent(
            targetState=selected,
            transitionSpec={
                if (targetState==null) {
                    (fadeIn(tween(260)) + slideInHorizontally(initialOffsetX={-it/5},animationSpec=tween(320)))
                        .togetherWith(fadeOut(tween(180)) + slideOutHorizontally(targetOffsetX={it/5},animationSpec=tween(220)))
                } else {
                    (fadeIn(tween(260)) + slideInHorizontally(initialOffsetX={it/5},animationSpec=tween(320)))
                        .togetherWith(fadeOut(tween(180)) + slideOutHorizontally(targetOffsetX={-it/5},animationSpec=tween(220)))
                }
            },
            label="screenTransition"
        ){slug ->
            if(slug==null) Home(onOpen={selected=it},onSources={showSources=true})
            else Details(slug){selected=null}
        }

        AnimatedVisibility(
            visible=showSources,
            enter=fadeIn(tween(180)),
            exit=fadeOut(tween(140))
        ){
            SourceDialog(onDismiss={showSources=false})
        }
    }
}

@Composable private fun Home(onOpen:(String)->Unit,onSources:()->Unit) {
    var q by remember{mutableStateOf("")}
    var submitted by remember{mutableStateOf("")}
    val context=LocalContext.current
    var data by remember{mutableStateOf<List<Anime>>(emptyList())}
    val continueWatching=remember{loadContinueWatchingList(context)}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf<String?>(null)}

    LaunchedEffect(submitted){
        loading=true
        error=null
        try{
            data=fetchAnime(submitted)
        }catch(e:kotlinx.coroutines.CancellationException){
            throw e
        }catch(e:Exception){
            error=e.message?:"Unable to load anime."
        }finally{
            loading=false
        }
    }

    val featured=data.firstOrNull()
    val discover=data.drop(1).take(12)
    val trending=data.drop(4).take(12)
    val fresh=data.drop(16).take(12)

    LazyColumn(
        Modifier.fillMaxSize().background(Color(0xFF07070B)),
        contentPadding=PaddingValues(bottom=42.dp)
    ){
        item{
            Row(
                Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=18.dp),
                verticalAlignment=Alignment.CenterVertically
            ){
                Column(Modifier.weight(1f)){
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(colors.primary),contentAlignment=Alignment.Center){
                            Text("A",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Black)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column{
                            Text("ANISTEAM",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Black,letterSpacing=1.5.sp)
                            Text("YOUR ANIME. YOUR WAY.",color=Color(0xFF777780),fontSize=8.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)
                        }
                    }
                }
                TextButton(onClick=onSources){
                    Text("ANIMESALT",color=colors.primary,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                }
            }
        }

        item{
            Column(Modifier.padding(horizontal=18.dp)){
                OutlinedTextField(
                    value=q,
                    onValueChange={q=it},
                    modifier=Modifier.fillMaxWidth().height(58.dp),
                    singleLine=true,
                    shape=RoundedCornerShape(18.dp),
                    colors=OutlinedTextFieldDefaults.colors(
                        focusedBorderColor=colors.primary,
                        unfocusedBorderColor=Color(0xFF24242D),
                        cursorColor=colors.primary,
                        focusedContainerColor=Color(0xFF111117),
                        unfocusedContainerColor=Color(0xFF111117)
                    ),
                    placeholder={Text("Search anime...",color=Color(0xFF666671),fontSize=14.sp)},
                    trailingIcon={
                        if(q.isNotBlank()){
                            TextButton(onClick={submitted=q.trim()}){
                                Text("SEARCH",color=colors.primary,fontSize=10.sp,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                )
            }
        }

        if(submitted.isBlank() && !loading && error==null && featured!=null){
            item{
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier.fillMaxWidth().height(410.dp)
                        .padding(horizontal=18.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .clickable{onOpen(featured.slug)}
                ){
                    AsyncImage(featured.image,featured.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent,Color(0xCC050509),Color(0xF5050509))
                            )
                        )
                    )
                    Column(
                        Modifier.align(Alignment.BottomStart).padding(22.dp)
                    ){
                        Text("FEATURED",color=colors.primary,fontSize=9.sp,fontWeight=FontWeight.Bold,letterSpacing=2.2.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(featured.title,color=Color.White,fontSize=31.sp,fontWeight=FontWeight.Black,maxLines=2,lineHeight=34.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            featured.type+"  •  "+featured.episodes+" episodes  •  ★ "+featured.score,
                            color=Color(0xFFD0D0D7),fontSize=11.sp
                        )
                        Spacer(Modifier.height(13.dp))
                        Row(
                            Modifier.clip(RoundedCornerShape(12.dp)).background(colors.primary)
                                .clickable{onOpen(featured.slug)}
                                .padding(horizontal=18.dp,vertical=11.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ){
                            Text("▶",color=Color.White,fontSize=12.sp)
                            Spacer(Modifier.width(7.dp))
                            Text("WATCH NOW",color=Color.White,fontWeight=FontWeight.Bold,fontSize=11.sp,letterSpacing=1.sp)
                        }
                    }
                }
            }
        }

        if(submitted.isBlank() && continueWatching.isNotEmpty()){
            item{
                SectionHeader("CONTINUE WATCHING", "Keep watching")
                LazyRow(
                    contentPadding=PaddingValues(horizontal=18.dp),
                    horizontalArrangement=Arrangement.spacedBy(12.dp)
                ){
                    items(continueWatching, key={it.url}){
                        val watch=it
                        val match=data.firstOrNull{anime->anime.slug==watch.animeSlug}
                        Column(
                            Modifier.width(190.dp).clip(RoundedCornerShape(17.dp))
                                .background(Color(0xFF111117))
                                .clickable{onOpen(watch.animeSlug)}
                                .padding(10.dp)
                        ){
                            Box(Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(12.dp))){
                                if(match!=null){
                                    AsyncImage(match.image,match.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                                    Box(Modifier.fillMaxSize().background(Color(0x66000000)))
                                }else{
                                    Box(Modifier.fillMaxSize().background(Color(0xFF1B1B23)))
                                }
                                Box(
                                    Modifier.align(Alignment.Center).size(38.dp).clip(RoundedCornerShape(50))
                                        .background(colors.primary),
                                    contentAlignment=Alignment.Center
                                ){Text("▶",color=Color.White,fontSize=12.sp)}
                            }
                            Spacer(Modifier.height(9.dp))
                            Text(match?.title ?: watch.title.ifBlank{"Anime"},color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp,maxLines=1)
                            Text("Episode "+watch.number,color=Color(0xFF858590),fontSize=11.sp)
                        }
                    }
                }
            }
        }

        if(submitted.isBlank() && discover.isNotEmpty()){
            item{SectionHeader("DISCOVER","Explore something new")}
            item{AnimeRail(discover,onOpen)}
            item{SectionHeader("TRENDING","Popular right now")}
            item{AnimeRail(trending,onOpen)}
            item{SectionHeader("JUST ADDED","Fresh from AnimeSalt")}
            item{AnimeRail(fresh,onOpen)}
        }

        item{
            Row(
                Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=18.dp),
                verticalAlignment=Alignment.CenterVertically
            ){
                Text(
                    if(submitted.isBlank())"ALL ANIME" else "SEARCH RESULTS",
                    color=Color.White,fontSize=20.sp,fontWeight=FontWeight.ExtraBold
                )
                Spacer(Modifier.weight(1f))
                if(submitted.isNotBlank()){
                    TextButton(onClick={q="";submitted=""}){
                        Text("CLEAR",color=colors.primary,fontSize=10.sp,fontWeight=FontWeight.Bold)
                    }
                }
            }
        }

        when{
            loading->item{
                Box(Modifier.fillMaxWidth().height(250.dp),contentAlignment=Alignment.Center){
                    CircularProgressIndicator(color=colors.primary,strokeWidth=2.5.dp)
                }
            }
            error!=null->item{
                Box(Modifier.fillMaxWidth().padding(30.dp),contentAlignment=Alignment.Center){
                    Text(error!!,color=Color(0xFF9999A4))
                }
            }
            data.isEmpty()->item{
                Box(Modifier.fillMaxWidth().height(220.dp),contentAlignment=Alignment.Center){
                    Text("No anime found.",color=Color(0xFF777783))
                }
            }
            else->{
                items(data, key={it.slug}){anime->
                    AnimeListCard(anime){onOpen(anime.slug)}
                }
            }
        }
    }
}

@Composable private fun SectionHeader(label:String,subtitle:String){
    Row(
        Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=18.dp),
        verticalAlignment=Alignment.Bottom
    ){
        Column(Modifier.weight(1f)){
            Text(label,color=colors.primary,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp)
            Spacer(Modifier.height(3.dp))
            Text(subtitle,color=Color.White,fontSize=18.sp,fontWeight=FontWeight.ExtraBold)
        }
        Text("›",color=Color(0xFF666671),fontSize=22.sp)
    }
}

@Composable private fun AnimeRail(anime:List<Anime>,onOpen:(String)->Unit){
    LazyRow(
        contentPadding=PaddingValues(horizontal=18.dp),
        horizontalArrangement=Arrangement.spacedBy(12.dp)
    ){
        items(anime,key={it.slug}){item->
            Column(
                Modifier.width(142.dp).clickable{onOpen(item.slug)}
            ){
                AsyncImage(
                    item.image,item.title,
                    Modifier.fillMaxWidth().height(202.dp).clip(RoundedCornerShape(16.dp)),
                    contentScale=ContentScale.Crop
                )
                Spacer(Modifier.height(8.dp))
                Text(item.title,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=12.sp,maxLines=2)
                Text("★ "+item.score,color=Color(0xFF8E8E9A),fontSize=10.sp)
            }
        }
    }
}

@Composable private fun AnimeListCard(anime:Anime,onClick:()->Unit){
    Row(
        Modifier.padding(horizontal=18.dp,vertical=5.dp).fillMaxWidth()
            .clip(RoundedCornerShape(18.dp)).background(Color(0xFF111116))
            .clickable(onClick=onClick).padding(10.dp),
        verticalAlignment=Alignment.CenterVertically
    ){
        AsyncImage(
            anime.image,anime.title,
            Modifier.size(76.dp,100.dp).clip(RoundedCornerShape(12.dp)),
            contentScale=ContentScale.Crop
        )
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)){
            Text(anime.title,color=Color.White,fontWeight=FontWeight.Bold,fontSize=14.sp,maxLines=2)
            Spacer(Modifier.height(5.dp))
            Text(anime.type+"  •  "+anime.episodes+" eps",color=Color(0xFF858590),fontSize=10.sp)
            Spacer(Modifier.height(4.dp))
            Text("★ "+anime.score,color=colors.secondary,fontSize=10.sp,fontWeight=FontWeight.Bold)
        }
        Text("›",color=Color(0xFF777783),fontSize=25.sp)
    }
}

@Composable private fun Details(slug:String,onBack:()->Unit) {
    val context=LocalContext.current
    var anime by remember{mutableStateOf<Anime?>(null)}
    var eps by remember{mutableStateOf<List<Episode>>(emptyList())}
    var loading by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf<String?>(null)}
    var chosen by remember{mutableStateOf<Episode?>(null)}
    
    var playingEpisode by remember{mutableStateOf<Episode?>(null)}
    var playerLoading by remember{mutableStateOf(false)}
    var playerError by remember{mutableStateOf<String?>(null)}

    LaunchedEffect(slug){
        loading=true;error=null;eps=emptyList()
        try{anime=fetchDetailsBySlug(slug)}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error=e.message?:"Unable to load details."}
        finally{loading=false}
    }
    LaunchedEffect(slug,anime){
        if(anime==null)return@LaunchedEffect
        loading=true;error=null
        try{eps=if(anime!!.kind=="movie") fetchMovieEpisode(slug) else fetchEpisodesBySlug(slug,anime!!.seasons)}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error=e.message?:"Unable to load episodes."}
        finally{loading=false}
    }
    LaunchedEffect(playingEpisode?.url){
        val ep=playingEpisode?:return@LaunchedEffect
        playerLoading=true;playerError=null
        try{
            val servers=withContext(Dispatchers.IO){fetchPlayerServers(ep.url)}
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if(servers.isEmpty()) playerError="No public player was found for this episode."
            else{
                val ready=ep.copy(servers=servers)
                playingEpisode=ready
                saveContinueWatching(context,ready)
            }
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){playerError="Could not load this episode player: "+(e.message?:"network error")}
        finally{playerLoading=false}
    }

    if(playingEpisode!=null){
        BackHandler(enabled=true){playingEpisode=null}
        if(playerLoading){
            Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){
                CircularProgressIndicator(color=colors.primary,strokeWidth=2.5.dp)
            }
        }else if(playerError!=null){
            Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp),contentAlignment=Alignment.Center){
                Column(horizontalAlignment=Alignment.CenterHorizontally){
                    Text("Player unavailable",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text(playerError!!,color=Color.Gray)
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                        TextButton(onClick={playerError=null;playingEpisode=playingEpisode?.copy()}){Text("Retry")}
                        TextButton(onClick={playingEpisode=null;playerError=null}){Text("Back")}
                    }
                }
            }
        }else SourcePlayer(playingEpisode!!,eps,onBack={playingEpisode=null},onEpisode={playingEpisode=it})
        return
    }

    chosen?.let{ep->
        AlertDialog(
            onDismissRequest={chosen=null},
            shape=RoundedCornerShape(24.dp),
            containerColor=Color(0xFF14141B),
            title={Text("Episode "+ep.number,color=Color.White,fontWeight=FontWeight.ExtraBold)},
            text={Text("Play this episode using AnimeSalt's original embedded JWPlayer.",color=Color(0xFF9D9DA8))},
            confirmButton={TextButton(onClick={chosen=null;playingEpisode=ep}){Text("PLAY",color=colors.primary,fontWeight=FontWeight.Bold)}},
            dismissButton={TextButton(onClick={chosen=null}){Text("CANCEL",color=Color(0xFF858590))}}
        )
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF07070B))){
        Row(
            Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),
            verticalAlignment=Alignment.CenterVertically
        ){
            TextButton(onClick=onBack){
                Text("‹  BACK",color=Color.White,fontSize=11.sp,fontWeight=FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text("ANISTEAM",color=Color(0xFF777783),fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.5.sp)
        }

        when{
            loading && anime==null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                CircularProgressIndicator(color=colors.primary,strokeWidth=2.5.dp)
            }
            error!=null && anime==null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){
                Text(error!!,color=Color.LightGray)
            }
            anime!=null->LazyColumn(contentPadding=PaddingValues(bottom=42.dp)){
                item{
                    Box(
                        Modifier.fillMaxWidth().height(430.dp)
                            .clip(RoundedCornerShape(bottomStart=30.dp,bottomEnd=30.dp))
                    ){
                        AsyncImage(anime!!.image,anime!!.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color(0x22000000),Color(0x55000000),Color(0xF507070B))
                                )
                            )
                        )
                        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)){
                            Text("ANIME",color=colors.primary,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=2.sp)
                            Spacer(Modifier.height(7.dp))
                            Text(anime!!.title,color=Color.White,fontSize=30.sp,fontWeight=FontWeight.ExtraBold,maxLines=3)
                            Spacer(Modifier.height(7.dp))
                            Text(
                                anime!!.type+"  •  "+anime!!.episodes+" episodes  •  ★ "+anime!!.score,
                                color=Color(0xFFD4D4DC),fontSize=11.sp
                            )
                        }
                    }
                }
                item{
                    Column(Modifier.padding(horizontal=18.dp,vertical=20.dp)){
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            Surface(color=Color(0xFF16161E),shape=RoundedCornerShape(10.dp)){
                                Text(anime!!.type,color=Color(0xFFB9B9C3),fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=11.dp,vertical=7.dp))
                            }
                            Surface(color=Color(0xFF16161E),shape=RoundedCornerShape(10.dp)){
                                Text(anime!!.episodes+" EP",color=Color(0xFFB9B9C3),fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=11.dp,vertical=7.dp))
                            }
                            if(anime!!.score.isNotBlank()){
                                Surface(color=Color(0xFF16161E),shape=RoundedCornerShape(10.dp)){
                                    Text("★ "+anime!!.score,color=colors.secondary,fontSize=10.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(horizontal=11.dp,vertical=7.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                        Text("ABOUT",color=colors.primary,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(anime!!.synopsis.ifBlank{"No synopsis available."},color=Color(0xFFB2B2BC),fontSize=13.sp,lineHeight=20.sp)
                        Spacer(Modifier.height(26.dp))
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){
                                Text("Episodes",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Black)
                                Text("Pick up where you left off",color=Color(0xFF777783),fontSize=10.sp)
                            }
                            Text(eps.size.toString(),color=colors.primary,fontSize=14.sp,fontWeight=FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }
                if(loading){
                    item{
                        Box(Modifier.fillMaxWidth().height(100.dp),contentAlignment=Alignment.Center){
                            CircularProgressIndicator(color=colors.primary,strokeWidth=2.dp)
                        }
                    }
                }else{
                    items(eps,key={it.url}){ep->
                        Row(
                            Modifier.padding(horizontal=18.dp,vertical=4.dp).fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)).background(Color(0xFF121217))
                                .clickable{chosen=ep}.padding(horizontal=13.dp,vertical=12.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ){
                            Text(ep.number.toString().padStart(2,'0'),color=colors.primary,fontSize=12.sp,fontWeight=FontWeight.ExtraBold,modifier=Modifier.width(42.dp))
                            Column(Modifier.weight(1f)){
                                Text(if(ep.title.isBlank())"Episode "+ep.number else ep.title,color=Color.White,fontWeight=FontWeight.SemiBold,fontSize=13.sp,maxLines=2)
                                if(ep.aired.isNotBlank())Text(ep.aired,color=Color(0xFF70707B),fontSize=9.sp)
                            }
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(Color(0xFF1B1B24)),contentAlignment=Alignment.Center){
                                Text("▶",color=colors.primary,fontSize=10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun saveContinueWatching(context:Context,ep:Episode){
    val prefs=context.getSharedPreferences("anisteam_watch",Context.MODE_PRIVATE)
    val current=loadContinueWatchingList(context).filterNot{it.animeSlug==ep.animeSlug}
    val updated=(listOf(ep)+current).take(20)
    val json=JSONArray()
    updated.forEach{
        json.put(JSONObject().apply{
            put("number",it.number)
            put("title",it.title)
            put("url",it.url)
            put("slug",it.animeSlug)
        })
    }
    prefs.edit().putString("items",json.toString()).apply()
}
private fun loadContinueWatchingList(context:Context):List<Episode>{
    val raw=context.getSharedPreferences("anisteam_watch",Context.MODE_PRIVATE).getString("items",null) ?: return emptyList()
    return try{
        val json=JSONArray(raw)
        (0 until json.length()).mapNotNull{index->
            val o=json.optJSONObject(index) ?: return@mapNotNull null
            Episode(
                o.optInt("number",1),
                o.optString("title","Episode"),
                "",
                o.optString("url",""),
                animeSlug=o.optString("slug","")
            )
        }.filter{it.animeSlug.isNotBlank() && it.url.isNotBlank()}
    }catch(_:Exception){emptyList()}
}
private fun loadContinueWatching(context:Context):Episode?=loadContinueWatchingList(context).firstOrNull()

private const val BASE_URL = "https://animesalt.me"

@Composable private fun SourceDialog(onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Sources")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SOURCES.forEach { source ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF17171D)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(source.name,color=Color.White,fontWeight=FontWeight.Bold);Text(source.baseUrl,color=Color.Gray,fontSize=11.sp)};Text(if(source.enabled)"ON" else "OFF",color=colors.secondary,fontWeight=FontWeight.Bold)}
    }}},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}

private suspend fun fetchAnime(q:String)=withContext(Dispatchers.IO){
    if(q.isBlank()) return@withContext fetchHome()

    val normalized=q.trim().replace(Regex("\\s+")," ")
    val terms=linkedSetOf(normalized)
    normalized.split(" ").filter{it.length>=3}.sortedByDescending{it.length}.forEach{terms.add(it)}

    val results=mutableListOf<Anime>()
    val failures=mutableListOf<Exception>()
    for(term in terms){
        val encoded=URLEncoder.encode(term,"UTF-8")
        val urls=buildList {
            add("$BASE_URL/?s=$encoded")
            add("$BASE_URL/tv/?s=$encoded")
            add("$BASE_URL/movies/?s=$encoded")
            for(page in 2..8){
                add("$BASE_URL/page/$page/?s=$encoded")
                add("$BASE_URL/tv/page/$page/?s=$encoded")
                add("$BASE_URL/movies/page/$page/?s=$encoded")
            }
        }
        for(url in urls){
            try{
                val found=extractSeries(getHtml(url))
                results += found
                if(results.distinctBy{it.kind+":"+it.slug}.size>=100) break
            }catch(e:kotlinx.coroutines.CancellationException){ throw e }
            catch(e:Exception){ failures += e }
        }
        if(results.isNotEmpty()) break
    }
    val unique=results.distinctBy{it.kind+":"+it.slug}.take(100)
    if(unique.isEmpty() && failures.isNotEmpty()) throw java.io.IOException("AnimeSalt search failed: "+(failures.first().message?: "network error"),failures.first())
    unique
}

private suspend fun fetchHome()=withContext(Dispatchers.IO){
    val results=mutableListOf<Anime>()
    val paths=listOf("$BASE_URL/","$BASE_URL/tv/","$BASE_URL/movies/")
    for(path in paths){
        try{ results += extractSeries(getHtml(path)) }
        catch(e:kotlinx.coroutines.CancellationException){ throw e }
        catch(_:Exception){}
    }
    results.distinctBy{it.kind+":"+it.slug}.take(100)
}

private fun extractSeries(doc:org.jsoup.nodes.Document):List<Anime>{
    return doc.select("a[href]").mapNotNull { a ->
        val href=a.absUrl("href").ifBlank { a.attr("href") }.substringBefore('?').substringBefore('#').trimEnd('/')
        val marker=when {
            href.contains("/series/",ignoreCase=true) -> "/series/"
            href.contains("/movies/",ignoreCase=true) -> "/movies/"
            href.contains("/tv/",ignoreCase=true) -> "/tv/"
            href.contains("/movie/",ignoreCase=true) -> "/movie/"
            else -> return@mapNotNull null
        }
        val index=href.indexOf(marker)
        if(index<0) return@mapNotNull null
        val tail=href.substring(index+marker.length).trim('/')
        if(tail.isBlank()) return@mapNotNull null
        val path=marker.removePrefix("/") + tail
        val container=a.closest("li, article, .item, .row, .item-list, .post, .anime-card, .film")
        val title=(a.selectFirst("h2,h3,h4,.entry-title,.title,.name")?.text()
            ?: container?.selectFirst("h2,h3,h4,.entry-title,.title,.name")?.text()
            ?: a.attr("title").ifBlank { a.text() }).replace(Regex("\\s+")," ").trim()
        if(title.isBlank()) return@mapNotNull null
        val image=posterFrom(a.selectFirst("img") ?: container?.selectFirst("img"))
        val kind=if(marker.contains("movie",ignoreCase=true)) "movie" else "series"
        Anime(path.hashCode(),title,image,"—","?","Anime",path,"animesalt",1,path,kind)
    }.distinctBy{it.kind+":"+it.slug}
}

private suspend fun fetchDetailsBySlug(slug:String)=withContext(Dispatchers.IO){
    val normalized=slug.trim('/')
    val prefix=normalized.substringBefore('/')
    val directPath=prefix in listOf("tv","series","movie","movies")
    val directUrl=if(directPath) "$BASE_URL/$normalized/" else null
    val seriesUrl=directUrl ?: "$BASE_URL/series/$normalized/"
    val movieUrl=directUrl ?: "$BASE_URL/movies/$normalized/"
    val seriesDoc=try{getHtml(seriesUrl)}catch(_:Exception){null}
    val doc=if(seriesDoc?.selectFirst(".fg1 .entry-title, h1.entry-title, h1")?.text()?.isNotBlank()==true) seriesDoc else getHtml(movieUrl)
    val kind=when {
        prefix=="movie" || prefix=="movies" -> "movie"
        prefix=="tv" || prefix=="series" -> "series"
        doc===seriesDoc -> "series"
        else -> "movie"
    }
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
    Anime(slug.hashCode(),title,poster,rating,episodes,"Movie".takeIf{kind=="movie"}?:"Anime",overview,"animesalt",if(kind=="movie") 1 else seasonCount,slug,kind)
}

private suspend fun fetchMovieEpisode(slug:String)=withContext(Dispatchers.IO){
    val normalized=slug.trim('/')
    val url=if(normalized.substringBefore('/') in listOf("movie","movies")) "$BASE_URL/$normalized/" else "$BASE_URL/movies/$normalized/"
    val doc=getHtml(url)
    val title=doc.selectFirst(".fg1 .entry-title, h1.entry-title, h1")?.text()?.trim().orEmpty()
    if(title.isBlank()) return@withContext emptyList<Episode>()
    listOf(Episode(1,title,"",url,animeSlug=slug))
}

private suspend fun fetchEpisodesBySlug(slug:String,season:Int=1)=withContext(Dispatchers.IO){
    val normalized=slug.trim('/')
    val prefix=normalized.substringBefore('/')
    val seriesUrl=if(prefix in listOf("tv","series")) "$BASE_URL/$normalized/" else "$BASE_URL/tv/$normalized/"
    val seriesDoc=getHtml(seriesUrl)

    // The current AnimeSalt /tv/ pages can expose all episodes directly.
    // Prefer the actual episode links on the page before attempting legacy season AJAX.
    val direct=parseEpisodeList(seriesDoc,slug,0,0)
        .distinctBy{it.url}
        .sortedBy{it.number}
    if(direct.isNotEmpty()){
        return@withContext direct.mapIndexed{index,ep->ep.copy(number=index+1)}
    }

    val seasonButtons=seriesDoc.select(".season-btn[data-season][data-post]")
        .mapNotNull { button ->
            val number=button.attr("data-season").toIntOrNull() ?: return@mapNotNull null
            val post=button.attr("data-post").trim()
            if(post.isBlank()) return@mapNotNull null
            Triple(number,post,button.text().replace(Regex("\\s+")," ").trim())
        }.distinctBy{it.first}.sortedBy{it.first}

    if(seasonButtons.isEmpty()){
        throw java.io.IOException("AnimeSalt page loaded, but no episode links were found. The episode HTML structure may have changed.")
    }

    val all=mutableListOf<Episode>()
    var nextGlobalNumber=1
    for((s,post,_) in seasonButtons){
        val doc=if(s==1) seriesDoc else getHtml("$BASE_URL/wp-admin/admin-ajax.php?action=action_select_season&season=$s&post=$post")
        val local=parseEpisodeList(doc,slug,s,0).distinctBy{it.url}.sortedBy{it.number}
        local.forEachIndexed{index,ep->all+=ep.copy(number=nextGlobalNumber+index)}
        nextGlobalNumber+=local.size
    }
    all.distinctBy{it.url}.sortedBy{it.number}
}

private fun parseEpisodeList(doc:org.jsoup.nodes.Document,slug:String,season:Int,episodeOffset:Int=0):List<Episode>{
    val candidates=doc.select("a[href]").mapNotNull { link ->
        val href=link.absUrl("href").ifBlank{link.attr("href")}.trim()
        if(href.isBlank()) return@mapNotNull null
        val path=try{Uri.parse(href).path.orEmpty()}catch(_:Exception){href}
        if(!path.contains("/episode/",ignoreCase=true)) return@mapNotNull null
        val tail=path.substringAfter("/episode/").trim('/')
        if(tail.isBlank()) return@mapNotNull null

        val seasonEpisode=Regex("(?:^|[-/])(\\d+)x(\\d+)(?:$|[-/])",RegexOption.IGNORE_CASE).find(tail)
        val localNumber=seasonEpisode?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: Regex("(?:episode|ep)[-_/]?(\\d+)",RegexOption.IGNORE_CASE).find(tail)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("-(\\d+)$").find(tail)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return@mapNotNull null
        val urlSeason=seasonEpisode?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        if(season>0 && seasonEpisode!=null && urlSeason!=season) return@mapNotNull null

        val container=link.closest("li,article,.item,.episode,.episode-item,.ep-item,[class*=episode]")
        val title=(link.selectFirst(".entry-title,h2,h3,h4,.title,.ep-title")?.text()
            ?: container?.selectFirst(".entry-title,h2,h3,h4,.title,.ep-title")?.text()
            ?: link.attr("title").ifBlank{link.text()}).replace(Regex("\\s+")," ").trim()
        Episode(number=localNumber+episodeOffset,title=title.ifBlank{"Episode $localNumber"},aired="",url=href,animeSlug=slug)
    }
    return candidates.groupBy{it.url}.mapNotNull{(_,items)->items.firstOrNull()}.sortedBy{it.number}
}

private class AnimeSaltChromeClient(
    private val activity: ComponentActivity,
    private val onFullscreenChanged: (Boolean) -> Unit
) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    fun exitFullscreen() {
        if (customView != null) onHideCustomView()
    }

    override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
        if (view == null) return

        // JWPlayer can issue fullscreen more than once while its controls animate.
        // Keep the first custom view alive instead of replacing the video surface.
        if (customView != null) {
            callback?.onCustomViewHidden()
            return
        }

        customView = view
        customViewCallback = callback
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val content = activity.findViewById<FrameLayout>(android.R.id.content)
        view.setBackgroundColor(android.graphics.Color.BLACK)
        content.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        view.bringToFront()

        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)

        // Do not recreate/reload the WebView while JWPlayer is entering fullscreen.
        // The existing video surface remains the active player surface.
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            content.windowInsetsController?.hide(android.view.WindowInsets.Type.systemBars())
            content.windowInsetsController?.systemBarsBehavior =
                android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION")
            content.systemUiVisibility =
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }

        onFullscreenChanged(true)
    }

    override fun onHideCustomView() {
        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val view = customView ?: return
        val content = activity.findViewById<FrameLayout>(android.R.id.content)

        content.removeView(view)
        customView = null

        customViewCallback?.onCustomViewHidden()
        customViewCallback = null

        activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            content.windowInsetsController?.show(android.view.WindowInsets.Type.systemBars())
        } else {
            @Suppress("DEPRECATION")
            content.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        }

        onFullscreenChanged(false)
    }
}

@Composable private fun SourcePlayer(
    episode:Episode,
    episodes:List<Episode>,
    onBack:()->Unit,
    onEpisode:(Episode)->Unit
){
    val context=LocalContext.current
    val activity=context as ComponentActivity
    var fullscreen by remember(episode.url){mutableStateOf(false)}
    val chromeClient=remember(episode.url){
        AnimeSaltChromeClient(activity){fullscreen=it}
    }

    BackHandler{
        if(fullscreen) chromeClient.exitFullscreen() else onBack()
    }

    // Embed AnimeSalt's own player directly. AniSteam does not add a second
    // player UI, server picker, episode navigation, or header underneath it.
    val server=remember(episode.url,episode.servers){
        episode.servers.firstOrNull{it.name.equals("Embedded",ignoreCase=true)}
            ?: episode.servers.firstOrNull()
    }

    if(server==null){
        Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){
            Text("No public AnimeSalt player found",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
        }
        return
    }

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
                webChromeClient=chromeClient
                setBackgroundColor(android.graphics.Color.BLACK)
                loadUrl(server.url,mapOf("Referer" to BASE_URL+"/"))
            }
        },
        update={webView->
            if(webView.url!=server.url && !fullscreen){
                webView.loadUrl(server.url,mapOf("Referer" to BASE_URL+"/"))
            }
        },
        modifier=Modifier.fillMaxSize().background(Color.Black)
    )
}

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

private val sourceHttpClient by lazy {
    okhttp3.OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .retryOnConnectionFailure(true)
        .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()
}

private fun getHtml(url:String):org.jsoup.nodes.Document{
    val request=okhttp3.Request.Builder()
        .url(url)
        .header("User-Agent","Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/121.0.0.0 Mobile Safari/537.36")
        .header("Accept","text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
        .header("Accept-Language","en-US,en;q=0.9")
        .header("Cache-Control","no-cache")
        .header("Referer","$BASE_URL/")
        .build()

    val response=sourceHttpClient.newCall(request).execute()
    response.use {
        if(!it.isSuccessful){
            throw java.io.IOException("AnimeSalt HTTP \${it.code} for $url")
        }
        val body=it.body.string()
        if(body.isBlank()) throw java.io.IOException("AnimeSalt returned an empty page")
        return org.jsoup.Jsoup.parse(body,url)
    }
}

private fun posterFrom(img:org.jsoup.nodes.Element?):String{
    if(img==null) return ""
    var value=img.attr("data-src").ifBlank { img.attr("data-lazy-src") }
        .ifBlank { img.attr("data-original") }.ifBlank { img.attr("src") }
    if(value.startsWith("//")) value="https:$value"
    return if(value.startsWith("data:image")) "" else value
}
