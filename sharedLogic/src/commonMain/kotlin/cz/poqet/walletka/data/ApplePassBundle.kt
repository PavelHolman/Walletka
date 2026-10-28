package cz.poqet.walletka.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64

/**
 * Request sent to the pass signing service ([WalletConfig.applePassSigningUrl]).
 *
 * Contract: `POST` with `Content-Type: application/json` and body `{"files": {"pass.json": "<base64>", ...}}`.
 * The service adds `manifest.json` and `signature` (signed with the Pass Type ID certificate), zips the bundle
 * and responds with `application/vnd.apple.pkpass` bytes.
 */
internal object ApplePassBundle {

    fun signingRequest(files: Map<String, ByteArray>): String {
        val body = buildJsonObject {
            putJsonObject("files") {
                files.forEach { (name, bytes) -> put(name, Base64.encode(bytes)) }
            }
        }
        return Json.encodeToString(JsonObject.serializer(), body)
    }
}
