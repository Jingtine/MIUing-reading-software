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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

data class Mark(val chapter: Int, val position: Float, val label: String)
data class Note(val chapter: Int, val text: String, val note: String = "")
data class Book(
    val id: String, val title: String, val author: String, val format: String,
    val source: String, val cover: String = "", val added: Long = System.currentTimeMillis(),
    val opened: Long = 0, val chapter: Int = 0, val position: Float = 0f,
    val finished: Boolean = false, val wantToRead: Boolean = false,
    val collections: List<String> = emptyList(), val marks: List<Mark> = emptyList(), val notes: List<Note> = emptyList()
)

class LibraryStore(private val context: Context, private val root: File = File(context.filesDir, "library")) {
    val books = mutableStateListOf<Book>()
    val collections = mutableStateListOf<String>()
    var loadError: String? = null
        private set
    private val index get() = File(root, "index.json")
    init {
        root.mkdirs()
        if (index.exists() || File(index.path+".bak").exists()) try {
            val data = JSONObject(String(android.util.AtomicFile(index).readFully(),Charsets.UTF_8))
            data.optJSONArray("collections")?.let { a -> repeat(a.length()) { collections.add(a.getString(it)) } }
            val a = data.getJSONArray("books")
            repeat(a.length()) { books.add(decode(a.getJSONObject(it))) }
        } catch (e: Exception) { loadError = "书库记录无法读取：${e.localizedMessage}" }
    }
    suspend fun import(uri: Uri): Book {
        check(loadError == null) { "书库记录损坏，请保留文件后修复" }
        val imported = withContext(Dispatchers.IO) {
            val name = if (uri.scheme == "file") File(uri.path!!).name else
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                } ?: "book"
            val format = name.substringAfterLast('.', "").lowercase()
            require(format in listOf("epub", "pdf", "txt")) { "暂不支持此格式：$name" }
            val staging = File(root, "import-${UUID.randomUUID()}")
            var destination: File? = null
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    staging.outputStream().use { output ->
                        val buffer = ByteArray(65536); var total = 0L
                        while (true) {
                            val n = input.read(buffer); if (n < 0) break
                            total += n; require(total <= 1024L * 1024 * 1024) { "文件超过 1 GB" }
                            digest.update(buffer, 0, n); output.write(buffer, 0, n)
                        }
                    }
                } ?: error("无法读取文件")
                val id = digest.digest().joinToString("") { "%02x".format(it) }
                val existing = books.firstOrNull { it.id == id }
                if (existing != null) existing else {
                    val dir = File(root, id); destination = dir; dir.mkdirs()
                    val source = File(dir, "source.$format")
                    check(staging.renameTo(source)) { "无法保存导入文件" }
                    if (format == "pdf") {
                        ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                            PdfRenderer(descriptor).use { require(it.pageCount > 0) { "PDF 没有页面" } }
                        }
                        Book(id, name.substringBeforeLast('.'), "未知作者", "PDF", source.path)
                    } else {
                        val p = PublicationParser.open(source.toPath(), File(dir, "content").toPath())
                        Book(id, if(format == "txt" || p.title=="source") name.substringBeforeLast('.') else p.title, p.author, p.format, source.path, p.cover?.toString() ?: "")
                    }
                }
            } catch (e: Exception) { destination?.deleteRecursively(); throw e }
            finally { staging.delete() }
        }
        withContext(Dispatchers.Main) { if (books.none { it.id == imported.id }) update(imported) }
        return imported
    }
    fun open(book: Book): Publication = if (book.format == "PDF")
        Publication(book.title, book.author, "PDF", emptyList(), null)
    else PublicationParser.open(File(book.source).toPath(), File(File(book.source).parentFile, "content").toPath())
    fun current(id: String) = books.first { it.id == id }
    fun update(book: Book) {
        check(loadError == null) { loadError!! }
        val at = books.indexOfFirst { it.id == book.id }
        if (at < 0) books.add(book) else books[at] = book
        persist()
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
            out.write(JSONObject().put("collections", JSONArray(collections)).put("books", JSONArray(books.map(::encode))).toString().toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) { file.failWrite(out); throw e }
    }
    private fun encode(b: Book) = JSONObject().apply {
        put("id",b.id); put("title",b.title); put("author",b.author); put("format",b.format)
        put("source",b.source); put("cover",b.cover); put("added",b.added); put("opened",b.opened)
        put("chapter",b.chapter); put("position",b.position); put("finished",b.finished); put("want",b.wantToRead)
        put("collections",JSONArray(b.collections))
        put("marks",JSONArray(b.marks.map { JSONObject().put("chapter",it.chapter).put("position",it.position).put("label",it.label) }))
        put("notes",JSONArray(b.notes.map { JSONObject().put("chapter",it.chapter).put("text",it.text).put("note",it.note) }))
    }
    private fun decode(o: JSONObject): Book {
        fun strings(key: String): List<String> = o.optJSONArray(key)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
        val marks = o.optJSONArray("marks")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { Mark(it.getInt("chapter"),it.getDouble("position").toFloat(),it.getString("label")) } } } ?: emptyList()
        val notes = o.optJSONArray("notes")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { Note(it.getInt("chapter"),it.getString("text"),it.optString("note")) } } } ?: emptyList()
        return Book(o.getString("id"),o.getString("title"),o.getString("author"),o.getString("format"),o.getString("source"),o.optString("cover"),o.getLong("added"),o.optLong("opened"),o.optInt("chapter"),o.optDouble("position",0.0).toFloat(),o.optBoolean("finished"),o.optBoolean("want"),strings("collections"),marks,notes)
    }
}
