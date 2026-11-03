import Foundation

/// Sample data for SwiftUI previews, widget galleries, screenshot runs, and the
/// user-facing "Use sample data" mode.
///
/// Rows are composed through ``AeyeFormatting`` rather than hardcoded strings so
/// the sample always matches the real bar width and title format. Periods are
/// relative to *now*, so the reset countdowns stay plausible however long the
/// app has been installed.
public enum MockSnapshot {
    /// Every row enabled, unbadged — previews and widget galleries.
    public static var preview: AeyeSnapshot { sample(markAsSample: false) }

    /// Sample rows honouring the user's row choices, so toggling a row off in
    /// Settings changes the sample view the same way it changes the real one.
    ///
    /// - Parameter markAsSample: flags the snapshot so every surface shows its
    ///   "Sample data" chip. Screenshot runs pass `false`: those images are meant
    ///   to depict the app as a signed-in user sees it, not to advertise the demo.
    public static func sample(
        visibility: RowVisibility = .allEnabled,
        markAsSample: Bool = true
    ) -> AeyeSnapshot {
        AeyeSnapshot(
            capturedAt: Date(),
            rows: [
                row(
                    id: .composerAuto,
                    percent: 13.0,
                    suffix: AeyeFormatting.periodSuffix(
                        end: date(daysFromNow: 11),
                        start: date(daysFromNow: -20),
                        spentPercent: 13.0,
                        style: .calendar
                    ),
                    subtitle: "You've used 13% of your included total usage · $2.60 / $20.00",
                    copy: "Cursor Composer/Auto",
                    dashboard: AeyeURLs.cursorDashboard
                ),
                row(
                    id: .otherModels,
                    percent: 4.0,
                    suffix: AeyeFormatting.periodSuffix(
                        end: date(daysFromNow: 11),
                        start: date(daysFromNow: -20),
                        spentPercent: 4.0,
                        style: .calendar
                    ),
                    subtitle: "You've used 4% of your included API usage",
                    copy: "Cursor other models",
                    dashboard: AeyeURLs.cursorDashboard
                ),
                row(
                    id: .grokBot,
                    percent: 1.0,
                    suffix: AeyeFormatting.periodSuffix(
                        end: date(daysFromNow: 6),
                        start: date(daysFromNow: -1),
                        spentPercent: 1.0,
                        style: .weekday
                    ),
                    subtitle: "Pro · Grok Bot Plan · weekly included",
                    copy: "Grok Bot",
                    dashboard: AeyeURLs.cursorDashboard
                ),
                row(
                    id: .hourly,
                    percent: 42.0,
                    suffix: AeyeFormatting.periodSuffix(
                        end: date(hoursFromNow: 2),
                        start: date(hoursFromNow: -3),
                        spentPercent: 42.0,
                        style: .clock
                    ),
                    subtitle: "plan=pro · experimental",
                    copy: "Claude hourly",
                    dashboard: AeyeURLs.claudeUsage
                ),
                row(
                    id: .weekly,
                    percent: 18.0,
                    suffix: AeyeFormatting.periodSuffix(
                        end: date(daysFromNow: 6),
                        start: date(daysFromNow: -1),
                        spentPercent: 18.0,
                        style: .weekday
                    ),
                    subtitle: "plan=pro · experimental",
                    copy: "Claude weekly",
                    dashboard: AeyeURLs.claudeUsage
                ),
            ],
            visibility: visibility,
            isMockData: markAsSample
        )
    }

    private static func row(
        id: AeyeRowID,
        percent: Double,
        suffix: String,
        subtitle: String,
        copy: String,
        dashboard: URL?
    ) -> OverviewRow {
        OverviewRow(
            id: id,
            label: id.label,
            percentUsed: percent,
            titleLine: AeyeFormatting.titleLine(label: id.label, percent: percent, suffix: suffix),
            subtitle: subtitle,
            copyText: "\(copy): \(AeyeFormatting.percentString(percent))\(suffix)",
            dashboardURL: dashboard,
            suffix: suffix
        )
    }

    private static func date(daysFromNow: Int = 0, hoursFromNow: Int = 0) -> Date {
        Date().addingTimeInterval(Double(daysFromNow) * 86_400 + Double(hoursFromNow) * 3_600)
    }
}

/// Shows ``MockSnapshot`` instead of live numbers, so Aeye can be tried,
/// demonstrated, and App Review–tested without a Cursor or Claude account.
///
/// The choice is persisted in the App Group, which means the widget and the
/// Watch see the sample too — they read whatever snapshot the app last wrote.
/// Unlike the old screenshot-only path this ships in release builds; every
/// surface labels the data as a sample so it cannot be mistaken for real usage.
public enum SampleDataMode {
    public static var isEnabled: Bool {
        get { isForcedByLaunchArgument || SnapshotStore.isSampleDataEnabled }
        set { SnapshotStore.isSampleDataEnabled = newValue }
    }

    /// `--screenshot` still forces the sample on for automated screenshot runs,
    /// without writing the user's preference.
    public static var isForcedByLaunchArgument: Bool {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        return args.contains("--screenshot") || args.contains("--screenshot-settings")
        #else
        false
        #endif
    }

    /// `--screenshot-settings` does the same and opens the Settings sheet, so the
    /// Settings shot can be captured from `simctl` without driving a tap.
    public static var opensSettingsAtLaunch: Bool {
        #if DEBUG
        ProcessInfo.processInfo.arguments.contains("--screenshot-settings")
        #else
        false
        #endif
    }
}
