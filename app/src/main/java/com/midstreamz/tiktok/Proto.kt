package com.midstreamz.tiktok

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

/**
 * Lightweight, zero-dependency Protobuf encoder and decoder for TikTok Webcast WebSocket protocol.
 */
object Proto {

    fun encode(block: (Writer) -> Unit): ByteArray {
        val w = Writer()
        block(w)
        return w.toByteArray()
    }

    class Writer {
        private val buf = ByteArrayOutputStream()

        fun writeVarint(value: Long) {
            var v = value
            while ((v and 0x7FL.inv()) != 0L) {
                buf.write(((v and 0x7F).toInt() or 0x80))
                v = v ushr 7
            }
            buf.write((v and 0x7F).toInt())
        }

        fun writeUint64(field: Int, v: Long) {
            writeVarint((field.toLong() shl 3))
            writeVarint(v)
        }

        fun writeInt64(field: Int, v: Long) = writeUint64(field, v)

        fun writeInt32(field: Int, v: Int) = writeUint64(field, v.toLong() and 0xFFFFFFFFL)

        fun writeBool(field: Int, v: Boolean) = writeUint64(field, if (v) 1L else 0L)

        fun writeString(field: Int, v: String) {
            writeBytes(field, v.toByteArray(StandardCharsets.UTF_8))
        }

        fun writeBytes(field: Int, v: ByteArray) {
            writeVarint(((field.toLong() shl 3) or 2L))
            writeVarint(v.size.toLong())
            buf.write(v, 0, v.size)
        }

        fun toByteArray(): ByteArray = buf.toByteArray()
    }

    fun decode(data: ByteArray, depth: Int = 0): ProtoMap {
        if (depth > 64 || data.isEmpty()) {
            return ProtoMap(emptyMap(), depth)
        }
        val fields = mutableMapOf<Int, MutableList<Any>>()
        var pos = 0
        val end = data.size

        while (pos < end) {
            // Read tag varint
            var tag = 0L
            var shift = 0
            while (pos < end) {
                val b = data[pos++].toInt()
                tag = tag or ((b and 0x7F).toLong() shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
                if (shift > 63) break
            }
            val fieldNum = (tag ushr 3).toInt()
            val wireType = (tag and 7).toInt()
            if (fieldNum == 0) break

            when (wireType) {
                0 -> { // Varint
                    var v = 0L
                    var s = 0
                    while (pos < end) {
                        val b = data[pos++].toInt()
                        v = v or ((b and 0x7F).toLong() shl s)
                        if ((b and 0x80) == 0) break
                        s += 7
                        if (s > 63) break
                    }
                    fields.getOrPut(fieldNum) { mutableListOf() }.add(v)
                }
                1 -> { // 64-bit
                    pos = (pos + 8).coerceAtMost(end)
                }
                2 -> { // Length-delimited
                    var len = 0L
                    var s = 0
                    while (pos < end) {
                        val b = data[pos++].toInt()
                        len = len or ((b and 0x7F).toLong() shl s)
                        if ((b and 0x80) == 0) break
                        s += 7
                        if (s > 63) break
                    }
                    val l = len.toInt().coerceAtMost(end - pos)
                    if (l >= 0 && pos + l <= end) {
                        val slice = data.copyOfRange(pos, pos + l)
                        pos += l
                        fields.getOrPut(fieldNum) { mutableListOf() }.add(slice)
                    } else {
                        break
                    }
                }
                5 -> { // 32-bit
                    pos = (pos + 4).coerceAtMost(end)
                }
                else -> {
                    // Unknown wire type, cannot skip safely without length
                    break
                }
            }
        }
        return ProtoMap(fields, depth)
    }

    class ProtoMap(
        val fields: Map<Int, List<Any>>,
        val depth: Int
    ) {
        fun getVarint(field: Int): Long {
            val list = fields[field] ?: return 0L
            val first = list.firstOrNull() ?: return 0L
            return if (first is Long) first else 0L
        }

        fun getInt(field: Int): Int = getVarint(field).toInt()

        fun getBool(field: Int): Boolean = getVarint(field) != 0L

        fun getRawBytes(field: Int): ByteArray {
            val list = fields[field] ?: return ByteArray(0)
            val first = list.firstOrNull() ?: return ByteArray(0)
            return if (first is ByteArray) first else ByteArray(0)
        }

        fun getString(field: Int): String {
            val b = getRawBytes(field)
            if (b.isEmpty()) return ""
            return String(b, StandardCharsets.UTF_8)
        }

        fun getMessage(field: Int): ProtoMap {
            val b = getRawBytes(field)
            if (b.isEmpty()) return ProtoMap(emptyMap(), depth)
            return decode(b, depth + 1)
        }

        fun getRepeatedMessages(field: Int): List<ProtoMap> {
            val list = fields[field] ?: return emptyList()
            return list.mapNotNull { item ->
                if (item is ByteArray && item.isNotEmpty()) {
                    decode(item, depth + 1)
                } else null
            }
        }
    }
}

/**
 * TikTok Live protocol frames (Heartbeat, EnterRoom, Ack, Gzip decompressor)
 */
object TikTokFrames {
    private const val GZIP_MAGIC_1 = 0x1F
    private const val GZIP_MAGIC_2 = 0x8B

    fun buildHeartbeat(roomId: String): ByteArray {
        val rIdLong = roomId.toLongOrNull() ?: 0L
        val hb = Proto.encode { w ->
            w.writeUint64(1, rIdLong)
        }
        return Proto.encode { w ->
            w.writeString(6, "pb")
            w.writeString(7, "hb")
            w.writeBytes(8, hb)
        }
    }

    fun buildEnterRoom(roomId: String): ByteArray {
        val rIdLong = roomId.toLongOrNull() ?: 0L
        val enter = Proto.encode { w ->
            w.writeInt64(1, rIdLong)
            w.writeInt64(4, 12L) // Client type: audience
            w.writeString(5, "audience")
            w.writeString(9, "0")
        }
        return Proto.encode { w ->
            w.writeString(6, "pb")
            w.writeString(7, "im_enter_room")
            w.writeBytes(8, enter)
        }
    }

    fun buildAck(logId: Long, internalExt: ByteArray): ByteArray {
        return Proto.encode { w ->
            w.writeString(6, "pb")
            w.writeString(7, "ack")
            w.writeUint64(2, logId)
            w.writeBytes(8, internalExt)
        }
    }

    fun decompressIfGzipped(data: ByteArray): ByteArray {
        if (data.size >= 2 &&
            (data[0].toInt() and 0xFF) == GZIP_MAGIC_1 &&
            (data[1].toInt() and 0xFF) == GZIP_MAGIC_2
        ) {
            try {
                GZIPInputStream(ByteArrayInputStream(data)).use { gis ->
                    val out = ByteArrayOutputStream()
                    val buf = ByteArray(4096)
                    var read: Int
                    while (gis.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                    }
                    return out.toByteArray()
                }
            } catch (_: IOException) {}
        }
        return data
    }
}
