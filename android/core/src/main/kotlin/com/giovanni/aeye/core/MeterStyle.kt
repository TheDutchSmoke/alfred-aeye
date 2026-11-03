package com.giovanni.aeye.core

/**
 * How the circle meter is drawn on every Android surface: the way the iPhone's emoji font draws
 * 🟢🟡🔴⚪ — a disc with a thin darker rim and a glossy highlight falling from the top. Android's
 * emoji font draws the same characters as flat discs, so typing them would not look the same.
 *
 * Geometry: at a font size of 16 the ten iPhone emoji measure 20.7 pt across and sit 23 pt apart.
 */
object MeterStyle {
    /** Slot pitch per point of font size. */
    const val PITCH_PER_FONT_SIZE = 1.4375f

    /** Disc diameter as a fraction of the pitch. */
    const val DIAMETER_PER_PITCH = 20.7f / 23f

    /** The inner, gradient-filled disc as a fraction of the whole; the rest is the rim. */
    const val INNER_PER_RADIUS = 0.93f

    /** Gradient stops, top to bottom: highlight, highlight, body, bottom. */
    val STOPS = floatArrayOf(0f, 0.12f, 0.55f, 1f)

    /** ARGB colours: rim, top highlight, body, bottom. */
    class Palette(val rim: Long, val top: Long, val body: Long, val bottom: Long)

    fun palette(slot: MeterSlot): Palette = when (slot) {
        // Sampled from the iPhone screenshot.
        MeterSlot.GREEN -> Palette(0xFF01A001, 0xFF85FF85, 0xFF03B403, 0xFF019201)
        MeterSlot.EMPTY -> Palette(0xFFD3D3D3, 0xFFFFFFFF, 0xFFD9D9D9, 0xFFD5D5D5)
        MeterSlot.YELLOW -> Palette(0xFFE0A800, 0xFFFFF4A0, 0xFFFFCC10, 0xFFF0B400)
        MeterSlot.RED -> Palette(0xFFC40A0A, 0xFFFF9A90, 0xFFEE2A22, 0xFFD01212)
    }
}
