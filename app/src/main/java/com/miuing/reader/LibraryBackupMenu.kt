package com.miuing.reader

import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

/** SAF ownership and operation lifetime stay outside the library's presentation code. */
@Composable internal fun LibraryBackupMenu(
    store: LibraryStore,
    busy: Boolean,
    snack: SnackbarHostState,
    onBusy: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<Job?>(null) }
    fun runOperation(label: String, block: suspend () -> String) {
        if (busy || job?.isActive == true) return
        onBusy(true); operation=label
        job=scope.launch {
            var message: String? = null
            try { message=block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message=e.localizedMessage ?: "$label 失败" }
            finally { operation=""; job=null; onBusy(false) }
            message?.let { snack.showSnackbar(it,duration=SnackbarDuration.Long) }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) runOperation("正在导出备份") {
            BackupDocumentExport.write(context.cacheDir,
                archive={LibraryBackup(store).export(it)},
                destination={context.contentResolver.openOutputStream(uri,"wt") ?: error("无法写入备份文件")},
                deleteDestination={DocumentsContract.deleteDocument(context.contentResolver,uri)})
            "书库备份已导出"
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri != null) runOperation("正在恢复备份") {
            val result=withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use {LibraryBackup(store).restore(it)} ?: error("无法读取备份文件")
            }
            "已恢复 ${result.restored} 本，保留已有 ${result.skipped} 本的阅读记录"
        }
    }
    IconButton(onClick={menu=true},enabled=!busy) {Icon(Icons.Default.MoreHoriz,"书库备份",tint=Accent)}
    if(menu) AlertDialog(
        onDismissRequest={menu=false}, title={Text("书库备份")},
        text={Column {
            Text("备份图书、进度、书签、批注和藏书。不含字体和阅读外观设置。")
            Spacer(Modifier.height(12.dp))
            Text("恢复时补入缺失图书，保留已有图书的阅读记录。")
            TextButton(onClick={
                menu=false
                val date=SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(Date())
                export.launch("MIUing-书库备份-$date.zip")
            }) {Text("导出备份")}
            TextButton(onClick={menu=false;restore.launch(arrayOf("application/zip","application/octet-stream","application/x-zip-compressed"))}) {Text("恢复备份")}
        }},confirmButton={TextButton(onClick={menu=false}) {Text("完成")}}
    )
    if(operation.isNotEmpty()) AlertDialog(
        onDismissRequest={job?.cancel()}, title={Text(operation)},
        text={Column {LinearProgressIndicator(Modifier.fillMaxWidth());Spacer(Modifier.height(12.dp));Text("请保持应用打开。完成验证后才会更新书库。")}},
        confirmButton={TextButton(onClick={job?.cancel()}) {Text("取消")}}
    )
}
