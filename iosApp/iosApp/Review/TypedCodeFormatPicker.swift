import SharedLogic
import SwiftUI

/// Explains why the code is typed in and lets the user pick its symbology (`CardForm.isCodeTyped`).
struct TypedCodeFormatPicker: View {
    let form: CardForm
    let onSelect: (CodeFormat) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(form.hasPhoto
                 ? String(localized: "No code found in the photo. Type the number printed on the card and pick the code type.")
                 : String(localized: "Type the number printed on the card and pick the code type."))
                .font(.footnote)
                .foregroundStyle(.secondary)
            Picker("Code Type", selection: Binding(get: { form.selectedCode.format }, set: onSelect)) {
                ForEach(0..<Int(form.typedCodeFormatCount), id: \.self) { index in
                    let format = form.typedCodeFormat(index: Int32(index))
                    Text(verbatim: format.localizedName).tag(format)
                }
            }
        }
    }
}
