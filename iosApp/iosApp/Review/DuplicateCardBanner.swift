import SharedLogic
import SwiftUI

/// Shown above a new card whose code is already in the library; the user can open that card or add this one anyway.
struct DuplicateCardBanner: View {
    let duplicate: DuplicateCard
    let onOpen: () -> Void

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    var body: some View {
        // Side by side normally; stacked at accessibility sizes so neither the text nor the button is squeezed.
        let layout = dynamicTypeSize.isAccessibilitySize
            ? AnyLayout(VStackLayout(alignment: .leading, spacing: 8))
            : AnyLayout(HStackLayout(spacing: 12))
        layout {
            HStack(alignment: .firstTextBaseline, spacing: 12) {
                Image(systemName: "square.on.square")
                    .foregroundStyle(.orange)
                    .accessibilityHidden(true)
                Text("You already have “\(duplicate.name)” with this code.")
                    .font(.subheadline)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            Button("Open Existing", action: onOpen)
                .font(.subheadline.weight(.semibold))
                .buttonStyle(.borderless)
                .accessibilityHint(Text("Opens “\(duplicate.name)” instead of adding this card"))
        }
        .accessibilityElement(children: .contain)
        .padding(12)
        .background(.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.top, 4)
        .background(.bar)
    }
}
