package cz.poqet.walletka.domain


/** Symbology of a code detected in a photo. */
enum class CodeFormat(val displayName: String, val isTwoDimensional: Boolean) {
    QR("QR code", true),
    AZTEC("Aztec", true),
    PDF417("PDF417", true),
    DATA_MATRIX("Data Matrix", true),
    CODE_128("Code 128", false),
    CODE_39("Code 39", false),
    CODE_93("Code 93", false),
    CODABAR("Codabar", false),
    EAN_13("EAN-13", false),
    EAN_8("EAN-8", false),
    UPC_A("UPC-A", false),
    UPC_E("UPC-E", false),
    ITF("ITF", false),
}
