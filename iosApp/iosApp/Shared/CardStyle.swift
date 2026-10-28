import CoreImage.CIFilterBuiltins
import SharedLogic
import SwiftUI

extension CardColor {
    var swiftUIColor: Color {
        Color(red: Double(red) / 255, green: Double(green) / 255, blue: Double(blue) / 255)
    }
}

/// Renders a code the way the wallet will show it (Core Image supports exactly Apple Wallet's symbologies).
enum CodeImage {
    static func make(format: CodeFormat, value: String) -> UIImage? {
        let message = Data(value.utf8)
        let filter: CIFilter
        switch format {
        case .QR:
            let qr = CIFilter.qrCodeGenerator()
            qr.message = message
            qr.correctionLevel = "M"
            filter = qr
        case .AZTEC:
            let aztec = CIFilter.aztecCodeGenerator()
            aztec.message = message
            filter = aztec
        case .PDF417:
            let pdf = CIFilter.pdf417BarcodeGenerator()
            pdf.message = message
            filter = pdf
        default:
            let code128 = CIFilter.code128BarcodeGenerator()
            code128.message = Data(value.utf8)
            code128.quietSpace = 4
            filter = code128
        }
        guard let output = filter.outputImage?.transformed(by: CGAffineTransform(scaleX: 8, y: 8)),
              let cgImage = CIContext().createCGImage(output, from: output.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}
