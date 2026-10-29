import SharedLogic
import SwiftUI

final class AppDelegate: NSObject, UIApplicationDelegate {
    /// The shared root component lives as long as the app process.
    let app = WalletkaApp(isCloudKitEntitled: CloudKitEntitlement.isPresent)

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // CloudKit's sync engine fetches iCloud changes on its silent pushes once the app is registered for them.
        if CloudKitEntitlement.isPresent { application.registerForRemoteNotifications() }
        return true
    }
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

    /// `walletka://import` (from the share extension), a library backup or an image file opened with the app.
    private func handle(_ url: URL) {
        if url.isFileURL && url.pathExtension.lowercased() == "walletka" {
            importBackup(url)
        } else if url.isFileURL {
            ImageInbox.store(fileURL: url).map { root.onImageReceived(source: $0) }
        } else if url.scheme == "walletka" {
            importPendingShare()
        }
    }

    /// Merges a `.walletka` backup into the library, on the home screen where the outcome is shown.
    private func importBackup(_ url: URL) {
        guard let path = BackupFiles.localCopy(of: url) else { return }
        root.onBackClicked(toIndex: 0)
        if case .home(let home) = root.children.value.first?.sealedType() {
            home.value.component.onImportFileSelected(path: path)
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
