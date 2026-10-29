import MachO

/// Whether the app was signed with its iCloud entitlement; CloudKit traps without one.
enum CloudKitEntitlement {
    static var isPresent: Bool {
        #if targetEnvironment(simulator)
        // Simulator builds carry their entitlements in a section of the executable; unsigned builds have none.
        guard let header = _dyld_get_image_header(0) else { return false }
        var size: UInt = 0
        let section = header.withMemoryRebound(to: mach_header_64.self, capacity: 1) {
            getsectiondata($0, "__TEXT", "__entitlements", &size)
        }
        guard let section else { return false }
        let plist = String(decoding: UnsafeBufferPointer(start: section, count: Int(size)), as: UTF8.self)
        return plist.contains("com.apple.developer.icloud-services")
        #else
        // Device builds are always signed, with the entitlements of iosApp.entitlements.
        return true
        #endif
    }
}
