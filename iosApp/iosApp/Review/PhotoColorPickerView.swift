import SharedLogic
import SwiftUI

/// Shows the card photo; tapping it previews the colour at that point (sampled in shared code). The card only changes
/// when the user confirms with "Use Colour".
struct PhotoColorPickerView: View {
    let photoPath: String
    /// The colour sampled at the last tap, `nil` before the first one.
    let preview: CardColor?
    let onPick: (_ x: Float, _ y: Float) -> Void
    let onConfirm: () -> Void
    let onCancel: () -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var photo: UIImage?
    /// Last tapped point, normalized to the photo.
    @State private var marker: CGPoint?
    @State private var isDragging = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                if let photo {
                    Image(uiImage: photo)
                        .resizable()
                        .scaledToFit()
                        .overlay {
                            GeometryReader { geometry in
                                ZStack(alignment: .topLeading) {
                                    Color.clear
                                        .contentShape(Rectangle())
                                        // Drag (or tap) across the photo; high priority so the sheet doesn't move.
                                        .highPriorityGesture(
                                            DragGesture(minimumDistance: 0)
                                                .onChanged { value in
                                                    let point = CGPoint(
                                                        x: min(max(value.location.x / geometry.size.width, 0), 1),
                                                        y: min(max(value.location.y / geometry.size.height, 0), 1)
                                                    )
                                                    marker = point
                                                    isDragging = true
                                                    onPick(Float(point.x), Float(point.y))
                                                }
                                                .onEnded { _ in isDragging = false }
                                        )
                                    if let marker, let preview {
                                        // While dragging, the loupe floats above the finger so it stays visible.
                                        Circle()
                                            .fill(preview.swiftUIColor)
                                            .frame(width: isDragging ? 56 : 36, height: isDragging ? 56 : 36)
                                            .overlay(Circle().strokeBorder(.white, lineWidth: 3))
                                            .shadow(radius: 4)
                                            .position(
                                                x: marker.x * geometry.size.width,
                                                y: marker.y * geometry.size.height - (isDragging ? 56 : 0)
                                            )
                                            .allowsHitTesting(false)
                                            .animation(.snappy(duration: 0.15), value: isDragging)
                                    }
                                }
                            }
                        }
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                } else {
                    ProgressView().frame(maxHeight: .infinity)
                }
                Spacer(minLength: 0)
                HStack(spacing: 12) {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(preview?.swiftUIColor ?? Color.secondary.opacity(0.15))
                        .frame(width: 56, height: 40)
                        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(.secondary.opacity(0.3)))
                    VStack(alignment: .leading, spacing: 2) {
                        Text(preview == nil ? "Tap or drag across the photo" : "Picked colour")
                            .font(.subheadline.weight(.semibold))
                        if let preview {
                            Text(preview.hex.uppercased()).font(.caption.monospaced()).foregroundStyle(.secondary)
                        }
                    }
                    Spacer()
                }
                .padding(12)
                .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 14))
            }
            .padding()
            .animation(.default, value: preview?.hex)
            .navigationTitle("Pick Colour")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        onCancel()
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Use Colour") {
                        onConfirm()
                        dismiss()
                    }
                    .disabled(preview == nil)
                }
            }
            .interactiveDismissDisabled()
            .onDisappear { if preview != nil { onCancel() } }
            .task(id: photoPath) {
                photo = await Task.detached { [photoPath] in
                    UIImage(contentsOfFile: photoPath)?.preparingThumbnail(of: CGSize(width: 2000, height: 2000))
                }.value
            }
        }
    }
}
