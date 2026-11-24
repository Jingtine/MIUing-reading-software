package com.miuing.reader

import kotlinx.coroutines.*
import java.io.File
import java.io.OutputStream
import java.util.UUID

/** Finish a document locally before touching the SAF destination; own both cleanup paths. */
internal object DocumentExport {
    suspend fun write(
        cache: File,
        content: suspend (OutputStream) -> Unit,
        destination: () -> OutputStream,
        deleteDestination: () -> Unit
    ) {
        val temporary=File(cache,"document-export-${UUID.randomUUID()}.tmp")
        var complete=false
        try {
            withContext(Dispatchers.IO) {
                temporary.outputStream().use {content(it)}
                destination().use {output -> temporary.inputStream().use {input ->
                    val buffer=ByteArray(65536)
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val n=input.read(buffer);if(n<0) break
                        output.write(buffer,0,n)
                    }
                }}
                complete=true
            }
        } finally {
            withContext(NonCancellable+Dispatchers.IO) {
                temporary.delete()
                if(!complete) runCatching {deleteDestination()}
            }
        }
    }
}
