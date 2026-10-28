import SharedLogic
import SwiftUI

final class AppDelegate: NSObject, UIApplicationDelegate {
    /// The shared root component lives as long as the app process.
    let app = WalletkaApp()
}

@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView(root: appDelegate.app.root)
                .onOpenURL(perform: handle)
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { importPendingShare() }
                }
        }
    }

    private var root: any RootComponent { appDelegate.app.root }

    /// `walletka://import` (from the share extension) or an image file opened with the app.
    private func handle(_ url: URL) {
        if url.isFileURL {
            ImageInbox.store(fileURL: url).map { root.onImageReceived(source: $0) }
        } else if url.scheme == "walletka" {
            importPendingShare()
        }
    }

    /// The share extension leaves the image in the App Group inbox; pick up the newest one.
    private func importPendingShare() {
        let pending = ImageInbox.pendingShares()
        if let newest = pending.first, let path = ImageInbox.claim(newest) {
            root.onImageReceived(source: path)
        }
        pending.dropFirst().forEach { try? FileManager.default.removeItem(at: $0) }
        ImageInbox.cleanUp()
    }
}
