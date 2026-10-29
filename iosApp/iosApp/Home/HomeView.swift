import PhotosUI
import SharedLogic
import SwiftUI
import UniformTypeIdentifiers

/// The card library, plus the ways to add a card (camera, photo library, paste, drop).
struct HomeView: View {
    private let component: any HomeComponent
    @State private var model: ObservedFlow<HomeModel>
    @State private var pickedPhoto: PhotosPickerItem?
    @State private var isDropTargeted = false
    @State private var isCameraPresented = false
    @State private var isPhotoPickerPresented = false
    @State private var cardPendingDeletion: CardSummary?

    init(component: any HomeComponent) {
        self.component = component
        _model = State(initialValue: ObservedFlow(component.model))
    }

    var body: some View {
        Group {
            if model.value.cards.isEmpty {
                emptyState
            } else {
                library
            }
        }
        .background(isDropTargeted ? Color.accentColor.opacity(0.08) : Color.clear)
        .onDrop(of: [.image], isTargeted: $isDropTargeted) { providers in
            guard let provider = providers.first else { return false }
            Task {
                if let path = await ImageInbox.load(from: provider) {
                    component.onImagePicked(source: path)
                }
            }
            return true
        }
        .onChange(of: pickedPhoto) { _, item in
            guard let item else { return }
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let path = ImageInbox.store(data) {
                    component.onImagePicked(source: path)
                }
                pickedPhoto = nil
            }
        }
        .photosPicker(isPresented: $isPhotoPickerPresented, selection: $pickedPhoto, matching: .images, photoLibrary: .shared())
        .fullScreenCover(isPresented: $isCameraPresented) {
            CameraCapture { jpeg in
                if let path = ImageInbox.store(jpeg, fileExtension: "jpg") {
                    component.onImagePicked(source: path)
                }
            }
            .ignoresSafeArea()
        }
    }

    // MARK: - Library

    private var library: some View {
        List {
            ForEach(model.value.cards, id: \.id) { card in
                Button {
                    component.onCardClicked(cardId: card.id)
                } label: {
                    CardTile(card: card)
                }
                .buttonStyle(.plain)
                .listRowSeparator(.hidden)
                .listRowInsets(EdgeInsets(top: 6, leading: 16, bottom: 6, trailing: 16))
                .swipeActions {
                    Button("Delete", systemImage: "trash", role: .destructive) { cardPendingDeletion = card }
                }
                .contextMenu {
                    Button("Edit", systemImage: "pencil") { component.onCardClicked(cardId: card.id) }
                    Button("Delete", systemImage: "trash", role: .destructive) { cardPendingDeletion = card }
                }
            }
        }
        .listStyle(.plain)
        .navigationTitle("Walletka")
        .safeAreaInset(edge: .bottom) {
            addMenu
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
        }
        // A centred alert: a swipe action has no stable button to anchor a dialog to.
        .alert(
            "Delete “\(cardPendingDeletion?.name ?? "")”?",
            isPresented: Binding(get: { cardPendingDeletion != nil }, set: { if !$0 { cardPendingDeletion = nil } })
        ) {
            Button("Delete", role: .destructive) {
                if let card = cardPendingDeletion { component.onCardDeleteClicked(cardId: card.id) }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The card is removed from Walletka and from Apple Wallet.")
        }
    }

    /// One primary control for every way of adding a card.
    private var addMenu: some View {
        Menu {
            if CameraCapture.isAvailable {
                Button("Take Photo", systemImage: "camera") { isCameraPresented = true }
            }
            Button("Choose Photo", systemImage: "photo.on.rectangle") { isPhotoPickerPresented = true }
            Button("Paste Image", systemImage: "doc.on.clipboard") { pasteImage() }
                .disabled(!UIPasteboard.general.hasImages)
            Button("Enter Manually", systemImage: "keyboard") { component.onEnterManuallyClicked() }
        } label: {
            Label("Add Card", systemImage: "plus")
                .font(.headline)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
        }
        .menuStyle(.button)
        .addCardButtonStyle()
        .controlSize(.large)
    }

    private func pasteImage() {
        guard let image = UIPasteboard.general.image, let data = image.jpegData(compressionQuality: 0.9),
              let path = ImageInbox.store(data, fileExtension: "jpg") else { return }
        component.onImagePicked(source: path)
    }

    // MARK: - Empty state

    private var emptyState: some View {
        VStack(spacing: 28) {
            Spacer()
            ZStack {
                RoundedRectangle(cornerRadius: 30, style: .continuous)
                    .fill(LinearGradient(colors: [.indigo, .teal], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 116, height: 116)
                Image(systemName: "qrcode.viewfinder")
                    .font(.system(size: 54, weight: .medium))
                    .foregroundStyle(.white)
            }
            VStack(spacing: 10) {
                Text("Walletka").font(.largeTitle.bold())
                Text("Snap or share a photo of any loyalty card, ticket or membership, and Walletka turns it into an Apple Wallet card.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            VStack(spacing: 12) {
                if CameraCapture.isAvailable {
                    Button {
                        isCameraPresented = true
                    } label: {
                        Label("Take Photo", systemImage: "camera")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                }

                PhotosPicker(selection: $pickedPhoto, matching: .images, photoLibrary: .shared()) {
                    Label("Choose Photo", systemImage: "photo.on.rectangle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(CameraCapture.isAvailable ? AnyPrimitiveButtonStyle(.bordered) : AnyPrimitiveButtonStyle(.borderedProminent))
                .controlSize(.large)

                pasteButton
                    .buttonBorderShape(.capsule)
                    .controlSize(.large)

                Button {
                    component.onEnterManuallyClicked()
                } label: {
                    Label("Enter Manually", systemImage: "keyboard")
                }
                .controlSize(.large)
            }
            Label("Tip: in Photos, tap Share → Add to Walletka", systemImage: "square.and.arrow.up")
                .font(.footnote)
                .foregroundStyle(.secondary)
            if model.value.isWalletAvailable == false {
                Text("Apple Wallet isn't available on this device.")
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
            Spacer()
        }
        .padding(28)
        .frame(maxWidth: 520)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .toolbar(.hidden, for: .navigationBar)
    }

    private var pasteButton: some View {
        PasteButton(supportedContentTypes: [.image]) { providers in
            guard let provider = providers.first else { return }
            Task {
                if let path = await ImageInbox.load(from: provider) {
                    component.onImagePicked(source: path)
                }
            }
        }
        .labelStyle(.titleAndIcon)
    }
}

/// A library card drawn like a small wallet pass.
private struct CardTile: View {
    let card: CardSummary

    var body: some View {
        let content = card.color.contentColor.swiftUIColor
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(card.name)
                    .font(.headline)
                    .lineLimit(1)
                Text("\(card.codeFormat.displayName) · \(card.codeValue)")
                    .font(.caption.monospaced())
                    .lineLimit(1)
                    .opacity(0.8)
            }
            .foregroundStyle(content)
            Spacer(minLength: 0)
            if let path = card.banner?.path, let banner = UIImage(contentsOfFile: path) {
                Image(uiImage: banner)
                    .resizable()
                    .scaledToFill()
                    .frame(width: 96, height: 32)
                    .clipShape(RoundedRectangle(cornerRadius: 6))
            }
            Image(systemName: "chevron.right")
                .font(.footnote.bold())
                .foregroundStyle(content.opacity(0.7))
        }
        .padding(16)
        .frame(maxWidth: .infinity, minHeight: 76)
        .background(card.color.swiftUIColor, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .contentShape(RoundedRectangle(cornerRadius: 16))
        .accessibilityElement(children: .combine)
        .accessibilityHint("Edit card")
    }
}

/// Type-erases a `PrimitiveButtonStyle` so the style can be chosen at runtime.
private struct AnyPrimitiveButtonStyle: PrimitiveButtonStyle {
    private let make: (Configuration) -> AnyView

    init<S: PrimitiveButtonStyle>(_ style: S) {
        make = { AnyView(style.makeBody(configuration: $0)) }
    }

    func makeBody(configuration: Configuration) -> some View { make(configuration) }
}

private extension View {
    /// Liquid Glass on iOS 26, the same material as the menu it opens; a prominent capsule before.
    @ViewBuilder
    func addCardButtonStyle() -> some View {
        if #available(iOS 26.0, *) {
            buttonStyle(.glass)
        } else {
            buttonStyle(.borderedProminent).buttonBorderShape(.capsule)
        }
    }
}
