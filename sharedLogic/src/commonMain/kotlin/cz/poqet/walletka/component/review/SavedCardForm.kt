package cz.poqet.walletka.component.review

import cz.poqet.walletka.component.review.CardReviewComponent.CardForm
import cz.poqet.walletka.data.LogoRegion
import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.CardDetails
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.platform.WalletPlatform
import kotlinx.serialization.Serializable

/**
 * [CardForm] snapshot for the StateKeeper. Kept separate (and internal) so the public models stay free of
 * kotlinx.serialization, which Swift Export would otherwise have to bridge.
 */
@Serializable
internal data class SavedCardForm(
    /** `null` for a card entered by hand. */
    val photo: String?,
    val name: String,
    val nameSuggestions: List<String>,
    val codes: List<Code>,
    val selectedCodeIndex: Int,
    val colors: List<Int>,
    val selectedColorIndex: Int,
    val suggestedColorCount: Int,
    val banner: String?,
    val isBannerEnabled: Boolean,
    val bannerRegion: List<Float>,
    val photoAspectRatio: Float,
    val walletPlatform: WalletPlatform,
    val cardId: String? = null,
    val isCodeTyped: Boolean = false,
    // Added later: optional, so library files and saved state from older versions still load.
    val logo: Logo? = null,
    val details: Details? = null,
) {
    @Serializable
    data class Code(val format: CodeFormat, val value: String, val bounds: List<Float>?)

    @Serializable
    data class Logo(val image: String?, val isEnabled: Boolean, val region: List<Float>)

    @Serializable
    data class Details(
        val memberNumber: String = "",
        val memberName: String = "",
        val expiryDate: String = "",
        val notes: String = "",
    )

    fun toForm() = CardForm(
        photo = photo?.let(::StoredImage),
        name = name,
        nameSuggestions = nameSuggestions,
        codes = codes.map { code ->
            ScannedCode(code.format, code.value, code.bounds?.let { (l, t, r, b) -> NormalizedRect(l, t, r, b) })
        },
        selectedCodeIndex = selectedCodeIndex,
        colors = colors.map(CardColor::fromArgb),
        selectedColorIndex = selectedColorIndex,
        suggestedColorCount = suggestedColorCount,
        banner = banner?.let(::StoredImage),
        isBannerEnabled = isBannerEnabled,
        bannerRegion = bannerRegion.let { (l, t, r, b) -> NormalizedRect(l, t, r, b) },
        photoAspectRatio = photoAspectRatio,
        walletPlatform = walletPlatform,
        cardId = cardId,
        isCodeTyped = isCodeTyped,
        logo = logo?.let { CardLogo(it.image?.let(::StoredImage), it.isEnabled, it.region.let { (l, t, r, b) -> NormalizedRect(l, t, r, b) }) }
            ?: CardLogo(image = null, isEnabled = false, region = LogoRegion.viewport(photoAspectRatio, walletPlatform).clamp(NormalizedRect.Full)),
        details = details?.run { CardDetails(memberNumber, memberName, expiryDate, notes) } ?: CardDetails.Empty,
    )

    companion object {
        fun of(form: CardForm) = SavedCardForm(
            photo = form.photo?.path,
            name = form.name,
            nameSuggestions = form.nameSuggestions,
            codes = form.codes.map { code ->
                Code(code.format, code.value, code.bounds?.run { listOf(left, top, right, bottom) })
            },
            selectedCodeIndex = form.selectedCodeIndex,
            colors = form.colors.map { (it.red shl 16) or (it.green shl 8) or it.blue },
            selectedColorIndex = form.selectedColorIndex,
            suggestedColorCount = form.suggestedColorCount,
            banner = form.banner?.path,
            isBannerEnabled = form.isBannerEnabled,
            bannerRegion = form.bannerRegion.run { listOf(left, top, right, bottom) },
            photoAspectRatio = form.photoAspectRatio,
            walletPlatform = form.walletPlatform,
            cardId = form.cardId,
            isCodeTyped = form.isCodeTyped,
            logo = form.logo.run { Logo(image?.path, isEnabled, region.run { listOf(left, top, right, bottom) }) },
            details = form.details.run { Details(memberNumber, memberName, expiryDate, notes) },
        )
    }
}
