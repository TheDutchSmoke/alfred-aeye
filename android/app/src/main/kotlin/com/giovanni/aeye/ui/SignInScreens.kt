package com.giovanni.aeye.ui

import android.content.Intent
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.giovanni.aeye.MainActivity
import com.giovanni.aeye.core.ClaudeOAuth
import com.giovanni.aeye.core.CursorAuth
import com.giovanni.aeye.core.CursorClient
import com.giovanni.aeye.core.HttpTransport
import com.giovanni.aeye.core.errorMessage
import com.giovanni.aeye.ui.ios.ActivityIndicator
import com.giovanni.aeye.ui.ios.ButtonRow
import com.giovanni.aeye.ui.ios.CaptionRow
import com.giovanni.aeye.ui.ios.ContentRow
import com.giovanni.aeye.ui.ios.FormSection
import com.giovanni.aeye.ui.ios.Ios
import com.giovanni.aeye.ui.ios.IosScreen
import com.giovanni.aeye.ui.ios.IosTextButton
import com.giovanni.aeye.ui.ios.LocalDismiss
import com.giovanni.aeye.ui.ios.RowIconInset
import com.giovanni.aeye.ui.ios.SafariIcon
import com.giovanni.aeye.ui.ios.TextFieldRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Claude sign-in, in a Custom Tab rather than an embedded web view: Google and the other SSO
 * providers refuse to serve OAuth inside a `WebView`, and the tab shares the browser's cookies so
 * an existing claude.ai login carries over.
 *
 * The Claude Code OAuth client only registers the copy-the-code redirect, so the last step is a
 * paste back into the app.
 */
@Composable
fun ClaudeSignInScreen(transport: HttpTransport, onCapture: (ClaudeOAuth.Tokens) -> Unit) {
    val dismiss = LocalDismiss.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var attempt by remember { mutableStateOf<ClaudeOAuth.Attempt?>(null) }
    var pasted by remember { mutableStateOf("") }
    var isExchanging by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val canFinish = attempt != null && pasted.isNotBlank() && !isExchanging

    fun startAuthorization() {
        error = null
        val next = ClaudeOAuth.begin()
        attempt = next
        // No redirect comes back to the app for this client — the user copies the code and
        // closes the tab. Returning without one is the expected ending, not an error.
        if (!openSignInTab(context, next.url)) {
            attempt = null
            error = "Could not open your browser for sign-in."
        }
    }

    fun finish() {
        val current = attempt ?: return
        isExchanging = true
        error = null
        scope.launch {
            try {
                val tokens = ClaudeOAuth.exchange(pasted, current, transport)
                onCapture(tokens)
                isExchanging = false
                dismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isExchanging = false
                error = errorMessage(e)
            }
        }
    }

    IosScreen(
        title = "Sign in to Claude",
        background = Ios.colors.groupedBackground,
        largeTitle = false,
        insetStatusBar = false,
        leading = { IosTextButton("Cancel", onClick = dismiss) },
    ) {
        FormSection(topSpacing = 16.dp) {
            row {
                CaptionRow("Aeye signs in with the same Claude Code OAuth flow your Mac uses, in your browser so Google and SSO work. Approve access, copy the code Claude shows you, then paste it below.")
            }
        }

        FormSection(header = "Step 1") {
            row(separatorInset = RowIconInset) {
                ButtonRow(
                    if (attempt == null) "Open Claude sign-in" else "Open Claude sign-in again",
                    enabled = !isExchanging,
                    icon = { SafariIcon(it) },
                ) { startAuthorization() }
            }
        }

        FormSection(header = "Step 2") {
            row { TextFieldRow(pasted, { pasted = it }, "Paste the code Claude showed", enabled = attempt != null) }
            row { ButtonRow("Paste from clipboard", enabled = attempt != null) { pasted = clipboardText(context) } }
            row {
                if (isExchanging) {
                    ContentRow { ActivityIndicator() }
                } else {
                    ButtonRow("Finish sign-in", enabled = canFinish) { finish() }
                }
            }
        }

        error?.let { message ->
            FormSection {
                row { CaptionRow(message, color = Ios.colors.red) }
            }
        }
    }
}

/**
 * Cursor sign-in through the browser, for the same reason as [ClaudeSignInScreen]. Cursor's flow
 * polls for the result, so unlike Claude there is nothing to paste — finishing in the browser is
 * enough, and Aeye comes back to the front on its own.
 */
@Composable
fun CursorSignInScreen(transport: HttpTransport, onCapture: (String) -> Unit) {
    val dismiss = LocalDismiss.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var status by remember { mutableStateOf("Not started") }
    var isWaiting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pollJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            pollJob?.cancel()
            pollJob = null
        }
    }

    fun start() {
        error = null
        pollJob?.cancel()

        val attempt = CursorAuth.begin()
        // No redirect to catch — Cursor hands the result back over the poll endpoint.
        if (!openSignInTab(context, attempt.url)) {
            error = "Could not open your browser for sign-in."
            return
        }

        isWaiting = true
        status = "Waiting for you to finish in your browser…"
        pollJob = scope.launch {
            try {
                val token = CursorAuth.awaitToken(attempt, transport)
                onCapture(CursorClient.normalizeCookieValue(token))
                isWaiting = false
                status = "Signed in"
                // Close the browser tab and bring Aeye back, as the finished auth session does.
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                dismiss()
            } catch (e: CancellationException) {
                // Sheet closed; nothing to report.
            } catch (e: Exception) {
                isWaiting = false
                status = "Not signed in"
                error = errorMessage(e)
            }
        }
    }

    IosScreen(
        title = "Sign in to Cursor",
        background = Ios.colors.groupedBackground,
        largeTitle = false,
        insetStatusBar = false,
        leading = { IosTextButton("Cancel", onClick = dismiss) },
    ) {
        FormSection(topSpacing = 16.dp) {
            row {
                CaptionRow("Aeye opens Cursor's sign-in in your browser, so Google and the other providers work. Finish there and come back — Aeye picks up the session on its own.")
            }
        }

        FormSection {
            row(separatorInset = RowIconInset) {
                ButtonRow(
                    if (isWaiting) "Open Cursor sign-in again" else "Open Cursor sign-in",
                    icon = { SafariIcon(it) },
                ) { start() }
            }
            row {
                ContentRow {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isWaiting) {
                            ActivityIndicator()
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(status, style = Ios.type.caption, color = Ios.colors.secondaryLabel)
                    }
                }
            }
        }

        error?.let { message ->
            FormSection {
                row { CaptionRow(message, color = Ios.colors.red) }
            }
        }
    }
}
