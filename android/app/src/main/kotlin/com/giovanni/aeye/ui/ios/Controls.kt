package com.giovanni.aeye.ui.ios

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A `UISwitch`: 51×31, green when on, a white thumb with a soft shadow. */
@Composable
fun IosSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = Ios.colors
    val track by animateColorAsState(if (checked) colors.green else colors.switchOff, tween(200), label = "track")
    val thumbX by animateDpAsState(if (checked) 22.dp else 2.dp, spring(dampingRatio = 0.75f, stiffness = 600f), label = "thumb")
    Box(
        modifier
            .size(51.dp, 31.dp)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(track)
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange,
            )
    ) {
        Box(
            Modifier
                .offset(x = thumbX, y = 2.dp)
                .size(27.dp)
                .shadow(2.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.2f), spotColor = Color.Black.copy(alpha = 0.2f))
                .background(Color.White, CircleShape)
        )
    }
}

/** `ProgressView()` — the eight-spoke iOS activity indicator. */
@Composable
fun ActivityIndicator(modifier: Modifier = Modifier, size: Dp = 20.dp, color: Color = Ios.colors.secondaryLabel) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val step by transition.animateFloat(
        initialValue = 0f,
        targetValue = 8f,
        animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing)),
        label = "step",
    )
    Canvas(modifier.size(size)) {
        val spokes = 8
        val head = step.toInt() % spokes
        val radius = this.size.minDimension / 2
        val width = radius * 0.22f
        for (i in 0 until spokes) {
            val angle = (i * 2 * PI / spokes) - PI / 2
            // The spoke at the head is darkest; the rest fade behind it.
            val age = (head - i + spokes) % spokes
            val alpha = 1f - age * 0.11f
            val start = Offset(center.x + cos(angle).toFloat() * radius * 0.48f, center.y + sin(angle).toFloat() * radius * 0.48f)
            val end = Offset(center.x + cos(angle).toFloat() * (radius - width / 2), center.y + sin(angle).toFloat() * (radius - width / 2))
            drawLine(color.copy(alpha = color.alpha * alpha.coerceIn(0.2f, 1f)), start, end, width, StrokeCap.Round)
        }
    }
}

/** A plain tinted text button, as SwiftUI draws `Button("…")` outside a list. */
@Composable
fun IosTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ios.colors.tint,
    bold: Boolean = false,
    style: androidx.compose.ui.text.TextStyle = Ios.type.body,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Text(
        text,
        style = if (bold) style.copy(fontWeight = FontWeight.SemiBold) else style,
        color = if (enabled) color else Ios.colors.tertiaryLabel,
        modifier = modifier
            .alpha(if (pressed) 0.35f else 1f)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    )
}

/** `.buttonStyle(.borderedProminent)` — the filled, tinted rounded rectangle. */
@Composable
fun BorderedProminentButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Ios.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(colors.tint)
            .alpha(if (pressed) 0.75f else 1f)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Ios.type.body, color = Color.White)
    }
}

/** A toolbar icon button: tinted, fades while pressed, 44-point touch target. */
@Composable
fun BarIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: @Composable (Color) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val color = if (enabled) Ios.colors.tint else Ios.colors.tertiaryLabel
    Box(
        modifier
            .size(44.dp)
            .alpha(if (pressed) 0.35f else 1f)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClickLabel = contentDescription, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        icon(color)
    }
}

// ---- SF Symbols, drawn ----------------------------------------------------------------------

/** `arrow.clockwise`. */
@Composable
fun ArrowClockwiseIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 22.dp) {
    Canvas(modifier.size(size)) {
        val stroke = size.toPx() * 0.085f
        val r = this.size.minDimension * 0.38f
        val c = center
        val head = this.size.minDimension * 0.17f
        // Open at the top right; the stroke runs clockwise and ends at 12 o'clock, where the
        // chevron points on, to the right.
        drawArc(color, -45f, 315f, false, Offset(c.x - r, c.y - r), Size(r * 2, r * 2), style = Stroke(stroke, cap = StrokeCap.Round))
        val tip = Offset(c.x + head * 0.55f, c.y - r)
        val path = Path().apply {
            moveTo(tip.x - head, tip.y - head)
            lineTo(tip.x, tip.y)
            lineTo(tip.x - head, tip.y + head)
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/** `gearshape`. */
@Composable
fun GearshapeIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val stroke = size.toPx() * 0.075f
        val c = center
        val outer = this.size.minDimension * 0.46f
        val inner = this.size.minDimension * 0.36f
        val teeth = 8
        val path = Path()
        val steps = teeth * 4
        for (i in 0..steps) {
            val angle = (i.toDouble() / steps) * 2 * PI - PI / 2 + PI / steps / 2
            val radius = if ((i / 2) % 2 == 0) outer else inner
            val p = Offset(c.x + cos(angle).toFloat() * radius, c.y + sin(angle).toFloat() * radius)
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        drawPath(path, color, style = Stroke(stroke, join = StrokeJoin.Round))
        drawCircle(color, radius = this.size.minDimension * 0.14f, center = c, style = Stroke(stroke))
    }
}

/** `person.crop.circle.badge.checkmark`. */
@Composable
fun PersonBadgeCheckIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 26.dp, badgeBorder: Color = Ios.colors.cell) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = s * 0.07f
        val c = Offset(s * 0.56f, s * 0.46f)
        val r = s * 0.4f
        drawCircle(color, r, c, style = Stroke(stroke))
        // Head and shoulders, clipped to the circle.
        drawCircle(color, s * 0.13f, Offset(c.x, c.y - s * 0.08f))
        val shoulders = Path().apply {
            arcTo(Rect(Offset(c.x - s * 0.25f, c.y + s * 0.1f), Size(s * 0.5f, s * 0.4f)), 180f, 180f, true)
            close()
        }
        drawPath(shoulders, color)
        // The checkmark badge, bottom left, punched out of the circle.
        val b = Offset(s * 0.22f, s * 0.76f)
        drawCircle(badgeBorder, s * 0.23f, b)
        drawCircle(color, s * 0.18f, b)
        checkmark(Color.White, b, s * 0.18f, stroke * 0.9f)
    }
}

private fun DrawScope.checkmark(color: Color, center: Offset, radius: Float, stroke: Float) {
    val path = Path().apply {
        moveTo(center.x - radius * 0.5f, center.y + radius * 0.02f)
        lineTo(center.x - radius * 0.12f, center.y + radius * 0.4f)
        lineTo(center.x + radius * 0.52f, center.y - radius * 0.38f)
    }
    drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** `safari` — a compass. */
@Composable
fun SafariIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val c = center
        drawCircle(color, s * 0.44f, c, style = Stroke(s * 0.07f))
        val needle = Path().apply {
            moveTo(c.x + s * 0.2f, c.y - s * 0.2f)
            lineTo(c.x + s * 0.06f, c.y + s * 0.06f)
            lineTo(c.x - s * 0.2f, c.y + s * 0.2f)
            lineTo(c.x - s * 0.06f, c.y - s * 0.06f)
            close()
        }
        drawPath(needle, color)
    }
}

/** `doc.on.doc` — the Copy action. */
@Composable
fun DocOnDocIcon(color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val stroke = Stroke(s * 0.075f, join = StrokeJoin.Round)
        val radius = androidx.compose.ui.geometry.CornerRadius(s * 0.1f)
        drawRoundRect(color, Offset(s * 0.12f, s * 0.04f), Size(s * 0.52f, s * 0.64f), radius, style = stroke)
        drawRoundRect(color, Offset(s * 0.36f, s * 0.3f), Size(s * 0.52f, s * 0.64f), radius, style = stroke)
    }
}

/** The disclosure chevron; [degrees] 90 points it down, for an expanded `DisclosureGroup`. */
@Composable
fun DisclosureChevron(modifier: Modifier = Modifier, degrees: Float = 0f, color: Color = Ios.colors.tertiaryLabel) {
    Canvas(modifier.size(8.dp, 14.dp).rotate(degrees)) {
        val path = Path().apply {
            moveTo(size.width * 0.15f, size.height * 0.1f)
            lineTo(size.width * 0.85f, size.height * 0.5f)
            lineTo(size.width * 0.15f, size.height * 0.9f)
        }
        drawPath(path, color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
