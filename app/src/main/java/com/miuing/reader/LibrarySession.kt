package com.miuing.reader

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

/** Activity-independent lifetime; every import uses the same store and serial worker. */
internal class LibrarySession(application:Application,private val state:SavedStateHandle):AndroidViewModel(application) {
    val store=LibraryStore(application)
    var reading by mutableStateOf(state.get<String>("reading"));private set
    var importing by mutableStateOf(false);private set
    var backupBusy by mutableStateOf(false)
    private var pending by mutableIntStateOf(0)
    val busy get()=importing || backupBusy || pending>0
    private data class Request(val uris:List<Uri>,val external:Boolean)
    private val queue=Channel<Request>(64)
    private val messages=Channel<String>(Channel.UNLIMITED)
    val notices=messages.receiveAsFlow()
    init {
        if(state.get<Boolean>("importPending")==true) notify("上次导入已中断，请重新选择文件")
        state["importPending"]=false
        viewModelScope.launch {
            for(request in queue) {
                while(backupBusy) delay(100)
                importing=true;state["importPending"]=true
                val added=mutableListOf<String>();val errors=mutableListOf<String>()
                try {
                    for(uri in request.uris) try {added.add(store.import(uri).id)}
                    catch(e:CancellationException) {throw e}
                    catch(e:Exception) {errors.add(if(e is SecurityException || e is java.io.FileNotFoundException) "文件不可读或授权已失效，请重新选择" else e.localizedMessage ?: "无法导入文件")}
                    if(request.external && added.isNotEmpty()) {
                        if(request.uris.size==1) open(store.current(added.single())) else close()
                    }
                    if(request.uris.isNotEmpty()) notify(if(errors.isEmpty()) "已导入 ${added.size} 本图书" else "成功 ${added.size} 本；${errors.take(3).joinToString("；")}")
                } catch(e:CancellationException) {throw e}
                catch(e:Exception) {notify("无法完成导入：${e.localizedMessage ?: "请稍后重试"}")}
                finally {importing=false;pending--;state["importPending"]=pending>0}
            }
        }
    }
    fun submit(uris:List<Uri>,external:Boolean=false) {
        if(uris.isEmpty()) return
        if(uris.size>64) {notify("一次最多64本，请稍后重试");return}
        pending++;state["importPending"]=true
        if(!queue.trySend(Request(uris.distinct(),external)).isSuccess) {
            pending--;state["importPending"]=pending>0
            notify("导入队列已满，请稍后重试")
        }
    }
    fun notify(message:String) {messages.trySend(message)}
    fun open(book:Book) {
        val current=store.current(book.id)
        store.update(current.copy(opened=System.currentTimeMillis()))
        reading=book.id;state["reading"]=book.id
    }
    fun close() {reading=null;state["reading"]=null}
}
