package com.giovanni.aeye.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.giovanni.aeye.AppLinks
import com.giovanni.aeye.AppModel
import com.giovanni.aeye.core.errorMessage
import com.giovanni.aeye.ui.ios.ButtonRow
import com.giovanni.aeye.ui.ios.CaptionRow
import com.giovanni.aeye.ui.ios.DisclosureRow
import com.giovanni.aeye.ui.ios.FormSection
import com.giovanni.aeye.ui.ios.Ios
import com.giovanni.aeye.ui.ios.IosScreen
import com.giovanni.aeye.ui.ios.IosTextButton
import com.giovanni.aeye.ui.ios.LabeledRow
import com.giovanni.aeye.ui.ios.LocalDismiss
import com.giovanni.aeye.ui.ios.LocalSheetController
import com.giovanni.aeye.ui.ios.PersonBadgeCheckIcon
import com.giovanni.aeye.ui.ios.RowIconInset
import com.giovanni.aeye.ui.ios.SectionFooterText
import com.giovanni.aeye.ui.ios.TextFieldRow
import com.giovanni.aeye.ui.ios.ToggleRow

private const val CURSOR_SIGN_IN_SHEET = "cursor-sign-in"
private const val CLAUDE_SIGN_IN_SHEET = "claude-sign-in"

@Composable
fun SettingsScreen(model: AppModel) {
    val dismiss = LocalDismiss.current
    val sheets = LocalSheetController.current
    val context = LocalContext.current

    var cursorToken by rememberSaveable { mutableStateOf("") }
    var claudeToken by rememberSaveable { mutableStateOf("") }
    var visibility by remember { mutableStateOf(model.visibility) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { visibility = model.visibility }

    fun save() {
        try {
            if (cursorToken.isNotBlank()) model.saveCursorToken(cursorToken)
            if (claudeToken.isNotBlank()) model.saveClaudeToken(claudeToken)
            model.updateVisibility(visibility)
            dismiss()
        } catch (e: Exception) {
            saveError = errorMessage(e)
        }
    }

    val openCursorSignIn = {
        sheets.present(CURSOR_SIGN_IN_SHEET) {
            CursorSignInScreen(
                transport = model.transport,
                onCapture = { token ->
                    model.saveCursorToken(token)
                    model.refreshInBackground(force = true)
                },
            )
        }
    }
    val openClaudeSignIn = {
        sheets.present(CLAUDE_SIGN_IN_SHEET) {
            ClaudeSignInScreen(
                transport = model.transport,
                onCapture = { tokens ->
                    model.saveClaudeTokens(tokens)
                    model.refreshInBackground(force = true)
                },
            )
        }
    }

    IosScreen(
        title = "Settings",
        background = Ios.colors.groupedBackground,
        insetStatusBar = false,
        leading = { IosTextButton("Close", onClick = dismiss) },
    ) {
        FormSection(topSpacing = 8.dp) {
            row { CaptionRow("Sign in on this phone. Credentials stay in the Android Keystore — there is no Aeye account or backend.") }
        }

        FormSection(header = "Cursor") {
            row { StatusRow(model.cursorConfigured) }
            row(separatorInset = RowIconInset) {
                ButtonRow(
                    if (model.cursorConfigured) "Sign in again" else "Sign in to Cursor",
                    icon = { PersonBadgeCheckIcon(it) },
                    onClick = openCursorSignIn,
                )
            }
            if (model.cursorConfigured) {
                row {
                    ButtonRow("Remove Cursor", color = Ios.colors.red) {
                        try {
                            model.clearCursorToken()
                            cursorToken = ""
                            saveError = null
                        } catch (e: Exception) {
                            saveError = errorMessage(e)
                        }
                    }
                }
            }
        }

        FormSection(header = "Claude") {
            row { StatusRow(model.claudeConfigured) }
            row(separatorInset = RowIconInset) {
                ButtonRow(
                    if (model.claudeConfigured) "Sign in again" else "Sign in to Claude",
                    icon = { PersonBadgeCheckIcon(it) },
                    onClick = openClaudeSignIn,
                )
            }
            row {
                CaptionRow("Opens your browser for the same OAuth flow Claude Code uses, then asks you to paste the code it shows. Yields the same token Alfred already reads on your Mac, and Aeye refreshes it for you.")
            }
            if (model.claudeConfigured) {
                row {
                    ButtonRow("Remove Claude", color = Ios.colors.red) {
                        try {
                            model.clearClaudeToken()
                            claudeToken = ""
                            saveError = null
                        } catch (e: Exception) {
                            saveError = errorMessage(e)
                        }
                    }
                }
            }
        }

        FormSection(header = "Visible rows") {
            row { ToggleRow("Composer / Auto", visibility.cursorAuto) { visibility = visibility.copy(cursorAuto = it) } }
            row { ToggleRow("Other models", visibility.cursorOther) { visibility = visibility.copy(cursorOther = it) } }
            row { ToggleRow("Grok Bot (weekly)", visibility.cursorGrokBot) { visibility = visibility.copy(cursorGrokBot = it) } }
            row { ToggleRow("Hourly (5h)", visibility.claudeHourly) { visibility = visibility.copy(claudeHourly = it) } }
            row { ToggleRow("Weekly", visibility.claudeWeekly) { visibility = visibility.copy(claudeWeekly = it) } }
        }

        FormSection(header = "Sample data") {
            row { ToggleRow("Use sample data", model.isSampleData) { model.setSampleData(it) } }
            row {
                CaptionRow("Fills every row with representative numbers so you can see how Aeye looks without signing in. The widget and the watch show the sample too, each labelled “Sample”. Turn it off to go back to your real usage.")
            }
        }

        FormSection {
            row { DisclosureRow("Advanced — paste a token", showAdvanced) { showAdvanced = !showAdvanced } }
            if (showAdvanced) {
                row { TextFieldRow(cursorToken, { cursorToken = it }, "Cursor WorkosCursorSessionToken", secure = true) }
                row { ButtonRow("Use clipboard for Cursor") { cursorToken = clipboardText(context) } }
                row { TextFieldRow(claudeToken, { claudeToken = it }, "Claude Code OAuth token", secure = true) }
                row { ButtonRow("Use clipboard for Claude") { claudeToken = clipboardText(context) } }
                row {
                    CaptionRow("On a Mac, Alfred/Aeye reads these automatically from Cursor and Claude Code. Copy from there only if in-app sign-in doesn’t catch the session.")
                }
            }
        }

        saveError?.let { error ->
            FormSection {
                row { CaptionRow(error, color = Ios.colors.red) }
            }
        }

        FormSection {
            row { ButtonRow("Save row choices", enabled = visibility.enabledRows.isNotEmpty()) { save() } }
        }

        FormSection(
            header = "About",
            footer = {
                SectionFooterText("Aeye is an independent companion app and is not affiliated with, endorsed by, or sponsored by Cursor, Anthropic, or Alfred. Those names appear only to identify which of your own accounts each row refers to.")
            },
        ) {
            row { LabeledRow("Version", AppLinks.version) }
            row { ButtonRow("Privacy policy") { openInBrowser(context, AppLinks.PRIVACY) } }
            row { ButtonRow("Support") { openInBrowser(context, AppLinks.SUPPORT) } }
        }
    }
}

@Composable
private fun StatusRow(saved: Boolean) {
    LabeledRow("Status", if (saved) "Signed in" else "Not signed in", if (saved) Ios.colors.green else Ios.colors.secondaryLabel)
}

fun clipboardText(context: Context): String {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return clipboard.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
}
