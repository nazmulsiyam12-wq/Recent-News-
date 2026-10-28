package bd.recentnews.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage

val Green = Color(0xFF0B6B3A)
val Red = Color(0xFFD72C1C)

@Composable
fun NewsTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(primary = Color(0xFF3DBE7A), onPrimary = Color.Black)
    else lightColorScheme(primary = Green, onPrimary = Color.White)
    MaterialTheme(colorScheme = scheme, content = content)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsApp(vm: NewsVM) {
    val post = vm.openPost
    if (post != null) { ArticleScreen(post) { vm.openPost = null }; return }

    var searching by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    BackHandler(searching) { searching = false; text = ""; vm.clearSearch() }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Green, titleContentColor = Color.White, actionIconContentColor = Color.White),
                title = {
                    if (searching) {
                        TextField(
                            value = text, onValueChange = { text = it }, singleLine = true,
                            placeholder = { Text("Search news…", color = Color.White.copy(alpha = .7f)) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White, unfocusedTextColor = Color.White, cursorColor = Color.White,
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { if (text.isNotBlank()) vm.search(text.trim()) }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Recent ", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                            Text("News", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Color(0xFFFFC9C4))
                            Text(" BD", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {
                        if (searching) { searching = false; text = ""; vm.clearSearch() } else searching = true
                    }) { Icon(if (searching) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.query.isEmpty()) CategoryBar(vm)
            else Text("Results for “${vm.query}”", fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp))
            NewsList(vm)
        }
    }
}

@Composable
fun CategoryBar(vm: NewsVM) {
    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Chip("Latest", vm.label == null) { vm.select(null) } }
        items(CATEGORIES) { c -> Chip(c, vm.label == c) { vm.select(c) } }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Green, selectedLabelColor = Color.White),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsList(vm: NewsVM) {
    val state = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val l = state.layoutInfo
            (l.visibleItemsInfo.lastOrNull()?.index ?: 0) >= l.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearEnd, vm.posts.size) { if (nearEnd) vm.more() }
    LaunchedEffect(vm.label, vm.query) { state.scrollToItem(0) }

    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.reload(true) }, modifier = Modifier.fillMaxSize()) {
        when {
            vm.loading && vm.posts.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = Green) }
            vm.posts.isEmpty() -> Column(Modifier.fillMaxSize().padding(32.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text(vm.error ?: "No news found.", fontSize = 16.sp)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { vm.reload() }, colors = ButtonDefaults.buttonColors(containerColor = Green)) { Text("Retry") }
            }
            else -> LazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(vm.posts, key = { _, p -> p.id }) { i, p ->
                    if (i == 0 && vm.query.isEmpty()) Hero(p) { vm.openPost = p } else NewsRow(p) { vm.openPost = p }
                }
                if (vm.loadingMore) item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(color = Green, modifier = Modifier.size(28.dp)) } }
            }
        }
    }
}

@Composable
fun Hero(p: Post, onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(Green).clickable(onClick = onClick)) {
        if (p.image != null) AsyncImage(p.image, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)), startY = 120f)))
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            p.label?.let { Text(it.uppercase(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Red).padding(horizontal = 8.dp, vertical = 3.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(p.title, color = Color.White, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                fontSize = 21.sp, lineHeight = 28.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(Api.prettyDate(p.published), color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
        }
    }
}

@Composable
fun NewsRow(p: Post, onClick: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column(Modifier.weight(1f)) {
                p.label?.let { Text(it.uppercase(), color = Red, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(4.dp))
                Text(p.title, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = 17.sp,
                    lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Text(Api.prettyDate(p.published), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
            }
            if (p.image != null) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(p.image, null, Modifier.size(width = 110.dp, height = 82.dp).clip(RoundedCornerShape(8.dp)).background(Green.copy(alpha = .15f)),
                    contentScale = ContentScale.Crop)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = .1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ArticleScreen(p: Post, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val html = remember(p, dark) { articleHtml(p, dark) }
    Scaffold(topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Green, titleContentColor = Color.White,
                navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
            title = { Text("Recent News BD", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onClick = {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, "${p.title}\n${p.url}")
                    ctx.startActivity(Intent.createChooser(i, "Share"))
                }) { Icon(Icons.Default.Share, "Share") }
            },
        )
    }) { pad ->
        AndroidView(modifier = Modifier.padding(pad).fillMaxSize(), factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(if (dark) android.graphics.Color.parseColor("#121212") else android.graphics.Color.WHITE)
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        runCatching { c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(r.url.toString()))) }
                        return true
                    }
                }
                loadDataWithBaseURL(Api.BASE, html, "text/html", "utf-8", null)
            }
        })
    }
}

fun articleHtml(p: Post, dark: Boolean): String {
    val bg = if (dark) "#121212" else "#ffffff"
    val fg = if (dark) "#e8e8e8" else "#1a1a1a"
    val muted = if (dark) "#9a9a9a" else "#666666"
    val tag = p.label?.let { "<span class='tag'>${TextUtils.htmlEncode(it.uppercase())}</span>" } ?: ""
    return """<html><head><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
body{margin:0;padding:16px 16px 40px;background:$bg;color:$fg;font-family:Georgia,'Noto Serif Bengali',serif;font-size:18px;line-height:1.8;word-wrap:break-word}
h1{font-size:25px;line-height:1.4;margin:10px 0 8px}
.tag{background:#d72c1c;color:#fff;font:bold 11px Arial,sans-serif;padding:3px 8px;border-radius:4px}
.meta{color:$muted;font:13px Arial,sans-serif;margin-bottom:16px;padding-bottom:12px;border-bottom:3px solid #0b6b3a}
img{max-width:100%!important;height:auto!important;border-radius:6px}
.separator,.tr-caption-container{text-align:center;margin:0!important}
a{color:#0b8a4a}iframe{max-width:100%}
table{max-width:100%}
</style></head><body>
$tag<h1>${TextUtils.htmlEncode(p.title)}</h1>
<div class="meta">${Api.prettyDate(p.published)}</div>
${p.html}
</body></html>"""
}
