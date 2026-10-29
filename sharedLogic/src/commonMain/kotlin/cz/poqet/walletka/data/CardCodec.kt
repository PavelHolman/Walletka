package cz.poqet.walletka.data

import cz.poqet.walletka.platform.WalletPlatform
import kotlinx.io.files.Path
import kotlinx.serialization.json.Json

/**
 * A library card as it travels to other copies of the library (iCloud records, backups): its image paths are bare
 * file names, the images going alongside.
 */
internal object CardCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(card: StoredCard): String = json.encodeToString(StoredCard.serializer(), portable(card))

    fun decode(text: String, platform: WalletPlatform, image: (name: String) -> String): StoredCard? =
        runCatching { json.decodeFromString(StoredCard.serializer(), text) }.getOrNull()?.let { local(it, platform, image) }

    /** [card] with its images as file names. */
    fun portable(card: StoredCard): StoredCard = card.withImages { Path(it).name }

    /**
     * A [portable] card with its images pointed at [image] (by file name). [platform] is this device's wallet: a card
     * made on the other platform is re-targeted to it.
     */
    fun local(card: StoredCard, platform: WalletPlatform, image: (name: String) -> String): StoredCard =
        card.withImages { image(Path(it).name) }.let { it.copy(form = it.form.copy(walletPlatform = platform)) }

    /** File names of the images [card] uses. */
    fun imageNames(card: StoredCard): List<String> = listOfNotNull(card.form.photo, card.form.banner).map { Path(it).name }

    private fun StoredCard.withImages(transform: (String) -> String) =
        copy(form = form.copy(photo = transform(form.photo), banner = form.banner?.let(transform)))
}
