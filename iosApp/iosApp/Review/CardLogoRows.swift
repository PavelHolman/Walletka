import SharedLogic
import SwiftUI

/// Rows that turn the logo cut out of the photo on and off and open its crop editor.
struct CardLogoRows: View {
    let form: CardForm
    let component: any CardReviewComponent
    /// Opens the crop editor; the sheet lives on the form (a sheet on rows inside a `Form` section doesn't present).
    let onAdjust: () -> Void

    var body: some View {
        Group {
            Toggle("Logo", isOn: Binding(get: { form.logo.isEnabled }, set: { component.onLogoToggled(enabled: $0) }))
            if form.logo.isEnabled {
                Button(action: onAdjust) {
                    HStack(spacing: 12) {
                        Group {
                            if let path = form.logo.image?.path {
                                PassLogoView(path: path, height: 24)
                            } else {
                                ProgressView()
                            }
                        }
                        .frame(width: 92, height: 30)
                        .background(form.selectedColor.swiftUIColor, in: RoundedRectangle(cornerRadius: 6))
                        .accessibilityHidden(true)
                        Text("Adjust Logo…")
                        Spacer()
                        Image(systemName: "chevron.right").font(.footnote.bold()).foregroundStyle(.tertiary)
                            .accessibilityHidden(true)
                    }
                }
                .foregroundStyle(.primary)
                .accessibilityHint(Text("Choose the part of the photo shown as the logo"))
            }
        }
    }
}
