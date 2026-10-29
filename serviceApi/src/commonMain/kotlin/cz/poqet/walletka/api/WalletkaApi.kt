package cz.poqet.walletka.api

/** GraphQL API of the Walletka pass service (Cloudflare Worker in `:passService`). */
object WalletkaApi {

    /** Path of the GraphQL endpoint, relative to the service base URL. */
    const val GRAPHQL_PATH = "/graphql"

    /** Path prefix under which uploaded banners are publicly served. */
    const val BANNERS_PATH = "/banners/"

    const val SCHEMA = """
        type Query {
          "Liveness check; returns the configured pass type identifier."
          health: String!

          "What this deployment is set up for; the app falls back to on-device behaviour for anything missing."
          capabilities: Capabilities!
        }

        type Mutation {
          "Signs an Apple Wallet pass. The service sets passTypeIdentifier and teamIdentifier in pass.json."
          signApplePass(files: [PassFileInput!]!): SignedPass!

          "Stores a PNG banner and returns its public URL (used as the Google Wallet hero image)."
          uploadBanner(pngBase64: String!): Banner!

          "Creates or replaces a Google Wallet generic object and returns a signed Save to Google Wallet JWT for it."
          saveGooglePass(pass: GooglePassInput!): GoogleSaveLink!

          "Replaces an existing Google Wallet object; found is false when it doesn't exist."
          updateGooglePass(pass: GooglePassInput!): GooglePassUpdate!

          "Expires a Google Wallet object (the REST API can't delete them); false when it doesn't exist."
          deleteGooglePass(id: ID!): Boolean!
        }

        type Capabilities {
          "Apple pass signing certificates are configured."
          appleWallet: Boolean!
          "A Google Wallet issuer service account is configured (saveGooglePass and friends work)."
          googleWallet: Boolean!
        }

        input GooglePassInput {
          "Stable card identity ([A-Za-z0-9._-], up to 64 characters); the service prefixes the issuer ID."
          id: ID!
          cardTitle: String!
          header: String!
          "#rrggbb"
          hexBackgroundColor: String!
          "Google Wallet BarcodeType, e.g. QR_CODE or EAN_13."
          barcodeType: String!
          barcodeValue: String!
          "A banner URL returned by uploadBanner."
          heroImageUrl: String
        }

        type GoogleSaveLink {
          "Signed JWT for PayClient.savePassesJwt."
          jwt: String!
          "https://pay.google.com/gp/v/save/<jwt>"
          saveUrl: String!
          objectId: String!
        }

        type GooglePassUpdate {
          found: Boolean!
        }

        input PassFileInput {
          "File name inside the pass bundle, e.g. pass.json, icon@2x.png, strip@2x.png."
          name: String!
          base64: String!
        }

        type SignedPass {
          "The signed .pkpass archive."
          pkpassBase64: String!
          serialNumber: String!
        }

        type Banner {
          id: ID!
          url: String!
        }
    """

    const val SIGN_APPLE_PASS = $$"""
        mutation SignApplePass($files: [PassFileInput!]!) {
          signApplePass(files: $files) { pkpassBase64 serialNumber }
        }
    """

    const val UPLOAD_BANNER = $$"""
        mutation UploadBanner($pngBase64: String!) {
          uploadBanner(pngBase64: $pngBase64) { id url }
        }
    """

    const val CAPABILITIES = """
        query Capabilities {
          capabilities { appleWallet googleWallet }
        }
    """

    const val SAVE_GOOGLE_PASS = $$"""
        mutation SaveGooglePass($pass: GooglePassInput!) {
          saveGooglePass(pass: $pass) { jwt saveUrl objectId }
        }
    """

    const val UPDATE_GOOGLE_PASS = $$"""
        mutation UpdateGooglePass($pass: GooglePassInput!) {
          updateGooglePass(pass: $pass) { found }
        }
    """

    const val DELETE_GOOGLE_PASS = $$"""
        mutation DeleteGooglePass($id: ID!) {
          deleteGooglePass(id: $id)
        }
    """

    /** All operations the app sends; validated against [SCHEMA] in the service's tests. */
    val operations: List<String> = listOf(
        SIGN_APPLE_PASS, UPLOAD_BANNER, CAPABILITIES, SAVE_GOOGLE_PASS, UPDATE_GOOGLE_PASS, DELETE_GOOGLE_PASS,
    )
}
