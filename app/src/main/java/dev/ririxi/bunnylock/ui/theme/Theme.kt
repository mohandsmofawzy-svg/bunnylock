@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF800020),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF4A1024),
        onPrimaryContainer = Color(0xFFFFFFFF),
        secondary = Color(0xFFB05A70),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFF421824),
        onSecondaryContainer = Color(0xFFFFFFFF),
        tertiary = Color(0xFFB05A70),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFF421824),
        onTertiaryContainer = Color(0xFFFFFFFF),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFFFFF),
        background = Color(0xFF000000),
        onBackground = Color(0xFFFFFFFF),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFFFFFFF),
        surfaceVariant = Color(0xFF171013),
        onSurfaceVariant = Color(0xFFFFFFFF),
        outline = Color(0xFFBFA6AC),
        outlineVariant = Color(0xFF4A343A),
        inverseSurface = Color(0xFFFFFFFF),
        inverseOnSurface = Color(0xFF000000),
        inversePrimary = Color(0xFF800020),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0A0708),
        surfaceContainer = Color(0xFF110B0D),
        surfaceContainerHigh = Color(0xFF171013),
        surfaceContainerHighest = Color(0xFF21171A),
    )

private val Burgundy = Color(0xFFB05A70)
val LocalLockGreen = staticCompositionLocalOf { Burgundy }
val LocalSignalScale =
    staticCompositionLocalOf {
        listOf(Color(0xFFB05A70), Color(0xFFC3A24A), Color(0xFFFFB95C), Color(0xFFFF8A7E))
    }

@Composable
fun BunnyTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLockGreen provides Burgundy,
    ) {
        MaterialExpressiveTheme(
            colorScheme = DarkColors,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
