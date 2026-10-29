package cz.poqet.walletka.service

import kotlin.js.Date

/** One issuer per isolate, so the OAuth token and the imported key are reused across requests. */
private var cachedIssuer: Pair<WorkerEnv, GoogleWalletIssuer?>? = null

/**
 * The Google Wallet issuer configured by `GOOGLE_SERVICE_ACCOUNT_JSON` and `GOOGLE_ISSUER_ID`, or `null` when they
 * are missing or invalid (the app then saves passes on the device as before).
 */
internal fun googleIssuerFor(env: WorkerEnv): GoogleWalletIssuer? {
    cachedIssuer?.takeIf { it.first === env }?.let { return it.second }
    val issuerId = env.GOOGLE_ISSUER_ID?.trim().orEmpty()
    val accountJson = env.GOOGLE_SERVICE_ACCOUNT_JSON.orEmpty()
    val issuer = if (issuerId.isEmpty() || accountJson.isBlank()) {
        null
    } else {
        val account = GoogleServiceAccount.parse(accountJson)
        val key = account?.let { runCatching { Pem.privateKeyPkcs8(it.privateKeyPem) }.getOrNull() }
        when {
            !GoogleWalletIssuer.isValidIssuerId(issuerId) -> null.also { console.error("GOOGLE_ISSUER_ID must be numeric") }
            account == null || key == null -> null.also { console.error("GOOGLE_SERVICE_ACCOUNT_JSON is not a service account key file") }
            else -> GoogleWalletIssuer(issuerId, account, WebPassCrypto(key), FetchTransport, now = { (Date.now() / 1000).toLong() })
        }
    }
    cachedIssuer = env to issuer
    return issuer
}

/** Adds the Google Wallet mutations to the GraphQL [root]; without an [issuer] they fail with a clear message. */
internal fun addGoogleResolvers(root: dynamic, issuer: GoogleWalletIssuer?, baseUrl: String) {
    fun configured() = issuer ?: throw ServiceException("Google Wallet is not configured on the pass service")

    root.saveGooglePass = { args: dynamic ->
        resolve {
            val link = configured().save(googlePass(args.pass), baseUrl)
            jsObject { jwt = link.jwt; saveUrl = link.saveUrl; objectId = link.objectId }
        }
    }
    root.updateGooglePass = { args: dynamic ->
        resolve {
            val found = configured().update(googlePass(args.pass), baseUrl)
            jsObject { this.found = found }
        }
    }
    root.deleteGooglePass = { args: dynamic ->
        resolve { configured().expire(args.id as String) }
    }
}

private fun googlePass(input: dynamic) = GooglePass(
    id = input.id as String,
    cardTitle = input.cardTitle as String,
    header = input.header as String,
    hexBackgroundColor = input.hexBackgroundColor as String,
    barcodeType = input.barcodeType as String,
    barcodeValue = input.barcodeValue as String,
    heroImageUrl = input.heroImageUrl as String?,
)
