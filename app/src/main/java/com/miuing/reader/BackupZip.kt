package com.miuing.reader

import java.io.File
import java.io.RandomAccessFile

/** Bound central-directory allocation before handing a user archive to Zip4j. */
internal object BackupZip {
    fun preflight(file: File, maxEntries: Int) {
        RandomAccessFile(file, "r").use { input ->
            val length = input.length()
            require(length >= 22) { "备份文件不完整" }
            val tailSize = minOf(length, 65557).toInt()
            val tail = ByteArray(tailSize)
            input.seek(length - tailSize); input.readFully(tail)
            fun u16(at: Int) = (tail[at].toInt() and 255) or ((tail[at+1].toInt() and 255) shl 8)
            val end = (tailSize-22 downTo 0).firstOrNull { i ->
                tail[i] == 0x50.toByte() && tail[i+1] == 0x4b.toByte() &&
                    tail[i+2] == 5.toByte() && tail[i+3] == 6.toByte()
            } ?: error("备份文件缺少完整 ZIP 目录")
            // Zip4j chooses the last signature, even inside a comment. Reject ambiguous tails
            // instead of validating a different directory from the one it will parse.
            require(end+22+u16(end+20) == tailSize) { "ZIP 目录尾部无效" }
            fun u32(at: Int): Long = (0..3).fold(0L) { value, n -> value or ((tail[at+n].toLong() and 255) shl (8*n)) }
            require(u16(end+4) == 0 && u16(end+6) == 0) { "不支持分卷备份" }
            var count = u16(end+10).toLong()
            var size = u32(end+12)
            var offset = u32(end+16)
            val endOffset = length-tailSize+end
            var directoryEnd = endOffset
            fun intLE() = Integer.reverseBytes(input.readInt())
            fun longLE() = java.lang.Long.reverseBytes(input.readLong())
            val hasLocator = if(endOffset>=20) {
                input.seek(endOffset-20); intLE()==0x07064b50
            } else false
            // Upstream detects ZIP64 by locator presence, independently of classic sentinel fields.
            if (hasLocator) {
                require(intLE() == 0) { "ZIP64 目录无效" }
                val locator = longLE()
                require(intLE() == 1 && locator in 0..endOffset-76) { "不支持分卷备份" }
                input.seek(locator)
                directoryEnd = locator
                require(intLE() == 0x06064b50) { "ZIP64 目录无效" }
                val recordSize=longLE()
                require(recordSize in 44..1024 && locator+12+recordSize==endOffset-20) { "ZIP64 目录超过限制或不完整" }
                input.skipBytes(4)
                require(intLE() == 0 && intLE() == 0) { "不支持分卷备份" }
                val diskCount = longLE(); count = longLE(); size = longLE(); offset = longLE()
                require(diskCount == count) { "ZIP64 条目数无效" }
            } else {
                require(count != 65535L && size != 0xffffffffL && offset != 0xffffffffL) { "ZIP64 目录不完整" }
                require(u16(end+8).toLong() == count) { "ZIP 条目数无效" }
            }
            require(count in 1..maxEntries.toLong() && size in 46..16L*1024*1024 &&
                offset >= 0 && offset <= directoryEnd && size == directoryEnd-offset) { "备份目录超过限制或已损坏" }
            // HeaderReader follows count and variable lengths, not EOCD's size. Walk the
            // fixed headers without allocation so those fields cannot escape our bound.
            input.seek(offset)
            repeat(count.toInt()) {
                val start=input.filePointer
                require(directoryEnd-start>=46 && intLE()==0x02014b50) { "备份中央目录不完整" }
                input.skipBytes(24)
                fun shortLE() = java.lang.Short.reverseBytes(input.readShort()).toInt() and 65535
                val nameLength=shortLE();val extraLength=shortLE();val commentLength=shortLE()
                val entrySize=46L+nameLength+extraLength+commentLength
                require(nameLength in 1..256 && extraLength<=4096 && commentLength<=4096 &&
                    entrySize<=directoryEnd-start) { "备份目录条目超过限制" }
                input.seek(start+entrySize)
            }
            require(input.filePointer==directoryEnd) { "备份中央目录大小不符" }
        }
    }
}
