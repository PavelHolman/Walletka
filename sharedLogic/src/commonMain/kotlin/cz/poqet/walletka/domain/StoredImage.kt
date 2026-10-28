package cz.poqet.walletka.domain


/** An image copied into the app's own storage, so it outlives the share/paste source it came from. */
data class StoredImage(val path: String)

/** Decoded, downscaled pixels of an image in ARGB_8888 packed ints, row by row. */
internal class ImagePixels(val width: Int, val height: Int, val argb: IntArray) {
    init {
        require(argb.size == width * height) { "Expected ${width * height} pixels, got ${argb.size}" }
    }
}
