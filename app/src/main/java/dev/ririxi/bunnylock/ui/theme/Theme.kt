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
        primary = Color(0xFFB3435A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF76283A),
        onPrimaryContainer = Color(0xFFFFD9DF),
        secondary = Color(0xFFBFC8D0),
        onSecondary = Color(0xFF283141),
        secondaryContainer = Color(0xFF394148),
        onSecondaryContainer = Color(0xFFE1E7EC),
        tertiary = Color(0xFF80CBC4),
        onTertiary = Color(0xFF003731),
        tertiaryContainer = Color(0xFF005048),
        onTertiaryContainer = Color(0xFF9CF2E8),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF000000),
        onBackground = Color(0xFFE8E1E3),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFE8E1E3),
        surfaceVariant = Color(0xFF40373A),
        onSurfaceVariant = Color(0xFFD0C3C6),
        outline = Color(0xFF988A8E),
        outlineVariant = Color(0xFF51464A),
        inverseSurface = Color(0xFFE8E1E3),
        inverseOnSurface = Color(0xFF21191C),
        inversePrimary = Color(0xFF8E3045),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF100D0E),
        surfaceContainer = Color(0xFF171214),
        surfaceContainerHigh = Color(0xFF211A1C),
        surfaceContainerHighest = Color(0xFF2B2225),
    )

    private val LockGreenOnDark = Color(0xFFFFA9B8)

val LocalLockGreen = staticCompositionLocalOf { LockGreenOnLight }

private val SignalScaleOnDark = listOf(Color(0xFF5BE584), Color(0xFFC3E35A), Color(0xFFFFB95C), Color(0xFFFF8A7E))

val LocalSignalScale = staticCompositionLocalOf { SignalScaleOnDark }

@Composable
fun BunnyTheme(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLockGreen provides LockGreenOnDark,
        LocalSignalScale provides SignalScaleOnDark,
    ) {
        MaterialExpressiveTheme(
            colorScheme = DarkColors,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
