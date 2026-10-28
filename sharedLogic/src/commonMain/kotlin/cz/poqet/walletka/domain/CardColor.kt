package cz.poqet.walletka.domain

import kotlin.math.pow

/** An opaque sRGB colour, each channel in `0..255`. */
data class CardColor(val red: Int, val green: Int, val blue: Int) {

    val hex: String get() = "#" + listOf(red, green, blue).joinToString("") { it.toString(16).padStart(2, '0') }

    /** `rgb(r, g, b)` notation used by Apple Wallet's pass.json. */
    val cssRgb: String get() = "rgb($red, $green, $blue)"

    /** Relative luminance as defined by WCAG 2.x, in `0..1`. */
    val luminance: Double
        get() {
            fun channel(c: Int): Double {
                val s = c / 255.0
                return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
        }

    /** Black or white, whichever reads better on top of this colour. */
    val contentColor: CardColor get() = if (luminance > 0.4) Black else White

    companion object {
        val Black = CardColor(0, 0, 0)
        val White = CardColor(255, 255, 255)
        val Default = CardColor(40, 44, 52)

        fun fromArgb(argb: Int): CardColor =
            CardColor((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)
    }
}
