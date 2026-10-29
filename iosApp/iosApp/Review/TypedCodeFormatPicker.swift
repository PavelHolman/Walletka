import SharedLogic
import SwiftUI

/// Explains why the code is typed in and lets the user pick its symbology (`CardForm.isCodeTyped`).
struct TypedCodeFormatPicker: View {
    let form: CardForm
    let onSelect: (CodeFormat) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(form.hasPhoto
                 ? "No code found in the photo. Type the number printed on the card and pick the code type."
                 : "Type the number printed on the card and pick the code type.")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Picker("Code Type", selection: Binding(get: { form.selectedCode.format }, set: onSelect)) {
                ForEach(form.typedCodeFormats, id: \.self) { format in
                    Text(format.displayName).tag(format)
                }
            }
        }
    }
}
