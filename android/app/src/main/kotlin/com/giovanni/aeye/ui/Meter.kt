package com.giovanni.aeye.ui

import android.graphics.Bitmap
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.giovanni.aeye.core.AeyeFormatting
import com.giovanni.aeye.core.MeterSlot
import com.giovanni.aeye.core.MeterStyle

/** The circle meter, sized like the iOS text it replaces — see [MeterStyle]. */
@Composable
fun MeterView(percent: Double?, modifier: Modifier = Modifier, fontSize: TextUnit = 16.sp, width: Int = 10) {
    val slots = AeyeFormatting.meterSlots(percent, width)
    val pitch = (fontSize.value * MeterStyle.PITCH_PER_FONT_SIZE).dp
    val label = AeyeFormatting.percentString(percent) + " used"
    Canvas(
        modifier
            .width(pitch * width)
            .height(pitch)
            .semantics { contentDescription = label }
    ) {
        val step = this.size.width / width
        val radius = step * MeterStyle.DIAMETER_PER_PITCH / 2
        slots.forEachIndexed { i, slot ->
            drawSphere(slot, Offset(step * i + step / 2, this.size.height / 2), radius)
        }
    }
}

private fun DrawScope.drawSphere(slot: MeterSlot, center: Offset, radius: Float) {
    val p = MeterStyle.palette(slot)
    drawCircle(Color(p.rim), radius, center)
    val inner = radius * MeterStyle.INNER_PER_RADIUS
    val s = MeterStyle.STOPS
    drawCircle(
        Brush.verticalGradient(
            s[0] to Color(p.top), s[1] to Color(p.top), s[2] to Color(p.body), s[3] to Color(p.bottom),
            startY = center.y - inner,
            endY = center.y + inner,
        ),
        inner,
        center,
    )
}

/**
 * The same meter as a bitmap, for the Home Screen widget — Glance cannot draw on a canvas.
 * [heightPx] is the meter's height (one slot's pitch); its width follows the slot count.
 */
fun meterBitmap(percent: Double?, heightPx: Int, width: Int = 10): Bitmap {
    val bitmap = Bitmap.createBitmap(heightPx * width, heightPx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val step = heightPx.toFloat()
    val radius = step * MeterStyle.DIAMETER_PER_PITCH / 2
    AeyeFormatting.meterSlots(percent, width).forEachIndexed { i, slot ->
        val p = MeterStyle.palette(slot)
        val cx = step * i + step / 2
        val cy = heightPx / 2f
        paint.shader = null
        paint.color = p.rim.toInt()
        canvas.drawCircle(cx, cy, radius, paint)
        val inner = radius * MeterStyle.INNER_PER_RADIUS
        paint.shader = LinearGradient(
            0f, cy - inner, 0f, cy + inner,
            intArrayOf(p.top.toInt(), p.top.toInt(), p.body.toInt(), p.bottom.toInt()),
            MeterStyle.STOPS,
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, inner, paint)
    }
    return bitmap
}
