import SharedLogic
import SwiftUI

typealias NormalizedRect = ExportedKotlinPackages.cz.poqet.walletka.domain.NormalizedRect
typealias BannerViewport = ExportedKotlinPackages.cz.poqet.walletka.domain.BannerViewport

/// Pan & pinch the photo under a fixed window to choose a crop of it (the banner, the logo). The geometry is the
/// shared `BannerViewport`, so it behaves like the Android editor.
struct CropEditorView: View {
    let title: String
    let hint: String
    let photoPath: String
    let viewport: BannerViewport
    let onDone: (NormalizedRect) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var region: NormalizedRect
    @State private var gestureStart: NormalizedRect?
    @State private var photo: UIImage?

    init(
        title: String,
        hint: String,
        photoPath: String,
        viewport: BannerViewport,
        region: NormalizedRect,
        onDone: @escaping (NormalizedRect) -> Void
    ) {
        self.title = title
        self.hint = hint
        self.photoPath = photoPath
        self.viewport = viewport
        self.onDone = onDone
        _region = State(initialValue: region)
    }

    /// The banner editor: a strip-shaped window.
    init(bannerOf form: CardForm, onDone: @escaping (NormalizedRect) -> Void) {
        self.init(
            title: "Adjust Banner",
            hint: "Drag and pinch the photo. The bright strip becomes the banner.",
            photoPath: form.photo.path,
            viewport: form.bannerViewport,
            region: form.bannerRegion,
            onDone: onDone
        )
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                GeometryReader { geometry in
                    editor(size: geometry.size)
                }
                Text(hint)
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            .padding(.horizontal)
            .padding(.bottom)
            .navigationTitle(title)
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
            .task(id: photoPath) {
                photo = await Task.detached { [path = photoPath] in
                    UIImage(contentsOfFile: path)?.preparingThumbnail(of: CGSize(width: 2400, height: 2400))
                }.value
            }
        }
        .presentationDetents([.medium, .large])
    }

    /// The crop window, centred in the available space (full width unless that makes it too tall); the photo around
    /// it stays visible but dimmed, so it's clear what the crop is cut from.
    private func editor(size: CGSize) -> some View {
        let aspectRatio = CGFloat(viewport.aspectRatio)
        let width = min(size.width, size.height * 0.6 * aspectRatio)
        let windowHeight = width / aspectRatio
        let windowLeft = (size.width - width) / 2
        let windowTop = max(0, (size.height - windowHeight) / 2)
        let transform = viewport.transformFor(region: region)
        return ZStack(alignment: .topLeading) {
            Color.black.opacity(0.9)
            if let photo {
                Image(uiImage: photo)
                    .resizable()
                    .frame(width: CGFloat(transform.width) * width, height: CGFloat(transform.height) * width)
                    .offset(x: windowLeft + CGFloat(transform.offsetX) * width, y: windowTop + CGFloat(transform.offsetY) * width)
            } else {
                ProgressView().tint(.white).frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            // Dim everything outside the window.
            Rectangle()
                .fill(Color.black.opacity(0.6))
                .reverseMask {
                    Rectangle().frame(width: width, height: windowHeight).offset(x: windowLeft, y: windowTop)
                }
                .allowsHitTesting(false)
            RoundedRectangle(cornerRadius: 3)
                .strokeBorder(.white, lineWidth: 2)
                .frame(width: width, height: windowHeight)
                .offset(x: windowLeft, y: windowTop)
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
