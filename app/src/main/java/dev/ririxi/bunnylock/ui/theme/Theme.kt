@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// تثبيت الأسود للخلفية والنبيتي للهوية يمنع تبدل الألوان مع ألوان النظام.
private val DarkColors =
    darkColorScheme(
        primary = Color(0xFFB3435A),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFF76283A),
        onPrimaryContainer = Color(0xFFFFD9DF),
        secondary = Color(0xFFC28B98),
        onSecondary = Color(0xFF301318),
        secondaryContainer = Color(0xFF47232C),
        onSecondaryContainer = Color(0xFFF1DDE1),
        tertiary = Color(0xFFD36B83),
        onTertiary = Color(0xFF2C0010),
        tertiaryContainer = Color(0xFF541222),
        onTertiaryContainer = Color(0xFFFFD9DF),
        error = Color(0xFFD32F2F),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFF5A1111),
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

private val LockAccent = Color(0xFFB3435A)
private val SignalScale = listOf(LockAccent, Color(0xFFC95E75), Color(0xFFFFB95C), Color(0xFFFF8A7E))

val LocalLockAccent = staticCompositionLocalOf { LockAccent }
val LocalSignalScale = staticCompositionLocalOf { SignalScale }

@Composable
fun BunnyTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLockAccent provides LockAccent,
        LocalSignalScale provides SignalScale,
    ) {
        MaterialExpressiveTheme(
            colorScheme = DarkColors,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
