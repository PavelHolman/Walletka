package cz.poqet.walletka.service

import cz.poqet.walletka.api.WalletkaApi
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.await
import kotlinx.coroutines.promise
import kotlin.io.encoding.Base64
import kotlin.js.Date
import kotlin.js.Promise

private val schema by lazy { buildSchema(WalletkaApi.SCHEMA) }

/** One service per isolate: the imported signing key is reused across requests. */
private var cached: Pair<WorkerEnv, PassService>? = null

private fun serviceFor(env: WorkerEnv): PassService {
    cached?.takeIf { it.first === env }?.let { return it.second }
    val config = PassServiceConfig(
        passTypeIdentifier = env.PASS_TYPE_IDENTIFIER.orEmpty(),
        teamIdentifier = env.TEAM_IDENTIFIER.orEmpty(),
        certificatePem = env.PASS_CERTIFICATE.orEmpty(),
        wwdrCertificatePem = env.WWDR_CERTIFICATE.orEmpty(),
    )
    val crypto = WebPassCrypto(Pem.privateKeyPkcs8(env.PASS_PRIVATE_KEY.orEmpty()))
    return PassService(config, crypto, R2BannerStore(env.BANNERS), now = { (Date.now() / 1000).toLong() })
        .also { cached = env to it }
}

/** Cloudflare Worker `fetch` handler; `worker/src/index.mjs` exports it as the module's default handler. */
@OptIn(DelicateCoroutinesApi::class, ExperimentalJsExport::class)
@JsExport
fun fetch(request: dynamic, env: dynamic, ctx: dynamic): Promise<dynamic> = GlobalScope.promise {
    val req = request.unsafeCast<WorkerRequest>()
    val workerEnv = env.unsafeCast<WorkerEnv>()
    val url = parseUrl(req.url)
    val path = url.pathname as String
    try {
        when {
            req.method == "GET" && path.startsWith(WalletkaApi.BANNERS_PATH) -> serveBanner(workerEnv, path)
            path == WalletkaApi.GRAPHQL_PATH && req.method == "POST" -> {
                if (!isAuthorized(req, workerEnv)) return@promise json(401, """{"errors":[{"message":"Unauthorized"}]}""")
                executeGraphQl(req, workerEnv, baseUrl = url.origin as String)
            }
            req.method == "GET" && path == "/" -> Response("Walletka pass service. POST GraphQL to ${WalletkaApi.GRAPHQL_PATH}.")
            else -> Response("Not found", jsObject { status = 404 })
        }
    } catch (e: Throwable) {
        console.error("Request failed", e)
        json(500, """{"errors":[{"message":"Internal error"}]}""")
    }
}

private suspend fun serveBanner(env: WorkerEnv, path: String): dynamic {
    val key = PassService.bannerKeyForPath(path) ?: return Response("Not found", jsObject { status = 404 })
    val obj = env.BANNERS.get(key).unsafeCast<Promise<dynamic>>().await()
        ?: return Response("Not found", jsObject { status = 404 })
    return Response(
        obj.body,
        jsObject {
            headers = jsObject {
                this["content-type"] = "image/png"
                this["cache-control"] = R2BannerStore.IMMUTABLE
                this["etag"] = obj.httpEtag
            }
        },
    )
}

/** Requires `Authorization: Bearer <API_KEY>` when the `API_KEY` secret is set. */
private fun isAuthorized(request: WorkerRequest, env: WorkerEnv): Boolean {
    val expected = env.API_KEY?.takeIf { it.isNotEmpty() } ?: return true
    val provided = (request.headers.get("authorization") as String?)?.removePrefix("Bearer ")?.trim().orEmpty()
    if (provided.length != expected.length) return false
    var diff = 0
    for (i in expected.indices) diff = diff or (expected[i].code xor provided[i].code)
    return diff == 0
}

private suspend fun executeGraphQl(request: WorkerRequest, env: WorkerEnv, baseUrl: String): dynamic {
    val body = runCatching { JSON.parse<dynamic>(request.text().await()) }.getOrNull()
        ?: return json(400, """{"errors":[{"message":"Body must be JSON"}]}""")
    val service = serviceFor(env)

    val root = jsObject {
        health = { env.PASS_TYPE_IDENTIFIER ?: "unconfigured" }
        signApplePass = { args: dynamic ->
            resolve {
                val files = (args.files as Array<dynamic>).map { file ->
                    (file.name as String) to decodeBase64(file.base64 as String, file.name as String)
                }
                val signed = service.signApplePass(files)
                jsObject { pkpassBase64 = Base64.encode(signed.pkpass); serialNumber = signed.serialNumber }
            }
        }
        uploadBanner = { args: dynamic ->
            resolve {
                val banner = service.uploadBanner(decodeBase64(args.pngBase64 as String, "pngBase64"), baseUrl)
                jsObject { id = banner.id; this.url = banner.url }
            }
        }
    }

    val result = graphql(
        jsObject {
            this.schema = schema
            source = body.query
            variableValues = body.variables
            operationName = body.operationName
            rootValue = root
        },
    ).await()
    return json(200, JSON.stringify(result))
}

/** Runs a resolver, exposing only [ServiceException] messages to clients. */
@OptIn(DelicateCoroutinesApi::class)
private fun resolve(block: suspend () -> dynamic): Promise<dynamic> = GlobalScope.promise {
    try {
        block()
    } catch (e: ServiceException) {
        throw js("Error")(e.message).unsafeCast<Throwable>()
    } catch (e: Throwable) {
        console.error("Resolver failed", e)
        throw js("Error")("Internal error").unsafeCast<Throwable>()
    }
}

private fun decodeBase64(value: String, field: String): ByteArray =
    runCatching { Base64.decode(value) }.getOrElse { throw ServiceException("$field is not valid base64") }

private fun parseUrl(value: String): dynamic = js("new URL(value)")

private fun json(status: Int, body: String): dynamic =
    Response(body, jsObject { this.status = status; headers = jsObject { this["content-type"] = "application/json" } })
