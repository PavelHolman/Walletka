import PassKit
import SharedLogic
import SwiftUI

/// How big a [CardPreview] draws the code.
enum CardPreviewSize {
    /// Small enough to keep the editor in view.
    case compact
    /// As Wallet shows the pass.
    case regular
    /// Big enough to be scanned off the screen.
    case large
}

/// The card roughly as Wallet shows it.
struct CardPreview: View {
    let form: CardForm
    var size = CardPreviewSize.regular
    var showName = true

    var body: some View {
        let background = form.selectedColor.swiftUIColor
        let content = form.selectedColor.contentColor.swiftUIColor
        VStack(spacing: 0) {
            HStack {
                // Like Wallet: a logo replaces the name in the header.
                if let logo = form.logo.activeImage?.path {
                    PassLogoView(path: logo)
                } else if showName {
                    Group {
                        if form.name.isEmpty { Text("Card Name") } else { Text(verbatim: form.name) }
                    }
                    .font(.headline)
                    .lineLimit(1)
                }
                Spacer()
            }
            .foregroundStyle(content)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            if form.isBannerEnabled, let path = form.banner?.path, let banner = UIImage(contentsOfFile: path) {
                Color.clear
                    .aspectRatio(375.0 / 123.0, contentMode: .fit)
                    .overlay {
                        Image(uiImage: banner)
                            .resizable()
                            .scaledToFill()
                    }
                    .clipped()
            }

            let isTwoDimensional = form.walletCodeFormat.isTwoDimensional
            let height = codeHeight(twoDimensional: isTwoDimensional)
            if let code = CodeImage.make(format: form.walletCodeFormat, value: form.selectedCode.value) {
                VStack(spacing: 4) {
                    Image(uiImage: code)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        // To be scanned, a 2D code takes the card's whole width: dense codes need every point.
                        .frame(maxHeight: size == .large && isTwoDimensional ? nil : height)
                    if !isTwoDimensional {
                        Text(form.selectedCode.value).font(.caption2.monospaced()).foregroundStyle(.black)
                    }
                }
                .padding(10)
                .background(.white, in: RoundedRectangle(cornerRadius: 8))
                .padding(size == .compact ? 14 : 20)
            } else {
                // No code typed yet: show where the code goes, laid out like the code itself, so the card doesn't
                // grow (and push the field being typed in under the keyboard) once there is one.
                VStack(spacing: 4) {
                    Color.clear.frame(width: isTwoDimensional ? height : 160, height: height)
                    if !isTwoDimensional {
                        Text(verbatim: " ").font(.caption2.monospaced())
                    }
                }
                .padding(10)
                .overlay {
                    Image(systemName: isTwoDimensional ? "qrcode" : "barcode")
                        .font(.system(size: isTwoDimensional ? 44 : 36, weight: .light))
                        .foregroundStyle(content.opacity(0.5))
                }
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .strokeBorder(content.opacity(0.35), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                )
                .accessibilityHidden(true)
                .padding(size == .compact ? 14 : 20)
            }
        }
        .frame(maxWidth: .infinity)
        .background(background, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        // It pictures the pass, whose text Wallet doesn't scale; VoiceOver reads it as a whole anyway.
        .dynamicTypeSize(...DynamicTypeSize.xLarge)
        // One shadow for the card as a whole, not one under each code, text and image on it.
        .compositingGroup()
        .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
        .padding(.vertical, 8)
        .animation(.default, value: form.selectedColor.hex)
        .animation(.default, value: form.isBannerEnabled)
        .animation(.default, value: form.logo.activeImage?.path)
        // One element: what the card looks like, not its parts.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Card preview"))
        .accessibilityValue(Text(verbatim: accessibilityValue))
        .accessibilityAddTraits(.isImage)
    }

    private func codeHeight(twoDimensional: Bool) -> CGFloat {
        switch size {
        case .compact: twoDimensional ? 96 : 52
        case .regular: twoDimensional ? 140 : 70
        case .large: twoDimensional ? 230 : 110
        }
    }

    /// Name, code type and value, colour, and which of banner and logo the card shows.
    private var accessibilityValue: String {
        var parts = [form.name.isEmpty ? String(localized: "No name") : form.name]
        parts.append(String(localized: "\(form.walletCodeFormat.localizedName): \(form.selectedCode.value)"))
        parts.append(form.selectedColor.accessibilityName)
        if form.logo.activeImage != nil { parts.append(String(localized: "with logo")) }
        if form.isBannerEnabled && form.banner != nil { parts.append(String(localized: "with banner")) }
        return parts.joined(separator: ", ")
    }
}

/// The system "Add to Apple Wallet" button, 50 points tall and with the corners of the app's other buttons (PassKit's own
/// are much tighter, with no setting for them).
struct AddToWalletButton: View {
    let action: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        PassKitAddButton(action: action)
            .frame(height: 50)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            // Keeps the black button visible on a dark background; PassKit's outlined style would be cut at the corners.
            .overlay {
                if colorScheme == .dark {
                    RoundedRectangle(cornerRadius: 12, style: .continuous)
                        .strokeBorder(.white.opacity(0.3), lineWidth: 1)
                }
            }
    }
}

private struct PassKitAddButton: UIViewRepresentable {
    let action: () -> Void

    func makeUIView(context: Context) -> PKAddPassButton {
        let button = PKAddPassButton(addPassButtonStyle: .black)
        button.addTarget(context.coordinator, action: #selector(Coordinator.tapped), for: .touchUpInside)
        return button
    }

    func updateUIView(_ uiView: PKAddPassButton, context: Context) {
        context.coordinator.action = action
    }

    func makeCoordinator() -> Coordinator { Coordinator(action: action) }

    final class Coordinator: NSObject {
        var action: () -> Void

        init(action: @escaping () -> Void) { self.action = action }

        @objc func tapped() { action() }
    }
}
