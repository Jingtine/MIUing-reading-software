package com.miuing.reader

import org.json.JSONArray
import org.json.JSONObject

/** Shared persistent and portable Book schema. */
internal object BookJson {
    fun encode(b: Book) = JSONObject().apply {
        put("id",b.id); put("title",b.title); put("author",b.author); put("format",b.format)
        put("source",b.source); put("cover",b.cover); put("added",b.added); put("opened",b.opened)
        put("chapter",b.chapter); put("position",b.position); put("anchor",b.anchor); put("finished",b.finished); put("want",b.wantToRead)
        put("collections",JSONArray(b.collections))
        put("marks",JSONArray(b.marks.map { JSONObject().put("chapter",it.chapter).put("position",it.position).put("label",it.label).put("anchor",it.anchor) }))
        put("notes",JSONArray(b.notes.map { JSONObject().put("chapter",it.chapter).put("text",it.text).put("note",it.note).put("start",it.start).put("end",it.end) }))
        put("pdfInk",JSONArray(b.pdfInk.map {stroke->JSONObject().put("page",stroke.page).put("color",stroke.color).put("width",stroke.width).put("points",JSONArray(stroke.points.map {JSONArray(listOf(it.x,it.y))}))}))
    }
    fun decode(o: JSONObject): Book {
        fun strings(key: String): List<String> = o.optJSONArray(key)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
        val marks = o.optJSONArray("marks")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { Mark(it.getInt("chapter"),it.getDouble("position").toFloat(),it.getString("label"),it.optInt("anchor",-1)) } } } ?: emptyList()
        val notes = o.optJSONArray("notes")?.let { a -> List(a.length()) { i -> a.getJSONObject(i).let { Note(it.getInt("chapter"),it.getString("text"),it.optString("note"),it.optInt("start",-1),it.optInt("end",-1)) } } } ?: emptyList()
        val ink=o.optJSONArray("pdfInk")?.let {a->List(a.length()) {i->a.getJSONObject(i).let {s->val points=s.getJSONArray("points");PdfInk(s.getInt("page"),List(points.length()) {j->points.getJSONArray(j).let {PdfPoint(it.getDouble(0).toFloat(),it.getDouble(1).toFloat())}},s.optString("color","#D96828"),s.optDouble("width",.004).toFloat())}}} ?: emptyList()
        return Book(o.getString("id"),o.getString("title"),o.getString("author"),o.getString("format"),o.getString("source"),o.optString("cover"),o.getLong("added"),o.optLong("opened"),o.optInt("chapter"),o.optDouble("position",0.0).toFloat(),o.optBoolean("finished"),o.optBoolean("want"),strings("collections"),marks,notes,o.optInt("anchor",-1),ink)
    }
}
