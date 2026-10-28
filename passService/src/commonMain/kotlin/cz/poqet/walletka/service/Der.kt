package cz.poqet.walletka.service

/** Minimal ASN.1 DER encoding and decoding, enough for X.509 certificates and CMS SignedData. */
internal object Der {

    const val INTEGER = 0x02
    const val OCTET_STRING = 0x04
    const val NULL = 0x05
    const val OID = 0x06
    const val UTC_TIME = 0x17
    const val SEQUENCE = 0x30
    const val SET = 0x31

    fun tlv(tag: Int, content: ByteArray): ByteArray = byteArrayOf(tag.toByte()) + length(content.size) + content

    fun sequence(vararg items: ByteArray) = tlv(SEQUENCE, concat(items.toList()))

    /** `SET OF`: DER requires the encoded elements in ascending byte order. */
    fun setOf(vararg items: ByteArray) = tlv(SET, concat(items.sortedWith(::compareUnsigned)))

    /** `[n]` context-specific constructed tag (EXPLICIT wrapper, or IMPLICIT SET/SEQUENCE). */
    fun context(n: Int, content: ByteArray) = tlv(0xA0 or n, content)

    fun integer(value: Int): ByteArray {
        require(value in 0..0x7F) { "Only small non-negative integers are needed" }
        return tlv(INTEGER, byteArrayOf(value.toByte()))
    }

    fun octetString(bytes: ByteArray) = tlv(OCTET_STRING, bytes)

    fun nullValue() = tlv(NULL, ByteArray(0))

    fun oid(dotted: String): ByteArray {
        val arcs = dotted.split('.').map { it.toLong() }
        val body = mutableListOf((arcs[0] * 40 + arcs[1]).toInt().toByte())
        for (arc in arcs.drop(2)) {
            val chunk = mutableListOf<Byte>()
            var v = arc
            do {
                chunk.add(0, (v and 0x7F).toByte())
                v = v shr 7
            } while (v > 0)
            for (i in 0 until chunk.size - 1) chunk[i] = (chunk[i].toInt() or 0x80).toByte()
            body += chunk
        }
        return tlv(OID, body.toByteArray())
    }

    /** `YYMMDDHHMMSSZ`. */
    fun utcTime(epochSeconds: Long): ByteArray {
        val days = epochSeconds.floorDiv(86_400)
        val secondOfDay = epochSeconds.mod(86_400L)
        val (year, month, day) = civilFromDays(days)
        val text = listOf(year % 100, month, day, (secondOfDay / 3600).toInt(), (secondOfDay / 60 % 60).toInt(), (secondOfDay % 60).toInt())
            .joinToString("") { it.toString().padStart(2, '0') } + "Z"
        return tlv(UTC_TIME, text.encodeToByteArray())
    }

    /** Parses one TLV element starting at [offset]. */
    fun read(bytes: ByteArray, offset: Int = 0): Element {
        val tag = bytes[offset].toInt() and 0xFF
        var pos = offset + 1
        val first = bytes[pos++].toInt() and 0xFF
        val length = if (first < 0x80) {
            first
        } else {
            val count = first and 0x7F
            require(count in 1..4) { "Unsupported DER length" }
            var value = 0
            repeat(count) { value = (value shl 8) or (bytes[pos++].toInt() and 0xFF) }
            value
        }
        require(pos + length <= bytes.size) { "Truncated DER" }
        return Element(bytes, tag, offset, pos, length)
    }

    class Element(private val source: ByteArray, val tag: Int, val start: Int, val contentStart: Int, val length: Int) {
        val end: Int get() = contentStart + length

        /** The complete TLV encoding. */
        val encoded: ByteArray get() = source.copyOfRange(start, end)

        val content: ByteArray get() = source.copyOfRange(contentStart, end)

        fun children(): List<Element> = buildList {
            var pos = contentStart
            while (pos < end) {
                val child = read(source, pos)
                add(child)
                pos = child.end
            }
        }
    }

    private fun length(n: Int): ByteArray = when {
        n < 0x80 -> byteArrayOf(n.toByte())
        n <= 0xFF -> byteArrayOf(0x81.toByte(), n.toByte())
        n <= 0xFFFF -> byteArrayOf(0x82.toByte(), (n shr 8).toByte(), n.toByte())
        else -> byteArrayOf(0x83.toByte(), (n shr 16).toByte(), (n shr 8).toByte(), n.toByte())
    }

    private fun concat(items: List<ByteArray>): ByteArray {
        val out = ByteArray(items.sumOf { it.size })
        var pos = 0
        for (item in items) {
            item.copyInto(out, pos)
            pos += item.size
        }
        return out
    }

    private fun compareUnsigned(a: ByteArray, b: ByteArray): Int {
        for (i in 0 until minOf(a.size, b.size)) {
            val diff = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (diff != 0) return diff
        }
        return a.size - b.size
    }

    /** Howard Hinnant's days-from-civil inverse. */
    private fun civilFromDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
        val z = daysSinceEpoch + 719_468
        val era = z.floorDiv(146_097)
        val doe = z - era * 146_097
        val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val day = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val month = (if (mp < 10) mp + 3 else mp - 9).toInt()
        val year = (yoe + era * 400 + if (month <= 2) 1 else 0).toInt()
        return Triple(year, month, day)
    }
}
