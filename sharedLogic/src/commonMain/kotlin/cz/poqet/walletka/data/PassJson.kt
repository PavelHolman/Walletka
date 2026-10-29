package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CardDetails
import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.platform.WalletPlatform
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArrayBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Builds the pass payloads for both wallets from a [CardDraft]. */
internal object PassJson {

    private val json = Json { prettyPrint = false }

    /**
     * Apple Wallet `pass.json` for a store card. The caller bundles it with `icon.png`, `strip.png` and, when
     * [CardDraft.logo] is set, `logo.png`, and has it signed; the pass service stamps `passTypeIdentifier` and
     * `teamIdentifier`.
     */
    fun apple(card: CardDraft, config: WalletConfig, serialNumber: String): String {
        val symbology = WalletSymbology.resolve(WalletPlatform.APPLE_WALLET, card.code.format, card.code.value)
        val details = card.details.trimmed()
        val pass = buildJsonObject {
            put("formatVersion", 1)
            put("serialNumber", serialNumber)
            put("organizationName", config.organizationName)
            put("description", card.name)
            // A logo cut from the card already shows the brand; Apple advises against repeating it as text.
            if (card.logo == null) put("logoText", card.name)
            put("backgroundColor", card.backgroundColor.cssRgb)
            put("foregroundColor", card.foregroundColor.cssRgb)
            put("labelColor", card.foregroundColor.cssRgb)
            details.expiryDate.takeIf { it.isNotEmpty() }?.let { put("expirationDate", "${it}T23:59:59Z") }
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
                    if (details.memberName.isNotEmpty()) appleField("memberName", "MEMBER", details.memberName)
                    if (details.memberNumber.isNotEmpty()) {
                        appleField("memberNumber", "NUMBER", details.memberNumber)
                    } else if (card.code.format.isTwoDimensional && card.code.value.length <= 32) {
                        appleField("code", card.code.format.displayName, card.code.value)
                    }
                }
                putJsonArray("auxiliaryFields") {
                    if (details.expiryDate.isNotEmpty()) appleDateField("expiry", "EXPIRES", details.expiryDate)
                }
                putJsonArray("backFields") { appleBackFields(details) }
            }
        }
        return json.encodeToString(JsonObject.serializer(), pass)
    }

    /**
     * Google Wallet "save to wallet" payload with a generic pass class and object, accepted unsigned by the
     * Android `PayClient.savePasses` API for allow-listed apps.
     */
    fun google(
        card: CardDraft,
        config: WalletConfig,
        objectId: String,
        heroImageUrl: String? = null,
        logoUrl: String? = null,
    ): String {
        val issuerId = requireNotNull(config.googleIssuerId)
        val symbology = WalletSymbology.resolve(WalletPlatform.GOOGLE_WALLET, card.code.format, card.code.value)
        val details = card.details.trimmed()
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
                        if (heroImageUrl != null) {
                            putJsonObject("heroImage") {
                                putJsonObject("sourceUri") { put("uri", heroImageUrl) }
                                putJsonObject("contentDescription") { localized(card.name) }
                            }
                        }
                        if (logoUrl != null) {
                            putJsonObject("logo") {
                                putJsonObject("sourceUri") { put("uri", logoUrl) }
                                putJsonObject("contentDescription") { localized(card.name) }
                            }
                        }
                        if (details.memberName.isNotEmpty()) putJsonObject("subheader") { localized(details.memberName) }
                        if (details.expiryDate.isNotEmpty()) {
                            putJsonObject("validTimeInterval") {
                                putJsonObject("end") { put("date", "${details.expiryDate}T23:59:59Z") }
                            }
                        }
                        val modules = googleTextModules(details)
                        if (modules.isNotEmpty()) {
                            putJsonArray("textModulesData") {
                                modules.forEach { (id, header, body) ->
                                    addJsonObject {
                                        put("id", id)
                                        put("header", header)
                                        put("body", body)
                                    }
                                }
                            }
                        }
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

    /**
     * `GooglePassInput` for the pass service, which builds and signs the Google Wallet object itself. [CardDraft.walletId]
     * becomes the object ID, so later edits update the same pass. Carries the same logo and details as [google].
     */
    fun googleServiceInput(
        card: CardDraft,
        config: WalletConfig,
        heroImageUrl: String? = null,
        logoUrl: String? = null,
    ): JsonObject {
        val symbology = WalletSymbology.resolve(WalletPlatform.GOOGLE_WALLET, card.code.format, card.code.value)
        val details = card.details.trimmed()
        return buildJsonObject {
            put("id", card.walletId)
            put("cardTitle", config.organizationName)
            put("header", card.name)
            put("hexBackgroundColor", card.backgroundColor.hex)
            put("barcodeType", symbology.id)
            put("barcodeValue", card.code.value)
            if (heroImageUrl != null) put("heroImageUrl", heroImageUrl)
            if (logoUrl != null) put("logoUrl", logoUrl)
            if (details.memberName.isNotEmpty()) put("subheader", details.memberName)
            if (details.expiryDate.isNotEmpty()) put("expiryDate", details.expiryDate)
            val modules = googleTextModules(details)
            if (modules.isNotEmpty()) {
                putJsonArray("textModules") {
                    modules.forEach { (id, header, body) ->
                        addJsonObject {
                            put("id", id)
                            put("header", header)
                            put("body", body)
                        }
                    }
                }
            }
        }
    }

    /** Apple back fields: all details, so they can be read in full (front fields are truncated). */
    private fun JsonArrayBuilder.appleBackFields(details: CardDetails) {
        if (details.memberNumber.isNotEmpty()) appleField("memberNumberBack", "Card number", details.memberNumber)
        if (details.memberName.isNotEmpty()) appleField("memberNameBack", "Member", details.memberName)
        if (details.expiryDate.isNotEmpty()) appleDateField("expiryBack", "Expires", details.expiryDate)
        if (details.notes.isNotEmpty()) appleField("notes", "Notes", details.notes)
    }

    private fun JsonArrayBuilder.appleField(key: String, label: String, value: String) {
        addJsonObject {
            put("key", key)
            put("label", label)
            put("value", value)
        }
    }

    /** A date shown without a time, on the calendar day given regardless of the device's time zone. */
    private fun JsonArrayBuilder.appleDateField(key: String, label: String, isoDate: String) {
        addJsonObject {
            put("key", key)
            put("label", label)
            put("value", "${isoDate}T00:00:00Z")
            put("dateStyle", "PKDateStyleMedium")
            put("timeStyle", "PKDateStyleNone")
            put("ignoresTimeZone", true)
        }
    }

    /** Google text modules (id, header, body) for the details; the member name is the pass subheader as well. */
    private fun googleTextModules(details: CardDetails): List<Triple<String, String, String>> = buildList {
        if (details.memberNumber.isNotEmpty()) add(Triple("member_number", "Card number", details.memberNumber))
        if (details.memberName.isNotEmpty()) add(Triple("member_name", "Member", details.memberName))
        if (details.expiryDate.isNotEmpty()) add(Triple("expiry", "Expires", details.expiryDate))
        if (details.notes.isNotEmpty()) add(Triple("notes", "Notes", details.notes))
    }

    private fun JsonObjectBuilder.localized(value: String) {
        putJsonObject("defaultValue") {
            put("language", "en")
            put("value", value)
        }
    }
}
