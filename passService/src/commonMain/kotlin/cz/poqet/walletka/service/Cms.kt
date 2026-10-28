package cz.poqet.walletka.service

/** Platform crypto primitives (WebCrypto in the Worker, `java.security` in tests). */
interface PassCrypto {
    suspend fun sha1(data: ByteArray): ByteArray

    suspend fun sha256(data: ByteArray): ByteArray

    /** RSASSA-PKCS1-v1_5 with SHA-256 using the pass certificate's private key. */
    suspend fun signRsaSha256(data: ByteArray): ByteArray
}

/**
 * Produces the detached CMS (PKCS#7) `SignedData` that Apple Wallet expects as a pass's `signature` file:
 * SHA-256, signed attributes (content type, signing time, message digest), and the signer and WWDR intermediate
 * certificates embedded.
 */
internal class Cms(
    private val crypto: PassCrypto,
    private val signerCertificate: ByteArray,
    private val intermediateCertificates: List<ByteArray>,
) {

    suspend fun signDetached(content: ByteArray, signingTimeEpochSeconds: Long): ByteArray {
        val sha256Algorithm = Der.sequence(Der.oid(SHA256), Der.nullValue())
        val signedAttributes = listOf(
            attribute(CONTENT_TYPE, Der.oid(DATA)),
            attribute(SIGNING_TIME, Der.utcTime(signingTimeEpochSeconds)),
            attribute(MESSAGE_DIGEST, Der.octetString(crypto.sha256(content))),
        )
        // The signature covers the attributes encoded as a plain SET; they are embedded with an IMPLICIT [0] tag.
        val attributesSet = Der.setOf(*signedAttributes.toTypedArray())
        val signature = crypto.signRsaSha256(attributesSet)

        val signerInfo = Der.sequence(
            Der.integer(1),
            issuerAndSerialNumber(signerCertificate),
            sha256Algorithm,
            Der.context(0, attributesSet.contentOfTlv()),
            Der.sequence(Der.oid(RSA_ENCRYPTION), Der.nullValue()),
            Der.octetString(signature),
        )
        val signedData = Der.sequence(
            Der.integer(1),
            Der.setOf(sha256Algorithm),
            Der.sequence(Der.oid(DATA)),
            Der.context(0, (listOf(signerCertificate) + intermediateCertificates).reduce(ByteArray::plus)),
            Der.setOf(signerInfo),
        )
        return Der.sequence(Der.oid(SIGNED_DATA), Der.context(0, signedData))
    }

    private fun attribute(type: String, value: ByteArray) = Der.sequence(Der.oid(type), Der.setOf(value))

    companion object {
        const val DATA = "1.2.840.113549.1.7.1"
        const val SIGNED_DATA = "1.2.840.113549.1.7.2"
        const val CONTENT_TYPE = "1.2.840.113549.1.9.3"
        const val MESSAGE_DIGEST = "1.2.840.113549.1.9.4"
        const val SIGNING_TIME = "1.2.840.113549.1.9.5"
        const val SHA256 = "2.16.840.1.101.3.4.2.1"
        const val RSA_ENCRYPTION = "1.2.840.113549.1.1.1"

        /** `IssuerAndSerialNumber` taken verbatim from the certificate's `TBSCertificate`. */
        fun issuerAndSerialNumber(certificate: ByteArray): ByteArray {
            val tbs = Der.read(certificate).children().first().children()
            val fields = if (tbs.first().tag == 0xA0) tbs.drop(1) else tbs // optional [0] version
            val serial = fields[0]
            val issuer = fields[2]
            require(serial.tag == Der.INTEGER && issuer.tag == Der.SEQUENCE) { "Not an X.509 certificate" }
            return Der.sequence(issuer.encoded, serial.encoded)
        }

        private fun ByteArray.contentOfTlv(): ByteArray = Der.read(this).content
    }
}
