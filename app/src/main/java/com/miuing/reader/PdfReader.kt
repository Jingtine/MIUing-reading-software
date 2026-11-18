package com.miuing.reader

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

private val pdfCleanup=CoroutineScope(SupervisorJob()+Dispatchers.IO)

@Composable fun PdfReader(book:Book,page:Int,jump:Int,onPosition:(Int)->Unit,onTap:()->Unit) {
    val renderer=remember(book.id) { PdfRenderer(ParcelFileDescriptor.open(File(book.source),ParcelFileDescriptor.MODE_READ_ONLY)) }
    val closed=remember(renderer) { AtomicBoolean(false) }
    val state=rememberLazyListState(page.coerceIn(0,renderer.pageCount-1))
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val transform=rememberTransformableState { scale,offset,_ -> zoom=(zoom*scale).coerceIn(1f,3f);pan=if(zoom==1f) Offset.Zero else pan+offset }
    val callback by rememberUpdatedState(onPosition)
    LaunchedEffect(jump) { state.scrollToItem(page.coerceIn(0,renderer.pageCount-1)) }
    LaunchedEffect(state) { snapshotFlow { state.firstVisibleItemIndex }.collect { callback(it) } }
    DisposableEffect(renderer) { onDispose { closed.set(true);pdfCleanup.launch { synchronized(renderer) { renderer.close() } } } }
    LazyColumn(state=state,modifier=Modifier.fillMaxSize().transformable(transform,canPan={zoom>1f}),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        items(renderer.pageCount) { index ->
            val bitmap by produceState<Bitmap?>(null,index) {
                value=withContext(Dispatchers.IO) { val owner=currentCoroutineContext(); synchronized(renderer) {
                    owner.ensureActive()
                    if(closed.get()) return@synchronized null
                    renderer.openPage(index).use { p ->
                        val scale=minOf(1200f/p.width,2400f/p.height)
                        Bitmap.createBitmap((p.width*scale).toInt().coerceAtLeast(1),(p.height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888).also { it.eraseColor(android.graphics.Color.WHITE);p.render(it,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                    }
                } }
            }
            bitmap?.let { image -> Image(image.asImageBitmap(),"第 ${index+1} 页",Modifier.fillMaxWidth().aspectRatio(image.width.toFloat()/image.height).graphicsLayer(scaleX=zoom,scaleY=zoom,translationX=pan.x,translationY=pan.y).clickable(onClick=onTap)) }
                ?: Box(Modifier.fillMaxWidth().height(400.dp)) { CircularProgressIndicator() }
        }
    }
}
