package com.miuing.reader

import kotlinx.coroutines.*
import java.io.File
import java.io.OutputStream
import java.util.UUID

/** Complete the archive locally before touching the SAF destination; own both cleanup paths. */
internal object BackupDocumentExport {
    suspend fun write(
        cache: File,
        archive: suspend (OutputStream) -> Unit,
        destination: () -> OutputStream,
        deleteDestination: () -> Unit
    ) {
        val temporary=File(cache,"library-backup-${UUID.randomUUID()}.zip")
        var complete=false
        try {
            withContext(Dispatchers.IO) {
                temporary.outputStream().use {archive(it)}
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
