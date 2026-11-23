package com.miuing.reader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException

val Accent = Color(0xFFD96828)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if(dark) darkColorScheme(primary=Accent,secondary=Accent) else lightColorScheme(primary=Accent,secondary=Accent,primaryContainer=Color(0xFFFFEADC),secondaryContainer=Color(0xFFFFEADC),background=Color.White,surface=Color.White)) {
                val store = remember { LibraryStore(this) }
                val libraryState=rememberSaveableStateHolder()
                var reading by rememberSaveable { mutableStateOf<String?>(null) }
                var importing by remember { mutableStateOf(false) }
                val scope = rememberCoroutineScope()
                val snack = remember { SnackbarHostState() }
                val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
                    scope.launch {
                        importing = true
                        var added=0; val errors=mutableListOf<String>()
                        for(uri in uris) try { store.import(uri); added++ } catch(e:CancellationException) {throw e} catch(e:Exception) { errors.add(e.localizedMessage ?: "导入失败") }
                        importing=false
                        if(uris.isNotEmpty()) snack.showSnackbar(if(errors.isEmpty()) "已导入 $added 本图书" else "成功 $added 本；${errors.joinToString("；")}",duration=SnackbarDuration.Long)
                    }
                }
                val active = store.books.firstOrNull { it.id == reading }
                BackHandler(active != null) { reading = null }
                if(active != null) ReaderScreen(active,store) { reading = null }
                else libraryState.SaveableStateProvider("library") {LibraryScreen(store, importing, snack, onImport={picker.launch(arrayOf("*/*"))},onOpen={
                    if(importing) scope.launch {snack.showSnackbar("正在导入图书，请稍候")}
                    else {store.update(it.copy(opened=System.currentTimeMillis())); reading=it.id}
                },backupActions={LibraryBackupMenu(store,importing,snack) {importing=it}})}
            }
        }
    }
}
