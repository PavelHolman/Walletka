package cz.poqet.walletka.component.review

import cz.poqet.walletka.component.review.CardReviewComponent.CardForm
import cz.poqet.walletka.data.ColorPalette
import cz.poqet.walletka.domain.BannerViewport
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.platform.WalletPlatform

/** The empty code the user types in when there is none to detect; Code 128 takes any text and every wallet shows it. */
internal val EmptyTypedCode = ScannedCode(CodeFormat.CODE_128, value = "")

/** A card entered by hand, without a photo: default palette, no banner, an empty code to type in. */
internal fun manualCardForm(walletPlatform: WalletPlatform) = CardForm(
    photo = null,
    name = "",
    nameSuggestions = emptyList(),
    codes = listOf(EmptyTypedCode),
    selectedCodeIndex = 0,
    colors = ColorPalette.Defaults,
    selectedColorIndex = 0,
    suggestedColorCount = ColorPalette.Defaults.size,
    banner = null,
    isBannerEnabled = false,
    bannerRegion = NormalizedRect.Full,
    photoAspectRatio = BannerViewport.ASPECT_RATIO,
    walletPlatform = walletPlatform,
    cardId = null,
    isCodeTyped = true,
)

/** Sets the format of a typed code; detected codes keep the format they were read with. */
internal fun CardForm.withTypedCodeFormat(format: CodeFormat): CardForm =
    if (!isCodeTyped) this else copy(codes = codes.mapIndexed { index, code -> if (index == selectedCodeIndex) code.copy(format = format) else code })
