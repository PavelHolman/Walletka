import SharedLogic
import SwiftUI

/// A library card shown big and bright, to be scanned instead of opening it in Wallet. Adding it to Wallet is an
/// option here; editing and deleting are secondary.
struct CardPreviewScreen: View {
    private let component: any CardPreviewComponent
    @State private var model: ObservedFlow<any PreviewModel>

    init(component: any CardPreviewComponent) {
        self.component = component
        _model = State(initialValue: ObservedFlow(component.model))
    }

    var body: some View {
        Group {
            switch model.value.sealedType() {
            case .loading:
                Color.clear
            case .showing(let showing):
                PreviewContent(
                    form: showing.value.form,
                    isInWallet: showing.value.isInWallet,
                    walletAction: showing.value.walletAction,
                    component: component
                )
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .fullScreenBrightness()
    }
}

private struct PreviewContent: View {
    let form: CardForm
    let isInWallet: Bool
    let walletAction: WalletAction
    let component: any CardPreviewComponent

    @State private var isConfirmingDelete = false
    @State private var isConfirmingRemoval = false

    var body: some View {
        // Centred where it's easy to hold up to a scanner; scrolls when details or large text don't fit.
        GeometryReader { geometry in
            ScrollView {
                VStack(spacing: 20) {
                    CardPreview(form: form, size: .large)
                    if !form.details.isEmpty {
                        DetailsList(details: form.details)
                    }
                }
                .padding(.horizontal, 20)
                .padding(.vertical, 12)
                .frame(maxWidth: 480)
                .frame(maxWidth: .infinity, minHeight: geometry.size.height)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .safeAreaInset(edge: .bottom) { walletBar }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("Edit", action: component.onEditClicked)
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("Delete Card", systemImage: "trash", role: .destructive) { isConfirmingDelete = true }
                } label: {
                    Label("More", systemImage: "ellipsis")
                }
                // Attached here so on iPad the dialog points at the menu.
                .confirmationDialog("Delete “\(form.name)”?", isPresented: $isConfirmingDelete, titleVisibility: .visible) {
                    Button("Delete Card", role: .destructive, action: component.onDeleteClicked)
                } message: {
                    Text(isInWallet
                         ? String(localized: "The card is removed from Walletka and from Apple Wallet.")
                         : String(localized: "The card is removed from Walletka."))
                }
            }
        }
        .alert(String(localized: "Couldn't Add the Card"), isPresented: isShowingFailure) {
            Button("OK", action: component.onWalletMessageDismissed)
        } message: {
            if case .failed(let failed) = walletAction.sealedType() {
                Text(verbatim: failed.value.failure.message(detail: failed.value.detail))
            }
        }
        .alert(String(localized: "Couldn't Remove the Card"), isPresented: isShowingRemoveUnsupported) {
            Button("OK", action: component.onWalletMessageDismissed)
        } message: {
            Text("Remove it in the Wallet app instead.")
        }
        // The pass made it into Wallet.
        .sensoryFeedback(trigger: isInWallet) { wasInWallet, inWallet in
            inWallet && !wasInWallet ? .success : nil
        }
    }

    /// Add to Wallet, or the card's place in Wallet with the way out of it.
    private var walletBar: some View {
        Group {
            if isInWallet {
                HStack(spacing: 8) {
                    Image(systemName: "checkmark.circle.fill")
                        .foregroundStyle(.green)
                        .accessibilityHidden(true)
                    Text("In Apple Wallet")
                    Spacer()
                    if isBusy {
                        ProgressView()
                    } else {
                        Button("Remove", role: .destructive) { isConfirmingRemoval = true }
                            .fontWeight(.medium)
                            .accessibilityLabel(Text("Remove from Apple Wallet"))
                            .confirmationDialog(
                                "Remove “\(form.name)” from Apple Wallet?",
                                isPresented: $isConfirmingRemoval,
                                titleVisibility: .visible
                            ) {
                                Button("Remove from Wallet", role: .destructive, action: component.onRemoveFromWalletClicked)
                            } message: {
                                Text("The card stays in Walletka, and you can add it again any time.")
                            }
                    }
                }
                .padding(.horizontal, 16)
                .frame(minHeight: 50)
                .background(.fill.tertiary, in: RoundedRectangle(cornerRadius: 12))
            } else {
                AddToWalletButton(action: component.onAddToWalletClicked)
                    .frame(height: 50)
                    .accessibilityLabel(isBusy ? String(localized: "Adding to Apple Wallet…") : String(localized: "Add to Apple Wallet"))
                    .opacity(isBusy ? 0 : 1)
                    .overlay {
                        if isBusy {
                            RoundedRectangle(cornerRadius: 12).fill(.black)
                                .overlay { ProgressView().tint(.white) }
                        }
                    }
                    .disabled(isBusy)
            }
        }
        .frame(maxWidth: 480)
        .padding(.horizontal, 20)
        .padding(.vertical, 12)
        .frame(maxWidth: .infinity)
        .background(.bar)
        .animation(.default, value: isInWallet)
    }

    private var isBusy: Bool {
        switch walletAction.sealedType() {
        case .adding, .removing: true
        default: false
        }
    }

    private var isShowingFailure: Binding<Bool> {
        Binding(
            get: {
                // Cancelling the system sheet is not an error.
                if case .failed(let failed) = walletAction.sealedType() { return failed.value.failure != .CANCELLED }
                return false
            },
            set: { if !$0 { component.onWalletMessageDismissed() } }
        )
    }

    private var isShowingRemoveUnsupported: Binding<Bool> {
        Binding(
            get: { if case .removeUnsupported = walletAction.sealedType() { return true } else { return false } },
            set: { if !$0 { component.onWalletMessageDismissed() } }
        )
    }
}

/// The card's details (as on the back of the pass), for reading out at the till.
private struct DetailsList: View {
    let details: CardDetails

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                if index > 0 { Divider() }
                VStack(alignment: .leading, spacing: 2) {
                    Text(row.label)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    Text(verbatim: row.value)
                        .textSelection(.enabled)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.vertical, 10)
                .accessibilityElement(children: .combine)
            }
        }
        .padding(.horizontal, 16)
        .background(.fill.quaternary, in: RoundedRectangle(cornerRadius: 12))
    }

    private var rows: [(label: String, value: String)] {
        var rows: [(String, String)] = []
        if !details.memberNumber.isEmpty { rows.append((String(localized: "Card Number"), details.memberNumber)) }
        if !details.memberName.isEmpty { rows.append((String(localized: "Member Name"), details.memberName)) }
        if let expiry = Self.isoDate.date(from: details.expiryDate) {
            rows.append((String(localized: "Expires On"), expiry.formatted(date: .long, time: .omitted)))
        }
        if !details.notes.isEmpty { rows.append((String(localized: "Notes"), details.notes)) }
        return rows
    }

    /// The calendar day as `CardDetails` stores it (`2027-05-31`), in the user's time zone.
    private static let isoDate: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()
}
