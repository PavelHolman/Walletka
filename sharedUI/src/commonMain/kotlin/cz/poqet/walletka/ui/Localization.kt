package cz.poqet.walletka.ui

import androidx.compose.runtime.Composable
import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.ui.resources.Res
import cz.poqet.walletka.ui.resources.code_format_qr
import cz.poqet.walletka.ui.resources.colour_name_black
import cz.poqet.walletka.ui.resources.colour_name_blue
import cz.poqet.walletka.ui.resources.colour_name_brown
import cz.poqet.walletka.ui.resources.colour_name_dark
import cz.poqet.walletka.ui.resources.colour_name_green
import cz.poqet.walletka.ui.resources.colour_name_grey
import cz.poqet.walletka.ui.resources.colour_name_light
import cz.poqet.walletka.ui.resources.colour_name_orange
import cz.poqet.walletka.ui.resources.colour_name_pink
import cz.poqet.walletka.ui.resources.colour_name_purple
import cz.poqet.walletka.ui.resources.colour_name_red
import cz.poqet.walletka.ui.resources.colour_name_teal
import cz.poqet.walletka.ui.resources.colour_name_white
import cz.poqet.walletka.ui.resources.colour_name_yellow
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.min

/**
 * The symbology's name as shown in the UI. Only "QR code" is a common noun to translate; the others (EAN-13, Code 128,
 * Aztec…) are proper names, kept as [CodeFormat.displayName].
 */
@Composable
internal fun CodeFormat.localizedName(): String =
    if (this == CodeFormat.QR) stringResource(Res.string.code_format_qr) else displayName

/** A rough spoken name of the colour ("dark blue"), for screen readers; the hex value says the exact one. */
@Composable
internal fun CardColor.spokenName(): String {
    val (base, shade) = describe()
    val name = stringResource(base)
    val full = when (shade) {
        Shade.DARK -> stringResource(Res.string.colour_name_dark, name)
        Shade.LIGHT -> stringResource(Res.string.colour_name_light, name)
        Shade.NONE -> name
    }
    return full.replaceFirstChar { it.uppercaseChar() }
}

/** `#1A2B3C` as upper case hex, which screen readers spell out. */
internal val CardColor.displayHex: String get() = hex.uppercase()

private enum class Shade { DARK, LIGHT, NONE }

private fun CardColor.describe(): Pair<StringResource, Shade> {
    val r = red / 255f
    val g = green / 255f
    val b = blue / 255f
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val delta = hi - lo
    val lightness = (hi + lo) / 2
    val saturation = if (delta == 0f) 0f else delta / (1 - kotlin.math.abs(2 * lightness - 1))
    if (saturation < 0.15f || delta < 0.06f) {
        return when {
            lightness < 0.12f -> Res.string.colour_name_black to Shade.NONE
            lightness > 0.92f -> Res.string.colour_name_white to Shade.NONE
            lightness < 0.35f -> Res.string.colour_name_grey to Shade.DARK
            lightness > 0.7f -> Res.string.colour_name_grey to Shade.LIGHT
            else -> Res.string.colour_name_grey to Shade.NONE
        }
    }
    val hue = when (hi) {
        r -> 60f * (((g - b) / delta).mod(6f))
        g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    val base = when {
        hue < 15f || hue >= 345f -> Res.string.colour_name_red
        hue < 45f -> if (lightness < 0.4f) Res.string.colour_name_brown else Res.string.colour_name_orange
        hue < 70f -> Res.string.colour_name_yellow
        hue < 160f -> Res.string.colour_name_green
        hue < 195f -> Res.string.colour_name_teal
        hue < 255f -> Res.string.colour_name_blue
        hue < 290f -> Res.string.colour_name_purple
        else -> Res.string.colour_name_pink
    }
    val shade = when {
        base == Res.string.colour_name_brown -> Shade.NONE
        lightness < 0.3f -> Shade.DARK
        lightness > 0.75f -> Shade.LIGHT
        else -> Shade.NONE
    }
    return base to shade
}
