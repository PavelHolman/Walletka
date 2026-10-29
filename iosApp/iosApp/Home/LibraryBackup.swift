import SharedLogic
import SwiftUI
import UniformTypeIdentifiers

extension UTType {
    /// A Walletka library backup (`.walletka`, a ZIP archive); declared in Info.plist.
    static let walletkaBackup = UTType(exportedAs: "cz.poqet.walletka.backup", conformingTo: .zip)
}

/// Copies backup files handed to the app into its temporary directory; the shared code reads them from there.
enum BackupFiles {
    static func localCopy(of url: URL) -> String? {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let target = FileManager.default.temporaryDirectory.appendingPathComponent("import-\(UUID().uuidString).walletka")
        do {
            try FileManager.default.copyItem(at: url, to: target)
            return target.path
        } catch {
            return nil
        }
    }
}

/// The exported backup, handed to the system save dialog.
private struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.walletkaBackup] }

    let url: URL

    init(url: URL) { self.url = url }

    init(configuration: ReadConfiguration) throws { throw CocoaError(.featureUnsupported) }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        // Read now: the shared code deletes the temporary file once the dialog is gone.
        try FileWrapper(url: url, options: .immediate)
    }
}

/// The "…" menu of the library: backups and iCloud sync.
struct LibraryMenu: View {
    let component: any HomeComponent
    let cloudSync: CloudSyncStatus?
    var cloudSyncError: String? = nil
    @Binding var isImporting: Bool

    var body: some View {
        Menu {
            Button("Export Library…", systemImage: "square.and.arrow.up") { component.onExportClicked() }
            Button("Import Library…", systemImage: "square.and.arrow.down") { isImporting = true }
            if let cloudSync {
                Section {
                    Toggle(isOn: Binding(
                        get: { cloudSync != .OFF },
                        set: { component.onCloudSyncToggled(enabled: $0) }
                    )) {
                        Label("iCloud Sync", systemImage: "icloud")
                        Text(Self.describe(cloudSync, error: cloudSyncError))
                    }
                    if cloudSync != .OFF && cloudSync != .UNAVAILABLE {
                        Button("Sync Now", systemImage: "arrow.triangle.2.circlepath", action: component.onSyncNowClicked)
                            .disabled(cloudSync == .SYNCING)
                    }
                }
            }
        } label: {
            Label("More", systemImage: "ellipsis.circle")
        }
        .accessibilityLabel(Text("More Options"))
        .accessibilityValue(cloudSync.map { Text(verbatim: String(localized: "iCloud Sync: \(Self.describe($0))")) } ?? Text(verbatim: ""))
        .accessibilityHint(Text("Export, import and iCloud sync"))
    }

    static func describe(_ status: CloudSyncStatus, error: String? = nil) -> String {
        switch status {
        case .OFF: UIDevice.current.userInterfaceIdiom == .pad
            ? String(localized: "Off: cards stay on this iPad")
            : String(localized: "Off: cards stay on this iPhone")
        // An error means iCloud is there but refused (e.g. the app's iCloud access is off); no error means no account.
        case .UNAVAILABLE: error.map { String(localized: "iCloud unavailable: \($0)") } ?? String(localized: "Sign in to iCloud to sync")
        case .SYNCING: String(localized: "Syncing…")
        case .UP_TO_DATE: String(localized: "Up to date")
        case .FAILED: error.map { String(localized: "Sync failed: \($0)") } ?? String(localized: "Sync failed, retrying later")
        @unknown default: ""
        }
    }
}

extension View {
    /// Export (save dialog), import (file picker) and their outcome, driven by `HomeModel.backup`.
    func libraryBackup(component: any HomeComponent, backup: any BackupState, isImporting: Binding<Bool>) -> some View {
        modifier(LibraryBackupModifier(component: component, backup: backup, isImporting: isImporting))
    }

    @ViewBuilder
    func libraryBackup(if condition: Bool, component: any HomeComponent, backup: any BackupState, isImporting: Binding<Bool>) -> some View {
        if condition { libraryBackup(component: component, backup: backup, isImporting: isImporting) } else { self }
    }
}

private struct LibraryBackupModifier: ViewModifier {
    let component: any HomeComponent
    let backup: any BackupState
    @Binding var isImporting: Bool

    private var export: (document: BackupDocument, fileName: String)? {
        if case .exportReady(let ready) = backup.sealedType() {
            return (BackupDocument(url: URL(fileURLWithPath: ready.value.path)), ready.value.fileName)
        }
        return nil
    }

    private var message: (title: String, text: String)? {
        switch backup.sealedType() {
        case .imported(let imported):
            let value = imported.value
            let added = Int(value.added), updated = Int(value.updated), skipped = Int(value.skipped)
            var lines = [String(localized: "\(added) cards added")]
            if updated > 0 { lines.append(String(localized: "\(updated) cards updated")) }
            if skipped > 0 { lines.append(String(localized: "\(skipped) cards already up to date")) }
            lines.append(String(localized: "Add imported cards to Apple Wallet from their editor."))
            return (String(localized: "Library Imported"), lines.joined(separator: "\n"))
        case .failed(let failed):
            let text = switch failed.value.operation {
            case .EXPORT: String(localized: "The library couldn't be saved to a backup file.")
            case .IMPORT: String(localized: "The file couldn't be read. Make sure it's a Walletka backup made by this or an older version of the app.")
            @unknown default: ""
            }
            let title = failed.value.operation == .EXPORT
                ? String(localized: "Couldn't Export the Library")
                : String(localized: "Couldn't Import the Backup")
            // The technical reason (in English) helps when reporting a problem.
            return (title, [text, failed.value.detail].compactMap { $0 }.joined(separator: "\n\n"))
        default:
            return nil
        }
    }

    private var isWorking: Bool {
        if case .inProgress = backup.sealedType() { return true }
        return false
    }

    func body(content: Content) -> some View {
        content
            .overlay {
                if isWorking {
                    ProgressView()
                        .padding(24)
                        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                }
            }
            .fileExporter(
                isPresented: Binding(get: { export != nil }, set: { if !$0 { component.onExportFinished() } }),
                document: export?.document,
                contentType: .walletkaBackup,
                defaultFilename: export?.fileName
            ) { _ in }
            .fileImporter(isPresented: $isImporting, allowedContentTypes: [.walletkaBackup, .zip]) { result in
                if case .success(let url) = result, let path = BackupFiles.localCopy(of: url) {
                    component.onImportFileSelected(path: path)
                }
            }
            .alert(
                message?.title ?? "",
                isPresented: Binding(get: { message != nil }, set: { if !$0 { component.onBackupMessageDismissed() } })
            ) {
                Button("OK") {}
            } message: {
                Text(message?.text ?? "")
            }
    }
}

/// Whether a library card is in Apple Wallet on this iPhone.
struct WalletStatusBadge: View {
    let isInWallet: Bool?
    let color: Color

    var body: some View {
        switch isInWallet {
        case true:
            Image(systemName: "wallet.pass.fill")
                .font(.caption)
                .foregroundStyle(color.opacity(0.8))
                .accessibilityLabel("In Wallet")
        case false:
            Text("Not in Wallet")
                .font(.caption2.weight(.semibold))
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(color.opacity(0.18), in: Capsule())
                .foregroundStyle(color)
        default:
            EmptyView()
        }
    }
}
