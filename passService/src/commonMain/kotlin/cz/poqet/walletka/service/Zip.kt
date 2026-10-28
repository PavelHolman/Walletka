package cz.poqet.walletka.service

/** Writes a ZIP archive with stored (uncompressed) entries, which is all a `.pkpass` needs. */
internal object Zip {

    fun write(entries: List<Pair<String, ByteArray>>): ByteArray {
        val out = Buffer()
        val central = Buffer()
        for ((name, data) in entries) {
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            val offset = out.size
            out.int32(0x04034b50); out.int16(20); out.int16(UTF8_FLAG); out.int16(0); out.int16(DOS_TIME); out.int16(DOS_DATE)
            out.int32(crc); out.int32(data.size); out.int32(data.size); out.int16(nameBytes.size); out.int16(0)
            out.bytes(nameBytes); out.bytes(data)

            central.int32(0x02014b50); central.int16(20); central.int16(20); central.int16(UTF8_FLAG); central.int16(0)
            central.int16(DOS_TIME); central.int16(DOS_DATE); central.int32(crc); central.int32(data.size); central.int32(data.size)
            central.int16(nameBytes.size); central.int16(0); central.int16(0); central.int16(0); central.int16(0); central.int32(0)
            central.int32(offset); central.bytes(nameBytes)
        }
        val centralOffset = out.size
        val centralBytes = central.toByteArray()
        out.bytes(centralBytes)
        out.int32(0x06054b50); out.int16(0); out.int16(0); out.int16(entries.size); out.int16(entries.size)
        out.int32(centralBytes.size); out.int32(centralOffset); out.int16(0)
        return out.toByteArray()
    }

    private const val UTF8_FLAG = 0x0800
    private const val DOS_TIME = 0 // 00:00:00
    private const val DOS_DATE = (2020 - 1980 shl 9) or (1 shl 5) or 1 // 2020-01-01

    private val crcTable = IntArray(256) { n ->
        var c = n
        repeat(8) { c = if (c and 1 != 0) (c ushr 1) xor 0xEDB88320.toInt() else c ushr 1 }
        c
    }

    fun crc32(data: ByteArray): Int {
        var crc = -1
        for (b in data) crc = crcTable[(crc xor b.toInt()) and 0xFF] xor (crc ushr 8)
        return crc.inv()
    }

    private class Buffer {
        private var data = ByteArray(4096)
        var size = 0
            private set

        private fun ensure(extra: Int) {
            if (size + extra > data.size) data = data.copyOf(maxOf(data.size * 2, size + extra))
        }

        fun int16(v: Int) {
            ensure(2); data[size++] = v.toByte(); data[size++] = (v shr 8).toByte()
        }

        fun int32(v: Int) {
            ensure(4); repeat(4) { data[size++] = (v shr (8 * it)).toByte() }
        }

        fun bytes(b: ByteArray) {
            ensure(b.size); b.copyInto(data, size); size += b.size
        }

        fun toByteArray() = data.copyOf(size)
    }
}
