package com.miuing.reader

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.mutableStateListOf
import com.miuing.core.Publication
import com.miuing.core.PublicationParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class Mark(val chapter: Int, val position: Float, val label: String, val anchor:Int = -1)
data class Note(val chapter: Int, val text: String, val note: String = "", val start:Int = -1,val end:Int = -1)
data class SelectedText(val text:String="",val start:Int=-1,val end:Int=-1)
data class Book(
    val id: String, val title: String, val author: String, val format: String,
    val source: String, val cover: String = "", val added: Long = System.currentTimeMillis(),
    val opened: Long = 0, val chapter: Int = 0, val position: Float = 0f,
    val finished: Boolean = false, val wantToRead: Boolean = false,
    val collections: List<String> = emptyList(), val marks: List<Mark> = emptyList(), val notes: List<Note> = emptyList(),
    val anchor:Int = -1, val pdfInk:List<PdfInk> = emptyList(),
    val txtLayoutVersion: Int = PublicationParser.TXT_LAYOUT_LEGACY
)

class LibraryStore(private val context: Context, private val root: File = File(context.filesDir, "library")) {
    val books = mutableStateListOf<Book>()
    val collections = mutableStateListOf<String>()
    var loadError: String? = null
        private set
    internal val directory get() = root
    internal fun snapshot(): Pair<List<Book>, List<String>> {
        check(loadError == null) { loadError!! }
        return books.toList() to collections.toList()
    }

    /** One publication boundary: old files/records are never replaced. Called on Main. */
    internal fun mergeBackup(incoming: List<Book>, names: List<String>, staging: File): RestoreResult {
        check(loadError == null) { loadError!! }
        val previousBooks = books.toList()
        val previousCollections = collections.toList()
        val missing = incoming.filter { candidate -> books.none { it.id == candidate.id } }
        val moved = mutableListOf<File>()
        try {
            // Check every destination before moving the first one, including orphan directories.
            missing.forEach { check(!File(root, it.id).exists()) { "发现同名书籍目录，请先修复书库后恢复" } }
            missing.forEach { book ->
                val destination = File(root, book.id)
                check(File(staging, book.id).renameTo(destination)) { "无法保存恢复的图书" }
                moved.add(destination)
            }
            books.addAll(missing)
            collections.addAll((names + missing.flatMap { it.collections }).filter { it !in collections }.distinct())
            persist()
            return RestoreResult(missing.size, incoming.size - missing.size)
        } catch (e: Exception) {
            books.clear(); books.addAll(previousBooks)
            collections.clear(); collections.addAll(previousCollections)
            moved.forEach { it.deleteRecursively() }
            throw e
        }
    }
    private val index get() = File(root, "index.json")
    init {
        root.mkdirs()
        if (index.exists() || File(index.path+".bak").exists()) try {
            val data = JSONObject(String(android.util.AtomicFile(index).readFully(),Charsets.UTF_8))
            data.optJSONArray("collections")?.let { a -> repeat(a.length()) { collections.add(a.getString(it)) } }
            val a = data.getJSONArray("books")
            repeat(a.length()) { books.add(BookJson.decode(a.getJSONObject(it))) }
        } catch (e: Exception) { loadError = "书库记录无法读取：${e.localizedMessage}" }
    }
    suspend fun import(uri: Uri): Book {
        check(loadError == null) { "书库记录损坏，请保留文件后修复" }
        var created:File?=null
        try {
        val imported = withContext(Dispatchers.IO) {
            val info=ImportSource.resolve(context,uri)
            val name=info.name
            val staging = File(root, "import-${UUID.randomUUID()}")
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    staging.outputStream().use { output ->
                        val buffer = ByteArray(65536); var total = 0L
                        while (true) {
                            kotlinx.coroutines.currentCoroutineContext().ensureActive()
                            val n = input.read(buffer); if (n < 0) break
                            total += n
                            require(total <= 1024L*1024*1024) {"文件超过 1 GB"}
                            digest.update(buffer, 0, n); output.write(buffer, 0, n)
                        }
                    }
                } ?: error("无法读取文件")
                val header=ByteArray(minOf(4096L,staging.length()).toInt())
                java.io.DataInputStream(staging.inputStream()).use {it.readFully(header)}
                val format=ImportSource.detect(name,info.mime,header)
                require(format !in BookFormatImporter.formats || staging.length()<=64L*1024*1024) {"此格式目前支持64MiB以内的文件"}
                val id = digest.digest().joinToString("") { "%02x".format(it) }
                val existing = books.firstOrNull { it.id == id }
                if (existing != null) {
                    val originalFormat=existing.format.lowercase()
                    if(originalFormat in BookFormatImporter.formats && !normalized(existing).isFile)
                        BookFormatImporter.convert(context,staging,normalized(existing),originalFormat)
                    existing
                } else {
                    val dir = File(root, id); created = dir; dir.mkdirs()
                    val source = File(dir, "source.$format")
                    check(staging.renameTo(source)) { "无法保存导入文件" }
                    if (format == "pdf") {
                        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            PdfRenderer(descriptor).use { require(it.pageCount > 0) { "PDF 没有页面" } }
                        }
                        Book(id, name.substringBeforeLast('.'), "未知作者", "PDF", source.path)
                    } else {
                        val converted=format in BookFormatImporter.formats
                        val reading=if(converted) File(dir,"normalized.epub").also {BookFormatImporter.convert(context,source,it,format)} else source
                        val p = PublicationParser.open(reading.toPath(), File(dir, "content").toPath())
                        Book(id, if(format == "txt" || p.title=="source" || p.title=="未命名图书") name.substringBeforeLast('.') else p.title, p.author, if(converted) format.uppercase() else p.format, source.path, p.cover?.toString() ?: "",
                            txtLayoutVersion=if(format == "txt") PublicationParser.TXT_LAYOUT_CURRENT else PublicationParser.TXT_LAYOUT_LEGACY)
                    }
                }
            } finally { staging.delete() }
        }
        withContext(Dispatchers.Main) { if (books.none { it.id == imported.id }) update(imported);created=null }
        return imported
        } finally {created?.let {dir->withContext(NonCancellable+Dispatchers.IO) {dir.deleteRecursively()}}}
    }
    fun open(book: Book): Publication = if (book.format == "PDF")
        Publication(book.title, book.author, "PDF", emptyList(), null)
    else {
        val source=if(book.format.lowercase() in BookFormatImporter.formats) normalized(book).also {
            check(it.isFile) {"阅读副本缺失，请重新导入同一本书以修复"}
        } else File(book.source)
        PublicationParser.open(source.toPath(), File(File(book.source).parentFile, "content").toPath(), book.txtLayoutVersion)
    }
    private fun normalized(book:Book)=File(File(book.source).parentFile,"normalized.epub")
    fun current(id: String) = books.first { it.id == id }
    fun update(book: Book) {
        check(loadError == null) { loadError!! }
        val at = books.indexOfFirst { it.id == book.id }
        val previous=books.getOrNull(at)
        if (at < 0) books.add(book) else books[at] = book
        try {persist()} catch(e:Exception) {
            if(at<0) books.removeAll {it.id==book.id} else books[at]=previous!!
            throw e
        }
    }
    fun delete(id: String) {
        val book = books.firstOrNull { it.id == id } ?: return
        books.removeAll { it.id == id }; persist()
        File(book.source).parentFile?.deleteRecursively()
    }
    fun addCollection(name: String) {
        val trimmed = name.trim()
        if (trimmed.isNotEmpty() && trimmed !in collections) { collections.add(trimmed); persist() }
    }
    fun removeCollection(name: String) {
        collections.remove(name)
        books.indices.forEach { i -> books[i] = books[i].copy(collections = books[i].collections - name) }
        persist()
    }
    private fun persist() {
        check(loadError == null) { loadError!! }
        val file = android.util.AtomicFile(index)
        val out = file.startWrite()
        try {
            val data = JSONObject().put("collections", JSONArray(collections)).put("books", JSONArray(books.map(BookJson::encode))).toString().toByteArray()
            out.write(data)
            file.finishWrite(out)
            // AtomicFile can log a failed rename without throwing; do not publish memory-only records.
            check(index.isFile && index.readBytes().contentEquals(data)) { "书库记录未能保存" }
        } catch (e: Exception) { file.failWrite(out); throw e }
    }
}
