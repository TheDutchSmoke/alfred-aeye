package com.giovanni.aeye.ui.ios

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Collects a section's rows so each knows whether a hairline goes under it. */
class FormRows {
    internal class Row(val separatorInset: Dp, val content: @Composable () -> Unit)

    internal val rows = mutableListOf<Row>()

    /**
     * One cell. [separatorInset] is where the hairline under it starts, measured from the card's
     * leading edge — past the icon for rows that have one, as UIKit does.
     */
    fun row(separatorInset: Dp = IosMetrics.cellPadding, content: @Composable () -> Unit) {
        rows += Row(separatorInset, content)
    }
}

/** One inset-grouped `Section`: header, rounded card of rows with hairlines between, footer. */
@Composable
fun FormSection(
    header: String? = null,
    footer: (@Composable () -> Unit)? = null,
    topSpacing: Dp = if (header != null) 22.dp else 35.dp,
    build: FormRows.() -> Unit,
) {
    val rows = FormRows().apply(build).rows
    Column(Modifier.fillMaxWidth()) {
        Spacer(Modifier.height(topSpacing))
        if (header != null) {
            Box(Modifier.padding(start = IosMetrics.cardMargin + IosMetrics.cellPadding, end = IosMetrics.cardMargin + IosMetrics.cellPadding, bottom = 7.dp)) {
                Text(header.uppercase(), style = Ios.type.footnote, color = Ios.colors.secondaryLabel)
            }
        }
        if (rows.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = IosMetrics.cardMargin)
                    .clip(RoundedCornerShape(IosMetrics.cornerRadius))
                    .background(Ios.colors.cell)
            ) {
                rows.forEachIndexed { index, row ->
                    Box {
                        row.content()
                        if (index != rows.lastIndex) {
                            Box(
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(start = row.separatorInset)
                                    .fillMaxWidth()
                                    .height(0.5.dp)
                                    .background(Ios.colors.separator)
                            )
                        }
                    }
                }
            }
        }
        if (footer != null) {
            Box(Modifier.padding(start = IosMetrics.cardMargin + IosMetrics.cellPadding, end = IosMetrics.cardMargin + IosMetrics.cellPadding, top = 7.dp)) {
                footer()
            }
        }
    }
}

@Composable
fun SectionFooterText(text: String) {
    Text(text, style = Ios.type.footnote, color = Ios.colors.secondaryLabel)
}

/**
 * A row's padding and press highlight. Rows with an [onClick] darken while pressed, as a table
 * cell does, instead of showing a Material ripple.
 */
@Composable
fun IosRow(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    verticalPadding: Dp = 11.dp,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val highlight = if (pressed) Ios.colors.opaqueSeparator.copy(alpha = 0.55f) else Color.Transparent
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
                else Modifier
            )
            .background(highlight)
            .heightIn(min = IosMetrics.rowMinHeight)
            .padding(horizontal = IosMetrics.cellPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
        content = content,
    )
}

/** Where text after a leading row icon — and the hairline under that row — starts. */
val RowIconInset: Dp = IosMetrics.cellPadding + 30.dp + 16.dp

/** `Button { Label(title, systemImage:) }` inside a `Form`: tinted icon and title. */
@Composable
fun ButtonRow(
    title: String,
    enabled: Boolean = true,
    color: Color = Ios.colors.tint,
    icon: (@Composable (Color) -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tint = if (enabled) color else Ios.colors.tertiaryLabel
    IosRow(onClick = onClick, enabled = enabled) {
        if (icon != null) {
            Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) { icon(tint) }
            Spacer(Modifier.width(16.dp))
        }
        Text(title, style = Ios.type.body, color = tint)
    }
}

/** A row holding [content] with the cell's padding and no interaction. */
@Composable
fun ContentRow(verticalPadding: Dp = 11.dp, content: @Composable RowScope.() -> Unit) {
    IosRow(verticalPadding = verticalPadding, content = content)
}

/** `Text(...).font(.caption).foregroundStyle(.secondary)` as a form row. */
@Composable
fun CaptionRow(text: String, color: Color = Ios.colors.secondaryLabel) {
    IosRow(verticalPadding = 11.dp) {
        Text(text, style = Ios.type.caption, color = color)
    }
}

/** `LabeledContent(label) { value }` — label leading, value trailing. */
@Composable
fun LabeledRow(label: String, value: String, valueColor: Color = Ios.colors.secondaryLabel) {
    IosRow {
        Text(label, style = Ios.type.body, color = Ios.colors.label, modifier = Modifier.weight(1f))
        Text(value, style = Ios.type.body, color = valueColor, textAlign = TextAlign.End)
    }
}

/** `Toggle(isOn:) { Text(title) }` in a `Form`. */
@Composable
fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    IosRow(verticalPadding = 6.dp) {
        Text(title, style = Ios.type.body, color = Ios.colors.label, modifier = Modifier.weight(1f))
        IosSwitch(checked, onCheckedChange)
    }
}

/**
 * `TextField` / `SecureField` in a `Form`: placeholder in tertiary, no autocapitalisation or
 * autocorrection, as the token and code fields set on iOS.
 */
@Composable
fun TextFieldRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    secure: Boolean = false,
    enabled: Boolean = true,
) {
    val colors = Ios.colors
    IosRow(verticalPadding = 11.dp) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(placeholder, style = Ios.type.body, color = colors.tertiaryLabel, maxLines = 1)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                enabled = enabled,
                singleLine = true,
                textStyle = Ios.type.body.copy(color = if (enabled) colors.label else colors.secondaryLabel),
                cursorBrush = SolidColor(colors.tint),
                visualTransformation = if (secure) PasswordVisualTransformation('•') else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = if (secure) KeyboardType.Password else KeyboardType.Ascii,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The `DisclosureGroup` label row: title in the tint, chevron that turns down when open. */
@Composable
fun DisclosureRow(title: String, expanded: Boolean, onToggle: () -> Unit) {
    IosRow(onClick = onToggle) {
        Text(title, style = Ios.type.body, color = Ios.colors.label, modifier = Modifier.weight(1f))
        DisclosureChevron(degrees = if (expanded) 90f else 0f, color = Ios.colors.tint)
    }
}
