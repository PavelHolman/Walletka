import SharedLogic
import SwiftUI

extension CodeFormat {
    /// The symbology's name for the UI: translated where it is a description ("QR code"), as-is for proper names.
    var localizedName: String {
        self == .QR ? String(localized: "QR code") : displayName
    }
}

extension CardColor {
    /// A rough, spoken name of the colour ("Dark blue") for VoiceOver; the exact value is [hex].
    var accessibilityName: String {
        let r = Double(red) / 255, g = Double(green) / 255, b = Double(blue) / 255
        let maximum = max(r, g, b), minimum = min(r, g, b)
        let lightness = (maximum + minimum) / 2
        let delta = maximum - minimum
        let saturation = delta == 0 ? 0 : delta / (1 - abs(2 * lightness - 1))

        if lightness < 0.1 { return String(localized: "Black") }
        if lightness > 0.93 && saturation < 0.5 { return String(localized: "White") }

        var hue: Double = 0
        if delta > 0 {
            switch maximum {
            case r: hue = 60 * ((g - b) / delta).truncatingRemainder(dividingBy: 6)
            case g: hue = 60 * ((b - r) / delta + 2)
            default: hue = 60 * ((r - g) / delta + 4)
            }
            if hue < 0 { hue += 360 }
        }

        let base: String
        if saturation < 0.15 {
            base = String(localized: "grey", comment: "Colour name, combined with Dark/Light")
        } else if (15..<70).contains(hue) && lightness < 0.35 {
            base = String(localized: "brown", comment: "Colour name, combined with Dark/Light")
        } else {
            base = switch hue {
            case 15..<40: String(localized: "orange", comment: "Colour name, combined with Dark/Light")
            case 40..<70: String(localized: "yellow", comment: "Colour name, combined with Dark/Light")
            case 70..<165: String(localized: "green", comment: "Colour name, combined with Dark/Light")
            case 165..<195: String(localized: "teal", comment: "Colour name, combined with Dark/Light")
            case 195..<255: String(localized: "blue", comment: "Colour name, combined with Dark/Light")
            case 255..<290: String(localized: "purple", comment: "Colour name, combined with Dark/Light")
            case 290..<345: String(localized: "pink", comment: "Colour name, combined with Dark/Light")
            default: String(localized: "red", comment: "Colour name, combined with Dark/Light")
            }
        }
        if lightness < 0.3 { return String(localized: "Dark \(base)", comment: "A dark shade of a colour, e.g. Dark blue") }
        if lightness > 0.72 { return String(localized: "Light \(base)", comment: "A light shade of a colour, e.g. Light blue") }
        return base.prefix(1).uppercased() + base.dropFirst()
    }

    /// The hex value spelled out character by character for VoiceOver, without the "#" it would read as "number".
    var accessibilityHex: String {
        hex.dropFirst().uppercased().map(String.init).joined(separator: " ")
    }

    /// Name and hex value, e.g. "Dark blue, 1 A 2 3 7 E".
    var accessibilityDescription: String {
        "\(accessibilityName), \(accessibilityHex)"
    }
}

extension WalletFailure {
    /// What went wrong, for an alert; [detail] (the wallet's own words) follows when there is one.
    func message(detail: String?) -> String {
        let text = switch self {
        case .UNAVAILABLE: String(localized: "Apple Wallet isn't available on this device.")
        case .NOT_CONFIGURED: String(localized: "Walletka can't create wallet passes right now. Try again later.")
        case .CANCELLED: String(localized: "Cancelled.")
        case .UNSUPPORTED_CODE: String(localized: "This code type can't be stored in Apple Wallet.")
        case .FAILED: String(localized: "Apple Wallet rejected the card.")
        case .NOT_IN_WALLET: String(localized: "The card is no longer in Apple Wallet.")
        }
        return [text, detail].compactMap { $0 }.joined(separator: "\n\n")
    }
}
