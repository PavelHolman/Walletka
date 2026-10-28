import SharedLogic
import SwiftUI

/// Renders the Decompose child stack with a native `NavigationStack`.
struct RootView: View {
    private let root: any RootComponent
    @State private var children: ObservedFlow<[any RootChild]>

    init(root: any RootComponent) {
        self.root = root
        _children = State(initialValue: ObservedFlow(root.children))
    }

    var body: some View {
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
