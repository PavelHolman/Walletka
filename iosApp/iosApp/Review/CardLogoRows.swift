import SharedLogic
import SwiftUI

/// Rows that turn the logo cut out of the photo on and off and open its crop editor.
struct CardLogoRows: View {
    let form: CardForm
    let component: any CardReviewComponent

    @State private var isEditing = false

    var body: some View {
        Group {
            Toggle("Logo", isOn: Binding(get: { form.logo.isEnabled }, set: { component.onLogoToggled(enabled: $0) }))
            if form.logo.isEnabled {
                Button {
                    isEditing = true
                } label: {
                    HStack(spacing: 12) {
                        Group {
                            if let path = form.logo.image?.path {
                                PassLogoView(path: path, height: 26)
                            } else {
                                ProgressView().frame(width: 26 * 160 / 50, height: 26)
                            }
                        }
                        .padding(4)
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
        .sheet(isPresented: $isEditing) {
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
    }
}
