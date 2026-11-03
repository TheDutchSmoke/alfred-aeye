package com.giovanni.aeye.core

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Overview rows — same order and labels as the Alfred workflow. */
@Serializable
enum class AeyeRowID(val rawValue: String) {
    @SerialName("cursor_auto") COMPOSER_AUTO("cursor_auto"),
    @SerialName("cursor_other") OTHER_MODELS("cursor_other"),
    @SerialName("cursor_grok") GROK_BOT("cursor_grok"),
    @SerialName("claude_hourly") HOURLY("claude_hourly"),
    @SerialName("claude_weekly") WEEKLY("claude_weekly");

    val label: String
        get() = when (this) {
            COMPOSER_AUTO -> "Composer / Auto"
            OTHER_MODELS -> "Other models"
            GROK_BOT -> "Grok Bot"
            HOURLY -> "Hourly"
            WEEKLY -> "Weekly"
        }

    /** Short label for watch faces and complications. */
    val shortLabel: String
        get() = when (this) {
            COMPOSER_AUTO -> "Auto"
            OTHER_MODELS -> "Other"
            GROK_BOT -> "Grok"
            HOURLY -> "Hourly"
            WEEKLY -> "Weekly"
        }

    /**
     * Which service the row comes from — shown next to the short label on the watch, where
     * "Auto" and "Hourly" alone do not say who they belong to.
     */
    val provider: String get() = if (isCursor) "Cursor" else "Claude"

    val isCursor: Boolean
        get() = when (this) {
            COMPOSER_AUTO, OTHER_MODELS, GROK_BOT -> true
            HOURLY, WEEKLY -> false
        }
}

@Serializable
data class RowVisibility(
    val cursorAuto: Boolean,
    val cursorOther: Boolean,
    // Added after the first release; older stored choices decode with it on.
    val cursorGrokBot: Boolean = true,
    val claudeHourly: Boolean,
    val claudeWeekly: Boolean,
) {
    fun isEnabled(row: AeyeRowID): Boolean = when (row) {
        AeyeRowID.COMPOSER_AUTO -> cursorAuto
        AeyeRowID.OTHER_MODELS -> cursorOther
        AeyeRowID.GROK_BOT -> cursorGrokBot
        AeyeRowID.HOURLY -> claudeHourly
        AeyeRowID.WEEKLY -> claudeWeekly
    }

    val enabledRows: List<AeyeRowID> get() = AeyeRowID.entries.filter { isEnabled(it) }

    companion object {
        val allEnabled = RowVisibility(
            cursorAuto = true,
            cursorOther = true,
            cursorGrokBot = true,
            claudeHourly = true,
            claudeWeekly = true,
        )
    }
}

@Serializable
data class OverviewRow(
    val id: AeyeRowID,
    val label: String,
    val percentUsed: Double? = null,
    val titleLine: String,
    val subtitle: String,
    val copyText: String,
    val isError: Boolean = false,
    val dashboardURL: String? = null,
    /**
     * The trailing `(🕐 …)` detail, kept apart from [titleLine] so wider layouts can give it its
     * own line. Optional so older cached snapshots still decode.
     */
    val suffix: String? = null,
)

@Serializable
data class AeyeSnapshot(
    @Serializable(with = Iso8601InstantSerializer::class)
    val capturedAt: Instant = Instant.now(),
    val rows: List<OverviewRow>,
    val visibility: RowVisibility = RowVisibility.allEnabled,
    val isMockData: Boolean = false,
) {
    /** Error rows stay visible even if that source's toggle is off. */
    val visibleRows: List<OverviewRow>
        get() = rows.filter { it.isError || visibility.isEnabled(it.id) }

    /** `capturedAt > .distantPast` on iOS: whether anything has been captured yet. */
    val hasCapture: Boolean get() = capturedAt.isAfter(DISTANT_PAST)

    companion object {
        /** `Date.distantPast` — what an empty snapshot is stamped with. */
        val DISTANT_PAST: Instant = Instant.parse("0001-01-01T00:00:00Z")

        val empty = AeyeSnapshot(
            capturedAt = DISTANT_PAST,
            rows = emptyList(),
            visibility = RowVisibility.allEnabled,
            isMockData = false,
        )
    }
}

object AeyeURLs {
    const val CURSOR_DASHBOARD = "https://cursor.com/dashboard"
    const val CLAUDE_USAGE = "https://claude.ai/settings/usage"
}

/**
 * `JSONEncoder.dateEncodingStrategy = .iso8601`: whole seconds, UTC, `Z`. Decoding accepts
 * fractions too, so a snapshot written by either platform reads on the other.
 */
object Iso8601InstantSerializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Iso8601Instant", PrimitiveKind.STRING)
    override fun serialize(encoder: Encoder, value: Instant) {
        encoder.encodeString(value.truncatedTo(ChronoUnit.SECONDS).toString())
    }
    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}

/** The snapshot's on-disk and over-the-air format — the same JSON the iOS `Codable` types write. */
object SnapshotCodec {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun encode(snapshot: AeyeSnapshot): String = json.encodeToString(AeyeSnapshot.serializer(), snapshot)

    fun decode(text: String): AeyeSnapshot? =
        try {
            json.decodeFromString(AeyeSnapshot.serializer(), text)
        } catch (_: Exception) {
            null
        }

    fun encodeVisibility(visibility: RowVisibility): String =
        json.encodeToString(RowVisibility.serializer(), visibility)

    fun decodeVisibility(text: String): RowVisibility? =
        try {
            json.decodeFromString(RowVisibility.serializer(), text)
        } catch (_: Exception) {
            null
        }
}
