import SharedLogic
import SwiftUI

/// Optional pass details (card number, member name, expiry, notes), collapsed under "More Details" unless the card
/// already has some. Apple Wallet shows them on the back of the pass, number and name also on the front.
struct CardDetailsSection: View {
    let form: CardForm
    let component: any CardReviewComponent

    @State private var isExpanded: Bool

    init(form: CardForm, component: any CardReviewComponent) {
        self.form = form
        self.component = component
        _isExpanded = State(initialValue: !form.details.isEmpty)
    }

    private var details: CardDetails { form.details }

    var body: some View {
        Section {
            DisclosureGroup("More Details", isExpanded: $isExpanded) {
                HStack {
                    TextField("Card Number", text: binding(\.memberNumber) { $0.with(memberNumber: $1) })
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                    if details.memberNumber.isEmpty && !form.selectedCode.value.isEmpty {
                        Button("Use Code") {
                            update(details.with(memberNumber: form.selectedCode.value.trimmingCharacters(in: .whitespacesAndNewlines)))
                        }
                        .font(.footnote)
                        .buttonStyle(.borderless)
                    }
                }
                TextField("Member Name", text: binding(\.memberName) { $0.with(memberName: $1) })
                    .textContentType(.name)
                    .textInputAutocapitalization(.words)
                Toggle("Expiry Date", isOn: Binding(
                    get: { expiry != nil },
                    set: { update(details.with(expiryDate: $0 ? Self.isoDate.string(from: Self.defaultExpiry) : "")) }
                ))
                if let expiry {
                    DatePicker("Expires", selection: Binding(
                        get: { expiry },
                        set: { update(details.with(expiryDate: Self.isoDate.string(from: $0))) }
                    ), displayedComponents: .date)
                }
                TextField("Notes", text: binding(\.notes) { $0.with(notes: $1) }, axis: .vertical)
                    .lineLimit(2...6)
            }
        } footer: {
            Text("Shown on the pass: the card number and member name on the front, everything on the back.")
        }
    }

    private var expiry: Date? { Self.isoDate.date(from: details.expiryDate) }

    private func update(_ details: CardDetails) {
        component.onDetailsChanged(details: details)
    }

    private func binding(_ keyPath: KeyPath<CardDetails, String>, _ set: @escaping (CardDetails, String) -> CardDetails) -> Binding<String> {
        Binding(get: { details[keyPath: keyPath] }, set: { update(set(details, $0)) })
    }

    /// The calendar day as `CardDetails` stores it (`2027-05-31`), in the user's time zone.
    private static let isoDate: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.calendar = Calendar(identifier: .gregorian)
        formatter.dateFormat = "yyyy-MM-dd"
        return formatter
    }()

    /// A year from today, a typical membership term.
    private static var defaultExpiry: Date { Calendar.current.date(byAdding: .year, value: 1, to: .now) ?? .now }
}

private extension CardDetails {
    /// A copy with the given fields replaced (Swift Export doesn't bridge data class `copy`).
    func with(memberNumber: String? = nil, memberName: String? = nil, expiryDate: String? = nil, notes: String? = nil) -> CardDetails {
        CardDetails(
            memberNumber: memberNumber ?? self.memberNumber,
            memberName: memberName ?? self.memberName,
            expiryDate: expiryDate ?? self.expiryDate,
            notes: notes ?? self.notes
        )
    }
}
