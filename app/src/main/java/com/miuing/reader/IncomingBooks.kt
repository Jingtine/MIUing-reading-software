package com.miuing.reader

import android.content.Intent
import android.net.Uri

/** External intake only; document picker and tests can still use the importer's file URIs. */
internal object IncomingBooks {
    @Suppress("DEPRECATION")
    fun parse(intent:Intent):List<Uri> {
        val uris=mutableListOf<Uri>()
        when(intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let {uris.add(it)}
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let {uris.add(it)}
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let {uris.addAll(it)}
            else -> return emptyList()
        }
        intent.clipData?.let {clip->
            require(clip.itemCount<=64) {"一次最多接收64本图书"}
            repeat(clip.itemCount) {clip.getItemAt(it).uri?.let(uris::add)}
        }
        require(uris.size<=128) {"一次最多接收64本图书"}
        val distinct=uris.distinct()
        require(distinct.size<=64) {"一次最多接收64本图书"}
        require(distinct.all {it.scheme=="content" && !it.authority.isNullOrEmpty()}) {"只能接收应用分享的本地文件"}
        require(distinct.isNotEmpty()) {"未收到可读取的图书文件"}
        return distinct
    }
}
