import UIKit
import UniformTypeIdentifiers

/// Base of the share and action extensions: stores the shared image in the App Group inbox and hands over to the
/// app, which runs the shared Kotlin pipeline (the extensions stay tiny and do not embed the Kotlin runtime).
class ImageHandoverViewController: UIViewController {

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        Task { await handOver() }
    }

    private func handOver() async {
        guard let provider = (extensionContext?.inputItems as? [NSExtensionItem])?
            .flatMap({ $0.attachments ?? [] })
            .first(where: { $0.hasItemConformingToTypeIdentifier(UTType.image.identifier) }),
            let data = await loadImage(from: provider),
            let inbox = inboxDirectory()
        else {
            finish(error: true)
            return
        }

        // The "share-" prefix marks the file for the app to pick up (see ImageInbox.pendingShares).
        let file = inbox.appendingPathComponent("share-\(UUID().uuidString).img")
        guard (try? data.write(to: file, options: .atomic)) != nil else {
            finish(error: true)
            return
        }
        openApp(URL(string: "walletka://import")!)
        finish(error: false)
    }

    private func loadImage(from provider: NSItemProvider) async -> Data? {
        await withCheckedContinuation { continuation in
            provider.loadDataRepresentation(forTypeIdentifier: UTType.image.identifier) { data, _ in
                continuation.resume(returning: data)
            }
        }
    }

    private func inboxDirectory() -> URL? {
        guard let group = Bundle.main.object(forInfoDictionaryKey: "WalletkaAppGroup") as? String,
              let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: group)
        else { return nil }
        let inbox = container.appendingPathComponent("Inbox", isDirectory: true)
        try? FileManager.default.createDirectory(at: inbox, withIntermediateDirectories: true)
        return inbox
    }

    /// Extensions can't call `UIApplication.open`; the application is reachable through the responder chain and the
    /// call is made through the Objective-C runtime. If opening fails, the app still picks the image up the next
    /// time it becomes active.
    private func openApp(_ url: URL) {
        typealias OpenURL = @convention(c) (
            NSObject, Selector, NSURL, NSDictionary, (@convention(block) (Bool) -> Void)?
        ) -> Void
        let selector = NSSelectorFromString("openURL:options:completionHandler:")
        var responder: UIResponder? = self
        while let current = responder {
            if current is UIApplication, current.responds(to: selector) {
                let open = unsafeBitCast(current.method(for: selector), to: OpenURL.self)
                open(current, selector, url as NSURL, NSDictionary(), nil)
                return
            }
            responder = current.next
        }
    }

    private func finish(error: Bool) {
        if error {
            extensionContext?.cancelRequest(withError: CocoaError(.fileReadUnknown))
        } else {
            extensionContext?.completeRequest(returningItems: nil)
        }
    }
}
