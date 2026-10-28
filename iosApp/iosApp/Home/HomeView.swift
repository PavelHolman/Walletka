import PhotosUI
import SharedLogic
import SwiftUI
import UniformTypeIdentifiers

struct HomeView: View {
    private let component: any HomeComponent
    @State private var model: ObservedFlow<HomeModel>
    @State private var pickedPhoto: PhotosPickerItem?
    @State private var isDropTargeted = false

    init(component: any HomeComponent) {
        self.component = component
        _model = State(initialValue: ObservedFlow(component.model))
    }

    var body: some View {
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
                Text("Turn a photo of any loyalty card, ticket or pass into an Apple Wallet card. Share a photo to Walletka, paste one, or pick it from your library.")
                    .font(.body)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }
            VStack(spacing: 12) {
                PhotosPicker(selection: $pickedPhoto, matching: .images, photoLibrary: .shared()) {
                    Label("Choose Photo", systemImage: "photo.on.rectangle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)

                PasteButton(supportedContentTypes: [.image]) { providers in
                    guard let provider = providers.first else { return }
                    Task {
                        if let path = await ImageInbox.load(from: provider) {
                            component.onImagePicked(source: path)
                        }
                    }
                }
                .labelStyle(.titleAndIcon)
                .buttonBorderShape(.capsule)
                .controlSize(.large)
            }
            Label("Tip: in Photos, tap Share → Walletka", systemImage: "square.and.arrow.up")
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
        .toolbar(.hidden, for: .navigationBar)
    }
}
