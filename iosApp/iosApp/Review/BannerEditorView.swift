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
            VStack(spacing: 20) {
                Text("Drag and pinch the photo to choose what the banner shows.")
                    .font(.callout)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                GeometryReader { geometry in
                    window(width: geometry.size.width)
                }
                .aspectRatio(CGFloat(BannerViewport.Companion.shared.ASPECT_RATIO), contentMode: .fit)
                Button("Fit Whole Width") {
                    region = viewport.clamp(region: NormalizedRect(left: 0, top: 0, right: 1, bottom: 1))
                }
                Spacer()
            }
            .padding()
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
            }
            .task(id: form.photo.path) {
                photo = await Task.detached { [path = form.photo.path] in
                    UIImage(contentsOfFile: path)?.preparingThumbnail(of: CGSize(width: 2400, height: 2400))
                }.value
            }
        }
    }

    private func window(width: CGFloat) -> some View {
        let transform = viewport.transformFor(region: region)
        return ZStack(alignment: .topLeading) {
            Color.black
            if let photo {
                Image(uiImage: photo)
                    .resizable()
                    .frame(width: CGFloat(transform.width) * width, height: CGFloat(transform.height) * width)
                    .offset(x: CGFloat(transform.offsetX) * width, y: CGFloat(transform.offsetY) * width)
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .frame(width: width, height: width / CGFloat(BannerViewport.Companion.shared.ASPECT_RATIO), alignment: .topLeading)
        .clipped()
        .overlay(RoundedRectangle(cornerRadius: 4).strokeBorder(Color.accentColor, lineWidth: 2))
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
                    let anchor = value.second?.startAnchor ?? .center
                    region = viewport.applyGesture(
                        region: start,
                        scaleBy: Float(magnification),
                        anchorX: Float(anchor.x),
                        anchorY: Float(anchor.y) * viewport.windowHeight,
                        panX: Float(drag.width / width),
                        panY: Float(drag.height / width)
                    )
                }
                .onEnded { _ in gestureStart = nil }
        )
    }
}
