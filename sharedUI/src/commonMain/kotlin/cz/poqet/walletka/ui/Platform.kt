package cz.poqet.walletka.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Platform ways of getting an image into the app (photo picker, clipboard). */
class ImageSourceActions(
    val pickPhoto: () -> Unit,
    /** Returns `false` when the clipboard holds no image. */
    val pasteImage: () -> Boolean,
)

@Composable
expect fun rememberImageSourceActions(onImage: (source: String) -> Unit): ImageSourceActions

/** Decodes an image stored by [cz.poqet.walletka.platform.ImageStore], downscaled for display. */
@Composable
expect fun rememberStoredImage(path: String?): ImageBitmap?
