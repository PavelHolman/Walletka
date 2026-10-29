package cz.poqet.walletka.data

import cz.poqet.walletka.domain.BannerViewport
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.TextLine
import cz.poqet.walletka.platform.WalletPlatform

/**
 * Shape and default placement of the logo cut out of the photo. Apple Wallet shows a logo of at most 160x50 points
 * in the pass header, so the crop has that shape; Google Wallet shows the logo in a circle, so it is square.
 */
internal object LogoRegion {

    fun aspectRatio(platform: WalletPlatform): Float = when (platform) {
        WalletPlatform.APPLE_WALLET -> 160f / 50f
        WalletPlatform.GOOGLE_WALLET -> 1f
    }

    /** Width of the cut-out PNG: Apple's logo @3x, Google's recommended 660 px. */
    fun targetWidth(platform: WalletPlatform): Int = when (platform) {
        WalletPlatform.APPLE_WALLET -> 480
        WalletPlatform.GOOGLE_WALLET -> 660
    }

    fun viewport(photoAspectRatio: Float, platform: WalletPlatform) = BannerViewport(photoAspectRatio, aspectRatio(platform))

    /** Frames the most prominent text clear of the codes (usually the brand name), else the top of the photo. */
    fun pick(viewport: BannerViewport, codes: List<ScannedCode>, lines: List<TextLine>): NormalizedRect =
        BannerRegion.framingText(viewport, codes.mapNotNull { it.bounds }, lines)
            ?: viewport.clamp(NormalizedRect(0f, 0f, 1f, 0f))
}
