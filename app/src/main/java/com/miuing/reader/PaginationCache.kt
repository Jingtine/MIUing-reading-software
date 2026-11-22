package com.miuing.reader

import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

internal fun paginationDigest(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") {"%02x".format(it)}

/** Disposable derived data; serialized across instances, including reopen during a final write. */
internal class PaginationCache(private val root:File) {
    companion object {private val gate=Mutex()}
    private fun file(book:String,key:String)=File(File(root,paginationDigest(book)),paginationDigest(key)+".json")
    suspend fun read(book:String,key:String,chapters:Int):IntArray=withContext(Dispatchers.IO) {gate.withLock {
        val empty=IntArray(chapters);val path=file(book,key)
        try {
            if(maxOf(path.length(),File(path.path+".bak").length())>2_000_000) return@withLock empty
            if(!path.exists() && !File(path.path+".bak").exists()) return@withLock empty
            val data=JSONObject(String(AtomicFile(path).readFully(),Charsets.UTF_8))
            if(data.optInt("version")!=1 || data.optString("key")!=key) return@withLock empty
            val values=data.getJSONArray("counts")
            if(values.length()!=chapters) return@withLock empty
            val counts=IntArray(chapters) {values.getInt(it)}
            if(counts.any {it !in 0..1_000_000}) return@withLock empty
            path.setLastModified(System.currentTimeMillis());counts
        } catch(_:Exception) {empty}
    }}
    suspend fun write(book:String,key:String,counts:IntArray)=gate.withLock {withContext(Dispatchers.IO) {
        if(counts.any {it !in 0..1_000_000}) return@withContext
        val path=file(book,key)
        try {
            path.parentFile!!.mkdirs()
            val atomic=AtomicFile(path);val out=atomic.startWrite()
            try {
                val data=JSONObject().put("version",1).put("key",key).put("counts",JSONArray(counts.toList()))
                out.write(data.toString().toByteArray());atomic.finishWrite(out)
            } catch(error:Exception) {atomic.failWrite(out);throw error}
            // Older filesystems can give several writes the same timestamp.
            // Keep the just-written entry even when the eviction order ties.
            path.parentFile!!.listFiles {f->f.extension=="json" && f!=path}.orEmpty().sortedByDescending {it.lastModified()}.drop(7).forEach {AtomicFile(it).delete()}
        } catch(_:java.io.IOException) { /* Cache failure never blocks reading. */ }
    }}
}
