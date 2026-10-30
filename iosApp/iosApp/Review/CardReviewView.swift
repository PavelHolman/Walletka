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
                CardFormView(
                    form: editing.value.form,
                    submission: editing.value.submission,
                    sync: editing.value.sync,
                    syncError: editing.value.syncError,
                    colorPreview: editing.value.colorPreview,
                    duplicate: editing.value.duplicateOf,
                    component: component
                )
            }
        }
        .navigationTitle(title)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            // In the iPad split view a new card is the root of its column, without a back button.
            if isSplitViewDetail && !isLibraryCard {
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

    private var title: String {
        isLibraryCard ? String(localized: "Edit Card") : String(localized: "New Card")
    }
}

// MARK: - Loading / failure

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
                    .compositingGroup()
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

// MARK: - Form

private struct CardFormView: View {
    let form: CardForm
    let submission: any Submission
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
    /// More swatches are scrolled off the trailing edge.
    @State private var hasMoreSwatches = false
    @FocusState private var isNameFocused: Bool
    @FocusState private var isCodeFocused: Bool

    /// The card is typed in and its code is still missing.
    private var needsTypedCode: Bool { form.isCodeTyped && form.selectedCode.value.isEmpty }

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
                CardPreview(form: form, size: .compact)
                    // Wallet passes have a fixed width; a stretched preview on iPad would look nothing like one. In
                    // landscape on iPhone it stays small enough to leave room for the form.
                    .frame(maxWidth: verticalSizeClass == .compact ? 220 : 420)
                    .frame(maxWidth: .infinity)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            } footer: {
                // Only cards in Wallet sync; the others just keep their edits in Walletka.
                if let sync, sync != .NOT_IN_WALLET {
                    SyncStatusView(status: sync, error: syncError, onRetry: component.onRetrySyncClicked)
                        .frame(maxWidth: .infinity)
                }
            }

            Section("Name") {
                HStack {
                    TextField("e.g. Coffee Club", text: Binding(get: { form.name }, set: { component.onNameChanged(name: $0) }))
                        .textInputAutocapitalization(.words)
                        // Typing a card in: Return moves on to its code.
                        .submitLabel(needsTypedCode ? .next : .done)
                        .onSubmit { if needsTypedCode { isCodeFocused = true } }
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

            // A new card is saved to Walletka (its preview then offers Wallet); a library card's edits save by
            // themselves.
            if !form.isLibraryCard {
                Section {
                    saveButton
                        .listRowInsets(EdgeInsets())
                        .listRowBackground(Color.clear)
                } footer: {
                    // Why the button is disabled; a code that can't be encoded is explained at the code.
                    if form.name.trimmingCharacters(in: .whitespaces).isEmpty {
                        Text("Enter a name to save the card.").frame(maxWidth: .infinity)
                    } else if form.selectedCode.value.trimmingCharacters(in: .whitespaces).isEmpty {
                        Text("Enter the card's code to save it.").frame(maxWidth: .infinity)
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
            // Edits are saved as they are made; Done just goes back to the card.
            if form.isLibraryCard {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", action: component.onCloseClicked)
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
                .focused($isCodeFocused)
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

    private var saveButton: some View {
        Button("Save Card", action: component.onSaveClicked)
            .buttonStyle(FormActionButtonStyle())
            .disabled(!form.isValid || isBusy)
    }
}

/// The full-width prominent button that ends a form.
private struct FormActionButtonStyle: ButtonStyle {
    @Environment(\.isEnabled) private var isEnabled

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.headline)
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity, minHeight: 50)
            .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
            .opacity(isEnabled ? (configuration.isPressed ? 0.75 : 1) : 0.4)
            .contentShape(Rectangle())
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
                Text("Not in Apple Wallet")
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
