package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.CodeFormat.*
import cz.poqet.walletka.platform.WalletPlatform

/**
 * How a detected code is rendered by a wallet. Wallets support only some symbologies; others are re-encoded
 * with the same payload (2D codes as QR, linear codes as Code 128), which most scanners still read.
 */
internal data class WalletSymbology(
    /** Identifier used in the wallet's pass format, e.g. `PKBarcodeFormatQR` or `QR_CODE`. */
    val id: String,
    /** The format the wallet will actually display. */
    val displayedAs: CodeFormat,
) {
    fun isExactFor(format: CodeFormat): Boolean = displayedAs == format

    companion object {
        fun resolve(platform: WalletPlatform, format: CodeFormat, value: String): WalletSymbology = when (platform) {
            WalletPlatform.APPLE_WALLET -> when (format) {
                QR -> WalletSymbology("PKBarcodeFormatQR", QR)
                AZTEC -> WalletSymbology("PKBarcodeFormatAztec", AZTEC)
                PDF417 -> WalletSymbology("PKBarcodeFormatPDF417", PDF417)
                DATA_MATRIX -> WalletSymbology("PKBarcodeFormatQR", QR)
                else -> WalletSymbology("PKBarcodeFormatCode128", CODE_128)
            }

            WalletPlatform.GOOGLE_WALLET -> when (format) {
                QR -> WalletSymbology("QR_CODE", QR)
                AZTEC -> WalletSymbology("AZTEC", AZTEC)
                PDF417 -> WalletSymbology("PDF_417", PDF417)
                DATA_MATRIX -> WalletSymbology("DATA_MATRIX", DATA_MATRIX)
                CODE_128 -> WalletSymbology("CODE_128", CODE_128)
                CODE_39 -> WalletSymbology("CODE_39", CODE_39)
                CODABAR -> WalletSymbology("CODABAR", CODABAR)
                EAN_13 -> WalletSymbology("EAN_13", EAN_13)
                EAN_8 -> WalletSymbology("EAN_8", EAN_8)
                UPC_A -> WalletSymbology("UPC_A", UPC_A)
                ITF -> if (value.length == 14 && value.all { it.isDigit() }) WalletSymbology("ITF_14", ITF)
                else WalletSymbology("CODE_128", CODE_128)
                CODE_93, UPC_E -> WalletSymbology("CODE_128", CODE_128)
            }
        }
    }
}
