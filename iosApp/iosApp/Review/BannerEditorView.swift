import SharedLogic
import SwiftUI

typealias NormalizedRect = ExportedKotlinPackages.cz.poqet.walletka.domain.NormalizedRect
typealias BannerViewport = ExportedKotlinPackages.cz.poqet.walletka.domain.BannerViewport

/// Pan & pinch the photo under a fixed window to choose a crop of it (the banner, the logo). The geometry is the
/// shared `BannerViewport`, so it behaves like the Android editor.
struct CropEditorView: View {
    let title: LocalizedStringKey
    let hint: LocalizedStringKey
    let photoPath: String
    let viewport: BannerViewport
    let onDone: (NormalizedRect) -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var region: NormalizedRect
    @State private var gestureStart: NormalizedRect?
    @State private var photo: UIImage?
    /// Where the editor opened, for the VoiceOver "Reset" action.
    private let initialRegion: NormalizedRect

    init(
        title: LocalizedStringKey,
        hint: LocalizedStringKey,
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
        initialRegion = region
        _region = State(initialValue: region)
    }

    /// The banner editor: a strip-shaped window. `nil` for a card without a photo (entered manually).
    init?(bannerOf form: CardForm, onDone: @escaping (NormalizedRect) -> Void) {
        guard let photo = form.photo else { return nil }
        self.init(
            title: "Adjust Banner",
            hint: "Drag and pinch the photo. The bright strip becomes the banner.",
            photoPath: photo.path,
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
                .accessibilityElement(children: .ignore)
                .accessibilityLabel(Text(title))
                .accessibilityValue(Text(verbatim: zoomDescription))
                .accessibilityHint(Text("Swipe up or down to zoom. Use the actions to move the photo or reset it."))
                .accessibilityAdjustableAction { direction in
                    switch direction {
                    case .increment: adjust(scaleBy: 1.25)
                    case .decrement: adjust(scaleBy: 0.8)
                    @unknown default: break
                    }
                }
                .accessibilityAction(named: Text("Move Photo Left")) { adjust(panX: -0.1) }
                .accessibilityAction(named: Text("Move Photo Right")) { adjust(panX: 0.1) }
                .accessibilityAction(named: Text("Move Photo Up")) { adjust(panY: -0.1) }
                .accessibilityAction(named: Text("Move Photo Down")) { adjust(panY: 0.1) }
                .accessibilityAction(named: Text("Reset")) { region = viewport.clamp(region: initialRegion) }
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
        .interactiveDismissDisabled()
    }

    /// "Zoom: 150 %", for VoiceOver.
    private var zoomDescription: String {
        let zoom = Double(viewport.transformFor(region: region).zoom)
        return String(localized: "Zoom: \(zoom.formatted(.percent.precision(.fractionLength(0))))")
    }

    /// One step of the VoiceOver adjustments: zoom around the window's centre and/or pan (in window widths).
    private func adjust(scaleBy: Float = 1, panX: Float = 0, panY: Float = 0) {
        region = viewport.applyGesture(
            region: region,
            scaleBy: scaleBy,
            anchorX: 0.5,
            anchorY: viewport.windowHeight / 2,
            panX: panX,
            panY: panY
        )
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
        // High priority, so dragging the photo never drags the sheet.
        .highPriorityGesture(
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
