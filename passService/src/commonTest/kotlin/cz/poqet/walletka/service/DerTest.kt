package cz.poqet.walletka.service

import kotlin.test.Test
import kotlin.test.assertEquals

class DerTest {

    private fun ByteArray.hex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    @Test
    fun encodesOids() {
        assertEquals("06092a864886f70d010702", Der.oid(Cms.SIGNED_DATA).hex())
        assertEquals("0609608648016503040201", Der.oid(Cms.SHA256).hex())
    }

    @Test
    fun encodesUtcTime() {
        // 2026-10-28T10:20:30Z
        assertEquals("261028102030Z", Der.read(Der.utcTime(1_793_182_830)).content.decodeToString())
    }

    @Test
    fun crc32MatchesReference() {
        assertEquals(0xCBF43926.toInt(), Zip.crc32("123456789".encodeToByteArray()))
    }
}
