package com.example.btchat.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.core.Device
import kotlin.math.cos
import kotlin.math.sin

// ------------------------------------------------------------------ haptics

fun View.hapticTap() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY)
}

fun View.hapticNo() {
    performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
}

// ------------------------------------------------------------------ text and press feedback

@Composable
fun BtText(
    text: String,
    style: TextStyle = Ty.body,
    color: Color = P.text,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    align: TextAlign = TextAlign.Unspecified
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = align),
        overflow = overflow,
        maxLines = maxLines
    )
}

/** Reacts the instant a finger lands (scale down), commits on release. */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    haptic: Boolean = false,
    scaleTo: Float = 0.97f,
    onClick: () -> Unit
): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed = source.collectIsPressedAsState()
    val calm = LocalCalm.current
    val scale = animateFloatAsState(if (pressed.value && !calm) scaleTo else 1f, quick(), label = "press")
    val view = LocalView.current
    return this
        .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
        .clickable(interactionSource = source, indication = null, enabled = enabled) {
            if (haptic) view.hapticTap()
            onClick()
        }
}

// ------------------------------------------------------------------ icons (drawn, not stock)

enum class Glyph { Plus, Up, Close, Check, Back, Phone, Laptop, File, Star }

@Composable
fun GlyphIcon(g: Glyph, color: Color, dp: Dp = 22.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(dp)) {
        val u = this.size.minDimension / 24f
        val sw = 2f * u
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun pt(x: Float, y: Float) = Offset(x * u, y * u)
        fun poly(vararg p: Float, closed: Boolean = false): Path = Path().apply {
            moveTo(p[0] * u, p[1] * u)
            var i = 2
            while (i < p.size) { lineTo(p[i] * u, p[i + 1] * u); i += 2 }
            if (closed) close()
        }
        when (g) {
            Glyph.Plus -> {
                drawLine(color, pt(12f, 5f), pt(12f, 19f), sw, StrokeCap.Round)
                drawLine(color, pt(5f, 12f), pt(19f, 12f), sw, StrokeCap.Round)
            }
            Glyph.Up -> {
                drawLine(color, pt(12f, 19f), pt(12f, 6f), sw, StrokeCap.Round)
                drawPath(poly(6.5f, 11.5f, 12f, 6f, 17.5f, 11.5f), color, style = st)
            }
            Glyph.Close -> {
                drawLine(color, pt(6f, 6f), pt(18f, 18f), sw, StrokeCap.Round)
                drawLine(color, pt(18f, 6f), pt(6f, 18f), sw, StrokeCap.Round)
            }
            Glyph.Check -> drawPath(poly(5f, 12.5f, 10f, 17.5f, 19f, 7f), color, style = st)
            Glyph.Back -> drawPath(poly(15f, 5.5f, 8.5f, 12f, 15f, 18.5f), color, style = st)
            Glyph.Phone -> {
                drawRoundRect(color, pt(7f, 2.5f), Size(10f * u, 19f * u), CornerRadius(2.6f * u), style = st)
                drawLine(color, pt(10.5f, 18.3f), pt(13.5f, 18.3f), sw, StrokeCap.Round)
            }
            Glyph.Laptop -> {
                drawRoundRect(color, pt(4f, 5.5f), Size(16f * u, 11f * u), CornerRadius(2f * u), style = st)
                drawLine(color, pt(2.5f, 19.5f), pt(21.5f, 19.5f), sw, StrokeCap.Round)
            }
            Glyph.Star -> drawPath(poly(12.0f, 3.4f, 14.35f, 9.36f, 20.75f, 9.76f, 15.8f, 13.84f, 17.41f, 20.04f, 12.0f, 16.6f, 6.59f, 20.04f, 8.2f, 13.84f, 3.25f, 9.76f, 9.65f, 9.36f, closed = true), color, style = st)
            Glyph.File -> {
                drawPath(poly(6f, 3.5f, 13.5f, 3.5f, 18.5f, 8.5f, 18.5f, 20.5f, 6f, 20.5f, closed = true), color, style = st)
                drawPath(poly(13.5f, 3.5f, 13.5f, 8.5f, 18.5f, 8.5f), color, style = st)
            }
        }
    }
}

// ------------------------------------------------------------------ logo and radar

/**
 * The mark: a lowercase "b" made of a bar and a dot. The bar is a device, the dot is a blip,
 * the arc is the signal between them. Same geometry as the launcher icon.
 */
@Composable
fun LogoMark(dp: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(dp)) {
        val u = this.size.minDimension / 108f
        val blue = Color(0xFF3D7BFF)
        drawCircle(blue, 17f * u, Offset(49f * u, 63.5f * u))
        drawLine(Color.White, Offset(34f * u, 35f * u), Offset(34f * u, 75f * u), 11f * u, StrokeCap.Round)
        drawArc(blue, -75f, 60f, false, Offset(22f * u, 36.5f * u), Size(54f * u, 54f * u), style = Stroke(5f * u, cap = StrokeCap.Round))
    }
}

@Composable
fun LogoTile(dp: Dp = 40.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(dp)
            .clip(RoundedCornerShape(dp * 0.28f))
            .background(Color(0xFF0B0F1A)),
        contentAlignment = Alignment.Center
    ) { LogoMark(dp) }
}

@Composable
fun Radar(scanning: Boolean, devices: List<Device>, modifier: Modifier = Modifier) {
    val calm = LocalCalm.current
    val t = rememberInfiniteTransition(label = "radar")
    val phase = t.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "phase")
    val accent = P.accent
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val maxR = this.size.minDimension / 2f
            val inner = 38.dp.toPx()
            for (i in 1..3) {
                drawCircle(accent.copy(alpha = 0.10f), inner + (maxR - inner) * i / 3f, c, style = Stroke(1.dp.toPx()))
            }
            if (scanning && !calm) {
                for (k in 0..2) {
                    val f = (phase.value + k / 3f) % 1f
                    drawCircle(accent.copy(alpha = 0.40f * (1f - f)), inner + (maxR - inner) * f, c, style = Stroke(2.dp.toPx()))
                }
            }
            for (d in devices.take(8)) {
                val h = d.address.hashCode()
                val ang = ((h and 0xFFFF) / 65535f) * 6.2832f
                val r = inner + (maxR - inner) * (0.25f + 0.65f * (((h ushr 16) and 0xFF) / 255f))
                drawCircle(accent, 5.dp.toPx(), Offset(c.x + cos(ang) * r, c.y + sin(ang) * r))
            }
        }
        LogoTile(64.dp)
    }
}

// ------------------------------------------------------------------ controls

@Composable
fun Pill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glyph: Glyph? = null,
    filled: Boolean = true,
    danger: Boolean = false,
    enabled: Boolean = true,
    height: Dp = 50.dp
) {
    val bg = when { !enabled -> P.high; danger -> P.bad; filled -> P.accent; else -> P.soft }
    val fg = when { !enabled -> P.text3; filled || danger -> Color.White; else -> P.accentText }
    Row(
        modifier
            .height(height)
            .pressable(enabled = enabled, haptic = true, onClick = onClick)
            .clip(RoundedCornerShape(height / 2))
            .background(bg)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (glyph != null) {
            GlyphIcon(glyph, fg, 20.dp)
            Spacer(Modifier.width(8.dp))
        }
        BtText(label, Ty.bodyB, fg, maxLines = 1)
    }
}

@Composable
fun TextBtn(label: String, color: Color = P.accentText, onClick: () -> Unit) {
    BtText(
        label, Ty.bodyB.copy(fontSize = 15.sp), color,
        Modifier.pressable(haptic = true, onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp)
    )
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val x = animateDpAsState(if (on) 20.dp else 0.dp, bouncy(), label = "thumb")
    val track = animateColorAsState(if (on) P.accent else P.high, label = "track")
    Box(
        Modifier
            .size(51.dp, 31.dp)
            .clip(CircleShape)
            .background(track.value)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onChange(!on) }
            .padding(2.dp)
    ) {
        Box(Modifier.offset(x.value).size(27.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(P.surface).border(0.5.dp, P.line, shape),
        content = content
    )
}

@Composable
fun Divider(inset: Dp = 74.dp) {
    Box(Modifier.padding(start = inset).fillMaxWidth().height(0.5.dp).background(P.line))
}

@Composable
fun SectionLabel(text: String) {
    BtText(text.uppercase(), Ty.cap, P.text3, Modifier.padding(start = 16.dp, top = 22.dp, bottom = 8.dp))
}

@Composable
fun Bar(progress: Float, modifier: Modifier = Modifier) {
    val a = animateFloatAsState(progress.coerceIn(0f, 1f), smooth(), label = "bar")
    Box(modifier.fillMaxWidth().height(5.dp).clip(CircleShape).background(P.high)) {
        Box(Modifier.fillMaxWidth(a.value).fillMaxHeight().clip(CircleShape).background(P.accent))
    }
}
