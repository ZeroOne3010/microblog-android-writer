package io.github.zeroone3010.yablogwriter.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.zeroone3010.yablogwriter.domain.AppTheme

private val AccentOrange = Color(0xFFE79254)
private val MutedBronze = Color(0xFFB88A62)
private val LogoDark = Color(0xFF192333)

private val LightColors = lightColorScheme(
    primary = AccentOrange,
    secondary = MutedBronze,
    tertiary = MutedBronze
)

private val DarkColors = darkColorScheme(
    primary = AccentOrange,
    secondary = MutedBronze,
    tertiary = MutedBronze,
    background = LogoDark,
    surface = LogoDark
)

@Composable
fun MicroblogWriterTheme(theme: AppTheme, content: @Composable () -> Unit) {
    val useDarkTheme = when (theme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.LIGHT -> false
        AppTheme.DARK -> true
    }

    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content
    )
}
