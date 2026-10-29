package cz.poqet.walletka.data

import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readIntLe
import kotlinx.io.readShortLe
import kotlinx.io.writeIntLe
import kotlinx.io.writeShortLe

/**
 * Minimal ZIP archives with stored (uncompressed) entries, written and read one entry at a time. Enough for
 * backups, whose content is JSON and already-compressed images (the same approach as the pass service's `.pkpass`
 * writer).
 */
internal object Zip {

    class Writer(private val sink: Sink) {
        private val central = Buffer()
        private var offset = 0L
        private var count = 0

        fun entry(name: String, data: ByteArray) {
            val nameBytes = name.encodeToByteArray()
            val crc = crc32(data)
            sink.writeIntLe(LOCAL_HEADER); sink.writeShortLe(20); sink.writeShortLe(UTF8_FLAG); sink.writeShortLe(0)
            sink.writeShortLe(DOS_TIME); sink.writeShortLe(DOS_DATE); sink.writeIntLe(crc); sink.writeIntLe(data.size)
            sink.writeIntLe(data.size); sink.writeShortLe(nameBytes.size.toShort()); sink.writeShortLe(0)
            sink.write(nameBytes); sink.write(data)

            central.writeIntLe(CENTRAL_HEADER); central.writeShortLe(20); central.writeShortLe(20)
            central.writeShortLe(UTF8_FLAG); central.writeShortLe(0); central.writeShortLe(DOS_TIME)
            central.writeShortLe(DOS_DATE); central.writeIntLe(crc); central.writeIntLe(data.size); central.writeIntLe(data.size)
            central.writeShortLe(nameBytes.size.toShort()); central.writeShortLe(0); central.writeShortLe(0)
            central.writeShortLe(0); central.writeShortLe(0); central.writeIntLe(0); central.writeIntLe(offset.toInt())
            central.write(nameBytes)
            offset += 30 + nameBytes.size + data.size
            count++
        }

        /** Writes the central directory; the sink stays open. */
        fun finish() {
            val size = central.size
            sink.transferFrom(central)
            sink.writeIntLe(END_OF_CENTRAL_DIRECTORY); sink.writeShortLe(0); sink.writeShortLe(0)
            sink.writeShortLe(count.toShort()); sink.writeShortLe(count.toShort()); sink.writeIntLe(size.toInt())
            sink.writeIntLe(offset.toInt()); sink.writeShortLe(0)
        }
    }

    /**
     * Reads the entries of an archive from [source] in order, until the central directory, calling [onEntry] for each.
     * Fails with [ZipException] on anything but stored entries with their sizes in the local header.
     */
    fun read(source: Source, onEntry: (name: String, data: ByteArray) -> Unit) {
        while (true) {
            val signature = try {
                source.readIntLe()
            } catch (_: EOFException) {
                throw ZipException("Unexpected end of archive")
            }
            if (signature == CENTRAL_HEADER || signature == END_OF_CENTRAL_DIRECTORY) return
            if (signature != LOCAL_HEADER) throw ZipException("Not a ZIP archive")
            source.readShortLe() // version
            val flags = source.readShortLe().toInt()
            val method = source.readShortLe().toInt()
            source.readIntLe() // time, date
            val crc = source.readIntLe()
            val compressedSize = source.readIntLe()
            source.readIntLe() // uncompressed size
            val nameLength = source.readShortLe().toInt() and 0xFFFF
            val extraLength = source.readShortLe().toInt() and 0xFFFF
            if (method != 0 || flags and DATA_DESCRIPTOR_FLAG != 0) throw ZipException("Unsupported ZIP entry")
            if (compressedSize < 0) throw ZipException("Entry too large")
            val name = source.readByteArray(nameLength).decodeToString()
            source.skip(extraLength.toLong())
            val data = source.readByteArray(compressedSize)
            if (crc32(data) != crc) throw ZipException("Corrupt entry $name")
            onEntry(name, data)
        }
    }

    private const val LOCAL_HEADER = 0x04034b50
    private const val CENTRAL_HEADER = 0x02014b50
    private const val END_OF_CENTRAL_DIRECTORY = 0x06054b50
    private const val UTF8_FLAG: Short = 0x0800
    private const val DATA_DESCRIPTOR_FLAG = 0x0008
    private const val DOS_TIME: Short = 0 // 00:00:00
    private const val DOS_DATE: Short = ((2020 - 1980 shl 9) or (1 shl 5) or 1).toShort() // 2020-01-01

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
}

internal class ZipException(message: String) : Exception(message)
