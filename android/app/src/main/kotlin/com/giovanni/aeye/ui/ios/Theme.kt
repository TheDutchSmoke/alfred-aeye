package com.giovanni.aeye.ui.ios

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The iOS system palette, light and dark — the colours SwiftUI resolves `.primary`, `.secondary`,
 * `.tertiary`, `.accentColor`, `.red`, `.green`, `.orange`, `.fill.tertiary` and grouped forms to.
 * The Android app wears the iOS design on purpose, so the two read as one product.
 */
@Immutable
data class IosPalette(
    /** `systemBackground` — the overview's `ScrollView`. */
    val background: Color,
    /** `systemGroupedBackground` — a `Form`. In a dark sheet, the elevated variant. */
    val groupedBackground: Color,
    /** `secondarySystemGroupedBackground` — a `Form` cell. */
    val cell: Color,
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    /** `Divider()` outside a list. */
    val opaqueSeparator: Color,
    val tint: Color,
    val green: Color,
    val orange: Color,
    val red: Color,
    /** `.fill.tertiary` — the setup card. */
    val tertiaryFill: Color,
    /** Bars once content scrolls under them: `.bar` material. */
    val chrome: Color,
    val chromeHairline: Color,
    val switchOff: Color,
    val menu: Color,
    val menuSeparator: Color,
    /** Behind a presented sheet: the dimmed, receding card stack. */
    val sheetBackdrop: Color,
    val dark: Boolean,
)

private val Light = IosPalette(
    background = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    cell = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    opaqueSeparator = Color(0xFFC6C6C8),
    tint = Color(0xFF007AFF),
    green = Color(0xFF34C759),
    orange = Color(0xFFFF9500),
    red = Color(0xFFFF3B30),
    tertiaryFill = Color(0x1F767680),
    chrome = Color(0xF2F9F9F9),
    chromeHairline = Color(0x4D000000),
    switchOff = Color(0xFFE9E9EA),
    menu = Color(0xFFF2F2F2),
    menuSeparator = Color(0x29000000),
    sheetBackdrop = Color(0xFF000000),
    dark = false,
)

private val Dark = IosPalette(
    background = Color(0xFF000000),
    groupedBackground = Color(0xFF000000),
    cell = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    opaqueSeparator = Color(0xFF38383A),
    tint = Color(0xFF0A84FF),
    green = Color(0xFF30D158),
    orange = Color(0xFFFF9F0A),
    red = Color(0xFFFF453A),
    tertiaryFill = Color(0x3D767680),
    chrome = Color(0xF21C1C1E),
    chromeHairline = Color(0x26FFFFFF),
    switchOff = Color(0xFF39393D),
    menu = Color(0xFF252525),
    menuSeparator = Color(0x33FFFFFF),
    sheetBackdrop = Color(0xFF000000),
    dark = true,
)

/** A sheet in dark mode sits on the elevated background, one step lighter. */
private val DarkElevated = Dark.copy(
    background = Color(0xFF1C1C1E),
    groupedBackground = Color(0xFF1C1C1E),
    cell = Color(0xFF2C2C2E),
    chrome = Color(0xF22C2C2E),
)

/**
 * The iOS Dynamic Type scale at its default size, in `sp` so it follows the system font size the
 * way Dynamic Type does.
 */
@Immutable
data class IosType(
    val largeTitle: TextStyle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp),
    val title2: TextStyle = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    val headline: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    val body: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, letterSpacing = (-0.3).sp),
    val subheadline: TextStyle = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = (-0.2).sp),
    val footnote: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = (-0.05).sp),
    val caption: TextStyle = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    val caption2: TextStyle = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.05.sp),
    /** `.system(.body, design: .monospaced)` — SF Mono's counterpart. */
    val bodyMono: TextStyle = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontFamily = FontFamily.Monospace),
)

/** `.monospacedDigit()` — tabular figures, so numbers do not jiggle as they change. */
fun TextStyle.monospacedDigit(): TextStyle = copy(fontFeatureSettings = "tnum")

object IosMetrics {
    /** Form cards sit this far from the screen edge… */
    val cardMargin: Dp = 20.dp

    /** …and their content this far inside the card. */
    val cellPadding: Dp = 20.dp

    /** Large title margin. */
    val barMargin: Dp = 16.dp
    val cornerRadius: Dp = 10.dp
    val rowMinHeight: Dp = 44.dp
    val navBarHeight: Dp = 44.dp
}

val LocalIosPalette = staticCompositionLocalOf { Light }
val LocalIosType = staticCompositionLocalOf { IosType() }

object Ios {
    val colors: IosPalette @Composable get() = LocalIosPalette.current
    val type: IosType @Composable get() = LocalIosType.current
}

@Composable
fun IosTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) Dark else Light
    // Material is only underneath for the few pieces borrowed from it (pull to refresh, text
    // cursors); give it the iOS tint so nothing turns Material purple.
    val scheme = if (dark) {
        darkColorScheme(primary = palette.tint, background = palette.background, surface = palette.cell)
    } else {
        lightColorScheme(primary = palette.tint, background = palette.background, surface = palette.cell)
    }
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(
            LocalIosPalette provides palette,
            LocalIosType provides IosType(),
            LocalTextSelectionColors provides TextSelectionColors(palette.tint, palette.tint.copy(alpha = 0.3f)),
            content = content,
        )
    }
}

/** Content presented in a sheet: the elevated palette in dark mode, as UIKit does. */
@Composable
fun ElevatedSurface(content: @Composable () -> Unit) {
    val palette = Ios.colors
    CompositionLocalProvider(LocalIosPalette provides if (palette.dark) DarkElevated else palette, content = content)
}
