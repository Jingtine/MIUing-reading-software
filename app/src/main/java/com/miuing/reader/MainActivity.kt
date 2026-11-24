package com.miuing.reader

import android.os.Bundle
import android.content.Intent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    private val session by viewModels<LibrarySession>()
    private fun acceptExternal(intent:Intent) {
        try {session.submit(IncomingBooks.parse(intent),external=true)}
        catch(e:Exception) {session.notify(e.localizedMessage ?: "无法接收文件")}
    }
    override fun onNewIntent(intent:Intent) {super.onNewIntent(intent);acceptExternal(intent)}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(savedInstanceState==null) acceptExternal(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            MaterialTheme(colorScheme = if(dark) darkColorScheme(primary=Accent,secondary=Accent) else lightColorScheme(primary=Accent,secondary=Accent,primaryContainer=Color(0xFFFFEADC),secondaryContainer=Color(0xFFFFEADC),background=Color.White,surface=Color.White)) {
                val store = session.store
                val libraryState=rememberSaveableStateHolder()
                val importing=session.busy
                val snack = remember { SnackbarHostState() }
                LaunchedEffect(session) {session.notices.collect {snack.showSnackbar(it,duration=SnackbarDuration.Long)}}
                val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {session.submit(it)}
                val active = store.books.firstOrNull { it.id == session.reading }
                BackHandler(active != null) {session.close()}
                Box(Modifier.fillMaxSize()) {
                    if(active != null) key(active.id) {ReaderScreen(active,store) {session.close()}}
                    else libraryState.SaveableStateProvider("library") {LibraryScreen(store, importing, snack,
                        onImport={picker.launch(arrayOf("*/*"))},
                        onOpen={if(!importing) session.open(it)},
                        backupActions={LibraryBackupMenu(store,importing,snack) {session.backupBusy=it}})}
                    if(active!=null) SnackbarHost(snack,Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
                }
            }
        }
    }
}
