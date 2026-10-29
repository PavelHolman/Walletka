import SharedLogic
import SwiftUI

/// Renders the Decompose child stack: a `NavigationStack` on iPhone, a split view (library next to the open card)
/// where there is room for both, i.e. on iPad.
struct RootView: View {
    private let root: any RootComponent
    @State private var children: ObservedFlow<[any RootChild]>
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass

    init(root: any RootComponent) {
        self.root = root
        _children = State(initialValue: ObservedFlow(root.children))
    }

    var body: some View {
        if horizontalSizeClass == .regular {
            splitView
        } else {
            stackView
        }
    }

    // MARK: - iPhone

    private var stackView: some View {
        NavigationStack(path: path) {
            if let first = children.value.first {
                ChildView(child: first)
                    .navigationDestination(for: Route.self) { route in
                        ChildView(child: route.child)
                    }
            }
        }
    }

    /// Everything above the bottom child is pushed. Popping natively (back button, swipe) pops in Kotlin.
    private var path: Binding<[Route]> {
        Binding(
            get: { children.value.dropFirst().map(Route.init) },
            set: { newPath in
                if newPath.count < children.value.count - 1 {
                    root.onBackClicked(toIndex: Int32(newPath.count))
                }
            }
        )
    }

    // MARK: - iPad

    /// The library is the sidebar; the card on top of the stack (or a welcome screen) is the detail.
    private var splitView: some View {
        NavigationSplitView {
            if case .home(let home) = children.value.first?.sealedType() {
                HomeView(component: home.value.component, presentation: .sidebar(selectedCardId: openCardId))
            }
        } detail: {
            NavigationStack {
                if let open = children.value.dropFirst().last {
                    ChildView(child: open)
                        .environment(\.isSplitViewDetail, true)
                } else if case .home(let home) = children.value.first?.sealedType() {
                    HomeView(component: home.value.component, presentation: .welcome)
                }
            }
        }
        .navigationSplitViewStyle(.balanced)
    }

    /// The library card open in the detail column, highlighted in the sidebar.
    private var openCardId: String? {
        guard case .review(let review) = children.value.dropFirst().last?.sealedType(),
              case .editing(let editing) = review.value.component.model.value.sealedType() else { return nil }
        return editing.value.form.cardId
    }

    /// Kotlin objects are bridged as cached `NSObject` wrappers, so object identity is stable.
    private struct Route: Hashable {
        let child: any RootChild

        static func == (lhs: Route, rhs: Route) -> Bool { lhs.child === rhs.child }

        func hash(into hasher: inout Hasher) { hasher.combine(ObjectIdentifier(child)) }
    }
}

private struct ChildView: View {
    let child: any RootChild

    var body: some View {
        switch child.sealedType() {
        case .home(let home):
            HomeView(component: home.value.component)
        case .review(let review):
            CardReviewView(component: review.value.component)
                .id(ObjectIdentifier(review.value))
        }
    }
}

extension EnvironmentValues {
    /// The view is the root of the split view's detail column, where there is no back button to leave it.
    @Entry var isSplitViewDetail = false
}
