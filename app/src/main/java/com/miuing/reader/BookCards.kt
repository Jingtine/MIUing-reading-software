package com.miuing.reader

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A shelf slot retains a cover's actual shape; decoration follows the image, not the empty slot. */
@Composable fun BookCover(book:Book,modifier:Modifier=Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,book.cover) {
        value=withContext(Dispatchers.IO) {if(book.cover.isBlank()) null else CoverDecoder.decode(book.cover)}
    }
    val shape=RoundedCornerShape(2.dp)
    BoxWithConstraints(modifier,contentAlignment=Alignment.BottomCenter) {
        val image=bitmap
        if(image!=null) {
            val ratio=image.width.toFloat()/image.height
            val width=minOf(maxWidth,maxHeight*ratio)
            Image(image.asImageBitmap(),book.title,Modifier.size(width,width/ratio).shadow(3.dp,shape).clip(shape),contentScale=ContentScale.Fit)
        } else {
            val small=maxWidth<80.dp
            val shades=listOf(Color(0xFF344F59),Color(0xFF8D6954),Color(0xFF6D7160),Color(0xFF786276))
            Column(Modifier.fillMaxSize().shadow(3.dp,shape).clip(shape).background(shades[(book.id.hashCode() and Int.MAX_VALUE)%shades.size]).padding(if(small) 6.dp else 12.dp),verticalArrangement=Arrangement.SpaceBetween) {
                Text(book.title,color=Color.White,fontFamily=FontFamily.Serif,fontSize=if(small) 11.sp else 18.sp,maxLines=if(small) 3 else 4,overflow=TextOverflow.Ellipsis)
                Text(book.author,color=Color.White.copy(alpha=.65f),fontSize=if(small) 8.sp else 10.sp,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

private fun readingStatus(book:Book)=when {
    book.finished->"已读完"
    book.opened<=0->book.format
    book.format=="PDF"->"第 ${book.chapter+1} 页"
    else->"第 ${book.chapter+1} 章 · 本章 ${(book.position.coerceIn(0f,1f)*100).toInt()}%"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun BookGridCard(book:Book,open:()->Unit,menu:()->Unit) {
    Column(Modifier.fillMaxWidth().combinedClickable(onClick=open,onLongClick=menu)) {
        BookCover(book,Modifier.fillMaxWidth().aspectRatio(.67f))
        Row(Modifier.fillMaxWidth().padding(top=8.dp),verticalAlignment=Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(book.title,fontSize=13.sp,minLines=2,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(book.author.ifBlank {"未知作者"},fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(readingStatus(book),Modifier.padding(top=3.dp),fontSize=10.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick=menu,modifier=Modifier.size(44.dp).semantics {contentDescription="图书选项"}) {Icon(Icons.Default.MoreHoriz,null,Modifier.size(18.dp))}
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun ContinueReadingCard(book:Book,open:()->Unit,menu:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.3f))
        .combinedClickable(onClick=open,onLongClick=menu).padding(16.dp),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.CenterVertically) {
        BookCover(book,Modifier.width(92.dp).height(138.dp))
        Column(Modifier.weight(1f)) {
            Text(book.title,fontSize=20.sp,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(book.author,Modifier.padding(top=6.dp),fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(readingStatus(book),Modifier.padding(top=6.dp),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("继续阅读  ›",Modifier.padding(top=12.dp),fontSize=14.sp,color=Accent)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun BookRow(book:Book,open:()->Unit,menu:()->Unit) {
    Column {
        Row(Modifier.fillMaxWidth().combinedClickable(onClick=open,onLongClick=menu).padding(vertical=12.dp),verticalAlignment=Alignment.CenterVertically) {
            BookCover(book,Modifier.width(60.dp).height(90.dp))
            Column(Modifier.weight(1f).padding(horizontal=16.dp)) {
                Text(book.title,fontWeight=FontWeight.Medium,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(book.author,fontSize=13.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(readingStatus(book),fontSize=11.sp,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick=menu,modifier=Modifier.semantics {contentDescription="图书选项"}) {Icon(Icons.Default.MoreHoriz,null)}
        }
        HorizontalDivider(Modifier.padding(start=76.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=.5f))
    }
}
