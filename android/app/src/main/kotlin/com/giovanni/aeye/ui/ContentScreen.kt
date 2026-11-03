package com.giovanni.aeye.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.giovanni.aeye.AppModel
import com.giovanni.aeye.core.AeyeFormatting
import com.giovanni.aeye.core.OverviewRow
import com.giovanni.aeye.ui.ios.ActivityIndicator
import com.giovanni.aeye.ui.ios.ArrowClockwiseIcon
import com.giovanni.aeye.ui.ios.BarIconButton
import com.giovanni.aeye.ui.ios.BorderedProminentButton
import com.giovanni.aeye.ui.ios.ContextMenuArea
import com.giovanni.aeye.ui.ios.ContextMenuItem
import com.giovanni.aeye.ui.ios.DocOnDocIcon
import com.giovanni.aeye.ui.ios.GearshapeIcon
import com.giovanni.aeye.ui.ios.Ios
import com.giovanni.aeye.ui.ios.IosDivider
import com.giovanni.aeye.ui.ios.IosScreen
import com.giovanni.aeye.ui.ios.LocalSheetController
import kotlinx.coroutines.launch

const val SETTINGS_SHEET = "settings"

@Composable
fun ContentScreen(model: AppModel) {
    val sheets = LocalSheetController.current
    val scope = rememberCoroutineScope()
    val openSettings = { sheets.present(SETTINGS_SHEET) { SettingsScreen(model) } }

    LaunchedEffect(Unit) {
        if (model.sampleData.opensSettingsAtLaunch) openSettings()
    }

    IosScreen(
        title = "Aeye",
        onRefresh = { model.refresh(force = true) },
        leading = {
            BarIconButton("Refresh", onClick = { scope.launch { model.refresh(force = true) } }, enabled = !model.isRefreshing) { color ->
                if (model.isRefreshing) ActivityIndicator(size = 20.dp) else ArrowClockwiseIcon(color)
            }
        },
        trailing = {
            BarIconButton("Settings", onClick = openSettings) { color -> GearshapeIcon(color) }
        },
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(model)
            if (model.needsSetup) {
                SetupCard(onOpenSettings = openSettings, onSample = { model.setSampleData(true) })
            } else {
                OverviewList(model.snapshot.visibleRows)
            }
            Footer(model)
        }
    }
}

@Composable
private fun Header(model: AppModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Keep an 👁️ on Cursor, Claude & Grok Bot", style = Ios.type.subheadline, color = Ios.colors.secondaryLabel)
        if (model.snapshot.isMockData) {
            SampleDataBadge(Modifier.padding(top = 2.dp))
        } else {
            Text("Same rows as the Alfred workflow", style = Ios.type.caption, color = Ios.colors.tertiaryLabel)
        }
    }
}

@Composable
private fun SetupCard(onOpenSettings: () -> Unit, onSample: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Ios.colors.tertiaryFill, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Sign in to get started", style = Ios.type.headline, color = Ios.colors.label)
        Text(
            "Open Settings and tap Sign in to Cursor and/or Claude. Same accounts as on your Mac — no API key, and nothing is sent to an Aeye server.",
            style = Ios.type.caption,
            color = Ios.colors.secondaryLabel,
        )
        BorderedProminentButton("Open Settings", onClick = onOpenSettings)
        LinkText("See it with sample data", style = Ios.type.caption, onClick = onSample)
    }
}

@Composable
private fun Footer(model: AppModel) {
    val snapshot = model.snapshot
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val error = model.lastError
        if (error != null && !model.needsSetup) {
            Text(error, style = Ios.type.caption, color = Ios.colors.red)
        }
        if (snapshot.hasCapture) {
            Text("Updated ${shortTime(LocalContext.current, snapshot.capturedAt)}", style = Ios.type.caption2, color = Ios.colors.tertiaryLabel)
        }
        if (snapshot.isMockData) {
            Text(
                "These are sample numbers, not your usage. Turn off “Use sample data” in Settings to see the real thing.",
                style = Ios.type.caption,
                color = Ios.colors.secondaryLabel,
            )
        } else {
            Text(
                "Add the Aeye widget from your Home Screen for the same view at a glance.",
                style = Ios.type.caption,
                color = Ios.colors.secondaryLabel,
            )
        }
    }
}

/** Marks every surface that is showing `MockSnapshot` rather than real usage. */
@Composable
fun SampleDataBadge(modifier: Modifier = Modifier) {
    val orange = Ios.colors.orange
    Text(
        "Sample data",
        style = Ios.type.caption2.copy(fontWeight = FontWeight.SemiBold),
        color = orange,
        modifier = modifier
            .background(orange.copy(alpha = 0.18f), CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .semantics { contentDescription = "Showing sample data, not your real usage" },
    )
}

@Composable
fun OverviewList(rows: List<OverviewRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            OverviewRowView(row)
            if (row.id != rows.last().id) IosDivider()
        }
    }
}

@Composable
fun OverviewRowView(row: OverviewRow) {
    val context = LocalContext.current
    val items = if (row.copyText.isNotEmpty()) {
        listOf(ContextMenuItem("Copy", { DocOnDocIcon(it) }) { copyToClipboard(context, row.copyText) })
    } else {
        emptyList()
    }
    ContextMenuArea(items) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (row.isError) {
                Text(row.titleLine, style = Ios.type.bodyMono, color = Ios.colors.red)
            } else {
                // Split rather than one wrapping line: the label plus ten meter slots plus the
                // percent never fits a phone's width.
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Text(row.label, style = Ios.type.bodyMono.copy(fontWeight = FontWeight.SemiBold), color = Ios.colors.label)
                    Spacer(Modifier.weight(1f).width(8.dp))
                    Text(AeyeFormatting.percentString(row.percentUsed), style = Ios.type.bodyMono, color = Ios.colors.label)
                }
                MeterView(row.percentUsed)
                val detail = row.suffix?.let { AeyeFormatting.standaloneSuffix(it) }.orEmpty()
                if (detail.isNotEmpty()) {
                    Text(detail, style = Ios.type.caption, color = Ios.colors.secondaryLabel)
                }
            }
            if (row.subtitle.isNotEmpty()) {
                Text(row.subtitle, style = Ios.type.caption, color = Ios.colors.secondaryLabel)
            }
            val url = row.dashboardURL
            if (url != null && !row.isError) {
                LinkText("Open dashboard", style = Ios.type.caption) { openInBrowser(context, url) }
            }
        }
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Aeye", text))
}
