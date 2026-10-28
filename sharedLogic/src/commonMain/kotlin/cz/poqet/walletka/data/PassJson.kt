package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.platform.WalletPlatform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Builds the pass payloads for both wallets from a [CardDraft]. */
internal object PassJson {

    private val json = Json { prettyPrint = false }

    /**
     * Apple Wallet `pass.json` for a store card. The caller bundles it with `icon.png` and `strip.png`
     * and has it signed.
     */
    fun apple(card: CardDraft, config: WalletConfig, serialNumber: String): String {
        val symbology = WalletSymbology.resolve(WalletPlatform.APPLE_WALLET, card.code.format, card.code.value)
        val pass = buildJsonObject {
            put("formatVersion", 1)
            put("passTypeIdentifier", config.applePassTypeIdentifier)
            put("teamIdentifier", config.appleTeamIdentifier)
            put("serialNumber", serialNumber)
            put("organizationName", config.organizationName)
            put("description", card.name)
            put("logoText", card.name)
            put("backgroundColor", card.backgroundColor.cssRgb)
            put("foregroundColor", card.foregroundColor.cssRgb)
            put("labelColor", card.foregroundColor.cssRgb)
            putJsonArray("barcodes") {
                addJsonObject {
                    put("format", symbology.id)
                    put("message", card.code.value)
                    put("messageEncoding", "iso-8859-1")
                    if (!card.code.format.isTwoDimensional) put("altText", card.code.value)
                }
            }
            putJsonObject("storeCard") {
                putJsonArray("primaryFields") {}
                putJsonArray("secondaryFields") {
                    if (card.code.format.isTwoDimensional && card.code.value.length <= 32) {
                        addJsonObject {
                            put("key", "code")
                            put("label", card.code.format.displayName)
                            put("value", card.code.value)
                        }
                    }
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), pass)
    }

    /**
     * Google Wallet "save to wallet" payload with a generic pass class and object, accepted unsigned by the
     * Android `PayClient.savePasses` API for allow-listed apps.
     */
    fun google(card: CardDraft, config: WalletConfig, objectId: String): String {
        val issuerId = requireNotNull(config.googleIssuerId)
        val symbology = WalletSymbology.resolve(WalletPlatform.GOOGLE_WALLET, card.code.format, card.code.value)
        val classId = "$issuerId.walletka_generic"
        val payload = buildJsonObject {
            put("iss", config.googleIssuerEmail)
            put("aud", "google")
            put("typ", "savetowallet")
            putJsonArray("origins") {}
            putJsonObject("payload") {
                putJsonArray("genericClasses") {
                    addJsonObject { put("id", classId) }
                }
                putJsonArray("genericObjects") {
                    addJsonObject {
                        put("id", "$issuerId.$objectId")
                        put("classId", classId)
                        put("genericType", "GENERIC_TYPE_UNSPECIFIED")
                        put("state", "ACTIVE")
                        put("hexBackgroundColor", card.backgroundColor.hex)
                        putJsonObject("cardTitle") { localized(config.organizationName) }
                        putJsonObject("header") { localized(card.name) }
                        putJsonObject("barcode") {
                            put("type", symbology.id)
                            put("value", card.code.value)
                            put("alternateText", card.code.value)
                        }
                    }
                }
            }
        }
        return json.encodeToString(JsonObject.serializer(), payload)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.localized(value: String) {
        putJsonObject("defaultValue") {
            put("language", "en")
            put("value", value)
        }
    }

}
