package cz.poqet.walletka.service

import kotlin.io.encoding.Base64

internal object Pem {

    class Block(val label: String, val der: ByteArray)

    private val blockRegex = Regex("-----BEGIN ([A-Z0-9 ]+)-----([\\s\\S]*?)-----END \\1-----")

    fun decode(pem: String): List<Block> = blockRegex.findAll(pem).map { match ->
        Block(match.groupValues[1], Base64.decode(match.groupValues[2].filterNot { it.isWhitespace() }))
    }.toList()

    /** DER certificates in [pem], in order. */
    fun certificates(pem: String): List<ByteArray> = decode(pem).filter { it.label == "CERTIFICATE" }.map { it.der }

    /** PKCS#8 private key; a PKCS#1 `RSA PRIVATE KEY` is wrapped into PKCS#8. */
    fun privateKeyPkcs8(pem: String): ByteArray {
        val block = decode(pem).firstOrNull { it.label == "PRIVATE KEY" || it.label == "RSA PRIVATE KEY" }
            ?: throw ServiceException("Signing key is not a PEM private key")
        if (block.label == "PRIVATE KEY") return block.der
        return Der.sequence(
            Der.integer(0),
            Der.sequence(Der.oid(Cms.RSA_ENCRYPTION), Der.nullValue()),
            Der.octetString(block.der),
        )
    }
}
