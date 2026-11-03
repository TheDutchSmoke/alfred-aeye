import SwiftUI
import UIKit

struct SettingsView: View {
    @Environment(AppModel.self) private var model
    @Environment(\.dismiss) private var dismiss

    @State private var showCursorSignIn = false
    @State private var showClaudeSignIn = false
    @State private var cursorToken = ""
    @State private var claudeToken = ""
    @State private var visibility: RowVisibility = SnapshotStore.loadVisibility()
    @State private var saveError: String?
    @State private var showAdvanced = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Sign in on this iPhone. Credentials stay in the Keychain — there is no Aeye account or backend.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Section("Cursor") {
                    labeledStatus(saved: model.cursorConfigured)
                    Button {
                        showCursorSignIn = true
                    } label: {
                        Label(
                            model.cursorConfigured ? "Sign in again" : "Sign in to Cursor",
                            systemImage: "person.crop.circle.badge.checkmark"
                        )
                    }
                    if model.cursorConfigured {
                        Button("Remove Cursor", role: .destructive) {
                            do {
                                try model.clearCursorToken()
                                cursorToken = ""
                                saveError = nil
                            } catch {
                                saveError = error.localizedDescription
                            }
                        }
                    }
                }

                Section("Claude") {
                    labeledStatus(saved: model.claudeConfigured)
                    Button {
                        showClaudeSignIn = true
                    } label: {
                        Label(
                            model.claudeConfigured ? "Sign in again" : "Sign in to Claude",
                            systemImage: "person.crop.circle.badge.checkmark"
                        )
                    }
                    Text("Opens Safari for the same OAuth flow Claude Code uses, then asks you to paste the code it shows. Yields the same token Alfred already reads on your Mac, and Aeye refreshes it for you.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    if model.claudeConfigured {
                        Button("Remove Claude", role: .destructive) {
                            do {
                                try model.clearClaudeToken()
                                claudeToken = ""
                                saveError = nil
                            } catch {
                                saveError = error.localizedDescription
                            }
                        }
                    }
                }

                Section("Visible rows") {
                    Toggle("Composer / Auto", isOn: $visibility.cursorAuto)
                    Toggle("Other models", isOn: $visibility.cursorOther)
                    Toggle("Grok Bot (weekly)", isOn: $visibility.cursorGrokBot)
                    Toggle("Hourly (5h)", isOn: $visibility.claudeHourly)
                    Toggle("Weekly", isOn: $visibility.claudeWeekly)
                }

                Section("Sample data") {
                    Toggle("Use sample data", isOn: Binding(
                        get: { model.isSampleData },
                        set: { model.setSampleData($0) }
                    ))
                    Text("Fills every row with representative numbers so you can see how Aeye looks without signing in. The widget and the Watch show the sample too, each labelled “Sample”. Turn it off to go back to your real usage.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                DisclosureGroup("Advanced — paste a token", isExpanded: $showAdvanced) {
                    SecureField("Cursor WorkosCursorSessionToken", text: $cursorToken)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button("Use clipboard for Cursor") {
                        cursorToken = UIPasteboard.general.string ?? ""
                    }
                    SecureField("Claude Code OAuth token", text: $claudeToken)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button("Use clipboard for Claude") {
                        claudeToken = UIPasteboard.general.string ?? ""
                    }
                    Text("On a Mac, Alfred/Aeye reads these automatically from Cursor and Claude Code. Copy from there only if in-app sign-in doesn’t catch the session.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                if let saveError {
                    Section {
                        Text(saveError)
                            .font(.caption)
                            .foregroundStyle(.red)
                    }
                }

                Section {
                    Button("Save row choices") {
                        save()
                    }
                    .disabled(visibility.enabledRows.isEmpty)
                }

                Section {
                    LabeledContent("Version", value: Self.versionString)
                    Link("Privacy policy", destination: Self.privacyPolicyURL)
                    Link("Support", destination: Self.supportURL)
                } header: {
                    Text("About")
                } footer: {
                    Text("Aeye is an independent companion app and is not affiliated with, endorsed by, or sponsored by Cursor, Anthropic, or Alfred. Those names appear only to identify which of your own accounts each row refers to.")
                }
            }
            .navigationTitle("Settings")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
            }
            .onAppear {
                visibility = model.visibility
            }
            .sheet(isPresented: $showCursorSignIn) {
                CursorSignInView { token in
                    try model.saveCursorToken(token)
                    Task { await model.refresh(force: true) }
                }
            }
            .sheet(isPresented: $showClaudeSignIn) {
                ClaudeSignInView { tokens in
                    try model.saveClaudeTokens(tokens)
                    Task { await model.refresh(force: true) }
                }
            }
        }
    }

    // App Review expects the disclaimer and both URLs to be reachable from
    // inside the app, not only from the landing page — see docs/app-store-submission.md.
    private static let privacyPolicyURL = URL(
        string: "https://giovannicoppola.github.io/alfred-aeye/ios-landing/privacy.html"
    )!
    private static let supportURL = URL(string: "https://github.com/giovannicoppola/alfred-aeye/issues")!

    private static var versionString: String {
        let info = Bundle.main.infoDictionary
        let short = info?["CFBundleShortVersionString"] as? String ?? "—"
        let build = info?["CFBundleVersion"] as? String ?? "—"
        return "\(short) (\(build))"
    }

    private func labeledStatus(saved: Bool) -> some View {
        HStack {
            Text("Status")
            Spacer()
            Text(saved ? "Signed in" : "Not signed in")
                .foregroundStyle(saved ? .green : .secondary)
        }
    }

    private func save() {
        do {
            if !cursorToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                try model.saveCursorToken(cursorToken)
            }
            if !claudeToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                try model.saveClaudeToken(claudeToken)
            }
            model.updateVisibility(visibility)
            dismiss()
        } catch {
            saveError = error.localizedDescription
        }
    }
}

#Preview {
    SettingsView()
        .environment(AppModel())
}
