package com.giovanni.aeye.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import com.giovanni.aeye.ui.ios.Ios
import java.time.Instant
import java.util.Date

/** `Link(destination:)` — opens the user's browser, outside the app. */
fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        // No browser at all; nothing sensible to do.
    }
}

/**
 * `ASWebAuthenticationSession` with `prefersEphemeralWebBrowserSession = false`: a Custom Tab
 * over the app, sharing the browser's cookies, so an existing sign-in carries over and Google and
 * other SSO providers (which refuse to run inside an app's web view) work. Returns false when there
 * is no browser to open it in.
 */
fun openSignInTab(context: Context, url: String): Boolean =
    try {
        CustomTabsIntent.Builder()
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
            .launchUrl(context, Uri.parse(url))
        true
    } catch (_: ActivityNotFoundException) {
        false
    }

/** `date.formatted(date: .omitted, time: .shortened)` — the user's 12/24-hour preference. */
fun shortTime(context: Context, instant: Instant): String =
    DateFormat.getTimeFormat(context).format(Date.from(instant))

/** A `Link`'s text: tinted, no padding, fades while pressed. */
@Composable
fun LinkText(text: String, style: TextStyle = Ios.type.body, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Text(
        text,
        style = style,
        color = Ios.colors.tint,
        modifier = Modifier
            .alpha(if (pressed) 0.35f else 1f)
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
    )
}
