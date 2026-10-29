import SwiftUI
import UIKit

extension View {
    /// Ends editing when the user taps anywhere outside a text input, without swallowing the tap.
    func dismissesKeyboardOnTapOutside() -> some View {
        background(KeyboardDismissInstaller())
    }
}

private struct KeyboardDismissInstaller: UIViewRepresentable {
    func makeUIView(context: Context) -> InstallerView { InstallerView() }

    func updateUIView(_ uiView: InstallerView, context: Context) {}

    final class InstallerView: UIView, UIGestureRecognizerDelegate {
        private weak var installedWindow: UIWindow?
        private lazy var recognizer: UITapGestureRecognizer = {
            let tap = UITapGestureRecognizer(target: self, action: #selector(tapped))
            tap.cancelsTouchesInView = false
            tap.delegate = self
            return tap
        }()

        override func didMoveToWindow() {
            super.didMoveToWindow()
            guard window !== installedWindow else { return }
            installedWindow?.removeGestureRecognizer(recognizer)
            window?.addGestureRecognizer(recognizer)
            installedWindow = window
        }

        @objc private func tapped() {
            installedWindow?.endEditing(true)
        }

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldReceive touch: UITouch) -> Bool {
            // Taps on (or inside) a text input must keep it focused.
            var view = touch.view
            while let current = view {
                if current is UITextField || current is UITextView { return false }
                view = current.superview
            }
            return true
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer
        ) -> Bool { true }
    }
}
