import Foundation
import KotlinCoroutineSupport
import Observation

/// Mirrors a Kotlin `StateFlow` into SwiftUI's observation system for as long as the instance lives.
@MainActor
@Observable
final class ObservedFlow<Value> {
    private(set) var value: Value

    @ObservationIgnored private var task: Task<Void, Never>?

    init(_ flow: any KotlinTypedStateFlow<Value>) {
        value = flow.value
        task = Task { [weak self] in
            do {
                for try await value in flow.asAsyncSequence() {
                    self?.value = value
                }
            } catch {
                // Cancelled together with the observer.
            }
        }
    }

    deinit {
        task?.cancel()
    }
}
