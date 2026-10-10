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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
    var loadingMore by remember{mutableStateOf(false)}
    var catalogPage by remember{mutableIntStateOf(1)}
    var hasMore by remember{mutableStateOf(true)}
    var error by remember{mutableStateOf<String?>(null)}
    var catalogReload by remember{mutableIntStateOf(0)}
    val submitSearch = {
        submitted=q.trim()
        catalogPage=1
    }

    LaunchedEffect(submitted,catalogReload){
        catalogPage=1
        loading=true
        loadingMore=false
        hasMore=true
        error=null
        try{
            data=fetchAnime(submitted,1)
            hasMore=data.isNotEmpty()
        }catch(e:kotlinx.coroutines.CancellationException){
            throw e
        }catch(e:Exception){
            error=e.message?:"Unable to load anime."
        }finally{
            loading=false
        }
    }

    LaunchedEffect(submitted,catalogPage){
        if(catalogPage<=1) return@LaunchedEffect
        loadingMore=true
        error=null
        try{
            val next=fetchAnime(submitted,catalogPage)
            val previous=data
            data=(previous+next).distinctBy{it.kind+":"+it.slug}
            hasMore=data.size>previous.size && next.isNotEmpty()
        }catch(e:kotlinx.coroutines.CancellationException){
            throw e
        }catch(e:Exception){
            error=e.message?:"Unable to load more anime."
            catalogPage=(catalogPage-1).coerceAtLeast(1)
        }finally{
            loadingMore=false
        }
    }

    val featured=data.firstOrNull()
    val discover=data.drop(1).take(12)
    val trending=data.drop(13).take(12)
    val fresh=data.drop(25).take(12)

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
                    keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                    keyboardActions=KeyboardActions(onSearch={submitSearch()}),
                    colors=OutlinedTextFieldDefaults.colors(
                        focusedBorderColor=colors.primary,
                        unfocusedBorderColor=Color(0xFF24242D),
                        cursorColor=colors.primary,
                        focusedContainerColor=Color(0xFF111117),
                        unfocusedContainerColor=Color(0xFF111117)
                    ),
                    placeholder={Text("Search anime...",color=Color(0xFF666671),fontSize=14.sp)},
                    trailingIcon={
                        Row(verticalAlignment=Alignment.CenterVertically){
                            if(q.isNotBlank()) TextButton(onClick={q=""}){
                                Text("CLEAR",color=Color(0xFF777783),fontSize=9.sp,fontWeight=FontWeight.Bold)
                            }
                            TextButton(onClick={submitSearch}){
                                Text("GO",color=colors.primary,fontSize=11.sp,fontWeight=FontWeight.ExtraBold)
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
                    Modifier.fillMaxWidth().height(350.dp)
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
            item{SectionHeader("EXPLORE","A few picks from AnimeSalt")}
            item{AnimeRail(discover,onOpen)}
            if(trending.isNotEmpty()){
                item{SectionHeader("MORE TO DISCOVER","Keep exploring")}
                item{AnimeRail(trending,onOpen)}
            }
            if(fresh.isNotEmpty()){
                item{SectionHeader("MORE TITLES","More from the catalogue")}
                item{AnimeRail(fresh,onOpen)}
            }
        }

        item{
            Row(
                Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=18.dp),
                verticalAlignment=Alignment.CenterVertically
            ){
                Column(Modifier.weight(1f)){
                    Text(
                        if(submitted.isBlank())"ALL ANIME" else "SEARCH RESULTS",
                        color=Color.White,fontSize=20.sp,fontWeight=FontWeight.ExtraBold
                    )
                    if(data.isNotEmpty()) Text("${data.size} titles loaded",color=Color(0xFF777783),fontSize=11.sp)
                }
                Spacer(Modifier.width(8.dp))
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
                items(data, key={it.kind+":"+it.slug}){anime->
                    AnimeListCard(anime){onOpen(anime.slug)}
                }
                if(hasMore){
                    item{
                        Box(Modifier.fillMaxWidth().padding(20.dp),contentAlignment=Alignment.Center){
                            if(loadingMore) CircularProgressIndicator(color=colors.primary,strokeWidth=2.dp)
                            else Button(onClick={catalogPage+=1},colors=ButtonDefaults.buttonColors(containerColor=colors.primary)){
                                Text("LOAD MORE",color=Color.White,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
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
        Spacer(Modifier.width(2.dp))
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
    var episodeQuery by remember{mutableStateOf("")}
    var episodeReload by remember{mutableIntStateOf(0)}
    
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
    LaunchedEffect(slug,anime,episodeReload){
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
            val servers=withContext(Dispatchers.IO){fetchPlayableMedia(ep.url)}
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

    val visibleEpisodes=remember(eps,episodeQuery){
        val term=episodeQuery.trim()
        if(term.isBlank()) eps else eps.filter {
            it.number.toString().contains(term,true) || it.title.contains(term,true)
        }
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
            text={Text("Play this episode with AniSteam's native video player.",color=Color(0xFF9D9DA8))},
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
                        Modifier.fillMaxWidth().height(350.dp)
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
                                Text(if(eps.isEmpty())"AnimeSalt episode list" else "${eps.size} episodes available",color=Color(0xFF777783),fontSize=11.sp)
                            }
                            Text(if(loading)"…" else eps.size.toString(),color=colors.primary,fontSize=14.sp,fontWeight=FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        if(!loading && eps.isNotEmpty()){
                            OutlinedTextField(
                                value=episodeQuery,
                                onValueChange={episodeQuery=it},
                                modifier=Modifier.fillMaxWidth().height(56.dp),
                                singleLine=true,
                                shape=RoundedCornerShape(15.dp),
                                placeholder={Text("Find episode by number or title",color=Color(0xFF666671),fontSize=12.sp)},
                                colors=OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor=colors.primary,
                                    unfocusedBorderColor=Color(0xFF24242D),
                                    cursorColor=colors.primary,
                                    focusedContainerColor=Color(0xFF111117),
                                    unfocusedContainerColor=Color(0xFF111117)
                                )
                            )
                        }
                    }
                }
                if(loading){
                    item{
                        Box(Modifier.fillMaxWidth().height(100.dp),contentAlignment=Alignment.Center){
                            CircularProgressIndicator(color=colors.primary,strokeWidth=2.dp)
                        }
                    }
                }else{
                    items(visibleEpisodes,key={it.url}){ep->
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
                    if(visibleEpisodes.isEmpty() && eps.isNotEmpty()){
                        item{
                            Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){
                                Text("No episodes match this search.",color=Color(0xFF858590),fontSize=12.sp)
                            }
                        }
                    }
                    if(!loading && eps.isEmpty()){
                        item{
                            Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){
                                Text("Episodes couldn't be loaded",color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text(error ?: "AnimeSalt returned no episode links.",color=Color(0xFF858590),fontSize=11.sp)
                                Spacer(Modifier.height(12.dp))
                                Button(onClick={episodeReload++},colors=ButtonDefaults.buttonColors(containerColor=colors.primary),shape=RoundedCornerShape(13.dp)){
                                    Text("RETRY",color=Color.White,fontWeight=FontWeight.Bold)
                                }
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

private suspend fun fetchAnime(q:String,page:Int=1)=withContext(Dispatchers.IO){
    if(q.isBlank()) return@withContext fetchHome(page)
    val normalized=q.trim().replace(Regex("\\s+")," ")
    val queries=buildList {
        add(normalized)
        normalized.split(" ").filter{it.length>=3}.sortedByDescending{it.length}
            .forEach{term->if(none{it.equals(term,ignoreCase=true)}) add(term)}
    }
    val failures=mutableListOf<Exception>()
    for(term in queries){
        val encoded=URLEncoder.encode(term,"UTF-8")
        val urls=listOf(
            if(page==1) "$BASE_URL/?s=$encoded" else "$BASE_URL/page/$page/?s=$encoded",
            if(page==1) "$BASE_URL/tv/?s=$encoded" else "$BASE_URL/tv/page/$page/?s=$encoded",
            if(page==1) "$BASE_URL/movies/?s=$encoded" else "$BASE_URL/movies/page/$page/?s=$encoded"
        )
        val termResults=mutableListOf<Anime>()
        for(url in urls){
            try{termResults+=extractSeries(getHtml(url))}
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){failures+=e}
        }
        val unique=termResults.distinctBy{it.kind+":"+it.slug}
        if(unique.isNotEmpty()) return@withContext unique
    }
    if(failures.isNotEmpty()) throw java.io.IOException("AnimeSalt search failed: "+(failures.first().message?:"network error"),failures.first())
    emptyList()
}

private suspend fun fetchHome(page:Int=1)=withContext(Dispatchers.IO){
    val results=mutableListOf<Anime>()
    val paths=listOf("/","/tv/","/movies/")
    for(path in paths){
        val url=when {
            page==1 -> "$BASE_URL$path"
            path=="/" -> "$BASE_URL/page/$page/"
            else -> "$BASE_URL"+path.trimEnd('/')+"/page/$page/"
        }
        try{results+=extractSeries(getHtml(url))}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){}
    }
    results.distinctBy{it.kind+":"+it.slug}
}

private fun extractSeries(doc:org.jsoup.nodes.Document):List<Anime>{
    val anchors=linkedMapOf<String,org.jsoup.nodes.Element>()
    doc.select("a[href], [data-href], [data-url]").forEach { element ->
        val raw=element.attr("href").ifBlank { element.attr("data-href") }.ifBlank { element.attr("data-url") }
        if(raw.isBlank()) return@forEach
        val resolved=try{java.net.URI(BASE_URL).resolve(raw).toString()}catch(_:Exception){raw}
        val href=resolved.substringBefore('?').substringBefore('#').trimEnd('/')
        val marker=listOf("/series/","/movies/","/tv/","/movie/").firstOrNull{href.contains(it,ignoreCase=true)} ?: return@forEach
        val tail=href.substringAfter(marker).trim('/')
        if(tail.isBlank() || tail.contains("/")) return@forEach
        val anchor=if(element.tagName()=="a") element else element.selectFirst("a[href]") ?: element
        anchors.putIfAbsent(href,anchor)
    }
    return anchors.mapNotNull { (href,a) ->
        val marker=listOf("/series/","/movies/","/tv/","/movie/").firstOrNull{href.contains(it,ignoreCase=true)} ?: return@mapNotNull null
        val tail=href.substringAfter(marker).trim('/')
        if(tail.isBlank()) return@mapNotNull null
        val sourcePath=marker.removePrefix("/") + tail
        val container=a.closest("li, article, .item, .row, .item-list, .post, .anime-card, .film, .movie-item, .series-item, .flw-item, .bs") ?: a.parent()
        val imageElement=a.selectFirst("img, source") ?: container?.selectFirst("img, source")
        val title=(a.selectFirst("h2,h3,h4,.entry-title,.title,.name,.film-name,.tt")?.text()
            ?: container?.selectFirst("h2,h3,h4,.entry-title,.title,.name,.film-name,.tt")?.text()
            ?: a.attr("title").ifBlank { a.attr("aria-label").ifBlank { a.text() } })
            .replace(Regex("\\s+")," ").trim()
        if(title.isBlank()) return@mapNotNull null
        val kind=if(marker.contains("movie",ignoreCase=true)) "movie" else "series"
        Anime(sourcePath.hashCode(),title,posterFrom(imageElement),"—","?","Anime",sourcePath,"animesalt",1,sourcePath,kind)
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
    val candidates=if(prefix in listOf("tv","series")) listOf("$BASE_URL/$normalized/") else listOf("$BASE_URL/series/$normalized/","$BASE_URL/tv/$normalized/")
    var seriesDoc:org.jsoup.nodes.Document?=null
    var lastError:Exception?=null
    for(url in candidates){
        try{
            val candidate=getHtml(url)
            val hasTitle=candidate.selectFirst("h1.entry-title, .fg1 .entry-title, h1")?.text()?.isNotBlank()==true
            val hasEpisodes=candidate.select("a[href*=/episode/]").isNotEmpty()
            if(hasTitle || hasEpisodes){seriesDoc=candidate;if(hasEpisodes) break}
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){lastError=e}
    }
    val doc=seriesDoc ?: throw java.io.IOException("Could not load AnimeSalt series page"+(lastError?.message?.let{": $it"}?:""),lastError)
    val seasonButtons=doc.select("[data-season][data-post], [data-season][data-id], .season-btn[data-season]")
        .mapNotNull { button ->
            val n=button.attr("data-season").toIntOrNull() ?: return@mapNotNull null
            val post=button.attr("data-post").ifBlank{button.attr("data-id")}.trim()
            if(post.isBlank()) return@mapNotNull null
            Triple(n,post,button.text().replace(Regex("\\s+")," ").trim())
        }.distinctBy{it.first}.sortedBy{it.first}
    val seasonsInLinks=doc.select("a[href*=/episode/]").mapNotNull { link ->
        val href=link.absUrl("href").ifBlank{link.attr("href")}
        val path=try{Uri.parse(href).path.orEmpty()}catch(_:Exception){href}
        Regex("(?:^|[-/])(\\d+)x\\d+(?:$|[-/])",RegexOption.IGNORE_CASE).find(path)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
    val seasons=(seasonButtons.map{it.first}+seasonsInLinks).distinct().sorted().ifEmpty{listOf(1)}
    fun seasonEpisodes(target:Int):List<Episode>{
        val fromInitial=parseEpisodeList(doc,slug,target).distinctBy{it.url}
        if(fromInitial.isNotEmpty()) return fromInitial
        val button=seasonButtons.firstOrNull{it.first==target} ?: return emptyList()
        try{
            val ajax=postHtml("$BASE_URL/wp-admin/admin-ajax.php",
                mapOf("action" to "action_select_season","season" to target.toString(),"post" to button.second),
                "$BASE_URL/")
            val parsed=parseEpisodeList(ajax,slug,target).distinctBy{it.url}
            if(parsed.isNotEmpty()) return parsed
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){lastError=e}
        return emptyList()
    }
    val all=mutableListOf<Episode>()
    var global=1
    for(s in seasons){
        seasonEpisodes(s).sortedBy{it.number}.forEach{ep->all+=ep.copy(number=global++)}
    }
    val unique=all.distinctBy{it.url}.sortedBy{it.number}
    if(unique.isEmpty()) throw java.io.IOException("AnimeSalt page loaded, but no episode links were found. The site's episode markup may have changed.")
    unique
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
    val server=remember(episode.url,episode.servers){episode.servers.firstOrNull()}
    if(server==null){
        Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){
            Text("No playable media link was found.",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        }
        return
    }
    var fullscreen by remember(server.url){mutableStateOf(false)}
    var controlsVisible by remember(server.url){mutableStateOf(true)}
    var isPlaying by remember(server.url){mutableStateOf(false)}
    var buffering by remember(server.url){mutableStateOf(true)}
    var playbackError by remember(server.url){mutableStateOf<String?>(null)}
    var positionMs by remember(server.url){mutableLongStateOf(0L)}
    var durationMs by remember(server.url){mutableLongStateOf(0L)}
    var scrubbing by remember(server.url){mutableStateOf(false)}
    val exoPlayer=remember(server.url){androidx.media3.exoplayer.ExoPlayer.Builder(context).build()}
    LaunchedEffect(exoPlayer,server.url){
        val uri=Uri.parse(server.url)
        val item=androidx.media3.common.MediaItem.Builder().setUri(uri).apply{
            if(uri.path.orEmpty().endsWith(".m3u8",ignoreCase=true)) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }.build()
        exoPlayer.setMediaItem(item)
        exoPlayer.prepare()
        exoPlayer.playWhenReady=true
    }
    DisposableEffect(exoPlayer){
        val listener=object:androidx.media3.common.Player.Listener{
            override fun onPlayerError(error:androidx.media3.common.PlaybackException){playbackError=error.message ?: "This video source could not be played."}
            override fun onPlaybackStateChanged(playbackState:Int){buffering=playbackState==androidx.media3.common.Player.STATE_BUFFERING}
            override fun onIsPlayingChanged(playing:Boolean){isPlaying=playing}
        }
        exoPlayer.addListener(listener)
        onDispose{exoPlayer.removeListener(listener);exoPlayer.release()}
    }
    LaunchedEffect(exoPlayer){
        while(true){
            if(!scrubbing){
                positionMs=exoPlayer.currentPosition.coerceAtLeast(0L)
                durationMs=exoPlayer.duration.takeIf{it>0L && it!=androidx.media3.common.C.TIME_UNSET} ?: 0L
            }
            isPlaying=exoPlayer.isPlaying
            buffering=exoPlayer.playbackState==androidx.media3.common.Player.STATE_BUFFERING
            kotlinx.coroutines.delay(350)
        }
    }
    LaunchedEffect(controlsVisible,isPlaying){
        if(controlsVisible && isPlaying){kotlinx.coroutines.delay(4500);controlsVisible=false}
    }
    LaunchedEffect(fullscreen){
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if(fullscreen){
            activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
                activity.window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
                activity.window.insetsController?.systemBarsBehavior=android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }else{
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility=(View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            }
        }else{
            activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R) activity.window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
            else{
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }
    DisposableEffect(activity){
        onDispose{
            activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R) activity.window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
            else{
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }
    BackHandler{if(fullscreen) fullscreen=false else onBack()}
    Box(Modifier.fillMaxSize().background(Color.Black).clickable{controlsVisible=!controlsVisible}){
        AndroidView(
            factory={ctx->
                androidx.media3.ui.PlayerView(ctx).apply{
                    useController=false
                    this.player=exoPlayer
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode=androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update={view->if(view.player!==exoPlayer)view.player=exoPlayer},
            modifier=Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000),Color.Transparent,Color(0xCC000000)))))
        if(buffering && playbackError==null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(42.dp),color=colors.primary,strokeWidth=3.dp)
        if(playbackError!=null){
            Column(Modifier.align(Alignment.Center).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){
                Text("Can't play this source",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text(playbackError!!,color=Color(0xFFB7B7C2),fontSize=12.sp)
                Spacer(Modifier.height(16.dp))
                Button(onClick={playbackError=null;exoPlayer.prepare();exoPlayer.play()},colors=ButtonDefaults.buttonColors(containerColor=colors.primary)){Text("RETRY",fontWeight=FontWeight.Bold)}
            }
        }
        AnimatedVisibility(visible=controlsVisible,enter=fadeIn(tween(180)),exit=fadeOut(tween(220)),modifier=Modifier.align(Alignment.TopCenter)){
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
                Surface(onClick=onBack,shape=RoundedCornerShape(50),color=Color(0x770D0D12)){Box(Modifier.size(42.dp),contentAlignment=Alignment.Center){Text("‹",color=Color.White,fontSize=30.sp)}}
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)){
                    Text("ANISTEAM PLAYER",color=colors.primary,fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.8.sp)
                    Text(episode.title.ifBlank{"Episode " + episode.number},color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=1)
                }
                Surface(onClick={fullscreen=!fullscreen},shape=RoundedCornerShape(12.dp),color=Color(0x770D0D12)){Box(Modifier.size(42.dp),contentAlignment=Alignment.Center){Text(if(fullscreen)"⊡" else "⛶",color=Color.White,fontSize=21.sp)}}
            }
        }
        AnimatedVisibility(visible=controlsVisible,enter=fadeIn(tween(160)),exit=fadeOut(tween(200)),modifier=Modifier.align(Alignment.Center)){
            Row(horizontalArrangement=Arrangement.spacedBy(24.dp),verticalAlignment=Alignment.CenterVertically){
                Surface(onClick={exoPlayer.seekTo((exoPlayer.currentPosition-10_000L).coerceAtLeast(0L));controlsVisible=true},shape=RoundedCornerShape(50),color=Color(0x880B0B10)){Box(Modifier.size(54.dp),contentAlignment=Alignment.Center){Text("↺10",color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold)}}
                Surface(onClick={if(exoPlayer.isPlaying)exoPlayer.pause() else exoPlayer.play();controlsVisible=true},shape=RoundedCornerShape(50),color=colors.primary){Box(Modifier.size(72.dp),contentAlignment=Alignment.Center){Text(if(isPlaying)"Ⅱ" else "▶",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold)}}
                Surface(onClick={exoPlayer.seekTo((exoPlayer.currentPosition+10_000L).coerceAtMost(if(durationMs>0)durationMs else Long.MAX_VALUE));controlsVisible=true},shape=RoundedCornerShape(50),color=Color(0x880B0B10)){Box(Modifier.size(54.dp),contentAlignment=Alignment.Center){Text("↻10",color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold)}}
            }
        }
        AnimatedVisibility(visible=controlsVisible,enter=fadeIn(tween(180)),exit=fadeOut(tween(220)),modifier=Modifier.align(Alignment.BottomCenter)){
            Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=16.dp)){
                Slider(
                    value=if(durationMs>0)(positionMs.toFloat()/durationMs.toFloat()).coerceIn(0f,1f) else 0f,
                    onValueChange={fraction->scrubbing=true;positionMs=(fraction*durationMs).toLong().coerceAtLeast(0L)},
                    onValueChangeFinished={exoPlayer.seekTo(positionMs);scrubbing=false},
                    colors=SliderDefaults.colors(thumbColor=colors.primary,activeTrackColor=colors.primary,inactiveTrackColor=Color(0x66777780)),
                    modifier=Modifier.fillMaxWidth().height(28.dp)
                )
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(formatPlayerTime(positionMs),color=Color.White,fontSize=11.sp,fontWeight=FontWeight.SemiBold)
                    Text(" / ",color=Color(0xFF888892),fontSize=11.sp)
                    Text(formatPlayerTime(durationMs),color=Color(0xFFB6B6C0),fontSize=11.sp)
                    Spacer(Modifier.weight(1f))
                    Text("AniSteam",color=Color(0xFFB6B6C0),fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                }
            }
        }
    }
}

private fun formatPlayerTime(milliseconds:Long):String{
    val seconds=(milliseconds.coerceAtLeast(0L)/1000L).toInt()
    val hours=seconds/3600
    val minutes=(seconds%3600)/60
    val remainder=seconds%60
    return if(hours>0) String.format("%d:%02d:%02d",hours,minutes,remainder) else String.format("%02d:%02d",minutes,remainder)
}

private fun fetchPlayableMedia(episodeUrl:String):List<PlayerServer>{
    val episodePage=getHtml(episodeUrl)
    val pageUrls=linkedMapOf<String,String>()
    val media=linkedMapOf<String,String>()
    fun normalize(raw:String,base:String):String{
        val cleaned=raw.replace("\\u0026","&").replace("\\/","/").replace("&amp;","&").trim().removeSurrounding("\"").removeSurrounding("'")
        return try{URL(base).let{URL(it,cleaned).toString()}}catch(_:Exception){cleaned}
    }
    fun isMedia(raw:String):Boolean{
        val path=try{Uri.parse(raw).path.orEmpty()}catch(_:Exception){raw.substringBefore('?')}
        return listOf(".m3u8",".mp4",".mpd",".webm",".m4v").any{path.endsWith(it,ignoreCase=true)}
    }
    fun inspectDocument(doc:org.jsoup.nodes.Document,base:String,serverName:String){
        doc.select("video[src],video source[src],source[src],video[data-src]").forEach{node->
            val raw=node.attr("src").ifBlank{node.attr("data-src")}
            if(raw.isNotBlank()){val target=normalize(raw,base);if(isMedia(target))media.putIfAbsent(target,serverName)}
        }
        val scripts=doc.select("script").joinToString("\n"){it.data().ifBlank{it.html()}}
        val quoted=Regex("""(?i)["'](?:file|src|source|url)["']\s*:\s*["']([^"']+)["']""")
        quoted.findAll(scripts).forEach{match->
            val target=normalize(match.groupValues[1],base)
            if(isMedia(target))media.putIfAbsent(target,serverName)
        }
        val urls=Regex("""(?i)(?:https?:\\?/\\?/|//)[^"'\s<>]+?\.(?:m3u8|mp4|mpd|webm|m4v)(?:\?[^"'\s<>]*)?""")
        urls.findAll(scripts).forEach{match->
            val target=normalize(match.value,base)
            if(isMedia(target))media.putIfAbsent(target,serverName)
        }
        doc.select("iframe[src],iframe[data-src]").forEach{iframe->
            val raw=iframe.attr("data-src").ifBlank{iframe.attr("src")}
            if(raw.isBlank())return@forEach
            val target=normalize(raw,base)
            if(target.startsWith("http",true))pageUrls.putIfAbsent(target,serverName)
            val encoded=try{Uri.parse(target).getQueryParameter("data")}catch(_:Exception){null}
            if(!encoded.isNullOrBlank()){
                try{
                    val entries=JSONArray(Base64.decode(encoded,Base64.DEFAULT).toString(Charsets.UTF_8))
                    for(i in 0 until entries.length()){
                        val item=entries.optJSONObject(i)?:continue
                        val link=item.optString("link").trim()
                        if(link.isNotBlank())pageUrls.putIfAbsent(normalize(link,base),item.optString("language").ifBlank{"Server " + (i+1)})
                    }
                }catch(_:Exception){}
            }
        }
    }
    inspectDocument(episodePage,episodeUrl,"AnimeSalt")
    for((pageUrl,name) in pageUrls.entries.take(8)){
        if(media.isNotEmpty())break
        try{inspectDocument(getHtml(pageUrl),pageUrl,name)}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){}
    }
    return media.map{(url,name)->PlayerServer(name,url)}
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

private fun postHtml(url:String,fields:Map<String,String>,referer:String):org.jsoup.nodes.Document{
    val form=okhttp3.FormBody.Builder().apply{fields.forEach{(key,value)->add(key,value)}}.build()
    val request=okhttp3.Request.Builder()
        .url(url)
        .post(form)
        .header("User-Agent","Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/121.0.0.0 Mobile Safari/537.36")
        .header("Accept","text/html, */*; q=0.01")
        .header("X-Requested-With","XMLHttpRequest")
        .header("Origin",BASE_URL)
        .header("Referer",referer)
        .build()
    val response=sourceHttpClient.newCall(request).execute()
    response.use{
        if(!it.isSuccessful) throw java.io.IOException("AnimeSalt season request HTTP ${it.code}")
        val body=it.body.string()
        if(body.isBlank()) throw java.io.IOException("AnimeSalt returned an empty season")
        return org.jsoup.Jsoup.parse(body,url)
    }
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
