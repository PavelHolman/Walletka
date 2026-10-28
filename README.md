# Walletka

Turn a photo of any loyalty card, ticket or membership card into an **Apple Wallet** / **Google Wallet** card.
Share a photo to Walletka (or paste / pick / drop one), and it finds the QR code or barcode, suggests a name
(from the text on the card), a background colour and a banner cut out of the photo, and adds the card to the wallet.

Generated from the [Kotlin Multiplatform wizard](https://kmp.jetbrains.com) (Android + iOS with SwiftUI).

## Stack

| | |
|---|---|
| Kotlin | 2.4.21, **Swift Export** (no Objective-C framework) |
| Architecture | Decompose 3.5 + Essenty (lifecycle, StateKeeper, InstanceKeeper, back handler) |
| Errors / effects | Arrow 2.3 (`Either`, `raise` DSL, `parZip`) |
| kotlinx | coroutines, serialization, io |
| Android UI | Compose Multiplatform (`sharedUI`) + Material 3 |
| iOS UI | SwiftUI (+ a UIKit share extension) |
| DI | none needed: one composition root per platform |

## Modules

```
sharedLogic/   KMP (Android + iOS): everything except UI
  commonMain/
    domain/      CodeFormat, ScannedCode, CardColor, … (plain types, Swift-exported)
    platform/    native abstractions: ImageStore, BarcodeDetector, TextRecognizer, ImageProcessor, WalletService
    data/        ImageAnalyzer (parallel detection), name/colour/banner heuristics, pass JSON builders
    component/   Decompose components: Root (child stack) → Home, CardReview
  androidMain/   ML Kit barcode + text recognition, ImageDecoder, Google Wallet PayClient, createRootComponent()
  iosMain/       Vision + Core Image, CoreGraphics, PassKit, URLSession, WalletkaApp (Swift entry point)
sharedUI/      Compose Multiplatform screens (used by Android)
androidApp/    Activity: Decompose context, share/view intents, Google Wallet result bridge
iosApp/        SwiftUI app + ShareExtension target
```

The platform interfaces are `internal`: each platform has a composition root
(`createRootComponent(...)` on Android, `WalletkaApp()` on iOS) that wires its implementations into the shared
`DefaultRootComponent`. UIs only see the component interfaces and their immutable models.

### Flow

1. An image arrives as a platform source string (Android `content://`/`file://` URI, iOS file path) →
   `RootComponent.onImageReceived` pushes a `CardReview` child.
2. `CardReview` (state retained in an `InstanceKeeper` instance, edit form saved through `StateKeeper`)
   copies the image into app storage, then `ImageAnalyzer` runs barcode detection, OCR and pixel sampling in
   parallel (`parZip`) and builds a suggestion: codes, names, palette, banner crop that avoids the code.
3. The user edits name / code / colour / banner and taps *Add to Wallet* → `WalletService`.

### Swift Export notes

- Component state is exposed as `StateFlow<Model>`: Swift Export bridges it typed (`KotlinTypedStateFlow<T>`,
  consumed via `asAsyncSequence()` in `ObservedFlow.swift`), whereas Decompose's `Value<T>` arrives type-erased.
  `RootComponent` therefore offers both `stack: Value<ChildStack>` (Compose) and `children: StateFlow<List<Child>>`
  (SwiftUI `NavigationStack`).
- Sealed models become Swift protocols with `sealedType()` enums for exhaustive `switch`.
- Exported models deliberately avoid `@Serializable` (kotlinx.serialization's Swift export doesn't compile);
  saved state uses an internal `SavedCardForm` snapshot instead.
- Images cross the boundary as file paths, not `ByteArray`s.

## Wallet issuer setup

Neither wallet accepts passes from an unregistered issuer. Without configuration the app works up to the
*Add to Wallet* step and then explains what is missing.

### Google Wallet (Android)

1. Create an issuer in the [Google Pay & Wallet Console](https://pay.google.com/business/console) and allow-list
   this app (package `cz.poqet.walletka` + signing certificate SHA-1) for the Android API.
2. Add to `local.properties` (not committed):
   ```properties
   walletka.googleIssuerId=3388000000000000000
   walletka.googleIssuerEmail=issuer@example.com
   ```

Walletka saves a *generic pass* through `PayClient.savePasses` (unsigned JSON, allowed for allow-listed apps).
Google Wallet only accepts images by public URL, so the banner is shown in the app but not sent to Google Wallet.

### Apple Wallet (iOS)

Passes must be signed with a Pass Type ID certificate. The private key must not ship in the app, so signing is
delegated to a small service. Set in `iosApp/Configuration/Config.xcconfig`:

```
TEAM_ID=ABCDE12345
WALLETKA_PASS_TYPE_ID=pass.cz.poqet.walletka
WALLETKA_SIGNING_URL=https:/$()/passes.example.com/sign
```

Signing service contract (see `ApplePassBundle.kt`): `POST` JSON `{"files": {"pass.json": "<base64>", "icon.png": …,
"strip@3x.png": …}}`; the service adds `manifest.json` + `signature`, zips it and returns
`application/vnd.apple.pkpass`. The app then shows the system *Add to Apple Wallet* sheet.

`TEAM_ID` is also needed for the App Group (`group.<bundle id>`) that lets the share extension hand images over.

## Building

```bash
./gradlew :androidApp:installDebug
```

```bash
open iosApp/iosApp.xcodeproj
```

The Xcode build phase runs `./gradlew :sharedLogic:embedSwiftExportForXcode`.

```bash
./gradlew :sharedLogic:allTests
```

## Known limitations

- **iOS Simulator:** Vision's barcode models need a GPU/Neural Engine context the Simulator lacks, so only QR
  codes are found there (via the Core Image fallback); the iOS 26.0 Simulator runtime returns nothing at all.
  On devices all symbologies work.
- Apple Wallet renders only QR, Aztec, PDF417 and Code 128; other codes are re-encoded with the same content
  (2D → QR, linear → Code 128) and the UI says so. Google Wallet supports most formats natively.
- iOS share extension → app hand-off opens the app via the responder chain; if iOS blocks that, the image is
  imported the next time Walletka becomes active.
