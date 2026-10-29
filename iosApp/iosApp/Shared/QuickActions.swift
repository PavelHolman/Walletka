import Observation
import UIKit

/// Home Screen quick actions (touch and hold the app icon): scan a card or start one typed in by hand.
enum QuickAction: String {
    case scanCard = "cz.poqet.walletka.scan-card"
    case newCard = "cz.poqet.walletka.new-card"

    /// Set at launch, so the titles follow the app's language.
    @MainActor
    static func install() {
        var items = [
            UIApplicationShortcutItem(
                type: newCard.rawValue,
                localizedTitle: String(localized: "New Card"),
                localizedSubtitle: String(localized: "Type it in by hand"),
                icon: UIApplicationShortcutIcon(systemImageName: "square.and.pencil")
            ),
        ]
        if CameraCapture.isAvailable {
            items.insert(UIApplicationShortcutItem(
                type: scanCard.rawValue,
                localizedTitle: String(localized: "Scan Card"),
                localizedSubtitle: nil,
                icon: UIApplicationShortcutIcon(systemImageName: "camera.viewfinder")
            ), at: 0)
        }
        UIApplication.shared.shortcutItems = items
    }
}

/// The quick action the user launched the app with, until Home handles it.
@MainActor
@Observable
final class QuickActionInbox {
    static let shared = QuickActionInbox()

    var pending: QuickAction?
}

/// Receives quick actions, both at launch and while the app runs.
final class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let item = connectionOptions.shortcutItem {
            MainActor.assumeIsolated { QuickActionInbox.shared.pending = QuickAction(rawValue: item.type) }
        }
    }

    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem) async -> Bool {
        await MainActor.run { QuickActionInbox.shared.pending = QuickAction(rawValue: shortcutItem.type) }
        return true
    }
}
