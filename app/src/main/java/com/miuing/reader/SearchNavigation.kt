package com.miuing.reader

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SearchMatchInfo(val index:Int,val total:Int) {
    val valid get()=total>=0 && if(total==0) index==0 else index in 0 until total
}

/** Search controls overlay the existing tools area, keeping the text viewport fixed. */
@Composable internal fun SearchNavigation(
    match:SearchMatchInfo?,ready:Boolean,theme:ReadingTheme,onMove:(Int)->Unit,onClose:()->Unit
) {
    Surface(color=theme.paper,contentColor=theme.ink,shape=RoundedCornerShape(24.dp),shadowElevation=2.dp) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={onMove(match!!.index-1)},enabled=ready && match!=null && match.index>0,
                modifier=Modifier.semantics {contentDescription="上一处匹配"}) {
                Icon(Icons.Default.KeyboardArrowUp,null)
            }
            Text(when {match==null->"正在查找…";match.total==0->"本章无匹配";else->"本章 ${match.index+1} / ${match.total}"},fontSize=12.sp)
            IconButton(onClick={onMove(match!!.index+1)},enabled=ready && match!=null && match.index+1<match.total,
                modifier=Modifier.semantics {contentDescription="下一处匹配"}) {
                Icon(Icons.Default.KeyboardArrowDown,null)
            }
            IconButton(onClick=onClose,enabled=ready,modifier=Modifier.semantics {contentDescription="关闭搜索导航"}) {Icon(Icons.Default.Close,null)}
        }
    }
}
