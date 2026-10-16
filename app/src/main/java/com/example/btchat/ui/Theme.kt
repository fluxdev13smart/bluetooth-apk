package com.example.btchat.ui

import android.provider.Settings
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
class Pal(
    val bg: Color,
    val surface: Color,
    val high: Color,
    val line: Color,
    val text: Color,
    val text2: Color,
    val text3: Color,
    val accent: Color,
    val accentText: Color,
    val soft: Color,
    val ok: Color,
    val bad: Color
)

val LightPal = Pal(
    bg = Color(0xFFF4F5F7), surface = Color(0xFFFFFFFF), high = Color(0xFFE6E8ED), line = Color(0x1A0B0F1A),
    text = Color(0xFF0B0F1A), text2 = Color(0xFF5B6270), text3 = Color(0xFF8A90A0),
    accent = Color(0xFF2D6CF6), accentText = Color(0xFF1F5BE0), soft = Color(0x1F2D6CF6),
    ok = Color(0xFF1B8F55), bad = Color(0xFFD93A3A)
)

val DarkPal = Pal(
    bg = Color(0xFF07090F), surface = Color(0xFF111522), high = Color(0xFF1C2236), line = Color(0x1AFFFFFF),
    text = Color(0xFFF3F5FA), text2 = Color(0xFFA0A8BE), text3 = Color(0xFF6B748C),
    accent = Color(0xFF4F8CFF), accentText = Color(0xFF8CB4FF), soft = Color(0x334F8CFF),
    ok = Color(0xFF3DDC97), bad = Color(0xFFFF5D6C)
)

val LocalPal = staticCompositionLocalOf { DarkPal }

/** True when the user turned animations off in system settings. */
val LocalCalm = staticCompositionLocalOf { false }

val P: Pal
    @Composable get() = LocalPal.current

/** Type scale. Big text is tighter, small text is looser, like system type. */
object Ty {
    val large = TextStyle(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
    val title = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp)
    val body = TextStyle(fontSize = 16.sp, lineHeight = 22.sp)
    val bodyB = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val small = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.1.sp)
    val cap = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.SemiBold)
}

// Springs. Critically damped by default; a little bounce only where something was thrown or popped in.
fun <T> quick(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 700f)
fun <T> smooth(): SpringSpec<T> = spring(dampingRatio = 1f, stiffness = 250f)
fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.8f, stiffness = 440f)

@Composable
fun BtTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val calm = remember {
        Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    CompositionLocalProvider(
        LocalPal provides DarkPal,
        LocalCalm provides calm
    ) { content() }
}
