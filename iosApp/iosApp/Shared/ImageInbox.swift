import Foundation
import UIKit
import UniformTypeIdentifiers

/// Hands images to the shared code as file paths: Kotlin copies them into its own storage on import.
enum ImageInbox {
    /// Folder shared with the share extension (App Group), falling back to the app's temp directory.
    static var directory: URL {
        let groupId = Bundle.main.object(forInfoDictionaryKey: "WalletkaAppGroup") as? String
        let base = groupId.flatMap { FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: $0) }
            ?? FileManager.default.temporaryDirectory
        let inbox = base.appendingPathComponent("Inbox", isDirectory: true)
        try? FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        return inbox
    }

    static func store(_ data: Data, fileExtension: String = "img") -> String? {
        let url = directory.appendingPathComponent("\(UUID().uuidString).\(fileExtension)")
        do {
            try data.write(to: url, options: .atomic)
            return url.path
        } catch {
            return nil
        }
    }

    /// Copies a file handed to the app (Files "Open in…", drag & drop), which may be security scoped.
    static func store(fileURL: URL) -> String? {
        let scoped = fileURL.startAccessingSecurityScopedResource()
        defer { if scoped { fileURL.stopAccessingSecurityScopedResource() } }
        guard let data = try? Data(contentsOf: fileURL) else { return nil }
        return store(data, fileExtension: fileURL.pathExtension.isEmpty ? "img" : fileURL.pathExtension)
    }

    static func load(from provider: NSItemProvider) async -> String? {
        await withCheckedContinuation { continuation in
            provider.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { data, _ in
                continuation.resume(returning: data.flatMap { store($0) })
            }
        }
    }

    /// Files dropped by the share extension, newest first.
    static func pendingShares() -> [URL] {
        let files = (try? FileManager.default.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: [.creationDateKey]
        )) ?? []
        return files
            .filter { $0.lastPathComponent.hasPrefix(sharePrefix) }
            .sorted { creationDate($0) > creationDate($1) }
    }

    /// Renames a share-extension file so it is not picked up again.
    static func claim(_ url: URL) -> String? {
        let claimed = directory.appendingPathComponent(String(url.lastPathComponent.dropFirst(sharePrefix.count)))
        do {
            try FileManager.default.moveItem(at: url, to: claimed)
            return claimed.path
        } catch {
            return nil
        }
    }

    /// Removes inbox files older than a day; Kotlin has long copied them by then.
    static func cleanUp() {
        let files = (try? FileManager.default.contentsOfDirectory(
            at: directory, includingPropertiesForKeys: [.creationDateKey]
        )) ?? []
        for file in files where creationDate(file) < Date().addingTimeInterval(-24 * 60 * 60) {
            try? FileManager.default.removeItem(at: file)
        }
    }

    /// Prefix the share extension gives the files it writes (see `ShareViewController`).
    static let sharePrefix = "share-"

    private static func creationDate(_ url: URL) -> Date {
        (try? url.resourceValues(forKeys: [.creationDateKey]).creationDate) ?? .distantPast
    }
}
