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

    /// The library is the sidebar; the rest of the stack (a card and its editor, or a new card) is the detail, or a
    /// welcome screen when nothing is open.
    private var splitView: some View {
        NavigationSplitView {
            if case .home(let home) = children.value.first?.sealedType() {
                SplitSidebar(home: home.value.component, detail: children.value.dropFirst().first)
            }
        } detail: {
            NavigationStack(path: detailPath) {
                if let detail = children.value.dropFirst().first {
                    ChildView(child: detail)
                        .environment(\.isSplitViewDetail, true)
                        .navigationDestination(for: Route.self) { route in
                            ChildView(child: route.child)
                        }
                } else if case .home(let home) = children.value.first?.sealedType() {
                    HomeView(component: home.value.component, presentation: .welcome)
                }
            }
        }
        .navigationSplitViewStyle(.balanced)
    }

    /// What is pushed in the detail column (a card's editor above the card); popping it pops in Kotlin.
    private var detailPath: Binding<[Route]> {
        Binding(
            get: { children.value.dropFirst(2).map(Route.init) },
            set: { newPath in
                if newPath.count < children.value.count - 2 {
                    root.onBackClicked(toIndex: Int32(newPath.count + 1))
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

/// The iPad sidebar: the library, highlighting the card open next to it and protecting a new card that isn't saved
/// yet from being replaced by tapping another one. One `HomeView` throughout, so scrolling and search survive.
private struct SplitSidebar: View {
    let home: any HomeComponent
    /// The child at the root of the detail column: a card's preview, or a new card's review.
    let detail: (any RootChild)?

    /// The new card's model, which changes after the child stack does (loading, saving).
    @State private var model: ObservedFlow<any ReviewModel>?

    var body: some View {
        HomeView(component: home, presentation: .sidebar(selectedCardId: selectedCardId, hasUnsavedNewCard: hasUnsavedNewCard))
            .onChange(of: review.map { ObjectIdentifier($0) }, initial: true) {
                model = review.map { ObservedFlow($0.model) }
            }
    }

    private var review: (any CardReviewComponent)? {
        if case .review(let review) = detail?.sealedType() { return review.value.component }
        return nil
    }

    private var selectedCardId: String? {
        if case .preview(let preview) = detail?.sealedType() { return preview.value.component.cardId }
        if case .editing(let editing) = model?.value.sealedType() { return editing.value.form.cardId }
        return nil
    }

    /// A scanned or typed-in card still being edited (not added yet).
    private var hasUnsavedNewCard: Bool {
        switch model?.value.sealedType() {
        case .editing(let editing):
            if case .added = editing.value.submission.sealedType() { return false }
            return !editing.value.form.isLibraryCard
        case .loading(let loading):
            return !loading.value.isLibraryCard
        default:
            return false
        }
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
        case .preview(let preview):
            CardPreviewScreen(component: preview.value.component)
                .id(ObjectIdentifier(preview.value))
        }
    }
}

extension EnvironmentValues {
    /// The view is the root of the split view's detail column, where there is no back button to leave it.
    @Entry var isSplitViewDetail = false
}
