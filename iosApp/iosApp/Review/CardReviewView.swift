import PassKit
import SharedLogic
import SwiftUI

struct CardReviewView: View {
    private let component: any CardReviewComponent
    @State private var model: ObservedFlow<any ReviewModel>
    @Environment(\.isSplitViewDetail) private var isSplitViewDetail

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
                        duplicate: editing.value.duplicateOf,
                        component: component
                    )
                }
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // In the iPad split view a new card is the root of its column, without a back button.
            if isSplitViewDetail && !isLibraryCard && !isAdded {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel", action: component.onCloseClicked)
                }
            }
        }
    }

    /** Also while a library card is still loading, so the title and the iPad Cancel button don't flicker. */
    private var isLibraryCard: Bool {
        switch model.value.sealedType() {
        case .editing(let editing): editing.value.form.isLibraryCard
        case .loading(let loading): loading.value.isLibraryCard
        default: false
        }
    }

    private var isAdded: Bool {
        if case .editing(let editing) = model.value.sealedType(), case .added = editing.value.submission.sealedType() { return true }
        return false
    }

    private var title: String {
        if isAdded { return "" } // the success screen has its own headline
        return isLibraryCard ? String(localized: "Edit Card") : String(localized: "New Card")
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
                    .accessibilityHidden(true)
            }
            HStack(spacing: 10) {
                ProgressView()
                Text(stage == .IMPORTING ? String(localized: "Reading the image…") : String(localized: "Looking for codes…"))
                    .foregroundStyle(.secondary)
            }
            .accessibilityElement(children: .combine)
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
            Label(title, systemImage: symbol)
        } description: {
            Text(message)
        } actions: {
            if failure == .DETECTION_FAILED {
                Button("Try Again", action: onRetry).buttonStyle(.borderedProminent)
            }
            Button("Back", action: onClose)
        }
    }

    private var symbol: String {
        switch failure {
        case .NO_CODE_FOUND: "qrcode.viewfinder"
        case .CARD_NOT_FOUND: "creditcard"
        default: "exclamationmark.triangle"
        }
    }

    private var title: String {
        switch failure {
        case .IMAGE_UNREADABLE: String(localized: "Can't Read Image")
        case .NO_CODE_FOUND: String(localized: "No Code Found")
        case .DETECTION_FAILED: String(localized: "Scanning Failed")
        case .CARD_NOT_FOUND: String(localized: "Card Not Found")
        }
    }

    private var message: String {
        switch failure {
        case .IMAGE_UNREADABLE: String(localized: "This doesn't look like an image Walletka can read.")
        case .NO_CODE_FOUND: String(localized: "Try a sharper photo with the whole QR code or barcode in view.")
        case .DETECTION_FAILED: String(localized: "Something went wrong while scanning the photo.")
        case .CARD_NOT_FOUND: String(localized: "The card has been deleted, maybe on another device.")
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
    @State private var hasAppeared = false

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 64))
                    .foregroundStyle(.green)
                    .symbolEffect(.bounce, value: true)
                    .accessibilityHidden(true)
                VStack(spacing: 6) {
                    Text(wasUpdate ? String(localized: "Updated in Apple Wallet") : String(localized: "Added to Apple Wallet"))
                        .font(.title2.bold())
                        .multilineTextAlignment(.center)
                        .accessibilityAddTraits(.isHeader)
                    Text("“\(form.name)” is ready to scan from Wallet.")
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                }
                CardPreview(form: form)
            }
            .padding(24)
            .frame(maxWidth: 460)
            .frame(maxWidth: .infinity)
        }
        .onAppear { hasAppeared = true }
        // The pass made it into Wallet.
        .sensoryFeedback(.success, trigger: hasAppeared)
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
            .frame(maxWidth: 460)
            .frame(maxWidth: .infinity)
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
    let duplicate: DuplicateCard?
    let component: any CardReviewComponent

    @State private var isEditingBanner = false
    @State private var isEditingLogo = false
    @Environment(\.verticalSizeClass) private var verticalSizeClass
    @ScaledMetric private var swatchSize: CGFloat = 38
    @State private var isPickingColorFromPhoto = false
    @State private var isConfirmingDelete = false
    /// More swatches are scrolled off the trailing edge.
    @State private var hasMoreSwatches = false
    @FocusState private var isNameFocused: Bool

    private var isBusy: Bool {
        if case .inProgress = submission.sealedType() { return true }
        return false
    }

    var body: some View {
        Form {
            if let duplicate {
                Section {
                    DuplicateCardBanner(duplicate: duplicate, onOpen: component.onOpenDuplicateClicked)
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                }
            }
            Section {
                CardPreview(form: form, compact: true)
                    // Wallet passes have a fixed width; a stretched preview on iPad would look nothing like one. In
                    // landscape on iPhone it stays small enough to leave room for the form.
                    .frame(maxWidth: verticalSizeClass == .compact ? 220 : 420)
                    .frame(maxWidth: .infinity)
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
                            Image(systemName: "xmark.circle.fill")
                                .foregroundStyle(.tertiary)
                                .frame(minWidth: 44, minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text("Clear Name"))
                    }
                }
            }

            codeSection
            colourSection
            photoSection
            CardDetailsSection(form: form, component: component)

            if showsWalletButton {
                Section {
                    walletButton
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                } footer: {
                    // Why the button is disabled; a code that can't be encoded is explained at the code.
                    if form.name.trimmingCharacters(in: .whitespaces).isEmpty {
                        Text("Enter a name to add the card.").frame(maxWidth: .infinity)
                    } else if form.selectedCode.value.trimmingCharacters(in: .whitespaces).isEmpty {
                        Text("Enter the card's code to add it.").frame(maxWidth: .infinity)
                    }
                }
            }
        }
        .listSectionSpacing(.compact)
        // While the pass is being issued, block input without greying the form out.
        .allowsHitTesting(!isBusy)
        .scrollDismissesKeyboard(.immediately)
        .dismissesKeyboardOnTapOutside()
        .toolbar {
            if form.isLibraryCard {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Delete Card", systemImage: "trash", role: .destructive) { isConfirmingDelete = true }
                        .tint(.red)
                        .accessibilityHint(Text("Removes the card from Walletka and Apple Wallet"))
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
        .sheet(isPresented: $isEditingLogo) {
            if let photo = form.photo {
                CropEditorView(
                    title: "Adjust Logo",
                    hint: "Drag and pinch the photo. The bright box becomes the logo.",
                    photoPath: photo.path,
                    viewport: form.logoViewport,
                    region: form.logo.region
                ) { region in
                    component.onLogoRegionChanged(region: region)
                    if !form.logo.isEnabled { component.onLogoToggled(enabled: true) }
                }
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
        .alert(form.isLibraryCard ? String(localized: "Couldn't Update the Card") : String(localized: "Couldn't Add the Card"), isPresented: Binding(
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
                        Text(verbatim: code.format.localizedName).tag(index)
                    }
                }
                .pickerStyle(.segmented)
            }
            if form.isCodeTyped {
                TypedCodeFormatPicker(form: form) { component.onCodeFormatSelected(format: $0) }
            }
            VStack(alignment: .leading, spacing: 4) {
                // A typed code's type is in the picker right above.
                if !form.isCodeTyped {
                    Text(verbatim: form.selectedCode.format.localizedName)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .accessibilityHidden(true)
                }
                // Typed codes are short, and only a single-line field takes the number pad; found codes (often URLs)
                // wrap.
                TextField("Code content", text: Binding(
                    get: { form.selectedCode.value },
                    set: { component.onCodeValueChanged(value: $0) }
                ), axis: form.isCodeTyped ? .horizontal : .vertical)
                .accessibilityLabel(Text("Code content, \(form.selectedCode.format.localizedName)"))
                // Monospaced like the printed code, but not the placeholder.
                .font(form.selectedCode.value.isEmpty ? .body : .body.monospaced())
                .autocorrectionDisabled()
                // The keyboard that fits what the symbology can hold (only while typing a code in).
                .keyboardType(form.isCodeTyped && Self.isNumeric(form.selectedCode.format) ? .numberPad : .default)
                .textInputAutocapitalization(form.isCodeTyped && Self.isUppercase(form.selectedCode.format) ? .characters : .never)
                .lineLimit(1...4)
            }
            .padding(.vertical, 2)
        } header: {
            if form.codes.count > 1 {
                Text("Codes Found (\(form.codes.count))")
            } else {
                Text("Code")
            }
        } footer: {
            if let issue = form.codeIssue {
                Label(Self.describe(issue, format: form.selectedCode.format), systemImage: "exclamationmark.triangle.fill")
                    .foregroundStyle(.orange)
            } else if form.walletCodeFormat != form.selectedCode.format {
                Text("Apple Wallet doesn't support \(form.selectedCode.format.localizedName); it will be shown as \(form.walletCodeFormat.localizedName) with the same content.")
            }
        }
    }

    private var colourSection: some View {
        Section("Colour") {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(Array(form.colors.enumerated()), id: \.offset) { index, color in
                        let selected = index == Int(form.selectedColorIndex)
                        Button {
                            component.onColorSelected(index: Int32(index))
                        } label: {
                            Circle()
                                .fill(color.swiftUIColor)
                                .frame(width: swatchSize, height: swatchSize)
                                // The border follows the theme, so black (dark mode) and white (light mode) swatches keep their outline.
                                .overlay(Circle().strokeBorder(selected ? Color.accentColor : Color.primary.opacity(0.25), lineWidth: selected ? 3 : 1))
                                .overlay {
                                    if selected {
                                        Image(systemName: "checkmark").font(.caption.bold()).foregroundStyle(color.contentColor.swiftUIColor)
                                    }
                                }
                                // At least 44 points to tap.
                                .frame(minWidth: 44, minHeight: 44)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel(Text(verbatim: color.accessibilityName))
                        .accessibilityValue(Text(verbatim: swatchValue(index: index, color: color)))
                        .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }
                .padding(.vertical, 2)
            }
            .onScrollGeometryChange(for: Bool.self) { geometry in
                geometry.contentOffset.x + geometry.containerSize.width < geometry.contentSize.width - 1
            } action: { _, hasMore in
                withAnimation(.easeOut(duration: 0.2)) { hasMoreSwatches = hasMore }
            }
            // Fades the cut-off swatch, so the row reads as scrollable rather than clipped.
            .mask {
                HStack(spacing: 0) {
                    Color.black
                    LinearGradient(colors: [.black, .black.opacity(hasMoreSwatches ? 0 : 1)], startPoint: .leading, endPoint: .trailing)
                        .frame(width: 32)
                }
            }
            .sensoryFeedback(.selection, trigger: form.selectedColorIndex)
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

    private static func isNumeric(_ format: CodeFormat) -> Bool {
        [.EAN_13, .EAN_8, .UPC_A, .UPC_E, .ITF].contains(format)
    }

    private static func isUppercase(_ format: CodeFormat) -> Bool {
        [.CODE_39, .CODE_93].contains(format)
    }

    /// Why a typed code can't be used, in words.
    private static func describe(_ issue: CodeIssue, format: CodeFormat) -> String {
        let name = format.localizedName
        switch issue {
        case .DIGITS_ONLY: return String(localized: "\(name) codes contain digits only.")
        case .LENGTH:
            switch format {
            case .EAN_13: return String(localized: "EAN-13 codes have 13 digits (12 without the check digit).")
            case .EAN_8: return String(localized: "EAN-8 codes have 8 digits (7 without the check digit).")
            case .UPC_A: return String(localized: "UPC-A codes have 12 digits (11 without the check digit).")
            case .UPC_E: return String(localized: "UPC-E codes have 6 to 8 digits.")
            default: return String(localized: "\(name) codes have an even number of digits.")
            }
        case .CHECK_DIGIT: return String(localized: "The last digit doesn't match the rest of the number. Check it for a typo.")
        case .CHARACTERS:
            switch format {
            case .CODE_39, .CODE_93: return String(localized: "\(name) holds only capital letters, digits, spaces and - . $ / + %.")
            case .CODABAR: return String(localized: "Codabar holds only digits and - $ : / . +.")
            default: return String(localized: "\(name) can't hold some of these characters. Try QR code instead.")
            }
        @unknown default: return ""
        }
    }

    /// Which swatch it is and its hex value: "Suggested colour 2 of 5, 1 A 2 3 7 E".
    private func swatchValue(index: Int, color: CardColor) -> String {
        let suggested = Int(form.suggestedColorCount)
        let kind = index >= suggested
            ? String(localized: "Custom colour")
            : String(localized: "Suggested colour \(index + 1) of \(suggested)")
        return "\(kind), \(color.accessibilityHex)"
    }

    /// The banner and the logo, both cut out of the photo (cards entered by hand have neither).
    @ViewBuilder
    private var photoSection: some View {
        if form.hasPhoto {
            Section {
                if form.banner != nil {
                    Toggle("Banner", isOn: Binding(get: { form.isBannerEnabled }, set: { component.onBannerToggled(enabled: $0) }))
                    if form.isBannerEnabled {
                        Button {
                            isEditingBanner = true
                        } label: {
                            HStack(spacing: 12) {
                                BannerThumbnail(path: form.banner?.path)
                                    .accessibilityHidden(true)
                                Text("Adjust Banner…")
                                Spacer()
                                Image(systemName: "chevron.right").font(.footnote.bold()).foregroundStyle(.tertiary)
                                    .accessibilityHidden(true)
                            }
                        }
                        .foregroundStyle(.primary)
                        .accessibilityHint(Text("Choose the part of the photo shown as the banner"))
                    }
                }
                CardLogoRows(form: form, component: component) { isEditingLogo = true }
            } header: {
                Text("From the Photo")
            } footer: {
                Text("The banner is a strip shown across the card; the logo replaces the name at the top.")
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
            .accessibilityLabel(isBusy ? String(localized: "Adding to Apple Wallet…") : String(localized: "Add to Apple Wallet"))
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
        case .UNAVAILABLE: String(localized: "Apple Wallet isn't available on this device.")
        case .NOT_CONFIGURED: String(localized: "Walletka can't create wallet passes right now. Try again later.")
        case .CANCELLED: String(localized: "Cancelled.")
        case .UNSUPPORTED_CODE: String(localized: "This code type can't be stored in Apple Wallet.")
        case .FAILED: String(localized: "Apple Wallet rejected the card.")
        case .NOT_IN_WALLET: String(localized: "The card is no longer in Apple Wallet.")
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
        VStack(spacing: 2) {
            row
            // Why it failed, as the wallet reported it.
            if status == .FAILED, let error, !error.isEmpty {
                Text(verbatim: error)
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                    .accessibilityHidden(true) // read as the row's hint
            }
        }
    }

    private var row: some View {
        HStack(spacing: 6) {
            switch status {
            case .SYNCED:
                Image(systemName: "checkmark.circle.fill").foregroundStyle(.green).accessibilityHidden(true)
                Text("Up to date in Wallet")
            case .PENDING:
                Image(systemName: "clock").accessibilityHidden(true)
                Text("Changes will sync to Wallet")
            case .SYNCING:
                ProgressView().controlSize(.mini)
                Text("Syncing with Wallet…")
            case .FAILED:
                Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange).accessibilityHidden(true)
                Text("Couldn't sync")
                Button("Retry", action: onRetry).font(.footnote.bold())
                    .accessibilityHidden(true)
            case .NOT_IN_WALLET:
                Image(systemName: "wallet.pass").foregroundStyle(.secondary).accessibilityHidden(true)
                Text("Not in Apple Wallet on this device. Add it below.")
                    .multilineTextAlignment(.center)
            }
        }
        .font(.footnote)
        .foregroundStyle(.secondary)
        .animation(.default, value: status)
        .accessibilityElement(children: .combine)
        .accessibilityHint(status == .FAILED ? Text(verbatim: error ?? "") : Text(verbatim: ""))
        // The retry button is part of the combined element, so offer it as an action.
        .accessibilityActions {
            if status == .FAILED { Button("Retry", action: onRetry) }
        }
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
        // As wide as the logo's thumbnail (CardLogoRows), so both rows' titles line up.
        .frame(width: 92, height: 30)
        .clipShape(RoundedRectangle(cornerRadius: 6))
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
                    Group {
                        if form.name.isEmpty { Text("Card Name") } else { Text(verbatim: form.name) }
                    }
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
            } else {
                // No code typed yet: keep the card's shape and show where the code goes.
                let isTwoDimensional = form.walletCodeFormat.isTwoDimensional
                Image(systemName: isTwoDimensional ? "qrcode" : "barcode")
                    .font(.system(size: isTwoDimensional ? 44 : 36, weight: .light))
                    .foregroundStyle(content.opacity(0.5))
                    .frame(width: isTwoDimensional ? 110 : 180, height: isTwoDimensional ? 110 : 64)
                    .overlay(
                        RoundedRectangle(cornerRadius: 8)
                            .strokeBorder(content.opacity(0.35), style: StrokeStyle(lineWidth: 1.5, dash: [5, 4]))
                    )
                    .padding(compact ? 14 : 20)
            }
        }
        .frame(maxWidth: .infinity)
        .background(background, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        // It pictures the pass, whose text Wallet doesn't scale; VoiceOver reads it as a whole anyway.
        .dynamicTypeSize(...DynamicTypeSize.xLarge)
        .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
        .padding(.vertical, 8)
        .animation(.default, value: form.selectedColor.hex)
        .animation(.default, value: form.isBannerEnabled)
        .animation(.default, value: form.logo.activeImage?.path)
        // One element: what the card looks like, not its parts.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Card preview"))
        .accessibilityValue(Text(verbatim: accessibilityValue))
        .accessibilityAddTraits(.isImage)
    }

    /// Name, code type and value, colour, and which of banner and logo the card shows.
    private var accessibilityValue: String {
        var parts = [form.name.isEmpty ? String(localized: "No name") : form.name]
        parts.append(String(localized: "\(form.walletCodeFormat.localizedName): \(form.selectedCode.value)"))
        parts.append(form.selectedColor.accessibilityName)
        if form.logo.activeImage != nil { parts.append(String(localized: "with logo")) }
        if form.isBannerEnabled && form.banner != nil { parts.append(String(localized: "with banner")) }
        return parts.joined(separator: ", ")
    }
}

/// The system "Add to Apple Wallet" button.
private struct AddToWalletButton: UIViewRepresentable {
    let action: () -> Void

    @Environment(\.colorScheme) private var colorScheme

    func makeUIView(context: Context) -> PKAddPassButton {
        // The outlined style keeps the black button visible on a dark background.
        let button = PKAddPassButton(addPassButtonStyle: colorScheme == .dark ? .blackOutline : .black)
        button.addTarget(context.coordinator, action: #selector(Coordinator.tapped), for: .touchUpInside)
        return button
    }

    func updateUIView(_ uiView: PKAddPassButton, context: Context) {
        context.coordinator.action = action
        uiView.addPassButtonStyle = colorScheme == .dark ? .blackOutline : .black
    }

    func makeCoordinator() -> Coordinator { Coordinator(action: action) }

    final class Coordinator: NSObject {
        var action: () -> Void

        init(action: @escaping () -> Void) { self.action = action }

        @objc func tapped() { action() }
    }
}
