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
        }

        type Mutation {
          "Signs an Apple Wallet pass. The service sets passTypeIdentifier and teamIdentifier in pass.json."
          signApplePass(files: [PassFileInput!]!): SignedPass!

          "Stores a PNG banner and returns its public URL (used as the Google Wallet hero image)."
          uploadBanner(pngBase64: String!): Banner!
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

    /** All operations the app sends; validated against [SCHEMA] in the service's tests. */
    val operations: List<String> = listOf(SIGN_APPLE_PASS, UPLOAD_BANNER)
}
