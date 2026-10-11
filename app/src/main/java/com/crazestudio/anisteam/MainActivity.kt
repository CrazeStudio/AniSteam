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
import androidx.compose.ui.graphics.graphicsLayer
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
import android.content.ContextWrapper
import android.util.Base64

private const val SOURCE = "https://animesalt.cx"

data class Anime(val id:Int,val title:String,val image:String,val score:String,val episodes:String,val type:String,val synopsis:String="",val sourceId:String="animesalt",val seasons:Int=1,val slug:String="",val kind:String="series")
data class AnimeSource(val id:String,val name:String,val baseUrl:String,val enabled:Boolean=true)
data class PlayerServer(val name:String,val url:String,val referer:String="")
data class Episode(val number:Int,val title:String,val aired:String,val url:String="",val servers:List<PlayerServer> = emptyList(),val animeSlug:String="",val displayNumber:String="",val season:Int=1)
private data class SeasonTarget(val number:Int,val postId:String,val title:String,val url:String)

class MainActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { App() } }
}

private val SOURCES = listOf(AnimeSource("animesalt","AnimeSalt","https://animesalt.cx"),AnimeSource("toonstream","ToonStream","https://toonstream.us"))

private val colors = darkColorScheme(background=Color(0xFF0B0D12),surface=Color(0xFF161820),surfaceVariant=Color(0xFF1C1F27),primary=Color(0xFFFF5C36),secondary=Color(0xFFFFC3A7))

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
    var showLibrary by remember{mutableStateOf(false)}
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
            hasMore=submitted.isBlank() && data.isNotEmpty()
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

    var catalogueFilter by remember{mutableStateOf("ALL")}
    val featured=data.firstOrNull()
    val continueItems=continueWatching.take(8)
    val filteredData=remember(data,catalogueFilter){
        when(catalogueFilter){
            "SERIES"->data.filter{it.kind!="movie"}
            "MOVIES"->data.filter{it.kind=="movie"}
            else->data
        }
    }
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF0A0A10),Color(0xFF08080C))))) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding=PaddingValues(bottom=98.dp)
    ){
        item{
            Row(Modifier.fillMaxWidth().padding(start=20.dp,end=18.dp,top=18.dp,bottom=15.dp),verticalAlignment=Alignment.CenterVertically){
                Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(Color(0xFFFF8B48),Color(0xFFFF5A36)))),contentAlignment=Alignment.Center){
                    Text("A",color=Color.White,fontSize=24.sp,fontWeight=FontWeight.Black)
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)){
                    Text("AniSteam",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Black,letterSpacing=(-0.5).sp)
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(Color(0xFF4ADE80)))
                        Spacer(Modifier.width(5.dp))
                        Text("ANIME, ALL IN ONE PLACE",color=Color(0xFF8D8D9B),fontSize=8.sp,fontWeight=FontWeight.Bold,letterSpacing=0.8.sp,maxLines=1)
                    }
                }
                Surface(onClick=onSources,shape=RoundedCornerShape(14.dp),color=Color(0xFF171720),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF282832))){
                    Text("SOURCES  ⌄",Modifier.padding(horizontal=12.dp,vertical=11.dp),color=Color(0xFFE5E5EC),fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=0.4.sp)
                }
            }
        }
        item{
            OutlinedTextField(
                value=q,onValueChange={q=it},
                modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp).height(58.dp),
                singleLine=true,shape=RoundedCornerShape(18.dp),
                keyboardOptions=KeyboardOptions(imeAction=ImeAction.Search),
                keyboardActions=KeyboardActions(onSearch={submitSearch()}),
                colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Color(0xFFFF8955),unfocusedBorderColor=Color(0xFF252530),cursorColor=colors.primary,focusedContainerColor=Color(0xFF13131B),unfocusedContainerColor=Color(0xFF111118)),
                placeholder={Text("Find an anime or movie",color=Color(0xFF777783),fontSize=13.sp)},
                leadingIcon={Text("⌕",modifier=Modifier.padding(start=8.dp),color=Color(0xFF9C9CA8),fontSize=25.sp)},
                trailingIcon={
                    Row(verticalAlignment=Alignment.CenterVertically){
                        if(q.isNotBlank())TextButton(onClick={q="";if(submitted.isNotBlank())submitted=""}){Text("×",color=Color(0xFF9C9CA8),fontSize=22.sp)}
                        Surface(onClick=submitSearch,shape=RoundedCornerShape(12.dp),color=colors.primary){
                            Text("GO",Modifier.padding(horizontal=13.dp,vertical=10.dp),color=Color.White,fontSize=10.sp,fontWeight=FontWeight.Black)
                        }
                        Spacer(Modifier.width(5.dp))
                    }
                }
            )
        }
        if(submitted.isBlank() && !loading && error==null && featured!=null){
            item{
                Spacer(Modifier.height(18.dp))
                Box(Modifier.fillMaxWidth().padding(horizontal=18.dp).height(300.dp).clip(RoundedCornerShape(27.dp)).clickable{onOpen(featured.slug)}){
                    AsyncImage(featured.image,featured.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x1106060A),Color(0x6606060A),Color(0xF508080D)))))
                    Box(Modifier.align(Alignment.TopStart).padding(15.dp).clip(RoundedCornerShape(9.dp)).background(Color(0xCC171720)).padding(horizontal=10.dp,vertical=7.dp)){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Box(Modifier.size(5.dp).clip(RoundedCornerShape(50)).background(colors.primary))
                            Spacer(Modifier.width(6.dp))
                            Text("EDITOR'S PICK",color=Color.White,fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.sp)
                        }
                    }
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp)){
                        Text(if(featured.kind=="movie")"FEATURED MOVIE" else "FEATURED SERIES",color=Color(0xFFFFA16F),fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.7.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(featured.title,color=Color.White,fontSize=27.sp,fontWeight=FontWeight.Black,maxLines=2,lineHeight=31.sp)
                        Spacer(Modifier.height(6.dp))
                        Text(featured.type+"  •  "+featured.episodes+" episodes"+if(featured.score!="—")"  •  ★ "+featured.score else "",color=Color(0xFFD3D3DC),fontSize=11.sp,maxLines=1)
                        Spacer(Modifier.height(14.dp))
                        Surface(onClick={onOpen(featured.slug)},shape=RoundedCornerShape(13.dp),color=colors.primary){
                            Row(Modifier.padding(horizontal=16.dp,vertical=11.dp),verticalAlignment=Alignment.CenterVertically){
                                Text("▶",color=Color.White,fontSize=11.sp)
                                Spacer(Modifier.width(8.dp))
                                Text("Explore title",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
        if(submitted.isBlank() && continueItems.isNotEmpty()){
            item{
                Row(Modifier.fillMaxWidth().padding(start=19.dp,end=18.dp,top=26.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){
                    Column(Modifier.weight(1f)){
                        Text("YOUR RECENTLY PLAYED",color=Color(0xFF8E8E9A),fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.2.sp)
                        Text("Continue watching",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.ExtraBold)
                    }
                    Text("RECENT",color=colors.primary,fontSize=9.sp,fontWeight=FontWeight.Black,letterSpacing=1.sp)
                }
                LazyRow(contentPadding=PaddingValues(horizontal=18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                    items(continueItems,key={it.url}){watch->
                        val match=data.firstOrNull{anime->anime.slug==watch.animeSlug}
                        Surface(onClick={onOpen(watch.animeSlug)},shape=RoundedCornerShape(17.dp),color=Color(0xFF15151D),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF24242F)),modifier=Modifier.width(230.dp)){
                            Row(Modifier.padding(10.dp),verticalAlignment=Alignment.CenterVertically){
                                Box(Modifier.size(68.dp,78.dp).clip(RoundedCornerShape(12.dp))){
                                    if(match!=null)AsyncImage(match.image,match.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop) else Box(Modifier.fillMaxSize().background(Color(0xFF24242D)))
                                    Box(Modifier.fillMaxSize().background(Color(0x44000000)))
                                    Box(Modifier.align(Alignment.Center).size(29.dp).clip(RoundedCornerShape(50)).background(colors.primary),contentAlignment=Alignment.Center){Text("▶",color=Color.White,fontSize=10.sp)}
                                }
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)){
                                    Text(match?.title?:watch.title.ifBlank{"Anime"},color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold,maxLines=2)
                                    Spacer(Modifier.height(6.dp))
                                    Text("Episode "+watch.displayNumber.ifBlank{watch.number.toString()},color=Color(0xFF9999A5),fontSize=10.sp)
                                    Spacer(Modifier.height(8.dp))
                                    Text("RESUME  →",color=colors.primary,fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=0.7.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        item{
            Row(Modifier.fillMaxWidth().padding(start=19.dp,end=18.dp,top=27.dp,bottom=12.dp),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){
                    Text(if(submitted.isBlank())"YOUR NEXT OBSESSION" else "SEARCH",color=Color(0xFF8E8E9A),fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.4.sp)
                    Text(if(submitted.isBlank())"Explore anime" else "Results",color=Color.White,fontSize=22.sp,fontWeight=FontWeight.Black)
                }
                if(data.isNotEmpty())Text(data.size.toString()+" loaded",color=Color(0xFF9C9CA8),fontSize=10.sp)
            }
        }
        item{
            Row(Modifier.fillMaxWidth().padding(horizontal=18.dp).padding(bottom=14.dp),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){
                listOf("ALL","SERIES","MOVIES").forEach{filter->
                    val selected=filter==catalogueFilter
                    Surface(onClick={catalogueFilter=filter},shape=RoundedCornerShape(12.dp),color=if(selected)colors.primary else Color(0xFF15151D),border=androidx.compose.foundation.BorderStroke(1.dp,if(selected)Color(0xFFFF9A6C) else Color(0xFF282832))){
                        Text(filter,Modifier.padding(horizontal=15.dp,vertical=9.dp),color=Color.White,fontSize=10.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=0.7.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                if(submitted.isNotBlank())TextButton(onClick={q="";submitted="";catalogueFilter="ALL"}){Text("CLEAR",color=colors.primary,fontSize=10.sp,fontWeight=FontWeight.Bold)}
            }
        }
        when{
            loading->item{
                Column(Modifier.fillMaxWidth().padding(vertical=45.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    CircularProgressIndicator(color=colors.primary,strokeWidth=2.5.dp)
                    Spacer(Modifier.height(12.dp))
                    Text("Loading titles…",color=Color(0xFF888894),fontSize=11.sp)
                }
            }
            error!=null->item{
                Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Text("Catalogue couldn't load",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(error!!,color=Color(0xFF9999A5),fontSize=11.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick={catalogReload++},shape=RoundedCornerShape(13.dp),colors=ButtonDefaults.buttonColors(containerColor=colors.primary)){Text("TRY AGAIN",fontWeight=FontWeight.Bold)}
                }
            }
            filteredData.isEmpty()->item{
                Column(Modifier.fillMaxWidth().padding(vertical=42.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Text(if(submitted.isBlank())"Nothing here yet" else "No matching titles",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(if(submitted.isBlank())"Try another category." else "Try a shorter title or just the main anime name.",color=Color(0xFF888894),fontSize=11.sp)
                    if(submitted.isNotBlank()){
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick={q="";submitted=""}){Text("SHOW ALL TITLES",color=colors.primary,fontWeight=FontWeight.Bold)}
                    }
                }
            }
            else->{
                items(filteredData.chunked(2),key={row->row.joinToString("|"){it.kind+":"+it.slug}}){row->
                    Row(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)){
                        AnimeGridCard(row[0],Modifier.weight(1f)){onOpen(row[0].slug)}
                        if(row.size>1)AnimeGridCard(row[1],Modifier.weight(1f)){onOpen(row[1].slug)} else Spacer(Modifier.weight(1f))
                    }
                }
                if(hasMore){
                    item{
                        Box(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=22.dp),contentAlignment=Alignment.Center){
                            if(loadingMore)CircularProgressIndicator(color=colors.primary,strokeWidth=2.dp)
                            else Button(onClick={catalogPage+=1},shape=RoundedCornerShape(15.dp),colors=ButtonDefaults.buttonColors(containerColor=Color(0xFF1A1A24)),modifier=Modifier.fillMaxWidth().height(50.dp)){Text("LOAD MORE TITLES",color=Color.White,fontWeight=FontWeight.ExtraBold,letterSpacing=0.8.sp)}
                        }
                    }
                }else item{Box(Modifier.fillMaxWidth().padding(20.dp),contentAlignment=Alignment.Center){Text("You're all caught up.",color=Color(0xFF70707C),fontSize=10.sp)}}
            }
        }
    }
    BottomNavigationBar(
        modifier=Modifier.align(Alignment.BottomCenter),
        active="HOME",
        onHome={q="";submitted="";catalogueFilter="ALL"},
        onSearch={if(q.isBlank())q=submitted;submitted=""},
        onLibrary={showLibrary=true},
        onProfile=onSources
    )
    if(showLibrary) LibraryDialog(
        items=continueWatching,
        onOpen={watch->showLibrary=false;onOpen(watch.animeSlug)},
        onDismiss={showLibrary=false}
    )
    }
}


@Composable private fun BottomNavigationBar(
    modifier:Modifier=Modifier,
    active:String,
    onHome:()->Unit,
    onSearch:()->Unit,
    onLibrary:()->Unit,
    onProfile:()->Unit
){
    Surface(
        modifier=modifier.fillMaxWidth(),
        color=Color(0xF5111118),
        tonalElevation=8.dp,
        shadowElevation=12.dp
    ){
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=10.dp,vertical=8.dp),
            horizontalArrangement=Arrangement.spacedBy(4.dp),
            verticalAlignment=Alignment.CenterVertically
        ){
            listOf(Triple("HOME","⌂",onHome),Triple("SEARCH","⌕",onSearch),Triple("LIBRARY","▣",onLibrary),Triple("SOURCES","●",onProfile)).forEach{item->
                val selected=active==item.first
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                        .background(if(selected)Color(0xFF2A1A18) else Color.Transparent)
                        .clickable{item.third()}.padding(vertical=5.dp),
                    horizontalAlignment=Alignment.CenterHorizontally
                ){
                    Text(item.second,color=if(selected)colors.primary else Color(0xFF858592),fontSize=21.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    Text(item.first,color=if(selected)colors.primary else Color(0xFF858592),fontSize=8.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=0.5.sp)
                }
            }
        }
    }
}

@Composable private fun LibraryDialog(
    items:List<Episode>,
    onOpen:(Episode)->Unit,
    onDismiss:()->Unit
){
    AlertDialog(
        onDismissRequest=onDismiss,
        shape=RoundedCornerShape(24.dp),
        containerColor=Color(0xFF14141B),
        title={Text("Continue watching",color=Color.White,fontWeight=FontWeight.ExtraBold)},
        text={
            if(items.isEmpty()){
                Text("Episodes you play will appear here.",color=Color(0xFF9999A5),fontSize=13.sp)
            }else{
                LazyColumn(Modifier.heightIn(max=390.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                    items(items,key={it.animeSlug+it.url}){episode->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                                .background(Color(0xFF202029)).clickable{onOpen(episode)}
                                .padding(12.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ){
                            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF38211F)),contentAlignment=Alignment.Center){
                                Text("▶",color=colors.primary,fontSize=11.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)){
                                Text(episode.title.ifBlank{"Anime"},color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold,maxLines=1)
                                Text("Episode "+episode.displayNumber.ifBlank{episode.number.toString()},color=Color(0xFF9999A5),fontSize=10.sp)
                            }
                            Text("›",color=colors.primary,fontSize=24.sp)
                        }
                    }
                }
            }
        },
        confirmButton={TextButton(onClick=onDismiss){Text("CLOSE",color=colors.primary,fontWeight=FontWeight.Bold)}}
    )
}

@Composable private fun AnimeGridCard(anime:Anime,modifier:Modifier=Modifier,onClick:()->Unit){
    var pressed by remember(anime.slug){mutableStateOf(false)}
    val scale by animateFloatAsState(if(pressed)0.975f else 1f,animationSpec=tween(180),label="animeCardScale")
    Column(
        modifier.graphicsLayer{scaleX=scale;scaleY=scale}.clip(RoundedCornerShape(19.dp))
            .background(Color(0xFF14141C)).clickable{pressed=true;onClick()}.padding(8.dp).animateContentSize()
    ){
        Box(Modifier.fillMaxWidth().height(174.dp).clip(RoundedCornerShape(13.dp))){
            AsyncImage(anime.image,anime.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0x8806060A)))))
            Box(Modifier.align(Alignment.TopStart).padding(8.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xCC101016)).padding(horizontal=7.dp,vertical=5.dp)){
                Text(if(anime.kind=="movie")"MOVIE" else "SERIES",color=Color.White,fontSize=8.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=0.7.sp)
            }
            Box(Modifier.align(Alignment.BottomEnd).padding(8.dp).size(31.dp).clip(RoundedCornerShape(50)).background(colors.primary),contentAlignment=Alignment.Center){Text("↗",color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Bold)}
        }
        Spacer(Modifier.height(10.dp))
        Text(anime.title,color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold,maxLines=2,minLines=2,lineHeight=16.sp)
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment=Alignment.CenterVertically){
            Text(anime.type,color=Color(0xFF9A9AA7),fontSize=9.sp,maxLines=1)
            Spacer(Modifier.weight(1f))
            if(anime.score.isNotBlank() && anime.score!="—")Text("★ "+anime.score,color=Color(0xFFFFC28F),fontSize=9.sp,fontWeight=FontWeight.Bold)
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
    var selectedSeason by remember{mutableIntStateOf(1)}
    var detailsTab by remember{mutableStateOf("EPISODES")}
    
    var playingEpisode by remember{mutableStateOf<Episode?>(null)}
    var playerLoading by remember{mutableStateOf(false)}
    var playerError by remember{mutableStateOf<String?>(null)}
    var playerRetry by remember{mutableIntStateOf(0)}

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
    LaunchedEffect(playingEpisode?.url,playerRetry){
        val ep=playingEpisode?:return@LaunchedEffect
        playerLoading=true;playerError=null
        try{
            val servers=withContext(Dispatchers.IO){fetchEmbeddedPlayer(ep.url)}
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            // Some episode pages render their player client-side or hide the iframe
            // until the page is opened in a real WebView. If extraction finds no iframe,
            // open the episode page itself instead of stopping at a scraper error.
            val availableServers=if(servers.isEmpty()){
                listOf(PlayerServer("Episode page",ep.url,ep.url))
            }else servers
            val ready=ep.copy(servers=availableServers)
            playingEpisode=ready
            saveContinueWatching(context,ready)
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){
            // If the API cannot reach AnimeSalt, open the episode page directly in WebView.
            val fallback=ep.copy(servers=listOf(PlayerServer("Episode page",ep.url,ep.url)))
            playingEpisode=fallback
            saveContinueWatching(context,fallback)
            playerError=null
        }finally{playerLoading=false}
    }

    val availableSeasons=remember(eps,anime){ val declared=(anime?.seasons ?: 1).coerceIn(1,30); (1..maxOf(declared,eps.maxOfOrNull{it.season} ?: 1)).toList() }
    LaunchedEffect(availableSeasons){
        if(availableSeasons.isNotEmpty() && selectedSeason !in availableSeasons) selectedSeason=availableSeasons.first()
    }
    val visibleEpisodes=remember(eps,episodeQuery,selectedSeason){
        val seasonEpisodes=eps.filter{it.season==selectedSeason}
        val term=episodeQuery.trim()
        if(term.isBlank()) seasonEpisodes else seasonEpisodes.filter {
            it.displayNumber.contains(term,true) || it.number.toString().contains(term,true) || it.title.contains(term,true)
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
                        TextButton(onClick={playerError=null;playerRetry++}){Text("Retry")}
                        TextButton(onClick={playingEpisode=null;playerError=null}){Text("Back")}
                    }
                }
            }
        }else if(playingEpisode!!.servers.firstOrNull()?.url?.let{url->
            val mediaPath=try{Uri.parse(url).path.orEmpty()}catch(_:Exception){url.substringBefore('?')}
            listOf(".m3u8",".mp4",".mpd",".webm",".m4v",".mov").any{mediaPath.endsWith(it,true)}
        }==true) SourcePlayer(playingEpisode!!,eps,onBack={playingEpisode=null},onEpisode={playingEpisode=it})
        else SourceWebPlayer(playingEpisode!!, playingEpisode!!.servers.first(), eps, onBack={playingEpisode=null}, onEpisode={playingEpisode=it})
        return
    }

    chosen?.let{ep->
        AlertDialog(
            onDismissRequest={chosen=null},
            shape=RoundedCornerShape(24.dp),
            containerColor=Color(0xFF14141B),
            title={Text("Episode "+ep.displayNumber.ifBlank{ep.number.toString()},color=Color.White,fontWeight=FontWeight.ExtraBold)},
            text={Text("Play this episode with AniSteam's native video player.",color=Color(0xFF9D9DA8))},
            confirmButton={TextButton(onClick={chosen=null;playingEpisode=ep}){Text("PLAY",color=colors.primary,fontWeight=FontWeight.Bold)}},
            dismissButton={TextButton(onClick={chosen=null}){Text("CANCEL",color=Color(0xFF858590))}}
        )
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF07070B))){
        Spacer(Modifier.height(0.dp))

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
                        Modifier.fillMaxWidth().height(324.dp)
                            .clip(RoundedCornerShape(bottomStart=28.dp,bottomEnd=28.dp))
                    ){
                        AsyncImage(anime!!.image,anime!!.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    listOf(Color(0x22000000),Color(0x55000000),Color(0xF507070B))
                                )
                            )
                        )
                        Row(Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal=14.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically){
                            Surface(onClick=onBack,shape=RoundedCornerShape(50),color=Color(0x99101016)){
                                Box(Modifier.size(40.dp),contentAlignment=Alignment.Center){Text("‹",color=Color.White,fontSize=29.sp)}
                            }
                            Spacer(Modifier.weight(1f))
                            Surface(shape=RoundedCornerShape(12.dp),color=Color(0x99101016)){
                                Text("ANISTEAM",Modifier.padding(horizontal=12.dp,vertical=10.dp),color=Color.White,fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.2.sp)
                            }
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(22.dp)){
                            Text(if(anime!!.kind=="movie")"FEATURED MOVIE" else "ACTION  •  ANIME",color=Color(0xFFFFA16F),fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.6.sp)
                            Spacer(Modifier.height(7.dp))
                            Text(anime!!.title,color=Color.White,fontSize=30.sp,fontWeight=FontWeight.ExtraBold,maxLines=3)
                            Spacer(Modifier.height(7.dp))
                            Text(
                                anime!!.type+"  •  "+anime!!.episodes+" episodes"+if(anime!!.score.isNotBlank() && anime!!.score!="—")"  •  ★ "+anime!!.score else "",
                                color=Color(0xFFD4D4DC),fontSize=11.sp
                            )
                        }
                    }
                }
                item{
                    Surface(
                        onClick={eps.lastOrNull()?.let{playingEpisode=it} ?: run{episodeReload++}},
                        modifier=Modifier.fillMaxWidth().padding(horizontal=18.dp).padding(top=16.dp),
                        shape=RoundedCornerShape(15.dp),color=colors.primary
                    ){
                        Row(Modifier.fillMaxWidth().padding(vertical=15.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                            Text("▶",color=Color.White,fontSize=13.sp)
                            Spacer(Modifier.width(10.dp))
                            Text("Play latest episode",color=Color.White,fontSize=14.sp,fontWeight=FontWeight.ExtraBold)
                        }
                    }
                }
                item{
                    Column(Modifier.padding(horizontal=18.dp,vertical=18.dp)){
                        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            listOf("OVERVIEW","EPISODES","RELATED").forEach{tab->
                                Surface(
                                    onClick={detailsTab=tab},
                                    modifier=Modifier.weight(1f),
                                    shape=RoundedCornerShape(11.dp),
                                    color=if(detailsTab==tab)Color(0xFF2A1A18) else Color(0xFF121219),
                                    border=androidx.compose.foundation.BorderStroke(1.dp,if(detailsTab==tab)colors.primary else Color(0xFF252530))
                                ){
                                    Box(Modifier.padding(vertical=11.dp),contentAlignment=Alignment.Center){
                                        Text(tab,color=if(detailsTab==tab)colors.primary else Color(0xFF92929F),fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=0.4.sp)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(18.dp))
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
                        if(detailsTab=="OVERVIEW"){
                            Text("ABOUT",color=colors.primary,fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.8.sp)
                            Spacer(Modifier.height(7.dp))
                            Text(anime!!.synopsis.ifBlank{"No synopsis available."},color=Color(0xFFB2B2BC),fontSize=13.sp,lineHeight=20.sp)
                            Spacer(Modifier.height(18.dp))
                            Text("A focused anime experience",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text("Choose a season, find an episode, and continue watching without losing your place.",color=Color(0xFF888894),fontSize=12.sp,lineHeight=18.sp)
                        }
                        if(detailsTab=="EPISODES"){
                        Row(verticalAlignment=Alignment.CenterVertically){
                            Column(Modifier.weight(1f)){
                                Text("Episodes",color=Color.White,fontSize=23.sp,fontWeight=FontWeight.Black)
                                Text(if(eps.isEmpty())"Full episode guide" else "${visibleEpisodes.size} episodes in Season $selectedSeason",color=Color(0xFF858A98),fontSize=11.sp)
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
                        if(!loading && eps.isNotEmpty()){
                            Spacer(Modifier.height(14.dp))
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                                Text("SEASON",color=Color(0xFF858590),fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.sp)
                                Spacer(Modifier.width(10.dp))
                                LazyRow(horizontalArrangement=Arrangement.spacedBy(7.dp)){
                                    items(availableSeasons){seasonNumber->
                                        Surface(
                                            onClick={selectedSeason=seasonNumber},
                                            shape=RoundedCornerShape(10.dp),
                                            color=if(selectedSeason==seasonNumber)colors.primary else Color(0xFF191920),
                                            border=androidx.compose.foundation.BorderStroke(1.dp,if(selectedSeason==seasonNumber)colors.primary else Color(0xFF2A2A34))
                                        ){
                                            Text("Season "+seasonNumber,Modifier.padding(horizontal=12.dp,vertical=9.dp),color=Color.White,fontSize=10.sp,fontWeight=FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                    }
                        if(detailsTab=="RELATED"){
                            Column(Modifier.fillMaxWidth().padding(vertical=28.dp),horizontalAlignment=Alignment.CenterHorizontally){
                                Text("Discover your next series",color=Color.White,fontSize=15.sp,fontWeight=FontWeight.Bold)
                                Spacer(Modifier.height(6.dp))
                                Text("Related-title recommendations will appear here when provided by the source.",color=Color(0xFF858590),fontSize=11.sp,lineHeight=17.sp)
                            }
                        }

                    }
                }
                if(detailsTab=="EPISODES"){
                if(loading){
                    item{
                        Box(Modifier.fillMaxWidth().height(100.dp),contentAlignment=Alignment.Center){
                            CircularProgressIndicator(color=colors.primary,strokeWidth=2.dp)
                        }
                    }
                }else{
                    items(visibleEpisodes,key={it.url}){ep->
                        Row(
                            Modifier.padding(horizontal=18.dp,vertical=5.dp).fillMaxWidth()
                                .clip(RoundedCornerShape(17.dp)).background(Color(0xFF151820))
                                .clickable{chosen=ep}.padding(9.dp),
                            verticalAlignment=Alignment.CenterVertically
                        ){
                            Box(Modifier.size(84.dp,58.dp).clip(RoundedCornerShape(11.dp))){
                                AsyncImage(anime!!.image,anime!!.title,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
                                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x11000000),Color(0xAA05050A)))))
                                Box(Modifier.align(Alignment.BottomStart).padding(5.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xDD101016)).padding(horizontal=5.dp,vertical=3.dp)){
                                    Text("S"+ep.season+"  ·  "+ep.displayNumber.ifBlank{ep.number.toString()},color=Color.White,fontSize=8.sp,fontWeight=FontWeight.ExtraBold,maxLines=1)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)){
                                Text(if(ep.title.isBlank())"Episode "+ep.number else ep.title,color=Color.White,fontWeight=FontWeight.Bold,fontSize=13.sp,maxLines=2,lineHeight=16.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("EPISODE "+ep.displayNumber.ifBlank{ep.number.toString()}+(if(ep.aired.isNotBlank())"  ·  "+ep.aired else ""),color=Color(0xFF858A98),fontSize=9.sp,fontWeight=FontWeight.Medium,maxLines=1)
                            }
                            Box(Modifier.size(34.dp).clip(RoundedCornerShape(50)).background(Color(0xFF30201E)),contentAlignment=Alignment.Center){
                                Text("▶",color=colors.primary,fontSize=10.sp)
                            }
                        }
                    }
                    if(visibleEpisodes.isEmpty() && eps.isNotEmpty()){
                        item{
                            Column(Modifier.fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally){
                                Text(if(episodeQuery.isBlank())"No episodes loaded for Season $selectedSeason" else "No episodes match this search.",color=Color(0xFF858590),fontSize=12.sp)
                                Spacer(Modifier.height(10.dp))
                                Button(onClick={if(episodeQuery.isNotBlank())episodeQuery="" else episodeReload++},colors=ButtonDefaults.buttonColors(containerColor=colors.primary),shape=RoundedCornerShape(13.dp)){
                                    Text(if(episodeQuery.isNotBlank())"CLEAR SEARCH" else "RETRY SEASON",color=Color.White,fontWeight=FontWeight.Bold)
                                }
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
}

private fun saveContinueWatching(context:Context,ep:Episode){
    val prefs=context.getSharedPreferences("anisteam_watch",Context.MODE_PRIVATE)
    val current=loadContinueWatchingList(context).filterNot{it.animeSlug==ep.animeSlug}
    val updated=(listOf(ep)+current).take(20)
    val json=JSONArray()
    updated.forEach{
        json.put(JSONObject().apply{
            put("number",it.number)
            put("displayNumber",it.displayNumber)
            put("season",it.season)
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
                animeSlug=o.optString("slug",""),
                displayNumber=o.optString("displayNumber",""),
                season=o.optInt("season",1).coerceAtLeast(1)
            )
        }.filter{it.animeSlug.isNotBlank() && it.url.isNotBlank()}
    }catch(_:Exception){emptyList()}
}
private fun loadContinueWatching(context:Context):Episode?=loadContinueWatchingList(context).firstOrNull()

private const val BASE_URL = "https://animesalt.cx"

@Composable private fun SourceDialog(onDismiss:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text("Sources")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)){SOURCES.forEach { source ->
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF17171D)).padding(14.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(source.name,color=Color.White,fontWeight=FontWeight.Bold);Text(source.baseUrl,color=Color.Gray,fontSize=11.sp)};Text(if(source.enabled)"ON" else "OFF",color=colors.secondary,fontWeight=FontWeight.Bold)}
    }}},confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}

private const val API_BASE_URL = "https://anisteam-api.vercel.app"

private fun fetchApiJson(path:String):JSONObject{
    val connection=(URL(API_BASE_URL+path).openConnection() as HttpURLConnection).apply{
        requestMethod="GET"
        connectTimeout=12_000
        readTimeout=22_000
        useCaches=true
        setRequestProperty("Accept","application/json")
        setRequestProperty("User-Agent","AniSteam-Android/1.0")
    }
    try{
        val status=connection.responseCode
        val stream=if(status in 200..299)connection.inputStream else connection.errorStream
        val body=stream?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}.orEmpty()
        if(status !in 200..299) {
            val message=try{
                val error=JSONObject(body).opt("error")
                if(error is JSONObject)error.optString("message").ifBlank{error.optString("code")} else error?.toString().orEmpty()
            }catch(_:Exception){""}
            throw java.io.IOException(message.ifBlank{"AniSteam API returned HTTP $status"})
        }
        val json=try{JSONObject(body)}catch(e:Exception){throw java.io.IOException("AniSteam API returned invalid JSON",e)}
        if(!json.optBoolean("success",true)){
            val error=json.opt("error")
            val message=if(error is JSONObject)error.optString("message") else error?.toString().orEmpty()
            throw java.io.IOException(message.ifBlank{"AniSteam API request failed"})
        }
        return json
    }finally{connection.disconnect()}
}
private fun animeRouteKey(sourceId:String,link:String)=sourceId+"::"+link
private fun unpackAnimeRouteKey(value:String):Pair<String,String>{
    val split=value.indexOf("::")
    if(split>0){
        val source=value.substring(0,split).lowercase()
        val link=value.substring(split+2)
        if(link.startsWith("https://") || link.startsWith("http://"))return (if(source=="toonstream")"toonstream" else "animesalt") to link
    }
    if(value.startsWith("https://") || value.startsWith("http://")){
        val host=Uri.parse(value).host.orEmpty()
        val source=if(host.contains("toonstream",true)||host.contains("toon-stream",true))"toonstream" else "animesalt"
        return source to value
    }
    val normalized=value.trim('/')
    val link=if(normalized.startsWith("http://")||normalized.startsWith("https://"))normalized else "$BASE_URL/$normalized/"
    return "animesalt" to link
}
private fun jsonArrayObjects(array:JSONArray?):List<JSONObject>{
    if(array==null)return emptyList()
    return (0 until array.length()).mapNotNull{array.optJSONObject(it)}
}
private fun catalogAnime(item:JSONObject,defaultSource:String="animesalt"):Anime?{
    val title=item.optString("title").trim()
    val link=item.optString("link").ifBlank{item.optString("url")}
    if(title.isBlank()||link.isBlank())return null
    val source=if(item.optString("source").contains("toonstream",true))"toonstream" else defaultSource
    val kind=if(item.optString("type").equals("movie",true)||link.contains("/movie",true)||link.contains("/movies/",true))"movie" else "series"
    val image=item.optString("image").ifBlank{item.optString("poster").ifBlank{item.optString("poster_image")}}
    val key=animeRouteKey(source,link)
    return Anime(key.hashCode(),title,image,"—","?",if(kind=="movie")"Movie" else "Anime","",source,1,key,kind)
}
private suspend fun fetchAnime(q:String,page:Int=1)=withContext(Dispatchers.IO){
    if(q.isBlank())return@withContext fetchHome(page)
    if(page>1)return@withContext emptyList<Anime>()
    val encoded=URLEncoder.encode(q.trim().replace(Regex("\\s+")," "),"UTF-8")
    val results=mutableListOf<Anime>()
    var lastError:Exception?=null
    try{
        val json=fetchApiJson("/search?q="+encoded)
        results+=jsonArrayObjects(json.optJSONArray("results")).mapNotNull{catalogAnime(it)}
    }catch(e:kotlinx.coroutines.CancellationException){throw e}
    catch(e:Exception){lastError=e}
    // A normal on-device request can work when the API host's IP is blocked.
    try{results+=fetchDirectAnime(q,1)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){lastError=e}
    val unique=results.distinctBy{it.sourceId+":"+it.slug}
    if(unique.isEmpty())throw java.io.IOException("Search sources are unavailable right now.",lastError)
    unique
}
private suspend fun fetchHome(page:Int=1)=withContext(Dispatchers.IO){
    val results=mutableListOf<Anime>()
    var lastError:Exception?=null
    try{
        val json=fetchApiJson("/catalogue?page="+page)
        results+=jsonArrayObjects(json.optJSONArray("results")).mapNotNull{catalogAnime(it)}
    }catch(e:kotlinx.coroutines.CancellationException){throw e}
    catch(e:Exception){lastError=e}
    if(results.isEmpty()){
        for(endpoint in listOf("/api/series?page="+page,"/api/movies?page="+page)){
            try{
                val json=fetchApiJson(endpoint)
                val envelope=json.optJSONObject("results")
                val items=envelope?.optJSONArray("results") ?: json.optJSONArray("results")
                results+=jsonArrayObjects(items).mapNotNull{catalogAnime(it,"animesalt")}
            }catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(e:Exception){lastError=e}
        }
    }
    try{results+=fetchDirectAnime("",page)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){lastError=e}
    val unique=results.distinctBy{it.sourceId+":"+it.slug}
    if(unique.isEmpty() && page==1)throw java.io.IOException("Catalogue sources are temporarily unavailable. Tap TRY AGAIN in a moment.",lastError)
    unique
}
private suspend fun fetchDirectAnime(query:String,page:Int)=withContext(Dispatchers.IO){
    val encoded=URLEncoder.encode(query.trim().replace(Regex("\\s+")," "),"UTF-8")
    val url=when{
        query.isNotBlank() -> BASE_URL+"/?s="+encoded
        page>1 -> BASE_URL+"/series/page/"+page+"/"
        else -> BASE_URL+"/"
    }
    val parsed=extractSeries(getHtml(url))
    if(query.isBlank())parsed
    else{
        val tokens=query.lowercase().split(Regex("\\s+")).filter{it.isNotBlank()}
        parsed.filter{anime->tokens.count{anime.title.lowercase().contains(it)}>=maxOf(1,kotlin.math.ceil(tokens.size*0.6).toInt())}
    }
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
        val container=a.closest("li, article, .item, .row, .item-list, .post, .anime-card, .film, .movie-item, .series-item, .flw-item, .bs, .TPost, .TPostMv, [class*=item], [class*=film], [class*=anime], [class*=post]") ?: a.parent()?.parent()?.parent() ?: a.parent()
        val imageElement=a.selectFirst("img, source") ?: container?.selectFirst("img, source")
        val title=(a.selectFirst("h1,h2,h3,h4,.entry-title,.title,.Title,.name,.Name,.film-name,.tt")?.text()
            ?: container?.selectFirst("h2,h3,h4,.entry-title,.title,.name,.film-name,.tt")?.text()
            ?: a.attr("title").ifBlank { a.attr("aria-label").ifBlank { a.text() } })
            .replace(Regex("\\s+")," ").trim().ifBlank { imageElement?.attr("alt").orEmpty().removePrefix("Image ").trim() }.removePrefix("Image ").trim()
        if(title.isBlank()) return@mapNotNull null
        val kind=if(marker.contains("movie",ignoreCase=true)) "movie" else "series"
        Anime(sourcePath.hashCode(),title,posterFrom(imageElement),"—","?","Anime",sourcePath,"animesalt",1,sourcePath,kind)
    }.distinctBy{it.kind+":"+it.slug}
}

private suspend fun fetchDetailsBySlug(slug:String)=withContext(Dispatchers.IO){
    val (sourceId,link)=unpackAnimeRouteKey(slug)
    val data=try{fetchApiJson("/details?url="+URLEncoder.encode(link,"UTF-8"))}
    catch(e:kotlinx.coroutines.CancellationException){throw e}
    catch(e:Exception){
        if(sourceId!="animesalt")throw e
        return@withContext parseDirectDetails(getHtml(link),sourceId,link)
    }
    val title=data.optString("title").ifBlank{Uri.parse(link).lastPathSegment.orEmpty().replace('-',' ').replaceFirstChar{it.uppercase()}}
    if(title.isBlank())throw java.io.IOException("The API did not return a title.")
    val kind=if(data.optString("kind").equals("movie",true))"movie" else "series"
    val episodes=data.optInt("episode_count",0)
    Anime(
        id=link.hashCode(),title=title,image=data.optString("poster"),
        score=data.optString("score").ifBlank{"—"},
        episodes=if(episodes>0)episodes.toString() else "?",
        type=if(kind=="movie")"Movie" else "Anime",synopsis=data.optString("overview"),
        sourceId=sourceId,seasons=data.optInt("seasons",1).coerceAtLeast(1),
        slug=animeRouteKey(sourceId,link),kind=kind
    )
}
private fun parseDirectDetails(doc:org.jsoup.nodes.Document,sourceId:String,link:String):Anime{
    val title=doc.selectFirst("h1.entry-title,h1,.entry-title,meta[property=og:title]")?.let{
        if(it.tagName()=="meta")it.attr("content") else it.text()
    }?.replace(Regex("\\s+")," ")?.trim().orEmpty()
    if(title.isBlank())throw java.io.IOException("AnimeSalt did not return a readable title page.")
    val image=posterFrom(doc.selectFirst(".post-thumbnail img,.poster img,.thumbook img,.thumb img,img[alt]"))
    val overview=doc.selectFirst(".description p,.entry-content p,.synopsis,.description,.desc,meta[name=description]")?.let{
        if(it.tagName()=="meta")it.attr("content") else it.text()
    }?.replace(Regex("\\s+")," ")?.trim().orEmpty()
    val body=doc.text()
    val seasons=Regex("(\\d+)\\s+seasons?",RegexOption.IGNORE_CASE).find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: doc.select(".season-btn,[data-season].season-btn").size.coerceAtLeast(1)
    val count=Regex("(\\d+)\\s+episodes?",RegexOption.IGNORE_CASE).find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()
        ?: doc.select("a[href*=episode]").size
    val kind=if(link.contains("/movie",true)||link.contains("/movies/",true))"movie" else "series"
    return Anime(link.hashCode(),title,image,"—",if(count>0)count.toString() else "?",
        if(kind=="movie")"Movie" else "Anime",overview,sourceId,seasons,animeRouteKey(sourceId,link),kind)
}
private suspend fun fetchMovieEpisode(slug:String)=withContext(Dispatchers.IO){
    val (sourceId,link)=unpackAnimeRouteKey(slug)
    val title=try{fetchApiJson("/details?url="+URLEncoder.encode(link,"UTF-8")).optString("title").ifBlank{"Movie"}}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){try{parseDirectDetails(getHtml(link),sourceId,link).title}catch(_:Exception){"Movie"}}
    listOf(Episode(1,title,"",link,animeSlug=animeRouteKey(sourceId,link),displayNumber="Movie",season=1))
}
private suspend fun fetchEpisodesBySlug(slug:String,season:Int=1)=withContext(Dispatchers.IO){
    val (sourceId,link)=unpackAnimeRouteKey(slug)
    val results=mutableListOf<Episode>()
    var lastError:Exception?=null
    try{
        val data=fetchApiJson("/"+sourceId+"/episodes?url="+URLEncoder.encode(link,"UTF-8"))
        jsonArrayObjects(data.optJSONArray("episodes")).forEach{item->
            val episodeUrl=item.optString("link").trim()
            if(episodeUrl.isBlank())return@forEach
            val rawEp=item.optString("episode").ifBlank{item.optString("epNum")}
            val parsedPair=Regex("(\\d+)x(\\d+(?:\\.\\d+)?)",RegexOption.IGNORE_CASE).find(rawEp)
            val episodeNumber=(parsedPair?.groupValues?.getOrNull(2)?.toDoubleOrNull()?.toInt()
                ?:rawEp.toDoubleOrNull()?.toInt()?:item.optInt("episode",0)).coerceAtLeast(1)
            val seasonNumber=(item.optString("season").toIntOrNull()
                ?:parsedPair?.groupValues?.getOrNull(1)?.toIntOrNull()?:season).coerceAtLeast(1)
            val label=item.optString("epNum").ifBlank{if(seasonNumber>1)seasonNumber.toString()+"x"+episodeNumber else episodeNumber.toString()}
            results.add(Episode(episodeNumber,item.optString("title").ifBlank{"Episode $label"},"",episodeUrl,
                animeSlug=animeRouteKey(sourceId,link),displayNumber=label,season=seasonNumber))
        }
    }catch(e:kotlinx.coroutines.CancellationException){throw e}
    catch(e:Exception){lastError=e}
    if(sourceId=="animesalt"){
        try{results+=fetchDirectEpisodes(link,animeRouteKey(sourceId,link),season)}
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){lastError=e}
    }
    val unique=results.groupBy{it.url}.mapNotNull{(_,items)->items.maxByOrNull{(if(it.displayNumber.contains("x",true))4 else 0)+(if(it.season>1)2 else 0)+(if(it.title.isNotBlank())1 else 0)}}.sortedWith(compareBy<Episode>{it.season}.thenBy{it.number}.thenBy{it.url})
    if(unique.isEmpty() && lastError!=null)throw java.io.IOException("Could not load episodes from API or AnimeSalt directly.",lastError)
    unique
}
private suspend fun fetchDirectEpisodes(link:String,slug:String,declaredSeasonCount:Int=1)=withContext(Dispatchers.IO){
    val doc=getHtml(link)
    var lastError:Exception?=null
    val rawTargets=doc.select(".season-btn, [data-season], [data-season-id], [class*=season] [data-url], [class*=season] a[href]")
        .mapNotNull{element->
            val label=element.text().replace(Regex("\\s+")," ").trim()
            val rawNumber=element.attr("data-season").ifBlank{element.attr("data-season-id")}
                .ifBlank{Regex("(?:season\\s*|\\bS)(\\d+)\\b",RegexOption.IGNORE_CASE).find(label)?.groupValues?.getOrNull(1).orEmpty()}
            val number=rawNumber.toIntOrNull() ?: return@mapNotNull null
            val postId=element.attr("data-post").ifBlank{element.attr("data-post-id")}.ifBlank{element.attr("data-id")}.trim()
            val rawUrl=element.attr("data-url").ifBlank{element.attr("href")}.trim()
            val targetUrl=if(rawUrl.isBlank() || rawUrl.startsWith("#") || rawUrl.startsWith("javascript:",true)) "" else try{
                java.net.URI(link).resolve(rawUrl).toString()
            }catch(_:Exception){""}
            val host=try{Uri.parse(targetUrl).host.orEmpty().lowercase()}catch(_:Exception){""}
            val safeUrl=if(host.contains("animesalt.") && targetUrl!=link)targetUrl else ""
            SeasonTarget(number,postId,label,safeUrl)
        }
    val seasonTargets=rawTargets.groupBy{it.number}.values.mapNotNull{group->
        group.maxByOrNull{(if(it.url.isNotBlank())2 else 0)+(if(it.postId.isNotBlank())1 else 0)}
    }.sortedBy{it.number}
    val seasonsInLinks=doc.select("a[href], a[data-href], a[data-url]").mapNotNull{anchor->
        val raw=anchor.attr("href").ifBlank{anchor.attr("data-href")}.ifBlank{anchor.attr("data-url")}
        val href=try{java.net.URI(link).resolve(raw).toString()}catch(_:Exception){raw}
        val path=try{Uri.parse(href).path.orEmpty()}catch(_:Exception){href}
        Regex("(?:^|[-/])(\\d+)x\\d+(?:$|[-/])",RegexOption.IGNORE_CASE)
            .find(path)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
    val seasonNumbers=((1..declaredSeasonCount.coerceIn(1,30)).toList()+seasonTargets.map{it.number}+seasonsInLinks)
        .filter{it>0}.distinct().sorted()
    val all=mutableListOf<Episode>()
    for(targetSeason in seasonNumbers){
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val target=seasonTargets.firstOrNull{it.number==targetSeason}
        var found=if(targetSeason==1 || seasonsInLinks.contains(targetSeason))
            parseEpisodeList(doc,slug,targetSeason).distinctBy{it.url}
        else emptyList()
        if(found.isNotEmpty() && target?.url.isNullOrBlank() && target?.postId.isNullOrBlank()){
            all+=found
            continue
        }
        if(target?.url?.isNotBlank()==true){
            for(attempt in 0..1){
                try{
                    val seasonHtml=getHtml(target.url)
                    found+=parseEpisodeList(seasonHtml,slug,targetSeason)
                    break
                }catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){lastError=e;if(attempt==0)kotlinx.coroutines.delay(300)}
            }
        }
        if(!target?.postId.isNullOrBlank()){
            for(attempt in 0..1){
                try{
                    val ajax=postHtml(BASE_URL+"/wp-admin/admin-ajax.php",
                        mapOf("action" to "action_select_season","season" to targetSeason.toString(),"post" to target!!.postId),
                        link)
                    found+=parseEpisodeList(ajax,slug,targetSeason)
                    if(found.isNotEmpty())break
                }catch(e:kotlinx.coroutines.CancellationException){throw e}
                catch(e:Exception){lastError=e;if(attempt==0)kotlinx.coroutines.delay(300)}
            }
        }
        all+=found.distinctBy{it.url}.sortedBy{it.number}
    }
    val unique=all.groupBy{it.url}.mapNotNull{(_,items)->
        items.maxByOrNull{(if(it.displayNumber.contains("x"))2 else 0)+(if(it.title.isNotBlank())1 else 0)}
    }.sortedWith(compareBy<Episode>{it.season}.thenBy{it.number}.thenBy{it.url})
    if(unique.isEmpty() && lastError!=null)throw java.io.IOException("AnimeSalt season extraction failed: "+(lastError.message ?: "unknown error"),lastError)
    unique
}
private fun parseEpisodeList(doc:org.jsoup.nodes.Document,slug:String,season:Int,episodeOffset:Int=0):List<Episode>{
    val candidates=doc.select("a[href], a[data-href], a[data-url]").mapNotNull { link ->
        val rawHref=link.attr("href").ifBlank{link.attr("data-href")}.ifBlank{link.attr("data-url")}.trim()
        val href=try{java.net.URI(link.baseUri().ifBlank{BASE_URL}).resolve(rawHref).toString()}catch(_:Exception){link.absUrl("href").ifBlank{rawHref}}
        if(href.isBlank()) return@mapNotNull null
        val path=try{Uri.parse(href).path.orEmpty()}catch(_:Exception){href}
        if(!Regex("/episodes?/").containsMatchIn(path)) return@mapNotNull null
        val tail=path.substringAfter("/episode/").trim('/')
        if(tail.isBlank()) return@mapNotNull null

        val seasonEpisode=Regex("(?:^|[-/])(\\d+)x(\\d+)(?:$|[-/])",RegexOption.IGNORE_CASE).find(tail)
        val localNumber=seasonEpisode?.groupValues?.getOrNull(2)?.toIntOrNull()
            ?: Regex("(?:episode|ep)[-_/]?(\\d+)",RegexOption.IGNORE_CASE).find(tail)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: Regex("-(\\d+)$").find(tail)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?: return@mapNotNull null
        val urlSeason=seasonEpisode?.groupValues?.getOrNull(1)?.toIntOrNull() ?: season.coerceAtLeast(1)
        if(season>0 && seasonEpisode!=null && urlSeason!=season) return@mapNotNull null

        val container=link.closest("li,article,.item,.episode,.episode-item,.ep-item,[class*=episode]")
        val title=(link.selectFirst(".entry-title,h2,h3,h4,.title,.ep-title")?.text()
            ?: container?.selectFirst(".entry-title,h2,h3,h4,.title,.ep-title")?.text()
            ?: link.attr("title").ifBlank{link.text()}).replace(Regex("\\s+")," ").trim()
        val label=if(urlSeason>1) urlSeason.toString()+"x"+localNumber else localNumber.toString()
        Episode(number=localNumber+episodeOffset,title=title.ifBlank{"Episode $label"},aired="",url=href,
            animeSlug=slug,displayNumber=label,season=urlSeason)
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

@Composable private fun SourceWebPlayer(
    episode:Episode,
    server:PlayerServer,
    episodes:List<Episode>,
    onBack:()->Unit,
    onEpisode:(Episode)->Unit
){
    val context=LocalContext.current
    val activity=remember(context){context.findComponentActivity()}
    if(activity==null){
        Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){
            Column(horizontalAlignment=Alignment.CenterHorizontally,modifier=Modifier.padding(24.dp)){
                Text("Player could not start",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("AniSteam could not access the current Android activity.",color=Color(0xFFB7B7C2),fontSize=12.sp)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick=onBack){Text("BACK",color=colors.primary)}
            }
        }
        return
    }
    val chromeClient=remember(activity){
        AnimeSaltChromeClient(activity){ /* embedded player owns its playback UI */ }
    }
    val ordered=remember(episodes){episodes.sortedWith(compareBy<Episode>{it.season}.thenBy{it.number}.thenBy{it.url})}
    val currentIndex=ordered.indexOfFirst{it.url==episode.url}
    val previous=ordered.getOrNull(currentIndex-1)
    val next=ordered.getOrNull(currentIndex+1)

    DisposableEffect(episode.url,server.url){
        onDispose{chromeClient.exitFullscreen()}
    }
    BackHandler{onBack()}

    Box(Modifier.fillMaxSize().background(Color.Black)){
        AndroidView(
            factory={ctx->
                android.webkit.WebView(ctx).apply{
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled=true
                    settings.domStorageEnabled=true
                    settings.loadsImagesAutomatically=true
                    settings.mediaPlaybackRequiresUserGesture=false
                    settings.javaScriptCanOpenWindowsAutomatically=true
                    settings.setSupportMultipleWindows(false)
                    settings.mixedContentMode=android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    settings.allowContentAccess=true
                    settings.allowFileAccess=false
                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this,true)
                    webChromeClient=chromeClient
                    webViewClient=object:WebViewClient(){
                        private fun handlePlayerUrl(view:android.webkit.WebView,target:Uri):Boolean{
                            val scheme=target.scheme?.lowercase().orEmpty()
                            if(scheme=="http" || scheme=="https") return false

                            // Embedded players sometimes use intent:// links to hand playback
                            // to another app. Parse and launch defensively so a missing handler
                            // or malformed intent never crashes AniSteam.
                            if(scheme=="intent"){
                                var fallback:String?=null
                                try{
                                    val intent=Intent.parseUri(target.toString(),Intent.URI_INTENT_SCHEME)
                                    fallback=intent.getStringExtra("browser_fallback_url")
                                    try{
                                        view.context.startActivity(intent)
                                        return true
                                    }catch(_:android.content.ActivityNotFoundException){
                                        // Continue to the player-provided web fallback below.
                                    }catch(_:SecurityException){
                                        // A blocked external target should not close AniSteam.
                                    }
                                }catch(_:Exception){
                                    // Ignore malformed intent URLs and keep the current player open.
                                }
                                if(!fallback.isNullOrBlank()){
                                    val fallbackUri=try{Uri.parse(fallback)}catch(_:Exception){null}
                                    if(fallbackUri?.scheme=="http" || fallbackUri?.scheme=="https"){
                                        view.loadUrl(fallback!!)
                                    }
                                }
                                return true
                            }

                            // Do not let unknown custom schemes navigate the WebView or terminate
                            // the player. Only explicit HTTP(S) pages stay inside AniSteam.
                            return true
                        }

                        override fun shouldOverrideUrlLoading(view:android.webkit.WebView,request:android.webkit.WebResourceRequest):Boolean{
                            return handlePlayerUrl(view,request.url)
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view:android.webkit.WebView,url:String):Boolean{
                            val target=try{Uri.parse(url)}catch(_:Exception){return true}
                            return handlePlayerUrl(view,target)
                        }
                    }
                    tag=server.url
                    loadUrl(server.url,mapOf("Referer" to episode.url))
                }
            },
            update={view->
                if(view.tag!=server.url){
                    view.tag=server.url
                    view.loadUrl(server.url,mapOf("Referer" to episode.url))
                }
            },
            modifier=Modifier.fillMaxSize()
        )

        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xB8000000),Color.Transparent)))
                .padding(horizontal=12.dp,vertical=10.dp),
            verticalAlignment=Alignment.CenterVertically
        ){
            Surface(onClick=onBack,shape=RoundedCornerShape(50),color=Color(0x990D0D12)){
                Box(Modifier.size(40.dp),contentAlignment=Alignment.Center){
                    Text("‹",color=Color.White,fontSize=28.sp)
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)){
                Text("ANISTEAM",color=Color(0xFFFFA16F),fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.3.sp)
                Text(episode.title.ifBlank{"Episode "+episode.number},color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold,maxLines=1)
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xE6000000))))
                .padding(horizontal=16.dp,vertical=18.dp),
            horizontalArrangement=Arrangement.spacedBy(12.dp),
            verticalAlignment=Alignment.CenterVertically
        ){
            Button(
                onClick={previous?.let{onEpisode(it)}},
                enabled=previous!=null,
                modifier=Modifier.weight(1f),
                shape=RoundedCornerShape(13.dp),
                colors=ButtonDefaults.buttonColors(
                    containerColor=Color(0xFF22222B),
                    contentColor=Color.White,
                    disabledContainerColor=Color(0xFF15151B),
                    disabledContentColor=Color(0xFF666671)
                )
            ){ Text("‹  Previous video",fontSize=12.sp,fontWeight=FontWeight.Bold) }
            Button(
                onClick={next?.let{onEpisode(it)}},
                enabled=next!=null,
                modifier=Modifier.weight(1f),
                shape=RoundedCornerShape(13.dp),
                colors=ButtonDefaults.buttonColors(
                    containerColor=colors.primary,
                    contentColor=Color.White,
                    disabledContainerColor=Color(0xFF15151B),
                    disabledContentColor=Color(0xFF666671)
                )
            ){ Text("Next video  ›",fontSize=12.sp,fontWeight=FontWeight.Bold) }
        }
    }
}

@Composable private fun SourcePlayer(
    episode:Episode, episodes:List<Episode>, onBack:()->Unit, onEpisode:(Episode)->Unit
){
    val context=LocalContext.current
    val activity=remember(context){context.findComponentActivity()}
    if(activity==null){
        Box(Modifier.fillMaxSize().background(Color.Black).padding(24.dp),contentAlignment=Alignment.Center){
            Column(horizontalAlignment=Alignment.CenterHorizontally){
                Text("Player could not start",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("AniSteam could not access the current Android activity.",color=Color(0xFFB7B7C2),fontSize=12.sp)
                Spacer(Modifier.height(12.dp))
                TextButton(onClick=onBack){Text("BACK",color=colors.primary)}
            }
        }
        return
    }
    val server=remember(episode.url,episode.servers){episode.servers.firstOrNull()}
    val orderedEpisodes=remember(episodes){episodes.sortedWith(compareBy<Episode>{it.season}.thenBy{it.number}.thenBy{it.url})}
    val currentEpisodeIndex=orderedEpisodes.indexOfFirst{it.url==episode.url}
    val nextEpisode=orderedEpisodes.getOrNull(currentEpisodeIndex+1)
    if(server==null){
        Box(Modifier.fillMaxSize().background(Color.Black),contentAlignment=Alignment.Center){
            Text("No playable media link was found.",color=Color.White,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
        }
        return
    }
    val serverPath=Uri.parse(server.url).path.orEmpty()
    val directMedia=listOf(".m3u8",".mp4",".mpd",".webm",".m4v").any{serverPath.endsWith(it,ignoreCase=true)}
    if(!directMedia){
        Box(Modifier.fillMaxSize().background(Color.Black).padding(28.dp),contentAlignment=Alignment.Center){
            Column(horizontalAlignment=Alignment.CenterHorizontally){
                Text("No direct video stream found",color=Color.White,fontSize=18.sp,fontWeight=FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("AniSteam's native player needs an MP4, HLS, or DASH stream URL. This server only returned a webpage, so it cannot be played as a video.",color=Color(0xFFB7B7C2),fontSize=12.sp)
                Spacer(Modifier.height(14.dp))
                Button(onClick=onBack,colors=ButtonDefaults.buttonColors(containerColor=colors.primary)){Text("BACK TO EPISODES")}
            }
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
    val exoPlayer=remember(server.url,server.referer){
        runCatching{
            val httpFactory=androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 Chrome/121.0.0.0 Mobile Safari/537.36")
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(mapOf("Referer" to server.referer.ifBlank{episode.url},"Origin" to (try{Uri.parse(server.referer.ifBlank{episode.url}).let{"${it.scheme}://${it.host}"}}catch(_:Exception){SOURCE})))
            val dataSourceFactory=androidx.media3.datasource.DefaultDataSource.Factory(context,httpFactory)
            androidx.media3.exoplayer.ExoPlayer.Builder(context)
                .setMediaSourceFactory(androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory))
                .build()
        }.getOrElse{
            // If custom HTTP headers are rejected by a device/Media3 version, retry
            // with Media3's standard data-source configuration instead of crashing.
            androidx.media3.exoplayer.ExoPlayer.Builder(context).build()
        }
    }
    LaunchedEffect(exoPlayer,server.url){
        try{
            val uri=Uri.parse(server.url)
            val item=androidx.media3.common.MediaItem.Builder().setUri(uri).apply{
                if(uri.path.orEmpty().endsWith(".m3u8",ignoreCase=true))setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
            }.build()
            exoPlayer.setMediaItem(item);exoPlayer.prepare();exoPlayer.playWhenReady=true
        }catch(e:Exception){
            playbackError=e.message ?: "This video source could not be initialized."
            buffering=false
        }
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
    LaunchedEffect(controlsVisible,isPlaying){if(controlsVisible && isPlaying){kotlinx.coroutines.delay(4500);controlsVisible=false}}
    LaunchedEffect(fullscreen){
        activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if(fullscreen){
            activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R){
                activity.window.insetsController?.hide(android.view.WindowInsets.Type.systemBars())
                activity.window.insetsController?.systemBarsBehavior=android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }else{
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }
        }else{
            activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R)activity.window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
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
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.R)activity.window.insetsController?.show(android.view.WindowInsets.Type.systemBars())
            else{
                @Suppress("DEPRECATION")
                activity.window.decorView.systemUiVisibility=View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }
    BackHandler{if(fullscreen)fullscreen=false else onBack()}
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0D12))) {
        Box(
            Modifier.fillMaxWidth()
                .then(if(fullscreen) Modifier.weight(1f) else Modifier.aspectRatio(16f/9f))
                .background(Color.Black)
                .clickable { controlsVisible=!controlsVisible }
        ){
            AndroidView(
                factory={ctx->androidx.media3.ui.PlayerView(ctx).apply{
                    useController=false
                    this.player=exoPlayer
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                    setShowBuffering(androidx.media3.ui.PlayerView.SHOW_BUFFERING_NEVER)
                    resizeMode=androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                }},
                update={view->if(view.player!==exoPlayer)view.player=exoPlayer},
                modifier=Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x99000000),Color.Transparent,Color(0xCC000000)))))
            if(buffering && playbackError==null)CircularProgressIndicator(Modifier.align(Alignment.Center).size(42.dp),color=colors.primary,strokeWidth=3.dp)
            if(playbackError!=null){
                Column(Modifier.align(Alignment.Center).padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){
                    Text("Can't play this source",color=Color.White,fontSize=19.sp,fontWeight=FontWeight.Bold)
                    Spacer(Modifier.height(8.dp));Text(playbackError!!,color=Color(0xFFB7B7C2),fontSize=12.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick={playbackError=null;exoPlayer.prepare();exoPlayer.play()},colors=ButtonDefaults.buttonColors(containerColor=colors.primary)){Text("RETRY",fontWeight=FontWeight.Bold)}
                }
            }
            if(controlsVisible){
                Box(Modifier.align(Alignment.TopCenter).fillMaxWidth()){
                Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=14.dp),verticalAlignment=Alignment.CenterVertically){
                    Surface(onClick=onBack,shape=RoundedCornerShape(50),color=Color(0x770D0D12)){Box(Modifier.size(42.dp),contentAlignment=Alignment.Center){Text("‹",color=Color.White,fontSize=30.sp)}}
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)){
                        Text("ANISTEAM PLAYER",color=colors.primary,fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.8.sp)
                        Text(episode.title.ifBlank{"Episode "+episode.number},color=Color.White,fontSize=14.sp,fontWeight=FontWeight.Bold,maxLines=1)
                    }
                    Surface(onClick={fullscreen=!fullscreen},shape=RoundedCornerShape(12.dp),color=Color(0x770D0D12)){Box(Modifier.size(42.dp),contentAlignment=Alignment.Center){Text(if(fullscreen)"⊡" else "⛶",color=Color.White,fontSize=21.sp)}}
                }
                }
            }
            if(controlsVisible){
                Box(Modifier.align(Alignment.Center).fillMaxSize(),contentAlignment=Alignment.Center){
                Row(horizontalArrangement=Arrangement.spacedBy(24.dp),verticalAlignment=Alignment.CenterVertically){
                    Surface(onClick={exoPlayer.seekTo((exoPlayer.currentPosition-10_000L).coerceAtLeast(0L));controlsVisible=true},shape=RoundedCornerShape(50),color=Color(0x880B0B10)){Box(Modifier.size(54.dp),contentAlignment=Alignment.Center){Text("↺10",color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold)}}
                    Surface(onClick={if(exoPlayer.isPlaying)exoPlayer.pause()else exoPlayer.play();controlsVisible=true},shape=RoundedCornerShape(50),color=colors.primary){Box(Modifier.size(72.dp),contentAlignment=Alignment.Center){Text(if(isPlaying)"Ⅱ" else "▶",color=Color.White,fontSize=25.sp,fontWeight=FontWeight.Bold)}}
                    Surface(onClick={exoPlayer.seekTo((exoPlayer.currentPosition+10_000L).coerceAtMost(if(durationMs>0)durationMs else Long.MAX_VALUE));controlsVisible=true},shape=RoundedCornerShape(50),color=Color(0x880B0B10)){Box(Modifier.size(54.dp),contentAlignment=Alignment.Center){Text("↻10",color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold)}}
                }
                }
            }
            if(controlsVisible){
                Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth(),contentAlignment=Alignment.BottomCenter){
                Column(Modifier.fillMaxWidth().padding(horizontal=18.dp,vertical=10.dp)){
                    Slider(value=if(durationMs>0)(positionMs.toFloat()/durationMs.toFloat()).coerceIn(0f,1f)else 0f,
                        onValueChange={fraction->scrubbing=true;positionMs=(fraction*durationMs).toLong().coerceAtLeast(0L)},
                        onValueChangeFinished={exoPlayer.seekTo(positionMs);scrubbing=false},
                        colors=SliderDefaults.colors(thumbColor=colors.primary,activeTrackColor=colors.primary,inactiveTrackColor=Color(0x66777780)),
                        modifier=Modifier.fillMaxWidth().height(24.dp))
                    Row(verticalAlignment=Alignment.CenterVertically){
                        Text(formatPlayerTime(positionMs),color=Color.White,fontSize=10.sp,fontWeight=FontWeight.SemiBold)
                        Text(" / ",color=Color(0xFF888892),fontSize=10.sp)
                        Text(formatPlayerTime(durationMs),color=Color(0xFFB6B6C0),fontSize=10.sp)
                        Spacer(Modifier.weight(1f));Text("AniSteam",color=Color(0xFFB6B6C0),fontSize=10.sp,fontWeight=FontWeight.Bold,letterSpacing=1.sp)
                    }
                }
                }
            }
        }
        if(!fullscreen){
            LazyColumn(
                Modifier.fillMaxWidth().weight(1f),
                contentPadding=PaddingValues(start=18.dp,end=18.dp,top=17.dp,bottom=24.dp),
                verticalArrangement=Arrangement.spacedBy(12.dp)
            ){
                item{
                    Text("NOW PLAYING",color=colors.primary,fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.6.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("S${episode.season} · Episode ${episode.displayNumber.ifBlank{episode.number.toString()}}",color=Color.White,fontSize=21.sp,fontWeight=FontWeight.Black)
                    Spacer(Modifier.height(4.dp))
                    Text(episode.title.ifBlank{"Episode "+episode.number},color=Color(0xFFD2D4DD),fontSize=13.sp,fontWeight=FontWeight.Medium)
                }
                item{
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                        Column(Modifier.weight(1f)){
                            Text("Subtitles",color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold)
                            Text("Subtitle and audio options depend on the source",color=Color(0xFF858A98),fontSize=10.sp)
                        }
                        Surface(shape=RoundedCornerShape(10.dp),color=Color(0xFF1B1E27),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF2B2E38))){
                            Text("SETTINGS  ⚙",Modifier.padding(horizontal=12.dp,vertical=10.dp),color=Color.White,fontSize=9.sp,fontWeight=FontWeight.ExtraBold)
                        }
                    }
                }
                if(nextEpisode!=null){
                    item{
                        Surface(onClick={onEpisode(nextEpisode)},shape=RoundedCornerShape(17.dp),color=Color(0xFF171A22),border=androidx.compose.foundation.BorderStroke(1.dp,Color(0xFF282C36))){
                            Row(Modifier.fillMaxWidth().padding(13.dp),verticalAlignment=Alignment.CenterVertically){
                                Box(Modifier.size(58.dp,66.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Color(0xFF3A245E),Color(0xFF202B4D)))),contentAlignment=Alignment.Center){
                                    Text(nextEpisode.displayNumber.ifBlank{nextEpisode.number.toString()},color=Color.White,fontSize=16.sp,fontWeight=FontWeight.Black)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)){
                                    Text("UP NEXT",color=colors.primary,fontSize=9.sp,fontWeight=FontWeight.ExtraBold,letterSpacing=1.4.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text(nextEpisode.title.ifBlank{"Episode "+nextEpisode.number},color=Color.White,fontSize=13.sp,fontWeight=FontWeight.Bold,maxLines=2)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Season ${nextEpisode.season} · Episode ${nextEpisode.displayNumber.ifBlank{nextEpisode.number.toString()}}",color=Color(0xFF858A98),fontSize=10.sp)
                                }
                                Text("→",color=colors.primary,fontSize=22.sp,fontWeight=FontWeight.Bold)
                            }
                        }
                    }
                }
                item{ Text("More episodes",color=Color.White,fontSize=17.sp,fontWeight=FontWeight.ExtraBold) }
                items(orderedEpisodes.take(8),key={it.url.ifBlank{"${it.season}-${it.number}-${it.title}"}}){item->
                    val current=item.url==episode.url
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp))
                            .background(if(current)Color(0xFF2A1D1B) else Color(0xFF151820))
                            .clickable{onEpisode(item)}.padding(11.dp),
                        verticalAlignment=Alignment.CenterVertically
                    ){
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(if(current)Color(0xFF5A2A22) else Color(0xFF252938)),contentAlignment=Alignment.Center){
                            Text(item.displayNumber.ifBlank{item.number.toString()},color=if(current)colors.primary else Color.White,fontSize=11.sp,fontWeight=FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)){
                            Text(item.title.ifBlank{"Episode "+item.number},color=Color.White,fontSize=12.sp,fontWeight=FontWeight.Bold,maxLines=1)
                            Text("Season ${item.season} · Episode ${item.displayNumber.ifBlank{item.number.toString()}}",color=Color(0xFF858A98),fontSize=10.sp)
                        }
                        if(current)Text("PLAYING",color=colors.primary,fontSize=8.sp,fontWeight=FontWeight.ExtraBold)
                        else Text("▶",color=Color(0xFF9A9EAA),fontSize=11.sp)
                    }
                }
            }
        }
    }
}

private tailrec fun Context.findComponentActivity():ComponentActivity? = when(this){
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}

private fun formatPlayerTime(milliseconds:Long):String{
    val seconds=(milliseconds.coerceAtLeast(0L)/1000L).toInt()
    val hours=seconds/3600;val minutes=(seconds%3600)/60;val remainder=seconds%60
    return if(hours>0)String.format("%d:%02d:%02d",hours,minutes,remainder)else String.format("%02d:%02d",minutes,remainder)
}

private fun fetchEmbeddedPlayer(episodeUrl:String):List<PlayerServer>{
    val json=fetchApiJson("/watch?url="+URLEncoder.encode(episodeUrl,"UTF-8"))
    val ordered=mutableListOf<PlayerServer>()
    val seen=linkedSetOf<String>()
    fun append(array:JSONArray?,defaultName:String){
        jsonArrayObjects(array).forEach{item->
            val url=item.optString("link").ifBlank{item.optString("url")}.trim()
            if(url.isBlank()||(!url.startsWith("https://")&&!url.startsWith("http://"))||!seen.add(url))return@forEach
            val type=item.optString("type").lowercase()
            val mediaPath=try{Uri.parse(url).path.orEmpty()}catch(_:Exception){url.substringBefore('?')}
            val direct=type=="direct"||listOf(".m3u8",".mp4",".mpd",".webm",".m4v",".mov").any{mediaPath.endsWith(it,true)}
            val label=item.optString("server").ifBlank{item.optString("name").ifBlank{if(direct)"Direct media" else defaultName+" "+(ordered.size+1)}}
            ordered.add(PlayerServer(label,url,item.optString("via").ifBlank{episodeUrl}))
        }
    }
    append(json.optJSONArray("direct_streams"),"Direct")
    append(json.optJSONArray("streams"),"Player")
    append(json.optJSONArray("player_streams"),"Player")
    return ordered
}

private fun fetchPlayableMedia(episodeUrl:String):List<PlayerServer>{
    val media=linkedMapOf<String,PlayerServer>()
    val visited=linkedSetOf<String>()
    val pending=ArrayDeque<Triple<String,String,Int>>()
    pending.add(Triple(episodeUrl,"AnimeSalt",0))

    fun normalize(raw:String,base:String):String{
        val cleaned=raw
            .replace("\\u0026","&").replace("\\u003d","=").replace("\\u002f","/")
            .replace("\\/","/").replace("&amp;","&")
            .replace("\\\\u0026","&").replace("\\\\u003d","=").trim()
            .trim('"','\'')
        return try{URL(URL(base),cleaned).toString()}catch(_:Exception){cleaned}
    }
    fun isMedia(raw:String):Boolean{
        val path=try{Uri.parse(raw).path.orEmpty()}catch(_:Exception){raw.substringBefore('?')}
        val full=raw.lowercase()
        return listOf(".m3u8",".mp4",".mpd",".webm",".m4v",".mov").any{path.endsWith(it,ignoreCase=true)} ||
            Regex("""[?&](?:type|format)=?(?:hls|mp4|dash)""",RegexOption.IGNORE_CASE).containsMatchIn(full)
    }
    fun addCandidate(raw:String,base:String,name:String,referer:String){
        if(raw.isBlank())return
        val target=normalize(raw,base)
        if(target.startsWith("http",true) && isMedia(target)){
            media.putIfAbsent(target,PlayerServer(name,target,referer))
        }
    }
    fun decodeServerData(target:String,base:String,name:String){
        val encoded=try{Uri.parse(target).getQueryParameter("data")}catch(_:Exception){null} ?: return
        if(encoded.isBlank())return
        val decoded=try{
            val normalized=encoded.replace(' ','+')
            Base64.decode(normalized,Base64.DEFAULT).toString(Charsets.UTF_8)
        }catch(_:Exception){return}
        try{
            val array=JSONArray(decoded)
            for(i in 0 until array.length()){
                val item=array.optJSONObject(i) ?: continue
                val link=item.optString("link").ifBlank{item.optString("url")}
                if(link.isNotBlank()){
                    val serverName=item.optString("language").ifBlank{item.optString("name").ifBlank{"Server \${i+1}"}}
                    pending.add(Triple(normalize(link,base),serverName,1))
                }
            }
        }catch(_:Exception){
            try{
                val obj=JSONObject(decoded)
                listOf("file","src","source","url","link","hls","m3u8").forEach{key->
                    val value=obj.optString(key)
                    if(value.isNotBlank())addCandidate(value,base,name,target)
                }
            }catch(_:Exception){}
        }
    }

    while(pending.isNotEmpty() && visited.size<18 && media.size<12){
        val (pageUrl,serverName,depth)=pending.removeFirst()
        if(!pageUrl.startsWith("http",true) || !visited.add(pageUrl))continue
        try{
            val doc=getHtml(pageUrl)
            val base=doc.location().ifBlank{pageUrl}
            doc.select("video[src],video[data-src],video[data-hls],source[src],source[data-src],source[data-hls],source[data-file],[data-video],[data-hls],[data-m3u8]").forEach{node->
                listOf("src","data-src","data-hls","data-file","data-video","data-m3u8").forEach{attr->
                    val raw=node.attr(attr)
                    if(raw.isNotBlank())addCandidate(raw,base,serverName,base)
                }
            }

            val scripts=doc.select("script").joinToString("\n"){it.data().ifBlank{it.html()}}
            val expanded=scripts.replace("\\/","/").replace("\\u0026","&").replace("\\u003d","=")
                .replace("\\u002f","/").replace("&amp;","&")
            val keyValue=Regex("""(?is)["'](?:file|src|source|url|hls|m3u8|contentUrl)["']\s*:\s*["']([^"']+)["']""")
            keyValue.findAll(expanded).forEach{match->addCandidate(match.groupValues[1],base,serverName,base)}
            val rawUrls=Regex("""(?i)(?:https?:)?//[^"'\s<>\\]+?\.(?:m3u8|mp4|mpd|webm|m4v|mov)(?:\?[^"'\s<>\\]*)?""")
            rawUrls.findAll(expanded).forEach{match->addCandidate(match.value,base,serverName,base)}

            Regex("""(?i)["'](?:data|source|sources|file|url)["']\s*[:=]\s*["']([A-Za-z0-9_+/=-]{24,})["']""")
                .findAll(expanded).take(12).forEach{match->
                    val decoded=try{Base64.decode(match.groupValues[1],Base64.DEFAULT).toString(Charsets.UTF_8)}catch(_:Exception){""}
                    if(decoded.startsWith("http",true))addCandidate(decoded,base,serverName,base)
                    else {
                        Regex("""https?://[^"'\s<>]+?\.(?:m3u8|mp4|mpd|webm|m4v)(?:\?[^"'\s<>]*)?""",RegexOption.IGNORE_CASE)
                            .findAll(decoded).forEach{addCandidate(it.value,base,serverName,base)}
                    }
                }

            if(depth<2){
                doc.select("iframe[src],iframe[data-src],iframe[data-lazy-src],a[href*=/embed/],a[href*=/player/]").forEach{node->
                    val raw=node.attr("data-src").ifBlank{node.attr("data-lazy-src")}.ifBlank{node.attr("src")}.ifBlank{node.attr("href")}
                    if(raw.isBlank())return@forEach
                    val target=normalize(raw,base)
                    if(target.startsWith("http",true)){
                        decodeServerData(target,base,serverName)
                        if(!visited.contains(target))pending.add(Triple(target,serverName,depth+1))
                    }
                }
                val sourceObjects=Regex("""(?is)["'](?:sources|tracks)["']\s*:\s*(\[[^\]]{1,12000}\])""")
                sourceObjects.findAll(expanded).take(8).forEach{match->
                    Regex("""(?is)["'](?:file|src|url)["']\s*:\s*["']([^"']+)["']""")
                        .findAll(match.groupValues[1]).forEach{addCandidate(it.groupValues[1],base,serverName,base)}
                }
            }
        }catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(_:Exception){}
    }
    return media.values.toList()
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
