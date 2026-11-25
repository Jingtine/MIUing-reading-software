package com.miuing.reader

import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.ByteArrayOutputStream

/** Host installs the released APK before seed and the candidate APK before verify. */
class TxtUpgradeFixture {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val root get() = File(context.cacheDir,"txt-upgrade-fixture")
    @Test fun seed() = runBlocking {
        check(!root.exists()) { "Previous fixture needs cleanup" }
        root.mkdirs()
        val store=LibraryStore(context,File(root,"library"))
        val source=File(root,"upgrade.txt").apply {writeText("第一章 开始\r第一段正文\r第二章 继续\r第二段正文")}
        val imported=store.import(Uri.fromFile(source))
        val json=BookJson.encode(imported)
        assertFalse(json.has("txtLayoutVersion"))
        json.put("chapter",0).put("position",.6).put("anchor",20)
        json.put("marks",JSONArray().put(JSONObject().put("chapter",0).put("position",.6).put("label","升级书签").put("anchor",20)))
        json.put("notes",JSONArray().put(JSONObject().put("chapter",0).put("text","第二段正文").put("note","升级笔记").put("start",20).put("end",25)))
        val saved=BookJson.decode(json)
        store.update(saved)
        val publication=store.open(saved)
        assertEquals(1,publication.chapters.size)
        File(root,"expected.html").writeBytes(publication.chapters.single().path.toFile().readBytes())
        File(root,"expected.json").writeText(BookJson.encode(saved).toString())
        File(root,"old-backup.zip").outputStream().use {LibraryBackup(store).export(it)}
    }
    @Test fun verify() = runBlocking {
        val store=LibraryStore(context,File(root,"library"))
        val expected=JSONObject(File(root,"expected.json").readText())
        fun checkStore(target:LibraryStore) {
            val book=target.books.single()
            val json=BookJson.encode(book)
            assertEquals(1,json.getInt("txtLayoutVersion"))
            for(key in listOf("chapter","position","anchor","marks","notes"))
                assertEquals(key,expected.get(key).toString(),json.get(key).toString())
            val publication=target.open(book)
            assertEquals(1,publication.chapters.size)
            assertArrayEquals(File(root,"expected.html").readBytes(),publication.chapters.single().path.toFile().readBytes())
        }
        checkStore(store)
        val oldRestored=LibraryStore(context,File(root,"old-restored"))
        File(root,"old-backup.zip").inputStream().use {LibraryBackup(oldRestored).restore(it)}
        checkStore(oldRestored)
        val bytes=ByteArrayOutputStream().also {LibraryBackup(store).export(it)}.toByteArray()
        val newRestored=LibraryStore(context,File(root,"new-restored"))
        LibraryBackup(newRestored).restore(bytes.inputStream())
        checkStore(newRestored)
    }
    @Test fun cleanup() { root.deleteRecursively() }
}
