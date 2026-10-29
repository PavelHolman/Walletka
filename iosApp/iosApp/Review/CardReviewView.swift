import PassKit
import SharedLogic
import SwiftUI

struct CardReviewView: View {
    private let component: any CardReviewComponent
    @State private var model: ObservedFlow<any ReviewModel>

    init(component: any CardReviewComponent) {
        self.component = component
        _model = State(initialValue: ObservedFlow(component.model))
    }

    var body: some View {
        Group {
            switch model.value.sealedType() {
            case .loading(let loading):
                LoadingView(stage: loading.value.stage, photoPath: loading.value.photo?.path)
            case .failed(let failed):
                FailedView(
                    failure: failed.value.failure,
                    onRetry: component.onRetryClicked,
                    onClose: component.onCloseClicked
                )
            case .editing(let editing):
                if case .added(let added) = editing.value.submission.sealedType() {
                    CardAddedView(
                        form: editing.value.form,
                        walletUrl: added.value.walletUrl,
                        wasUpdate: added.value.replacedExisting,
                        onDone: component.onCloseClicked
                    )
                } else {
                    CardFormView(
                        form: editing.value.form,
                        submission: editing.value.submission,
                        hasChanges: editing.value.hasChanges,
                        sync: editing.value.sync,
                        syncError: editing.value.syncError,
                        colorPreview: editing.value.colorPreview,
                        component: component
                    )
                    .safeAreaInset(edge: .top) {
                        if let duplicate = editing.value.duplicateOf {
                            DuplicateCardBanner(duplicate: duplicate, onOpen: component.onOpenDuplicateClicked)
                        }
                    }
                }
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
    }

    private var title: String {
        if case .editing(let editing) = model.value.sealedType(), editing.value.form.isLibraryCard { return "Edit Card" }
        return "New Card"
    }
}

// MARK: - Loading / failure / success

private struct LoadingView: View {
    let stage: LoadingStage
    let photoPath: String?

    @State private var photo: UIImage?
    @State private var scanOffset: CGFloat = -1

    var body: some View {
        VStack(spacing: 24) {
            if let photo {
                // The photo being scanned, with a sweeping scan line.
                Image(uiImage: photo)
                    .resizable()
                    .scaledToFit()
                    .frame(maxHeight: 360)
                    .overlay {
                        GeometryReader { geometry in
                            LinearGradient(
                                colors: [.clear, Color.accentColor.opacity(0.55), .clear],
                                startPoint: .top, endPoint: .bottom
                            )
                            .frame(height: 64)
                            .offset(y: (scanOffset + 1) / 2 * (geometry.size.height - 64))
                        }
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                    .shadow(color: .black.opacity(0.12), radius: 12, y: 4)
                    .onAppear {
                        withAnimation(.easeInOut(duration: 1.2).repeatForever(autoreverses: true)) { scanOffset = 1 }
                    }
                    .transition(.opacity)
            }
            HStack(spacing: 10) {
                ProgressView()
                Text(stage == .IMPORTING ? "Reading the image…" : "Looking for codes…")
                    .foregroundStyle(.secondary)
            }
        }
        .padding(32)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .animation(.default, value: photo != nil)
        .task(id: photoPath) {
            guard let photoPath else { return }
            photo = await Task.detached {
                UIImage(contentsOfFile: photoPath)?.preparingThumbnail(of: CGSize(width: 1200, height: 1200))
            }.value
        }
    }
}

private struct FailedView: View {
    let failure: AnalysisFailure
    let onRetry: () -> Void
    let onClose: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: failure == .NO_CODE_FOUND ? "qrcode.viewfinder" : "exclamationmark.triangle")
        } description: {
            Text(message)
        } actions: {
            if failure == .DETECTION_FAILED {
                Button("Try Again", action: onRetry).buttonStyle(.borderedProminent)
            }
            Button("Choose Another Photo", action: onClose)
        }
    }

    private var title: String {
        switch failure {
        case .IMAGE_UNREADABLE: "Can't Read Image"
        case .NO_CODE_FOUND: "No Code Found"
        case .DETECTION_FAILED: "Scanning Failed"
        }
    }

    private var message: String {
        switch failure {
        case .IMAGE_UNREADABLE: "This doesn't look like an image Walletka can read."
        case .NO_CODE_FOUND: "Try a sharper photo with the whole QR code or barcode in view."
        case .DETECTION_FAILED: "Something went wrong while scanning the photo."
        }
    }
}

/// Shown once PassKit confirms the pass is in the library.
private struct CardAddedView: View {
    let form: CardForm
    let walletUrl: String?
    let wasUpdate: Bool
    let onDone: () -> Void

    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(.green)
                    .symbolEffect(.bounce, value: true)
                VStack(spacing: 6) {
                    Text(wasUpdate ? "Updated in Apple Wallet" : "Added to Apple Wallet").font(.title2.bold())
                    Text("“\(form.name)” is ready to scan from Wallet.")
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                CardPreview(form: form)
            }
            .padding(24)
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 10) {
                if let url = walletUrl.flatMap(URL.init(string:)) {
                    Button {
                        openURL(url)
                    } label: {
                        Label("Open in Wallet", systemImage: "wallet.pass").frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                }
                Button(action: onDone) {
                    Text("Done").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
            .controlSize(.large)
            .padding(.horizontal, 24)
            .padding(.vertical, 12)
            .background(.bar)
        }
        .navigationBarBackButtonHidden()
    }
}

// MARK: - Form

private struct CardFormView: View {
    let form: CardForm
    let submission: any Submission
    let hasChanges: Bool
    let sync: SyncStatus?
    let syncError: String?
    let colorPreview: CardColor?
    let component: any CardReviewComponent

    @State private var isEditingBanner = false
    @State private var isPickingColorFromPhoto = false
    @State private var isConfirmingDelete = false
    @FocusState private var isNameFocused: Bool

    private var isBusy: Bool {
        if case .inProgress = submission.sealedType() { return true }
        return false
    }

    var body: some View {
        Form {
            Section {
                CardPreview(form: form, compact: true)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            } footer: {
                if let sync {
                    SyncStatusView(status: sync, error: syncError, onRetry: component.onRetrySyncClicked)
                        .frame(maxWidth: .infinity)
                }
            }

            Section("Name") {
                HStack {
                    TextField("e.g. Coffee Club", text: Binding(get: { form.name }, set: { component.onNameChanged(name: $0) }))
                        .textInputAutocapitalization(.words)
                        .submitLabel(.done)
                        .focused($isNameFocused)
                    if isNameFocused && !form.name.isEmpty {
                        Button {
                            component.onNameChanged(name: "")
                        } label: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(.tertiary)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Clear name")
                    }
                }
            }

            codeSection
            colourSection
            bannerSection
            if form.hasPhoto {
                CardLogoSection(form: form, component: component)
            }
            CardDetailsSection(form: form, component: component)

            if showsWalletButton {
                Section {
                    walletButton
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }
            }
        }
        // While the pass is being issued, block input without greying the form out.
        .allowsHitTesting(!isBusy)
        .scrollDismissesKeyboard(.immediately)
        .dismissesKeyboardOnTapOutside()
        .toolbar {
            if form.isLibraryCard {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Delete Card", systemImage: "trash", role: .destructive) { isConfirmingDelete = true }
                        .tint(.red)
                        // Attached to the button so the dialog points at it.
                        .confirmationDialog("Delete “\(form.name)”?", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                            Button("Delete from Walletka and Wallet", role: .destructive, action: component.onDeleteClicked)
                        } message: {
                            Text("The card is removed from Walletka and from Apple Wallet.")
                        }
                }
            }
        }
        .sheet(isPresented: $isPickingColorFromPhoto) {
            if let photo = form.photo {
                PhotoColorPickerView(
                    photoPath: photo.path,
                    preview: colorPreview,
                    onPick: { x, y in component.onColorPreviewRequested(x: x, y: y) },
                    onConfirm: component.onColorPreviewConfirmed,
                    onCancel: component.onColorPreviewDismissed
                )
            }
        }
        .sheet(isPresented: $isEditingBanner) {
            if let editor = CropEditorView(bannerOf: form, onDone: { region in
                component.onBannerRegionChanged(region: region)
                if !form.isBannerEnabled { component.onBannerToggled(enabled: true) }
            }) {
                editor
            }
        }
        .alert(form.isLibraryCard ? "Couldn't Update the Card" : "Couldn't Add the Card", isPresented: Binding(
            get: { failure != nil },
            set: { if !$0 { component.onSubmissionErrorDismissed() } }
        )) {
            Button("OK") {}
        } message: {
            Text(failure.map(Self.message) ?? "")
        }
    }

    private var codeSection: some View {
        Section {
            if form.codes.count > 1 {
                Picker("Code", selection: Binding(
                    get: { Int(form.selectedCodeIndex) },
                    set: { component.onCodeSelected(index: Int32($0)) }
                )) {
                    ForEach(Array(form.codes.enumerated()), id: \.offset) { index, code in
                        Text(code.format.displayName).tag(index)
                    }
                }
                .pickerStyle(.segmented)
            }
            if form.isCodeTyped {
                TypedCodeFormatPicker(form: form) { component.onCodeFormatSelected(format: $0) }
            }
            VStack(alignment: .leading, spacing: 4) {
                Text(form.selectedCode.format.displayName)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField("Code content", text: Binding(
                    get: { form.selectedCode.value },
                    set: { component.onCodeValueChanged(value: $0) }
                ), axis: .vertical)
                .font(.body.monospaced())
                .autocorrectionDisabled()
                .textInputAutocapitalization(.never)
                .lineLimit(1...4)
            }
            .padding(.vertical, 2)
        } header: {
            Text(form.codes.count > 1 ? "Codes Found (\(form.codes.count))" : "Code")
        } footer: {
            if form.walletCodeFormat != form.selectedCode.format {
                Text("Apple Wallet doesn't support \(form.selectedCode.format.displayName); it will be shown as \(form.walletCodeFormat.displayName) with the same content.")
            }
        }
    }

    private var colourSection: some View {
        Section("Colour") {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(Array(form.colors.enumerated()), id: \.offset) { index, color in
                        let selected = index == Int(form.selectedColorIndex)
                        Button {
                            component.onColorSelected(index: Int32(index))
                        } label: {
                            Circle()
                                .fill(color.swiftUIColor)
                                .frame(width: 38, height: 38)
                                .overlay(Circle().strokeBorder(selected ? Color.accentColor : Color.secondary.opacity(0.3), lineWidth: selected ? 3 : 1))
                                .overlay {
                                    if selected {
                                        Image(systemName: "checkmark").font(.caption.bold()).foregroundStyle(color.contentColor.swiftUIColor)
                                    }
                                }
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(index >= Int(form.suggestedColorCount) ? "Custom colour" : "Colour \(index + 1)")
                        .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }
                .padding(.vertical, 4)
            }
            ColorPicker("Custom Colour", selection: Binding(
                get: { (form.customColor ?? form.selectedColor).swiftUIColor },
                set: { component.onCustomColorSelected(color: CardColor(color: $0)) }
            ), supportsOpacity: false)
            if form.hasPhoto {
                Button {
                    isPickingColorFromPhoto = true
                } label: {
                    Label("Pick from Photo", systemImage: "eyedropper")
                }
            }
        }
    }

    @ViewBuilder
    private var bannerSection: some View {
        if form.banner != nil {
            Section {
                Toggle("Banner from Photo", isOn: Binding(get: { form.isBannerEnabled }, set: { component.onBannerToggled(enabled: $0) }))
                if form.isBannerEnabled {
                    Button {
                        isEditingBanner = true
                    } label: {
                        HStack(spacing: 12) {
                            BannerThumbnail(path: form.banner?.path)
                            Text("Adjust Banner…")
                            Spacer()
                            Image(systemName: "chevron.right").font(.footnote.bold()).foregroundStyle(.tertiary)
                        }
                    }
                    .foregroundStyle(.primary)
                }
            } footer: {
                Text("A strip from your photo, shown at the top of the card in Wallet.")
            }
        }
    }

    /// New cards are added with the system button; library cards sync by themselves unless the pass is gone.
    private var showsWalletButton: Bool {
        !form.isLibraryCard || sync == nil || sync == .NOT_IN_WALLET
    }

    private var walletButton: some View {
        AddToWalletButton(action: component.onAddToWalletClicked)
            .frame(height: 50)
            .opacity(isBusy ? 0 : (form.isValid ? 1 : 0.4))
            .overlay {
                if isBusy {
                    RoundedRectangle(cornerRadius: 12).fill(.black)
                        .overlay { ProgressView().tint(.white) }
                }
            }
            .disabled(!form.isValid || isBusy)
    }

    /// Failed submissions worth telling the user about (cancelling the sheet is not an error).
    private var failure: (WalletFailure, String?)? {
        if case .failed(let failed) = submission.sealedType(), failed.value.failure != .CANCELLED {
            return (failed.value.failure, failed.value.detail)
        }
        return nil
    }

    private static func message(_ failure: (WalletFailure, String?)) -> String {
        let text = switch failure.0 {
        case .UNAVAILABLE: "Apple Wallet isn't available on this device."
        case .NOT_CONFIGURED: "Apple Wallet passes are signed by the Walletka pass service. Set WALLETKA_SERVICE_URL (and WALLETKA_SERVICE_API_KEY) in Config.xcconfig."
        case .CANCELLED: "Cancelled."
        case .UNSUPPORTED_CODE: "This code type can't be stored in Apple Wallet."
        case .FAILED: "Apple Wallet rejected the card."
        case .NOT_IN_WALLET: "The card is no longer in Apple Wallet."
        }
        return [text, failure.1].compactMap { $0 }.joined(separator: "\n\n")
    }
}

/// Where a library card stands with Wallet; edits are pushed automatically.
private struct SyncStatusView: View {
    let status: SyncStatus
    let error: String?
    let onRetry: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            switch status {
            case .SYNCED:
                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green)
                Text("Up to date in Wallet")
            case .PENDING:
                Image(systemName: "clock")
                Text("Changes will sync to Wallet")
            case .SYNCING:
                ProgressView().controlSize(.mini)
                Text("Syncing with Wallet…")
            case .FAILED:
                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
                Text("Couldn't sync")
                Button("Retry", action: onRetry).font(.footnote.bold())
            case .NOT_IN_WALLET:
                Image(systemName: "wallet.pass").foregroundStyle(.secondary)
                Text("Not in Wallet anymore. Add it again below.")
            }
        }
        .font(.footnote)
        .foregroundStyle(.secondary)
        .animation(.default, value: status)
        .accessibilityElement(children: .combine)
        .accessibilityHint(status == .FAILED ? (error ?? "") : "")
    }
}

private struct BannerThumbnail: View {
    let path: String?

    var body: some View {
        Group {
            if let path, let image = UIImage(contentsOfFile: path) {
                Image(uiImage: image).resizable().scaledToFill()
            } else {
                Color.secondary.opacity(0.15)
            }
        }
        .frame(width: 84, height: 28)
        .clipShape(RoundedRectangle(cornerRadius: 5))
    }
}

// MARK: - Card preview

/// The card roughly as Wallet shows it. `compact` shrinks the code so the editor stays in view.
private struct CardPreview: View {
    let form: CardForm
    var compact = false

    var body: some View {
        let background = form.selectedColor.swiftUIColor
        let content = form.selectedColor.contentColor.swiftUIColor
        VStack(spacing: 0) {
            HStack {
                // Like Wallet: a logo replaces the name in the header.
                if let logo = form.logo.activeImage?.path {
                    PassLogoView(path: logo)
                } else {
                    Text(form.name.isEmpty ? "Card Name" : form.name)
                        .font(.headline)
                        .lineLimit(1)
                }
                Spacer()
            }
            .foregroundStyle(content)
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            if form.isBannerEnabled, let path = form.banner?.path, let banner = UIImage(contentsOfFile: path) {
                Color.clear
                    .aspectRatio(375.0 / 123.0, contentMode: .fit)
                    .overlay {
                        Image(uiImage: banner)
                            .resizable()
                            .scaledToFill()
                    }
                    .clipped()
            }

            if let code = CodeImage.make(format: form.walletCodeFormat, value: form.selectedCode.value) {
                let height: CGFloat = form.walletCodeFormat.isTwoDimensional ? (compact ? 96 : 140) : (compact ? 52 : 70)
                VStack(spacing: 4) {
                    Image(uiImage: code)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: height)
                    if !form.walletCodeFormat.isTwoDimensional {
                        Text(form.selectedCode.value).font(.caption2.monospaced()).foregroundStyle(.black)
                    }
                }
                .padding(10)
                .background(.white, in: RoundedRectangle(cornerRadius: 8))
                .padding(compact ? 14 : 20)
            }
        }
        .frame(maxWidth: .infinity)
        .background(background, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
        .padding(.vertical, 8)
        .animation(.default, value: form.selectedColor.hex)
    }
}

/// The system "Add to Apple Wallet" button.
private struct AddToWalletButton: UIViewRepresentable {
    let action: () -> Void

    func makeUIView(context: Context) -> PKAddPassButton {
        let button = PKAddPassButton(addPassButtonStyle: .black)
        button.addTarget(context.coordinator, action: #selector(Coordinator.tapped), for: .touchUpInside)
        return button
    }

    func updateUIView(_ uiView: PKAddPassButton, context: Context) {
        context.coordinator.action = action
    }

    func makeCoordinator() -> Coordinator { Coordinator(action: action) }

    final class Coordinator: NSObject {
        var action: () -> Void

        init(action: @escaping () -> Void) { self.action = action }

        @objc func tapped() { action() }
    }
}
