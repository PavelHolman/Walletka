import SharedLogic
import SwiftUI

typealias NormalizedRect = ExportedKotlinPackages.cz.poqet.walletka.domain.NormalizedRect
typealias BannerViewport = ExportedKotlinPackages.cz.poqet.walletka.domain.BannerViewport

/// Pan & pinch the photo under a fixed strip-shaped window to choose the banner. The geometry is the shared
/// `BannerViewport`, so it behaves like the Android editor.
struct BannerEditorView: View {
    let form: CardForm
    let onDone: (NormalizedRect) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var region: NormalizedRect
    @State private var gestureStart: NormalizedRect?
    @State private var photo: UIImage?

    init(form: CardForm, onDone: @escaping (NormalizedRect) -> Void) {
        self.form = form
        self.onDone = onDone
        _region = State(initialValue: form.bannerRegion)
    }

    private var viewport: BannerViewport { form.bannerViewport }

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                GeometryReader { geometry in
                    editor(size: geometry.size)
                }
                Text("Drag and pinch the photo. The bright strip becomes the banner.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal)
            .padding(.bottom)
            .navigationTitle("Adjust Banner")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") {
                        onDone(region)
                        dismiss()
                    }
                }
                ToolbarItem(placement: .bottomBar) {
                    Button("Fit Whole Width") {
                        withAnimation {
                            region = viewport.clamp(region: NormalizedRect(left: 0, top: 0, right: 1, bottom: 1))
                        }
                    }
                }
            }
            .task(id: form.photo.path) {
                photo = await Task.detached { [path = form.photo.path] in
                    UIImage(contentsOfFile: path)?.preparingThumbnail(of: CGSize(width: 2400, height: 2400))
                }.value
            }
        }
        .presentationDetents([.medium, .large])
    }

    /// The strip-shaped window, vertically centred in the available space; the photo around it stays visible but
    /// dimmed, so it's clear what the crop is cut from.
    private func editor(size: CGSize) -> some View {
        let width = size.width
        let windowHeight = width / CGFloat(BannerViewport.Companion.shared.ASPECT_RATIO)
        let windowTop = max(0, (size.height - windowHeight) / 2)
        let transform = viewport.transformFor(region: region)
        return ZStack(alignment: .topLeading) {
            Color.black.opacity(0.9)
            if let photo {
                Image(uiImage: photo)
                    .resizable()
                    .frame(width: CGFloat(transform.width) * width, height: CGFloat(transform.height) * width)
                    .offset(x: CGFloat(transform.offsetX) * width, y: windowTop + CGFloat(transform.offsetY) * width)
            } else {
                ProgressView().tint(.white).frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            // Dim everything outside the window.
            Rectangle()
                .fill(Color.black.opacity(0.6))
                .reverseMask {
                    Rectangle().frame(width: width, height: windowHeight).offset(y: windowTop)
                }
                .allowsHitTesting(false)
            RoundedRectangle(cornerRadius: 3)
                .strokeBorder(.white, lineWidth: 2)
                .frame(width: width, height: windowHeight)
                .offset(y: windowTop)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 12))
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .simultaneously(with: MagnifyGesture())
                .onChanged { value in
                    // SwiftUI reports totals since the gesture began, so apply them to the region it started from.
                    let start = gestureStart ?? region
                    gestureStart = start
                    let drag = value.first?.translation ?? .zero
                    let magnification = value.second?.magnification ?? 1
                    region = viewport.applyGesture(
                        region: start,
                        scaleBy: Float(magnification),
                        anchorX: 0.5,
                        anchorY: viewport.windowHeight / 2,
                        panX: Float(drag.width / width),
                        panY: Float(drag.height / width)
                    )
                }
                .onEnded { _ in gestureStart = nil }
        )
    }
}

private extension View {
    /// Masks out [mask] (shows everything except it).
    func reverseMask<Mask: View>(@ViewBuilder _ mask: () -> Mask) -> some View {
        self.mask {
            Rectangle()
                .overlay(alignment: .topLeading) { mask().blendMode(.destinationOut) }
                .compositingGroup()
        }
    }
}
