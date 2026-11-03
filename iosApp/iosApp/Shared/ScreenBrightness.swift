import SwiftUI
import UIKit

extension View {
    /// Full screen brightness while this view is on screen, so a scanner at the till reads the code; the user's
    /// brightness comes back when it goes away or the app leaves the foreground.
    func fullScreenBrightness() -> some View {
        modifier(FullScreenBrightness())
    }
}

private struct FullScreenBrightness: ViewModifier {
    @Environment(\.scenePhase) private var scenePhase
    @State private var isHolding = false

    func body(content: Content) -> some View {
        content
            .onAppear {
                guard !isHolding else { return }
                isHolding = true
                ScreenBrightness.hold()
            }
            .onDisappear {
                guard isHolding else { return }
                isHolding = false
                ScreenBrightness.release()
            }
            // iOS keeps an app's brightness until the device locks, even in other apps: hand it back meanwhile.
            .onChange(of: scenePhase) { _, phase in
                guard isHolding else { return }
                ScreenBrightness.setForeground(phase == .active)
            }
    }
}

/// Counts the views that want full brightness: when one card's preview replaces another (the iPad sidebar), the new
/// one appears before the old one disappears.
@MainActor
private enum ScreenBrightness {
    private static var holders = 0
    private static var isForeground = true
    /// The user's brightness while it is raised.
    private static var saved: CGFloat?
    /// Keep the exact screen whose brightness we changed, even while its scene is disappearing.
    private static var controlledScreen: UIScreen?
    private static var ramp: Task<Void, Never>?

    static func hold() {
        holders += 1
        update(animated: true)
    }

    static func release() {
        holders = max(0, holders - 1)
        update(animated: true)
    }

    static func setForeground(_ foreground: Bool) {
        isForeground = foreground
        // Leaving: at once, the app may not run much longer.
        update(animated: foreground)
    }

    private static func update(animated: Bool) {
        let screen = controlledScreen ?? UIApplication.shared.connectedScenes.compactMap { ($0 as? UIWindowScene)?.screen }.first
        guard let screen else { return }
        if holders > 0 && isForeground {
            if saved == nil {
                saved = screen.brightness
                controlledScreen = screen
            }
            set(screen, to: 1, animated: animated)
        } else if let brightness = saved {
            set(screen, to: brightness, animated: animated) {
                // Keep the original value while restoring. A preview that appears mid-ramp can cancel the
                // restore and continue using the same baseline instead of saving an in-between brightness.
                guard holders == 0 || !isForeground else { return }
                saved = nil
                controlledScreen = nil
            }
        }
    }

    private static func set(_ screen: UIScreen, to target: CGFloat, animated: Bool, completion: @escaping () -> Void = {}) {
        ramp?.cancel()
        guard animated else {
            screen.brightness = target
            completion()
            return
        }
        let start = screen.brightness
        ramp = Task {
            let steps = 12
            for step in 1...steps {
                try? await Task.sleep(for: .milliseconds(20))
                if Task.isCancelled { return }
                screen.brightness = start + (target - start) * CGFloat(step) / CGFloat(steps)
            }
            completion()
        }
    }
}
