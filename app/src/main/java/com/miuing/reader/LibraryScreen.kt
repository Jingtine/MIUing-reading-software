package com.miuing.reader

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable fun LibraryScreen(store: LibraryStore, importing: Boolean, snack: SnackbarHostState, onImport:()->Unit,onOpen:(Book)->Unit) {
    var tab by rememberSaveable { mutableStateOf("主页") }
    var filter by rememberSaveable { mutableStateOf("全部") }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf("最近阅读") }
    var grid by rememberSaveable { mutableStateOf(true) }
    var sidebar by rememberSaveable { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var collectionsDialog by remember { mutableStateOf(false) }
    var createCollection by remember { mutableStateOf(false) }
    var collectionName by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Book?>(null) }
    var delete by remember { mutableStateOf<Book?>(null) }
    var assign by remember { mutableStateOf<Book?>(null) }
    var info by remember { mutableStateOf<Book?>(null) }
    val navigate:(String)->Unit = { tab=it; if(it!="书库") filter="全部" }
    val filtered = store.books.filter { b ->
        (when(filter) { "图书"->b.format!="PDF"; "PDF"->b.format=="PDF"; "欲读清单"->b.wantToRead; "已读完"->b.finished; "全部","已下载"->true; else->filter in b.collections }) &&
            (query.isBlank() || "${b.title} ${b.author}".contains(query,true))
    }.let { items -> when(sort) { "书名"->items.sortedBy { it.title }; "作者"->items.sortedBy { it.author }; "导入时间"->items.sortedByDescending { it.added }; else->items.sortedByDescending { it.opened } } }
    BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        val wide=maxWidth>=700.dp
        Row(Modifier.fillMaxSize()) {
            if(wide && sidebar) {
                Column(Modifier.width(220.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.35f)).padding(18.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.miuing_icon),"MIUing",Modifier.size(36.dp))
                        Text("MIUing",fontSize=23.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(8.dp))
                    }
                    Spacer(Modifier.height(32.dp))
                    listOf("主页" to Icons.Default.Home,"书库" to Icons.AutoMirrored.Filled.MenuBook,"搜索" to Icons.Default.Search).forEach { (name,icon) ->
                        NavigationDrawerItem(label={Text(name)},selected=tab==name,onClick={navigate(name)},icon={Icon(icon,null)})
                    }
                    Text("书库",Modifier.padding(top=24.dp,bottom=8.dp),style=MaterialTheme.typography.labelMedium)
                    (listOf("全部","欲读清单","已读完","图书","PDF")+store.collections).forEach { name ->
                        TextButton(onClick={tab="书库";filter=name}) { Text(name,color=if(filter==name) Accent else MaterialTheme.colorScheme.onSurface) }
                    }
                    TextButton(onClick={createCollection=true}) { Icon(Icons.Default.Add,null); Text("新建藏书") }
                }
            }
            Scaffold(modifier=Modifier.weight(1f),snackbarHost={SnackbarHost(snack)},bottomBar={
                if(!wide) NavigationBar(containerColor=MaterialTheme.colorScheme.surface) {
                    listOf(Triple("主页",Icons.Default.Home,"主页"),Triple("书库",Icons.AutoMirrored.Filled.MenuBook,"书库"),Triple("搜索",Icons.Default.Search,"搜索")).forEach { (name,icon,description) ->
                        NavigationBarItem(selected=tab==name,onClick={navigate(name)},icon={Icon(icon,description)},label={Text(name)},colors=NavigationBarItemDefaults.colors(selectedIconColor=Accent,selectedTextColor=Accent,indicatorColor=Color.Transparent))
                    }
                }
            }) { inset ->
                Column(Modifier.fillMaxSize().padding(inset).padding(horizontal=if(wide) 36.dp else 22.dp)) {
                    Row(Modifier.fillMaxWidth().height(62.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(wide) {
                            IconButton(onClick={sidebar=!sidebar}) { Icon(Icons.Default.Menu,"显示或隐藏导航") }
                            if(!sidebar) listOf("主页","书库","搜索").forEach { name -> TextButton(onClick={navigate(name)}) { Text(name,color=if(tab==name) Accent else MaterialTheme.colorScheme.onSurface) } }
                        } else Image(painterResource(R.drawable.miuing_icon),"MIUing",Modifier.size(32.dp))
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick=onImport,enabled=!importing) { Icon(Icons.Default.Add,"导入图书",tint=Accent) }
                    }
                    Text(if(tab=="书库" && filter!="全部") filter else tab,fontSize=34.sp,fontWeight=FontWeight.Bold,letterSpacing=(-1).sp)
                    if(importing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical=10.dp))
                    store.loadError?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                    if(tab=="搜索") OutlinedTextField(query,{query=it},placeholder={Text("书名或作者")},leadingIcon={Icon(Icons.Default.Search,null)},singleLine=true,modifier=Modifier.fillMaxWidth().padding(vertical=16.dp),shape=RoundedCornerShape(12.dp))
                    if(tab=="书库" || tab=="搜索") {
                        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            if(tab=="书库") TextButton(onClick={collectionsDialog=true}) { Text("藏书  ›") }
                            Text("${filtered.size} 本图书",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Box {
                                IconButton(onClick={sortMenu=true}) { Icon(Icons.AutoMirrored.Filled.Sort,"排序方式") }
                                DropdownMenu(sortMenu,{sortMenu=false}) { listOf("最近阅读","书名","作者","导入时间").forEach { name -> DropdownMenuItem(text={Text(name)},onClick={sort=name;sortMenu=false}) } }
                            }
                            IconButton(onClick={grid=!grid},modifier=Modifier.semantics {contentDescription=if(grid) "切换显示方式：当前为网格" else "切换显示方式：当前为列表"}) { Icon(if(grid) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,null) }
                        }
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    }
                    if(store.books.isEmpty()) {
                        Column(Modifier.weight(1f).fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook,null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("书库为空",Modifier.padding(top=20.dp),fontSize=23.sp,fontWeight=FontWeight.SemiBold)
                            Text("支持 EPUB、PDF、TXT、MOBI、AZW、AZW3、FB2。",Modifier.padding(top=8.dp,bottom=24.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(onClick=onImport,enabled=!importing) { Text("导入图书") }
                        }
                    } else if(tab=="主页") {
                        LazyColumn(verticalArrangement=Arrangement.spacedBy(18.dp)) {
                            item { Spacer(Modifier.height(16.dp)); Text("继续阅读",fontSize=24.sp,fontWeight=FontWeight.Bold) }
                            val reading=store.books.filter { it.opened>0 && !it.finished }.sortedByDescending { it.opened }
                            if(reading.isEmpty()) item { Text("挑选一本图书，开启新的篇章。",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                            items(reading.take(4),key={"continue-${it.id}"}) { b ->
                                ContinueReadingCard(b,{onOpen(b)},{selected=b})
                            }
                            item { Text("最近添加",fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=12.dp)) }
                            items(store.books.sortedByDescending { it.added }.take(6),key={"recent-${it.id}"}) { b -> BookRow(b,{onOpen(b)},{selected=b}) }
                            item { Spacer(Modifier.height(20.dp)) }
                        }
                    } else if(filtered.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) { Text("没有找到图书",color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    else if(grid) LazyVerticalGrid(GridCells.Adaptive(if(wide) 145.dp else 140.dp),horizontalArrangement=Arrangement.spacedBy(if(wide) 28.dp else 20.dp),verticalArrangement=Arrangement.spacedBy(24.dp),contentPadding=PaddingValues(vertical=22.dp)) {
                        items(filtered,key={it.id}) { b ->
                            BookGridCard(b,{onOpen(b)},{selected=b})
                        }
                    } else LazyColumn(contentPadding=PaddingValues(vertical=12.dp)) { items(filtered,key={it.id}) { b -> BookRow(b,{onOpen(b)},{selected=b}) } }
                }
            }
        }
    }
    if(collectionsDialog) AlertDialog(onDismissRequest={collectionsDialog=false},title={Text("藏书")},text={
        Column(Modifier.verticalScroll(rememberScrollState())) {
            (listOf("全部","图书","PDF","欲读清单","已读完","已下载")+store.collections).forEach { name ->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    TextButton(onClick={filter=name;collectionsDialog=false},modifier=Modifier.weight(1f)) { Text(name) }
                    if(name in store.collections) IconButton(onClick={store.removeCollection(name); if(filter==name) filter="全部"}) { Icon(Icons.Default.DeleteOutline,"删除藏书，保留图书") }
                }
            }
        }
    },confirmButton={TextButton(onClick={collectionsDialog=false;createCollection=true}) { Text("新建藏书") }},dismissButton={TextButton(onClick={collectionsDialog=false}) { Text("完成") }})
    if(createCollection) AlertDialog(onDismissRequest={createCollection=false},title={Text("新建藏书")},text={OutlinedTextField(collectionName,{collectionName=it},label={Text("名称")},singleLine=true)},confirmButton={TextButton(onClick={store.addCollection(collectionName);collectionName="";createCollection=false},enabled=collectionName.isNotBlank()) { Text("创建") }},dismissButton={TextButton(onClick={createCollection=false}) { Text("取消") }})
    selected?.let { b -> AlertDialog(onDismissRequest={selected=null},title={Text(b.title)},text={Column {
        Action("阅读",Icons.AutoMirrored.Filled.MenuBook) { selected=null;onOpen(b) }
        Action(if(b.wantToRead) "从欲读清单移除" else "添加到欲读清单",Icons.Default.BookmarkBorder) { store.update(b.copy(wantToRead=!b.wantToRead));selected=null }
        Action(if(b.finished) "标记为未读完" else "标记为已读完",Icons.Default.CheckCircleOutline) { store.update(b.copy(finished=!b.finished));selected=null }
        Action("添加到藏书",Icons.Default.FolderOpen) { assign=b;selected=null }
        Action("图书信息",Icons.Default.Info) { info=b;selected=null }
        Action("移除",Icons.Default.DeleteOutline) { delete=b;selected=null }
    }},confirmButton={TextButton(onClick={selected=null}) { Text("取消") }}) }
    info?.let { b -> AlertDialog(onDismissRequest={info=null},title={Text(b.title)},text={Text("${b.author}\n${b.format}\n文件大小：${File(b.source).length()/1024} KB")},confirmButton={TextButton(onClick={info=null}) { Text("完成") }}) }
    assign?.let { b -> AlertDialog(onDismissRequest={assign=null},title={Text("添加到藏书")},text={Column {
        if(store.collections.isEmpty()) Text("请先创建藏书。")
        store.collections.forEach { name -> Row(verticalAlignment=Alignment.CenterVertically) {
            val current=store.current(b.id)
            Checkbox(name in current.collections,{checked->store.update(current.copy(collections=if(checked) current.collections+name else current.collections-name))})
            Text(name)
        } }
    }},confirmButton={TextButton(onClick={assign=null}) { Text("完成") }},dismissButton={TextButton(onClick={assign=null;createCollection=true}) { Text("新建藏书") }}) }
    delete?.let { b -> AlertDialog(onDismissRequest={delete=null},title={Text("移除“${b.title}”？")},text={Text("删除应用内副本、阅读进度和笔记。原始文件会保留。")},confirmButton={TextButton(onClick={store.delete(b.id);delete=null}) { Text("删除",color=MaterialTheme.colorScheme.error) }},dismissButton={TextButton(onClick={delete=null}) { Text("取消") }}) }
}

@Composable private fun Action(label:String,icon:ImageVector,action:()->Unit) { TextButton(onClick=action) { Icon(icon,null,Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(label) } }
