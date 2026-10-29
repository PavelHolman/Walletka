import SwiftUI
import UIKit
import VisionKit

/// Photographs a card, returning the photo as JPEG data. Uses VisionKit's document camera where available: it finds
/// the card's edges, captures it when steady and straightens it, so the banner and colours come from the card
/// alone. Falls back to the plain system camera.
struct CameraCapture: UIViewControllerRepresentable {
    static var isAvailable: Bool { UIImagePickerController.isSourceTypeAvailable(.camera) }

    let onCapture: (Data) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeUIViewController(context: Context) -> UIViewController {
        if VNDocumentCameraViewController.isSupported {
            let scanner = VNDocumentCameraViewController()
            scanner.delegate = context.coordinator
            return scanner
        }
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.cameraCaptureMode = .photo
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate,
        VNDocumentCameraViewControllerDelegate {
        private let parent: CameraCapture

        init(_ parent: CameraCapture) { self.parent = parent }

        // MARK: Document camera

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFinishWith scan: VNDocumentCameraScan) {
            // One card per photo: the first scan is the card, extra pages are ignored.
            if scan.pageCount > 0, let jpeg = scan.imageOfPage(at: 0).jpegData(compressionQuality: 0.92) {
                parent.onCapture(jpeg)
            }
            parent.dismiss()
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            parent.dismiss()
        }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
            parent.dismiss()
        }

        // MARK: System camera

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            if let image = info[.originalImage] as? UIImage, let jpeg = image.jpegData(compressionQuality: 0.9) {
                parent.onCapture(jpeg)
            }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.dismiss()
        }
    }
}
