import PhotosUI
import SharedLogic
import SwiftUI
import UniformTypeIdentifiers

/// How Home is shown: the whole screen (iPhone), or split on iPad into the library sidebar and a welcome screen in
/// the detail column while no card is open.
enum HomePresentation: Equatable {
    case full
    case sidebar(selectedCardId: String?)
    case welcome
}

/// The card library, plus the ways to add a card (camera, photo library, paste, drop).
struct HomeView: View {
    private let component: any HomeComponent
    private let presentation: HomePresentation
    @State private var model: ObservedFlow<HomeModel>
    @State private var pickedPhoto: PhotosPickerItem?
    @State private var isDropTargeted = false
    @State private var isCameraPresented = false
    @State private var isPhotoPickerPresented = false
    @State private var cardPendingDeletion: CardSummary?
    @State private var isImportingBackup = false
    /// A picked photo is being read from the library; that can take a moment for large or iCloud photos.
    @State private var isLoadingPhoto = false
    /// Checked without reading the clipboard, so no paste prompt appears.
    @State private var hasPasteboardImage = UIPasteboard.general.hasImages

    init(component: any HomeComponent, presentation: HomePresentation = .full) {
        self.component = component
        self.presentation = presentation
        _model = State(initialValue: ObservedFlow(component.model))
    }

    var body: some View {
        Group {
            switch presentation {
            case .full:
                if model.value.cards.isEmpty { emptyState } else { library }
            case .sidebar:
                library
            case .welcome:
                emptyState
            }
        }
        .overlay {
            if isLoadingPhoto {
                ProgressView("Opening Photo…")
                    .padding(20)
                    .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .transition(.opacity)
            }
        }
        .animation(.default, value: isLoadingPhoto)
        .onChange(of: QuickActionInbox.shared.pending, initial: true) { _, action in
            // Only one Home handles it (the iPad shows two: the sidebar and the welcome screen).
            guard let action, presentation != .welcome else { return }
            QuickActionInbox.shared.pending = nil
            switch action {
            case .scanCard: isCameraPresented = true
            case .newCard: component.onEnterManuallyClicked()
            }
        }
        .onReceive(NotificationCenter.default.publisher(for: UIPasteboard.changedNotification)) { _ in
            hasPasteboardImage = UIPasteboard.general.hasImages
        }
        .onReceive(NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)) { _ in
            hasPasteboardImage = UIPasteboard.general.hasImages
        }
        .libraryBackup(component: component, backup: model.value.backup, isImporting: $isImportingBackup)
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
            isLoadingPhoto = true
            Task {
                if let data = try? await item.loadTransferable(type: Data.self), let path = ImageInbox.store(data) {
                    component.onImagePicked(source: path)
                }
                pickedPhoto = nil
                isLoadingPhoto = false
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
            ForEach(Array(model.value.visibleCards.enumerated()), id: \.element.id) { index, card in
                Button {
                    component.onCardClicked(cardId: card.id)
                } label: {
                    CardTile(card: card, isSelected: selectedCardId == card.id)
                }
                .buttonStyle(.plain)
                // VoiceOver: the tile reads as one element; Edit and reordering are its actions (the swipe action
                // already offers Delete).
                .accessibilityAction(named: Text("Edit")) { component.onCardClicked(cardId: card.id) }
                .accessibilityActions {
                    if model.value.canReorder && index > 0 {
                        Button("Move Up") { component.onCardMoved(fromIndex: Int32(index), toIndex: Int32(index - 1)) }
                    }
                    if model.value.canReorder && index < model.value.visibleCards.count - 1 {
                        Button("Move Down") { component.onCardMoved(fromIndex: Int32(index), toIndex: Int32(index + 1)) }
                    }
                }
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
            .onMove(perform: model.value.canReorder ? moveCards : nil)
        }
        .listStyle(.plain)
        .overlay {
            if model.value.cards.isEmpty {
                ContentUnavailableView("No Cards Yet", systemImage: "wallet.pass", description: Text("Cards you add appear here."))
            } else if model.value.visibleCards.isEmpty {
                ContentUnavailableView.search(text: model.value.searchQuery)
            }
        }
        .navigationTitle("Walletka")
        .searchable(if: model.value.isSearchAvailable, text: Binding(
            get: { model.value.searchQuery },
            set: { component.onSearchQueryChanged(query: $0) }
        ), prompt: "Name or code")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                LibraryMenu(component: component, cloudSync: model.value.cloudSync, cloudSyncError: model.value.cloudSyncError, isImporting: $isImportingBackup)
            }
        }
        .addCardPlacement(
            inBottomToolbar: presentation == .full,
            withSearch: model.value.isSearchAvailable
        ) { addMenu }
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
                Button("Scan Card", systemImage: "camera.viewfinder") { isCameraPresented = true }
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

    private var selectedCardId: String? {
        if case .sidebar(let id) = presentation { return id }
        return nil
    }

    /// SwiftUI reports the destination before the source is removed; the shared code wants the final index.
    private func moveCards(from source: IndexSet, to destination: Int) {
        guard let from = source.first else { return }
        component.onCardMoved(fromIndex: Int32(from), toIndex: Int32(destination > from ? destination - 1 : destination))
    }

    private func pasteImage() {
        guard let image = UIPasteboard.general.image, let data = image.jpegData(compressionQuality: 0.9),
              let path = ImageInbox.store(data, fileExtension: "jpg") else { return }
        component.onImagePicked(source: path)
    }

    // MARK: - Empty state

    private var emptyState: some View {
        // Centred while it fits; scrolls at large Dynamic Type sizes instead of clipping.
        GeometryReader { proxy in
            ScrollView {
                emptyStateContent
                    .frame(maxWidth: .infinity, minHeight: proxy.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .toolbar(.hidden, for: .navigationBar)
    }

    private var emptyStateContent: some View {
        VStack(spacing: 32) {
            VStack(spacing: 16) {
                Image("BrandMark")
                    .resizable()
                    .frame(width: 112, height: 112)
                    .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                    .shadow(color: .indigo.opacity(0.25), radius: 16, y: 8)
                    .accessibilityHidden(true)
                VStack(spacing: 8) {
                    Text(isWelcomeWithCards ? String(localized: "Pick a Card") : String(localized: "Walletka"))
                        .font(.largeTitle.bold())
                        .accessibilityAddTraits(.isHeader)
                    Text(isWelcomeWithCards
                         ? String(localized: "Choose a card in the library to edit it, or add a new one.")
                         : String(localized: "Snap or share a photo of any loyalty card, ticket or membership, and Walletka turns it into an Apple Wallet card."))
                        .font(.body)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
            }
            VStack(spacing: 12) {
                if CameraCapture.isAvailable {
                    Button {
                        isCameraPresented = true
                    } label: {
                        Label("Scan Card", systemImage: "camera.viewfinder")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }

                PhotosPicker(selection: $pickedPhoto, matching: .images, photoLibrary: .shared()) {
                    Label("Choose Photo", systemImage: "photo.on.rectangle")
                        .frame(maxWidth: .infinity)
                }
                .buttonStyle(CameraCapture.isAvailable ? AnyPrimitiveButtonStyle(.bordered) : AnyPrimitiveButtonStyle(.borderedProminent))

                HStack(spacing: 12) {
                    if hasPasteboardImage { pasteButton }
                    Button {
                        component.onEnterManuallyClicked()
                    } label: {
                        Label("Enter Manually", systemImage: "keyboard")
                    }
                    .buttonStyle(.bordered)
                }
                .buttonBorderShape(.capsule)
            }
            .controlSize(.large)
            .buttonBorderShape(.capsule)

            VStack(spacing: 14) {
                Label("Tip: in Photos, tap Share → Add to Walletka", systemImage: "square.and.arrow.up")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                if !isWelcomeWithCards {
                    Button("Restore from Backup…") { isImportingBackup = true }
                        .font(.footnote.weight(.medium))
                }
                if model.value.isWalletAvailable == false {
                    Label("Apple Wallet isn't available on this device.", systemImage: "exclamationmark.circle")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .padding(28)
        .frame(maxWidth: 460)
    }

    /// The iPad detail column while the library has cards but none is open.
    private var isWelcomeWithCards: Bool { presentation == .welcome && !model.value.cards.isEmpty }

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
    var isSelected = false

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var width: CGFloat = 0

    var body: some View {
        let content = card.color.contentColor.swiftUIColor
        let isLarge = dynamicTypeSize.isAccessibilitySize
        // With the banner thumbnail while the text keeps room next to it (not in a narrow sidebar or at large sizes).
        row(content: content, isLarge: isLarge, showsBanner: !isLarge && card.banner != nil && width >= 320)
        .padding(16)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { width = $0 }
        .frame(maxWidth: .infinity, minHeight: 76)
        .background {
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(card.color.swiftUIColor)
                // A soft sheen, like a printed card.
                .overlay(
                    LinearGradient(colors: [.white.opacity(0.14), .clear], startPoint: .top, endPoint: .center),
                    in: RoundedRectangle(cornerRadius: 16, style: .continuous)
                )
        }
        .overlay {
            // The card open next to the library (iPad).
            if isSelected {
                RoundedRectangle(cornerRadius: 19, style: .continuous)
                    .strokeBorder(Color.accentColor, lineWidth: 3)
                    .padding(-4)
            }
        }
        .contentShape(RoundedRectangle(cornerRadius: 16))
        // Lifts under the pointer on iPad.
        .contentShape(.hoverEffect, RoundedRectangle(cornerRadius: 16, style: .continuous))
        .hoverEffect(.lift)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: card.name))
        .accessibilityValue(Text(verbatim: accessibilityValue))
        .accessibilityHint(Text("Opens the card editor"))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }

    private func row(content: Color, isLarge: Bool, showsBanner: Bool) -> some View {
        HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(verbatim: card.name)
                    .font(.headline)
                    .lineLimit(isLarge ? 3 : 1)
                Text(verbatim: "\(card.codeFormat.localizedName) · \(card.codeValue)")
                    .font(.caption.monospaced())
                    .lineLimit(isLarge ? 2 : 1)
                    .truncationMode(.middle)
                    .opacity(0.8)
                WalletStatusBadge(isInWallet: card.isInWallet, color: content)
            }
            .foregroundStyle(content)
            Spacer(minLength: 0)
            if showsBanner, let path = card.banner?.path, let banner = UIImage(contentsOfFile: path) {
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
    }

    /// Code type and value, and whether the card is in Apple Wallet.
    private var accessibilityValue: String {
        var parts = [card.codeFormat.localizedName, card.codeValue]
        switch card.isInWallet {
        case true?: parts.append(String(localized: "In Apple Wallet"))
        case false?: parts.append(String(localized: "Not in Apple Wallet"))
        case nil: break
        }
        return parts.joined(separator: ", ")
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
    /// `.searchable` only when [condition] holds, so short libraries keep a plain title bar.
    @ViewBuilder
    func searchable(if condition: Bool, text: Binding<String>, prompt: LocalizedStringKey) -> some View {
        if condition {
            searchable(text: text, prompt: Text(prompt))
        } else {
            self
        }
    }

    /// The Add Card menu below the library. While the iPhone library shows a search field (iOS 26 puts it in the bottom
    /// toolbar), the menu joins it there as in Notes or Mail; otherwise it floats above the list with its title.
    @ViewBuilder
    func addCardPlacement<Menu: View>(inBottomToolbar: Bool, withSearch: Bool, @ViewBuilder menu: () -> Menu) -> some View {
        if #available(iOS 26.0, *), inBottomToolbar, withSearch {
            let menu = menu()
            toolbar {
                DefaultToolbarItem(kind: .search, placement: .bottomBar)
                ToolbarSpacer(.fixed, placement: .bottomBar)
                ToolbarItem(placement: .bottomBar) { menu }
            }
        } else {
            safeAreaInset(edge: .bottom) {
                menu()
                    .padding(.horizontal, 20)
                    .padding(.vertical, 12)
            }
        }
    }

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
