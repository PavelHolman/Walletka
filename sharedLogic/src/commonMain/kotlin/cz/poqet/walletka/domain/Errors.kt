package cz.poqet.walletka.domain

/** Why a photo could not be turned into a card suggestion. */
enum class AnalysisFailure {
    /** The shared/pasted content could not be read or decoded as an image. */
    IMAGE_UNREADABLE,

    /** The image was decoded, but there is no barcode or QR code in it. */
    NO_CODE_FOUND,

    /** A platform detector failed unexpectedly. */
    DETECTION_FAILED,
}

/** Why adding a card to the wallet did not succeed. */
enum class WalletFailure {
    /** The wallet app is missing or not supported on this device. */
    UNAVAILABLE,

    /** Issuer credentials (Google issuer ID / Apple signing service) are not set up. */
    NOT_CONFIGURED,

    /** The user dismissed the system "add pass" sheet. */
    CANCELLED,

    /** The selected code symbology cannot be represented by this wallet. */
    UNSUPPORTED_CODE,

    /** Signing the pass or talking to the wallet failed. */
    FAILED,

    /** An update was requested, but the pass is no longer in the wallet (the user removed it there). */
    NOT_IN_WALLET,
}

internal data class WalletError(val failure: WalletFailure, val detail: String? = null)

internal data class AnalysisError(val failure: AnalysisFailure, val detail: String? = null)
