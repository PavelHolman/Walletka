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
| Pass service | Kotlin/JS on Cloudflare Workers, GraphQL (graphql-js), Workers KV for banners |

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
serviceApi/    GraphQL schema + operations shared by the app and the service (the API contract)
passService/   Cloudflare Worker (Kotlin/JS): Apple pass signing + banner hosting
  commonMain/    pass packaging, CMS/PKCS#7 signing (own DER encoder), zip writer, validation
  jsMain/        Worker fetch handler, GraphQL resolvers, WebCrypto, KV
  jvmTest/       signs with test certificates and verifies with `openssl cms -verify`
  worker/        wrangler.toml + entry module wrapping the compiled Kotlin
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

## Pass service

`passService` is a Cloudflare Worker written in Kotlin (compiled to JS). It exposes one GraphQL endpoint
(`POST /graphql`, schema in [`WalletkaApi.kt`](serviceApi/src/commonMain/kotlin/cz/poqet/walletka/api/WalletkaApi.kt)):

- `signApplePass(files)` validates the pass files (allow-listed names, PNG check, size limits), stamps
  `passTypeIdentifier`/`teamIdentifier` from its own config into `pass.json`, writes `manifest.json`, signs it
  (detached CMS, SHA-256, signer + WWDR certificates) and returns the zipped `.pkpass`.
- `uploadBanner(pngBase64)` stores a PNG in Workers KV under a content hash and returns its public URL,
  `GET /banners/<id>.png` (immutable caching). Android uses it as the Google Wallet `heroImage`.

Requests need `Authorization: Bearer <API_KEY>`; until the `API_KEY` secret is set, every GraphQL request is refused. Signing uses WebCrypto (native),
so a request stays within the Workers free plan's CPU budget; KV is part of the Free plan with hard limits (1 GB, 1,000 writes a day): past them uploads fail instead of
being billed, so the service can't incur charges and needs no payment method.

The app's operations come from the same `:serviceApi` module the Worker serves, and a JS test validates them
against the schema with graphql-js, so client and server can't drift.

### Run locally

```bash
cd passService/worker && npm install && ./scripts/dev-vars.sh && npm run dev
```

`dev-vars.sh` writes `.dev.vars` with throwaway test certificates (from `passService/src/jvmTest/resources/test-certs`)
and `API_KEY=dev-key`; KV is emulated locally. PassKit rejects passes signed with those certificates, so only a
real Pass Type ID certificate gets a pass into Apple Wallet.

### Deploy to Cloudflare (free plan)

1. In the [Apple Developer portal](https://developer.apple.com/account/resources/identifiers/list/passTypeId),
   register a Pass Type ID (`pass.cz.poqet.walletka`) and create its certificate from a CSR made with OpenSSL, so
   the private key never needs exporting from Keychain:
   ```bash
   mkdir -p passService/worker/.secrets && cd passService/worker/.secrets && openssl req -new -newkey rsa:2048 -noenc -keyout pass-key.pem -out pass.certSigningRequest -subj "/CN=Walletka Pass Type ID"
   ```
2. Upload `pass.certSigningRequest`, download `pass.cer`, and convert it and Apple's WWDR G4 intermediate to PEM
   (in `.secrets/`, which git ignores):
   ```bash
   openssl x509 -inform DER -in ~/Downloads/pass.cer -out pass-cert.pem
   ```
   ```bash
   curl -s https://www.apple.com/certificateauthority/AppleWWDRCAG4.cer | openssl x509 -inform DER -out wwdr.pem
   ```
   ```bash
   openssl rand -hex 24 > api-key.txt
   ```
3. Set `PASS_TYPE_IDENTIFIER` and `TEAM_IDENTIFIER` in `wrangler.toml` to the certificate's UID and OU
   (`openssl x509 -in pass-cert.pem -noout -subject`), or PassKit rejects the pass.
4. Log in and deploy; the script creates the KV namespace, deploys, and stores the secrets:
   ```bash
   cd passService/worker && npx wrangler login && ./scripts/deploy.sh
   ```

`./scripts/dev-vars.sh .secrets` runs `wrangler dev` with the real certificate instead of the test one.

The API key ships inside the app, so it only keeps casual traffic out. For a public release, add App Attest /
Play Integrity checks or Cloudflare rate limiting in front of `/graphql`.

## App configuration

### Google Wallet (Android)

1. Create an issuer in the [Google Pay & Wallet Console](https://pay.google.com/business/console) and allow-list
   this app (package `cz.poqet.walletka` + signing certificate SHA-1) for the Android API.
2. Add to `local.properties` (not committed):
   ```properties
   walletka.googleIssuerId=3388000000000000000
   walletka.googleIssuerEmail=issuer@example.com
   walletka.serviceUrl=https://walletka-pass-service.<account>.workers.dev
   walletka.serviceApiKey=<API_KEY>
   ```

Walletka saves a *generic pass* through `PayClient.savePasses` (unsigned JSON, allowed for allow-listed apps). The
service settings are optional on Android: without them the card is saved without a hero image.

### Apple Wallet (iOS)

Set in `iosApp/Configuration/Config.xcconfig`:

```
TEAM_ID=ABCDE12345
WALLETKA_SERVICE_URL=https:/$()/walletka-pass-service.<account>.workers.dev
```

and the key in `iosApp/Configuration/Secrets.xcconfig` (gitignored, included by `Config.xcconfig`):

```
WALLETKA_SERVICE_API_KEY=<API_KEY>
```

`TEAM_ID` also enables the App Group (`group.<bundle id>`) that lets the share extension hand images over.

## Building

```bash
./gradlew :androidApp:installDebug
```

```bash
open iosApp/iosApp.xcodeproj
```

The Xcode build phase runs `./gradlew :sharedLogic:embedSwiftExportForXcode`.

```bash
./gradlew :sharedLogic:allTests :passService:allTests
```

## Known limitations

- **iOS Simulator:** Vision's barcode models need a GPU/Neural Engine context the Simulator lacks, so only QR
  codes are found there (via the Core Image fallback); the iOS 26.0 Simulator runtime returns nothing at all.
  On devices all symbologies work.
- Apple Wallet renders only QR, Aztec, PDF417 and Code 128; other codes are re-encoded with the same content
  (2D → QR, linear → Code 128) and the UI says so. Google Wallet supports most formats natively.
- iOS share extension → app hand-off opens the app via the responder chain; if iOS blocks that, the image is
  imported the next time Walletka becomes active.
