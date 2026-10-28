package cz.poqet.walletka.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import cz.poqet.walletka.domain.CardColor

private val Brand = Color(0xFF3D5AFE)

@Composable
fun WalletkaTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF8C9EFF), secondary = Color(0xFF80CBC4))
    } else {
        lightColorScheme(primary = Brand, secondary = Color(0xFF00897B))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

fun CardColor.toColor(): Color = Color(red, green, blue)
