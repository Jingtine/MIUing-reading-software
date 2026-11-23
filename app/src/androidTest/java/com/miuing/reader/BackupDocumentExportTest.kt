package com.miuing.reader

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class BackupDocumentExportTest {
    private fun fixture(block: suspend (File) -> Unit)=runBlocking {
        val root=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"backup-document-${System.nanoTime()}").apply {mkdirs()}
        try {block(root)} finally {root.deleteRecursively()}
    }
    @Test fun destinationWriteFailureDeletesPartialDocumentAndTemporaryArchive()=fixture {root ->
        var removed=0
        val result=runCatching {BackupDocumentExport.write(root,
            archive={it.write(ByteArray(1000))},
            destination={object:OutputStream() {override fun write(b:Int) {throw IOException("provider write failed")}}},
            deleteDestination={removed++})}
        assertTrue(result.exceptionOrNull() is IOException);assertEquals(1,removed);assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun destinationCloseFailureAlsoDeletesPartialDocument()=fixture {root ->
        var removed=0
        val result=runCatching {BackupDocumentExport.write(root,
            archive={it.write(ByteArray(1000))},
            destination={object:ByteArrayOutputStream() {override fun close() {throw IOException("provider close failed")}}},
            deleteDestination={removed++})}
        assertTrue(result.exceptionOrNull() is IOException);assertEquals(1,removed);assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun realCoroutineCancellationDuringCopyDeletesDestinationAndCache()=fixture {root ->
        val copied=CompletableDeferred<Unit>();val release=CountDownLatch(1);var removed=0
        val operation=CoroutineScope(currentCoroutineContext()).launch {
            BackupDocumentExport.write(root,archive={it.write(ByteArray(100000))},
                destination={object:ByteArrayOutputStream() {
                    override fun write(bytes:ByteArray,offset:Int,length:Int) {
                        super.write(bytes,offset,length);copied.complete(Unit)
                        check(release.await(5,TimeUnit.SECONDS))
                    }
                }},deleteDestination={removed++})
        }
        try {withTimeout(10000) {copied.await()};operation.cancel()}
        finally {release.countDown();operation.join()}
        assertTrue(operation.isCancelled);assertEquals(1,removed);assertTrue(root.listFiles()!!.isEmpty())
    }
    @Test fun completedExportKeepsDestinationAndRemovesOnlyCache()=fixture {root ->
        var removed=0;val output=ByteArrayOutputStream();val data="portable backup".toByteArray()
        BackupDocumentExport.write(root,archive={it.write(data)},destination={output},deleteDestination={removed++})
        assertArrayEquals(data,output.toByteArray());assertEquals(0,removed);assertTrue(root.listFiles()!!.isEmpty())
    }
}
