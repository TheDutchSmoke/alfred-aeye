import SwiftUI
import UIKit

struct ContentView: View {
    @Environment(AppModel.self) private var model
    @State private var showSettings = SampleDataMode.opensSettingsAtLaunch

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    header
                    if model.needsSetup {
                        setupCard
                    } else {
                        OverviewListView(rows: model.snapshot.visibleRows)
                    }
                    footer
                }
                .padding()
            }
            .refreshable {
                await model.refresh(force: true)
            }
            .navigationTitle("Aeye")
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        Task { await model.refresh(force: true) }
                    } label: {
                        if model.isRefreshing {
                            ProgressView()
                        } else {
                            Image(systemName: "arrow.clockwise")
                        }
                    }
                    .disabled(model.isRefreshing)
                    .accessibilityLabel("Refresh")
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showSettings = true
                    } label: {
                        Image(systemName: "gearshape")
                    }
                    .accessibilityLabel("Settings")
                }
            }
            .sheet(isPresented: $showSettings) {
                SettingsView()
            }
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Keep an 👁️ on Cursor, Claude & Grok Bot")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            if model.snapshot.isMockData {
                SampleDataBadge()
                    .padding(.top, 2)
            } else {
                Text("Same rows as the Alfred workflow")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
    }

    private var setupCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Sign in to get started")
                .font(.headline)
            Text("Open Settings and tap Sign in to Cursor and/or Claude. Same accounts as on your Mac — no API key, and nothing is sent to an Aeye server.")
                .font(.caption)
                .foregroundStyle(.secondary)
            Button("Open Settings") {
                showSettings = true
            }
            .buttonStyle(.borderedProminent)
            Button("See it with sample data") {
                model.setSampleData(true)
            }
            .font(.caption)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(.fill.tertiary, in: RoundedRectangle(cornerRadius: 12))
    }

    private var footer: some View {
        VStack(alignment: .leading, spacing: 8) {
            if let error = model.lastError, !model.needsSetup {
                Text(error)
                    .font(.caption)
                    .foregroundStyle(.red)
            }
            if model.snapshot.capturedAt > .distantPast {
                Text("Updated \(model.snapshot.capturedAt.formatted(date: .omitted, time: .shortened))")
                    .font(.caption2)
                    .foregroundStyle(.tertiary)
            }
            if model.snapshot.isMockData {
                Text("These are sample numbers, not your usage. Turn off “Use sample data” in Settings to see the real thing.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            } else {
                Text("Add the Aeye widget from your Home Screen for the same view at a glance.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
    }
}

/// Marks every surface that is showing ``MockSnapshot`` rather than real usage.
struct SampleDataBadge: View {
    var body: some View {
        Text("Sample data")
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 7)
            .padding(.vertical, 2)
            .background(.orange.opacity(0.18), in: Capsule())
            .foregroundStyle(.orange)
            .accessibilityLabel("Showing sample data, not your real usage")
    }
}

struct OverviewListView: View {
    let rows: [OverviewRow]

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            ForEach(rows) { row in
                OverviewRowView(row: row)
                if row.id != rows.last?.id {
                    Divider()
                }
            }
        }
    }
}

struct OverviewRowView: View {
    let row: OverviewRow

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if row.isError {
                Text(row.titleLine)
                    .font(.system(.body, design: .monospaced))
                    .foregroundStyle(.red)
                    .textSelection(.enabled)
            } else {
                // Split rather than one wrapping line: the label plus ten emoji
                // slots plus the percent never fits a phone's width.
                HStack(alignment: .firstTextBaseline) {
                    Text(row.label)
                        .font(.system(.body, design: .monospaced).weight(.semibold))
                    Spacer(minLength: 8)
                    Text(AeyeFormatting.percentString(row.percentUsed))
                        .font(.system(.body, design: .monospaced))
                }
                .textSelection(.enabled)

                Text(AeyeFormatting.bar(percent: row.percentUsed))
                    .font(.system(size: 16))
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)

                if let suffix = row.suffix {
                    let detail = AeyeFormatting.standaloneSuffix(suffix)
                    if !detail.isEmpty {
                        Text(detail)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .textSelection(.enabled)
                    }
                }
            }

            if !row.subtitle.isEmpty {
                Text(row.subtitle)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .textSelection(.enabled)
            }

            if let url = row.dashboardURL, !row.isError {
                Link("Open dashboard", destination: url)
                    .font(.caption)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .contextMenu {
            if !row.copyText.isEmpty {
                Button {
                    UIPasteboard.general.string = row.copyText
                } label: {
                    Label("Copy", systemImage: "doc.on.doc")
                }
            }
        }
    }
}

#Preview {
    ContentView()
        .environment(AppModel())
}
