import SharedLogic
import SwiftUI

/// Shown above a new card whose code is already in the library; the user can open that card or add this one anyway.
struct DuplicateCardBanner: View {
    let duplicate: DuplicateCard
    let onOpen: () -> Void

    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: "square.on.square")
                .foregroundStyle(.orange)
            Text("You already have “\(duplicate.name)” with this code.")
                .font(.subheadline)
                .frame(maxWidth: .infinity, alignment: .leading)
            Button("Open Existing", action: onOpen)
                .font(.subheadline.weight(.semibold))
                .buttonStyle(.borderless)
        }
        .padding(12)
        .background(.orange.opacity(0.12), in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.top, 4)
        .background(.bar)
    }
}
