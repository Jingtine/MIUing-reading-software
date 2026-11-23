package com.miuing.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.security.MessageDigest
import java.util.UUID

internal data class RestoreResult(val restored: Int, val skipped: Int)
internal data class BackupLimits(
    val sourceBytes: Long = 1024L*1024*1024,
    val archiveBytes: Long = 8L*1024*1024*1024,
    val totalBytes: Long = 16L*1024*1024*1024,
    val manifestBytes: Long = 16L*1024*1024,
    val books: Int = 10000
)

/** Portable, bounded ZIP transport; LibraryStore alone publishes the merged index. */
internal class LibraryBackup(private val store: LibraryStore, private val limits: BackupLimits = BackupLimits()) {
    private data class Payload(val name: String, val file: File, val size: Long, val hash: String)
    private val formats = setOf("epub","txt","pdf","mobi","azw","azw3","fb2")
    private val converted = setOf("mobi","azw","azw3","fb2")
    private val idPattern = Regex("[0-9a-f]{64}")
    private fun maxSize(name: String): Long = when {
        name.endsWith("/normalized.epub") -> 256L*1024*1024
        name.endsWith("/cover") -> 16L*1024*1024
        name.substringAfterLast('.') in converted -> minOf(limits.sourceBytes,64L*1024*1024)
        else -> limits.sourceBytes
    }
    private fun path(id: String, file: String) = "books/$id/$file"
    private fun digest() = MessageDigest.getInstance("SHA-256")
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private suspend fun copy(input: InputStream, output: OutputStream, max: Long, hash: MessageDigest? = null): Long {
        val buffer = ByteArray(65536); var total = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buffer)
            if (n < 0) return total
            if (n == 0) continue
            total += n
            require(total <= max) { "备份文件超过允许大小" }
            hash?.update(buffer,0,n); output.write(buffer,0,n)
        }
    }

    suspend fun export(output: OutputStream) {
        val (books,collections) = withContext(Dispatchers.Main) { store.snapshot() }
        withContext(Dispatchers.IO) {
            require(books.size <= limits.books) { "图书数量超过备份限制" }
            val payloads = mutableListOf<Payload>()
            var total = 0L
            suspend fun payload(name: String, file: File) {
                require(file.isFile) { "图书文件缺失，请先修复：${file.name}" }
                val hash = digest()
                val sink = object: OutputStream() {
                    override fun write(b: Int) = Unit
                    override fun write(b: ByteArray, off: Int, len: Int) = Unit
                }
                val size = file.inputStream().use { copy(it,sink,maxSize(name),hash) }
                total += size; require(total <= limits.totalBytes) { "书库超过备份大小限制" }
                payloads.add(Payload(name,file,size,hex(hash.digest())))
            }
            val records = JSONArray()
            for (book in books) {
                require(idPattern.matches(book.id) && book.format.lowercase() in formats) { "书籍记录无效" }
                val directory = File(store.directory,book.id).canonicalFile
                val source = File(book.source).canonicalFile
                require(source.parentFile == directory) { "书籍原文件不在当前书库中" }
                val sourceName = path(book.id,"source.${book.format.lowercase()}")
                payload(sourceName,source)
                require(payloads.last().hash == book.id) { "图书原文件校验失败：${book.title}" }
                if (book.format.lowercase() in converted) payload(path(book.id,"normalized.epub"),File(directory,"normalized.epub"))
                val coverName = if (book.cover.isNotEmpty() && File(book.cover).isFile) path(book.id,"cover") else ""
                if (coverName.isNotEmpty()) payload(coverName,File(book.cover))
                records.put(BookJson.encode(book.copy(source=sourceName,cover=coverName)))
            }
            val manifest = JSONObject().put("format","miuing-library").put("version",1)
                .put("books",records).put("collections",JSONArray(collections))
                .put("files",JSONArray(payloads.map { JSONObject().put("path",it.name).put("size",it.size).put("sha256",it.hash) }))
                .toString().toByteArray(Charsets.UTF_8)
            require(manifest.size <= limits.manifestBytes) { "阅读记录超过备份限制" }
            // Bound bytes emitted as well as expanded input, including central-directory overhead.
            val bounded = object: FilterOutputStream(output) {
                var written = 0L
                override fun write(b: Int) { require(++written <= limits.archiveBytes); out.write(b) }
                override fun write(b: ByteArray, off: Int, len: Int) {
                    written += len; require(written <= limits.archiveBytes) { "备份超过大小限制" }; out.write(b,off,len)
                }
            }
            ZipOutputStream(bounded).use { zip ->
                fun entry(name: String) = zip.putNextEntry(ZipParameters().apply { fileNameInZip=name })
                entry("manifest.json"); zip.write(manifest); zip.closeEntry()
                for (p in payloads) {
                    entry(p.name)
                    val hash=digest()
                    val count=p.file.inputStream().use { copy(it,zip,p.size,hash) }
                    require(count==p.size && hex(hash.digest())==p.hash) { "备份过程中图书文件发生变化，请重试" }
                    zip.closeEntry()
                }
            }
        }
    }

    suspend fun restore(input: InputStream): RestoreResult {
        withContext(Dispatchers.Main) { store.snapshot() }
        val temporary = File(store.directory,".restore-${UUID.randomUUID()}")
        try {
            val prepared = withContext(Dispatchers.IO) {
                check(temporary.mkdirs()) { "无法创建恢复临时目录" }
                val archive = File(temporary,"backup.zip")
                archive.outputStream().use { copy(input,it,limits.archiveBytes) }
                BackupZip.preflight(archive,limits.books*3+1)
                ZipFile(archive).use { zip ->
                    require(!zip.isSplitArchive && !zip.isEncrypted) { "不支持加密或分卷备份" }
                    val headers = zip.fileHeaders
                    val entries = headers.associateBy { it.fileName }
                    require(entries.size == headers.size && headers.none { it.isDirectory }) { "备份包含重复或异常条目" }
                    val header = entries["manifest.json"] ?: error("不是 MIUing 书库备份")
                    require(header.uncompressedSize in 1..limits.manifestBytes) { "备份目录超过限制" }
                    val bytes = ByteArrayOutputStream()
                    zip.getInputStream(header).use { copy(it,bytes,limits.manifestBytes) }
                    val manifest = JSONObject(bytes.toString("UTF-8"))
                    require(manifest.getString("format") == "miuing-library" && manifest.getInt("version") == 1) { "不支持此备份版本" }
                    val array = manifest.getJSONArray("books")
                    require(array.length() <= limits.books) { "备份图书数量超过限制" }
                    val books = List(array.length()) { BookJson.decode(array.getJSONObject(it)) }
                    require(books.map { it.id }.toSet().size == books.size) { "备份书籍记录重复" }
                    val expected = mutableSetOf<String>()
                    for (book in books) {
                        require(idPattern.matches(book.id) && book.format.lowercase() in formats) { "备份书籍记录无效" }
                        require(book.source == path(book.id,"source.${book.format.lowercase()}")) { "备份文件路径无效" }
                        require(book.cover.isEmpty() || book.cover == path(book.id,"cover")) { "备份封面路径无效" }
                        require(book.position.isFinite() && book.position in 0f..1f) { "备份阅读位置无效" }
                        expected.add(book.source)
                        if (book.cover.isNotEmpty()) expected.add(book.cover)
                        if (book.format.lowercase() in converted) expected.add(path(book.id,"normalized.epub"))
                    }
                    require(entries.keys == expected + "manifest.json") { "备份文件缺失或包含未知条目" }
                    val files = manifest.getJSONArray("files")
                    require(files.length() == expected.size) { "备份校验目录不完整" }
                    val inventory = (0 until files.length()).map { files.getJSONObject(it) }
                    require(inventory.map { it.getString("path") }.toSet() == expected) { "备份校验路径无效" }
                    var total = 0L
                    val staging = File(temporary,"books").apply { mkdirs() }
                    for (record in inventory) {
                        val name = record.getString("path")
                        val size = record.getLong("size")
                        val hash = record.getString("sha256")
                        require(size in 0..maxSize(name) && idPattern.matches(hash)) { "备份文件大小或校验记录无效" }
                        total += size; require(total <= limits.totalBytes) { "备份展开大小超过限制" }
                        val fileHeader = entries.getValue(name)
                        require(fileHeader.uncompressedSize == size) { "备份文件大小不符" }
                        val destination = File(temporary,name)
                        check(destination.parentFile!!.isDirectory || destination.parentFile!!.mkdirs()) { "无法创建恢复目录" }
                        val actual = digest()
                        val count = zip.getInputStream(fileHeader).use { stream -> destination.outputStream().use { copy(stream,it,size,actual) } }
                        require(count == size && hex(actual.digest()) == hash) { "备份文件校验失败" }
                        if (name.substringAfterLast('/').startsWith("source.")) require(hash == name.split('/')[1]) { "图书原文件校验失败" }
                    }
                    val names = manifest.getJSONArray("collections").let { a -> List(a.length()) { a.getString(it) } }
                    val localized = books.map { book -> book.copy(
                        source=File(store.directory,"${book.id}/${File(book.source).name}").path,
                        cover=if(book.cover.isEmpty()) "" else File(store.directory,"${book.id}/cover").path) }
                    Triple(localized,names,staging)
                }
            }
            return withContext(Dispatchers.Main) { store.mergeBackup(prepared.first,prepared.second,prepared.third) }
        } finally {
            withContext(kotlinx.coroutines.NonCancellable + Dispatchers.IO) { temporary.deleteRecursively() }
        }
    }
}
