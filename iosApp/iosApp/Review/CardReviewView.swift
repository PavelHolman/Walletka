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
                LoadingView(stage: loading.value.stage)
            case .failed(let failed):
                FailedView(
                    failure: failed.value.failure,
                    onRetry: component.onRetryClicked,
                    onClose: component.onCloseClicked
                )
            case .editing(let editing):
                CardFormView(form: editing.value.form, submission: editing.value.submission, component: component)
            }
        }
        .navigationTitle("New Card")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct LoadingView: View {
    let stage: LoadingStage

    var body: some View {
        VStack(spacing: 16) {
            ProgressView().controlSize(.large)
            Text(stage == .IMPORTING ? "Reading the image…" : "Looking for codes…")
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private struct FailedView: View {
    let failure: AnalysisFailure
    let onRetry: () -> Void
    let onClose: () -> Void

    var body: some View {
        ContentUnavailableView {
            Label(title, systemImage: failure == .NO_CODE_FOUND ? "qrcode" : "exclamationmark.triangle")
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
        case .NO_CODE_FOUND: "Try a sharper photo with the whole QR code or barcode visible."
        case .DETECTION_FAILED: "Something went wrong while scanning the photo."
        }
    }
}

private struct CardFormView: View {
    let form: CardForm
    let submission: any Submission
    let component: any CardReviewComponent

    @State private var isEditingBanner = false

    private var isBusy: Bool {
        if case .inProgress = submission.sealedType() { return true }
        return false
    }

    var body: some View {
        Form {
            Section {
                CardPreview(form: form)
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            }

            Section("Name") {
                TextField("e.g. Coffee Club", text: Binding(get: { form.name }, set: { component.onNameChanged(name: $0) }))
                    .textInputAutocapitalization(.words)
                if form.nameSuggestions.count > 1 || form.nameSuggestions.first.map({ $0 != form.name }) == true {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(form.nameSuggestions, id: \.self) { name in
                                Button(name) { component.onNameChanged(name: name) }
                                    .buttonStyle(.bordered)
                                    .buttonBorderShape(.capsule)
                                    .controlSize(.small)
                            }
                        }
                    }
                }
            }

            Section {
                if form.codes.count > 1 {
                    Picker("Code", selection: Binding(
                        get: { Int(form.selectedCodeIndex) },
                        set: { component.onCodeSelected(index: Int32($0)) }
                    )) {
                        ForEach(Array(form.codes.enumerated()), id: \.offset) { index, code in
                            Text("\(code.format.displayName) · \(code.value)").lineLimit(1).tag(index)
                        }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                } else {
                    LabeledContent(form.selectedCode.format.displayName) {
                        Text(form.selectedCode.value).font(.body.monospaced()).lineLimit(2)
                    }
                }
            } header: {
                Text(form.codes.count > 1 ? "Codes Found (\(form.codes.count))" : "Code")
            } footer: {
                if form.walletCodeFormat != form.selectedCode.format {
                    Text("Apple Wallet doesn't support \(form.selectedCode.format.displayName); it will be shown as \(form.walletCodeFormat.displayName) with the same content.")
                }
            }

            Section("Colour") {
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
                        .accessibilityLabel("Colour \(index + 1)")
                        .accessibilityAddTraits(selected ? .isSelected : [])
                    }
                }
                .padding(.vertical, 4)
            }

            if form.banner != nil {
                Section {
                    Toggle("Banner from Photo", isOn: Binding(get: { form.isBannerEnabled }, set: { component.onBannerToggled(enabled: $0) }))
                    Button("Adjust Banner…") { isEditingBanner = true }
                }
            }

            Section {
                AddToWalletButton(action: component.onAddToWalletClicked)
                    .frame(height: 52)
                    .opacity(form.isValid && !isBusy ? 1 : 0.4)
                    .disabled(!form.isValid || isBusy)
                    .overlay { if isBusy { ProgressView() } }
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            }
        }
        .disabled(isBusy)
        .sheet(isPresented: $isEditingBanner) {
            BannerEditorView(form: form) { region in
                component.onBannerRegionChanged(region: region)
                if !form.isBannerEnabled { component.onBannerToggled(enabled: true) }
            }
        }
        .alert("Card Added", isPresented: .constant(isAdded)) {
            Button("Done", action: component.onCloseClicked)
        } message: {
            Text("“\(form.name)” is now in Apple Wallet.")
        }
        .alert("Couldn't Add the Card", isPresented: .constant(failure != nil)) {
            Button("OK", action: component.onSubmissionErrorDismissed)
        } message: {
            Text(failure.map(Self.message) ?? "")
        }
    }

    private var isAdded: Bool {
        if case .added = submission.sealedType() { return true }
        return false
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
        }
        return [text, failure.1].compactMap { $0 }.joined(separator: "\n\n")
    }
}

private struct CardPreview: View {
    let form: CardForm

    var body: some View {
        let background = form.selectedColor.swiftUIColor
        let content = form.selectedColor.contentColor.swiftUIColor
        VStack(spacing: 0) {
            HStack {
                Text(form.name.isEmpty ? "Card Name" : form.name)
                    .font(.headline)
                    .lineLimit(1)
                Spacer()
            }
            .foregroundStyle(content)
            .padding(16)

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
                VStack(spacing: 6) {
                    Image(uiImage: code)
                        .interpolation(.none)
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: form.walletCodeFormat.isTwoDimensional ? 130 : 70)
                    if !form.walletCodeFormat.isTwoDimensional {
                        Text(form.selectedCode.value).font(.caption.monospaced()).foregroundStyle(.black)
                    }
                }
                .padding(12)
                .background(.white, in: RoundedRectangle(cornerRadius: 10))
                .padding(20)
            }
        }
        .frame(maxWidth: .infinity)
        .background(background, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .shadow(color: .black.opacity(0.15), radius: 10, y: 4)
        .padding(.vertical, 8)
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
