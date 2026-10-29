package cz.poqet.walletka.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap

/** Platform ways of getting an image into the app (photo picker, clipboard). */
class ImageSourceActions(
    val pickPhoto: () -> Unit,
    val takePhoto: () -> Unit,
    /** Returns `false` when the clipboard holds no image. */
    val pasteImage: () -> Boolean,
)

@Composable
expect fun rememberImageSourceActions(onImage: (source: String) -> Unit): ImageSourceActions

/** Decodes an image stored by [cz.poqet.walletka.platform.ImageStore], downscaled for display. */
@Composable
expect fun rememberStoredImage(path: String?, maxSize: Int = 1600): ImageBitmap?

/** The code drawn as the wallet will show it (black on white), or `null` when it can't be drawn. */
@Composable
expect fun rememberCodeImage(format: cz.poqet.walletka.domain.CodeFormat, value: String): ImageBitmap?
