@file:Suppress("FunctionName")

package dev.ririxi.bunnylock.ui.theme

import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// الخلفية سوداء، والنبيتي لون الهوية والإجراءات؛ تبقى ألوان الإشارة مستقلة لتوضيح جودتها.
private val DarkColors =
    darkColorScheme(
        primary = Color(0xFF8F1838),
        onPrimary = Color(0xFFFFF1F4),
        primaryContainer = Color(0xFF561025),
        onPrimaryContainer = Color(0xFFFFD9E2),
        secondary = Color(0xFFCA8798),
        onSecondary = Color(0xFF35131D),
        secondaryContainer = Color(0xFF51212F),
        onSecondaryContainer = Color(0xFFFFD9E2),
        tertiary = Color(0xFFE0B66D),
        onTertiary = Color(0xFF302100),
        tertiaryContainer = Color(0xFF4A3510),
        onTertiaryContainer = Color(0xFFFFE2A8),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
        background = Color(0xFF000000),
        onBackground = Color(0xFFF2E9EC),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFF2E9EC),
        surfaceVariant = Color(0xFF292126),
        onSurfaceVariant = Color(0xFFC9BBC0),
        outline = Color(0xFF88777D),
        outlineVariant = Color(0xFF49373D),
        inverseSurface = Color(0xFFF2E9EC),
        inverseOnSurface = Color(0xFF24191D),
        inversePrimary = Color(0xFF7B1733),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0A0709),
        surfaceContainer = Color(0xFF120B0E),
        surfaceContainerHigh = Color(0xFF1C1115),
        surfaceContainerHighest = Color(0xFF28171D),
    )

private val LockGreen = Color(0xFF8F1838)
private val SignalScale = listOf(Color(0xFF7BE5A0), Color(0xFFE0D879), Color(0xFFF0B86E), Color(0xFFFF8A7E))

val LocalLockGreen = staticCompositionLocalOf { LockGreen }
val LocalSignalScale = staticCompositionLocalOf { SignalScale }

@Composable
fun BunnyTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLockGreen provides LockGreen,
        LocalSignalScale provides SignalScale,
    ) {
        MaterialExpressiveTheme(
            colorScheme = DarkColors,
            motionScheme = MotionScheme.expressive(),
            content = content,
        )
    }
}
