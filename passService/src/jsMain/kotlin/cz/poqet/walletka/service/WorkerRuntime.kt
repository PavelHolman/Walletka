package cz.poqet.walletka.service

import kotlinx.coroutines.await
import kotlin.js.Promise

// Thin typed views of the Workers runtime (Fetch API, WebCrypto, KV); everything else stays `dynamic`.

internal external class Response(body: dynamic = definedExternally, init: dynamic = definedExternally)

internal external interface WorkerRequest {
    val method: String
    val url: String
    val headers: dynamic
    fun text(): Promise<String>
}

internal external interface WorkerEnv {
    val BANNERS: dynamic
    val PASS_TYPE_IDENTIFIER: String?
    val TEAM_IDENTIFIER: String?
    val PASS_CERTIFICATE: String?
    val PASS_PRIVATE_KEY: String?
    val WWDR_CERTIFICATE: String?
    val API_KEY: String?
}

internal fun jsObject(build: dynamic.() -> Unit): dynamic {
    val obj: dynamic = js("({})")
    build(obj)
    return obj
}

internal fun ArrayBufferLike.toByteArray(): ByteArray = int8Array(this).unsafeCast<ByteArray>()

private fun int8Array(buffer: ArrayBufferLike): dynamic = js("new Int8Array(buffer)")

internal external interface ArrayBufferLike

private val subtle: dynamic get() = js("crypto.subtle")

/** WebCrypto: native digests and RSA signing, cheap enough for the Workers free plan's CPU budget. */
internal class WebPassCrypto(private val privateKeyPkcs8: ByteArray) : PassCrypto {

    private var key: dynamic = null

    override suspend fun sha1(data: ByteArray): ByteArray = digest("SHA-1", data)

    override suspend fun sha256(data: ByteArray): ByteArray = digest("SHA-256", data)

    override suspend fun signRsaSha256(data: ByteArray): ByteArray {
        if (key == null) {
            key = subtle.importKey(
                "pkcs8",
                privateKeyPkcs8,
                jsObject { name = "RSASSA-PKCS1-v1_5"; hash = "SHA-256" },
                false,
                arrayOf("sign"),
            ).unsafeCast<Promise<dynamic>>().await()
        }
        val signature = subtle.sign("RSASSA-PKCS1-v1_5", key, data).unsafeCast<Promise<ArrayBufferLike>>().await()
        return signature.toByteArray()
    }

    private suspend fun digest(algorithm: String, data: ByteArray): ByteArray =
        subtle.digest(algorithm, data).unsafeCast<Promise<ArrayBufferLike>>().await().toByteArray()
}

/**
 * Banners in the Workers KV namespace bound as `BANNERS`. KV's free tier has hard limits (1 GB, 1,000 writes a day):
 * past them writes fail instead of being billed.
 */
internal class KvBannerStore(private val namespace: dynamic) : BannerStore {
    override suspend fun put(key: String, png: ByteArray) {
        namespace.put(key, png).unsafeCast<Promise<Unit>>().await()
    }

    companion object {
        const val IMMUTABLE = "public, max-age=31536000, immutable"
    }
}
