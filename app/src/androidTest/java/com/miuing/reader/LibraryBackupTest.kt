package com.miuing.reader

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.*
import java.util.zip.*
import java.nio.ByteBuffer
import java.nio.ByteOrder

@RunWith(AndroidJUnit4::class)
class LibraryBackupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(block: suspend (File, LibraryStore, LibraryStore) -> Unit) = runBlocking {
        val root = File(context.cacheDir, "backup-test-${System.nanoTime()}").apply { mkdirs() }
        try { block(root, LibraryStore(context, File(root,"a")), LibraryStore(context, File(root,"b"))) }
        finally { root.deleteRecursively() }
    }
    private suspend fun add(root: File, store: LibraryStore, name: String = "示例"): Book {
        val source = File(root, "$name.txt").apply { writeText("第一章\n$name 正文。") }
        return store.import(Uri.fromFile(source))
    }
    private suspend fun archive(store: LibraryStore): ByteArray = ByteArrayOutputStream().also { LibraryBackup(store).export(it) }.toByteArray()
    private fun rewrite(bytes: ByteArray, change: (String,ByteArray) -> Pair<String,ByteArray>?): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> ZipInputStream(bytes.inputStream()).use { input ->
            while(true) {
                val entry = input.nextEntry ?: break
                change(entry.name, input.readBytes())?.let { (name,data) -> zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry() }
            }
        } }
        return output.toByteArray()
    }
    @Test fun roundTripRetainsEveryReadingFieldAndSurvivesOriginalDeletion() = fixture { root,a,b ->
        val original = add(root,a)
        a.addCollection("阅读计划")
        val saved = original.copy(opened=1234, chapter=0, position=.41f, anchor=8, finished=true, wantToRead=true,
            collections=listOf("阅读计划"), marks=listOf(Mark(0,.3f,"书签",4)), notes=listOf(Note(0,"正文","想法",1,3)),
            pdfInk=listOf(PdfInk(0,listOf(PdfPoint(.2f,.4f)))))
        a.update(saved)
        val bytes = archive(a)
        rewrite(bytes) {name,data ->
            if(name=="manifest.json") assertFalse(String(data).contains(root.path))
            name to data
        }
        File(root,"a").deleteRecursively()
        val result = LibraryBackup(b).restore(bytes.inputStream())
        assertEquals(1,result.restored); assertEquals(0,result.skipped)
        val restored = b.books.single()
        assertEquals(saved.copy(source=restored.source,cover=restored.cover),restored)
        assertEquals(listOf("阅读计划"),b.collections.toList())
        assertTrue(b.open(restored).chapters.isNotEmpty())
        assertEquals(restored,LibraryStore(context,File(root,"b")).books.single())
    }
    @Test fun mergeKeepsCurrentProgressAndUnionsCollections() = fixture {root,a,b ->
        val source=add(root,a);a.addCollection("备份藏书");a.update(source.copy(position=.2f))
        val current=add(root,b);b.addCollection("当前藏书");b.update(current.copy(position=.8f,notes=listOf(Note(0,"当前","保留"))))
        add(root,a,"另一本")
        val result=LibraryBackup(b).restore(archive(a).inputStream())
        assertEquals(1,result.restored);assertEquals(1,result.skipped)
        assertEquals(.8f,b.current(current.id).position);assertEquals("保留",b.current(current.id).notes.single().note)
        assertEquals(setOf("备份藏书","当前藏书"),b.collections.toSet());assertEquals(2,b.books.size)
    }
    @Test fun convertedBookAndCoverRemainReadableWithoutConversionOrOldRoot()=fixture {root,a,b ->
        val source=MultiformatFixtures.fb2(File(root,"sample.fb2"))
        val book=a.import(Uri.fromFile(source));assertTrue(File(book.cover).isFile)
        val bytes=archive(a);File(root,"a").deleteRecursively();source.delete()
        LibraryBackup(b).restore(bytes.inputStream())
        val restored=b.books.single();assertEquals("FB2",restored.format);assertTrue(File(restored.cover).isFile)
        assertTrue(File(File(restored.source).parentFile,"normalized.epub").isFile)
        assertEquals(2,b.open(restored).chapters.size)
    }
    @Test fun corruptedLaterPayloadPublishesNothing()=fixture {root,a,b ->
        add(root,a);add(root,a,"第二本")
        var sources=0
        val bytes=rewrite(archive(a)) {name,data->name to if(name.endsWith(".txt") && ++sources==2) "tampered".toByteArray() else data}
        assertTrue(runCatching {LibraryBackup(b).restore(bytes.inputStream())}.isFailure)
        assertTrue(b.books.isEmpty());assertTrue(File(root,"b").listFiles().orEmpty().none {it.isDirectory})
    }
    @Test fun unsafePathsUnknownSchemaAndMissingPayloadAreRejected()=fixture {root,a,b ->
        add(root,a);val original=archive(a)
        val unsafe=rewrite(original) {name,data->(if(name.endsWith(".txt")) "../outside.txt" else name) to data}
        val schema=rewrite(original) {name,data->name to if(name=="manifest.json") JSONObject(String(data)).put("version",99).toString().toByteArray() else data}
        val missing=rewrite(original) {name,data->if(name.endsWith(".txt")) null else name to data}
        val extra=ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                ZipInputStream(original.inputStream()).use { input -> while(true) {
                    val entry=input.nextEntry?:break
                    zip.putNextEntry(ZipEntry(entry.name));input.copyTo(zip);zip.closeEntry()
                } }
                zip.putNextEntry(ZipEntry("unknown"));zip.write(1);zip.closeEntry()
            }
        }.toByteArray()
        for(bytes in listOf(unsafe,schema,missing,extra)) {
            assertTrue(runCatching {LibraryBackup(b).restore(bytes.inputStream())}.isFailure)
            assertTrue(b.books.isEmpty())
        }
        assertFalse(File(root,"outside.txt").exists())
    }
    @Test fun payloadLimitsAndCancellationCleanStaging()=fixture {root,a,b ->
        add(root,a);val bytes=archive(a)
        assertTrue(runCatching {LibraryBackup(b,BackupLimits(sourceBytes=2)).restore(bytes.inputStream())}.isFailure)
        val interrupted=object:ByteArrayInputStream(bytes) {
            override fun read(b:ByteArray,off:Int,len:Int):Int {throw CancellationException("cancel fixture")}
        }
        assertTrue(runCatching {LibraryBackup(b).restore(interrupted)}.exceptionOrNull() is CancellationException)
        assertTrue(b.books.isEmpty());assertTrue(File(root,"b").listFiles().orEmpty().none {it.isDirectory})
    }
    @Test fun orphanDirectoryIsNeverOverwrittenOrDeleted()=fixture {root,a,b ->
        val book=add(root,a);val orphan=File(root,"b/${book.id}").apply {mkdirs()};val sentinel=File(orphan,"keep").apply {writeText("keep")}
        assertTrue(runCatching {LibraryBackup(b).restore(archive(a).inputStream())}.isFailure)
        assertEquals("keep",sentinel.readText());assertTrue(b.books.isEmpty())
    }
    @Test fun failedIndexCommitRollsBackFilesAndMemory()=fixture {root,a,b ->
        add(root,a);a.addCollection("新藏书");b.addCollection("旧藏书")
        val bytes=archive(a)
        File(root,"b/index.json").delete();File(root,"b/index.json").mkdir()
        File(root,"b/index.json/block").writeText("Force AtomicFile publication failure")
        if(android.os.Build.VERSION.SDK_INT < 31) {
            File(root,"b/index.json.bak").mkdir()
            File(root,"b/index.json.bak/block").writeText("Prevent legacy backup rename")
        }
        assertTrue(runCatching {LibraryBackup(b).restore(bytes.inputStream())}.isFailure)
        assertTrue(b.books.isEmpty());assertEquals(listOf("旧藏书"),b.collections.toList())
        assertTrue(File(root,"b").listFiles().orEmpty().filter {it.isDirectory}.all {it.name in setOf("index.json","index.json.bak")})
    }
    @Test fun zip64CentralDirectoryIsAcceptedWithoutAllocatingLargePayload()=fixture {root,a,b ->
        add(root,a)
        val bytes=archive(a);val end=bytes.size-22
        val old=ByteBuffer.wrap(bytes.copyOfRange(end,bytes.size)).order(ByteOrder.LITTLE_ENDIAN)
        val count=old.getShort(10).toLong() and 65535
        val size=old.getInt(12).toLong() and 0xffffffffL
        val offset=old.getInt(16).toLong() and 0xffffffffL
        val zip64=ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x06064b50).putLong(44).putShort(45).putShort(45).putInt(0).putInt(0)
            .putLong(count).putLong(count).putLong(size).putLong(offset).array()
        val locator=ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x07064b50).putInt(0).putLong(end.toLong()).putInt(1).array()
        old.putShort(8,(-1).toShort());old.putShort(10,(-1).toShort());old.putInt(12,-1);old.putInt(16,-1)
        val result=LibraryBackup(b).restore((bytes.copyOf(end)+zip64+locator+old.array()).inputStream())
        assertEquals(1,result.restored);assertTrue(b.open(b.books.single()).chapters.isNotEmpty())
    }
    @Test fun oversizedCentralDirectoryIsRejectedBeforeZipLibraryAllocation()=fixture {root,a,b ->
        add(root,a);val bytes=archive(a)
        val tail=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        tail.putShort(bytes.size-22+8,60000.toShort());tail.putShort(bytes.size-22+10,60000.toShort())
        assertTrue(runCatching {LibraryBackup(b).restore(bytes.inputStream())}.isFailure)
        assertTrue(b.books.isEmpty())
    }
    @Test fun cancellationAfterPartialInputAndDuringExportKeepsLibraryUntouched()=fixture {root,a,b ->
        val book=add(root,a);val bytes=archive(a)
        val input=object:ByteArrayInputStream(bytes) {
            var reads=0
            override fun read(b:ByteArray,off:Int,len:Int):Int {
                if(reads++>0) throw CancellationException("cancel after partial copy")
                return super.read(b,off,minOf(64,len))
            }
        }
        assertTrue(runCatching {LibraryBackup(b).restore(input)}.exceptionOrNull() is CancellationException)
        val output=object:OutputStream() {override fun write(b:Int) {throw CancellationException("export cancelled")}}
        assertTrue(runCatching {LibraryBackup(a).export(output)}.exceptionOrNull() is CancellationException)
        assertEquals(book,a.books.single());assertTrue(b.books.isEmpty())
        assertTrue(File(root,"b").listFiles().orEmpty().isEmpty())
    }
    @Test fun zip64WithoutClassicSentinelsCannotBypassPreflightAllocationBounds()=fixture {root,_,_ ->
        val zip64=ByteBuffer.allocate(56).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x06064b50).putLong(0x7fffffff).putShort(45).putShort(45).putInt(0).putInt(0)
            .putLong(1).putLong(1).putLong(0).putLong(0).array()
        val locator=ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x07064b50).putInt(0).putLong(0).putInt(1).array()
        val end=ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x06054b50).putShort(0).putShort(0).putShort(1).putShort(1).putInt(0).putInt(0).putShort(0).array()
        val file=File(root,"oversized-record.zip").apply {writeBytes(zip64+locator+end)}
        // Check preflight only: handing this to the unguarded upstream parser can allocate ~2 GiB.
        assertTrue(runCatching {BackupZip.preflight(file,30001)}.isFailure)
    }
    @Test fun falseEocdInsideCommentCannotSelectDifferentParserDirectory()=fixture {root,_,_ ->
        fun end(comment:Int)=ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x06054b50).putShort(0).putShort(0).putShort(1).putShort(1).putInt(0).putInt(0).putShort(comment.toShort()).array()
        val file=File(root,"false-eocd.zip").apply {writeBytes(end(22)+end(1))}
        assertTrue(runCatching {BackupZip.preflight(file,30001)}.isFailure)
    }
    @Test fun centralHeadersCannotAllocatePastDeclaredDirectorySize()=fixture {root,a,_ ->
        add(root,a);val bytes=archive(a)
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).putInt(bytes.size-22+12,0)
        val file=File(root,"false-directory-size.zip").apply {writeBytes(bytes)}
        assertTrue(runCatching {BackupZip.preflight(file,30001)}.isFailure)
    }
    @Test fun variableHeaderLengthCannotEscapeCentralDirectoryWalk()=fixture {root,a,_ ->
        add(root,a);val bytes=archive(a)
        val buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val offset=buffer.getInt(bytes.size-22+16)
        buffer.putShort(offset+28,32767.toShort())
        val file=File(root,"oversized-header.zip").apply {writeBytes(bytes)}
        val error=runCatching {BackupZip.preflight(file,30001)}.exceptionOrNull()
        assertNotNull(error);assertTrue(error!!.message.orEmpty().contains("条目"))
    }
}
