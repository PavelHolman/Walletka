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
                        Text(Self.describe(cloudSync))
                    }
                }
            }
        } label: {
            Label("More", systemImage: "ellipsis.circle")
        }
    }

    static func describe(_ status: CloudSyncStatus) -> String {
        switch status {
        case .OFF: "Off: cards stay on this iPhone"
        case .UNAVAILABLE: "Sign in to iCloud to sync"
        case .SYNCING: "Syncing…"
        case .UP_TO_DATE: "Up to date"
        case .FAILED: "Sync failed, retrying later"
        @unknown default: ""
        }
    }
}

extension View {
    /// Export (save dialog), import (file picker) and their outcome, driven by `HomeModel.backup`.
    func libraryBackup(component: any HomeComponent, backup: any BackupState, isImporting: Binding<Bool>) -> some View {
        modifier(LibraryBackupModifier(component: component, backup: backup, isImporting: isImporting))
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
            var parts = ["\(value.added) added"]
            if value.updated > 0 { parts.append("\(value.updated) updated") }
            if value.skipped > 0 { parts.append("\(value.skipped) already up to date") }
            return ("Library Imported", parts.joined(separator: ", ") + ". Add imported cards to Apple Wallet from their editor.")
        case .failed(let failed):
            return ("Backup Failed", failed.value.message)
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
