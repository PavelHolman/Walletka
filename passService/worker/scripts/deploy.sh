#!/bin/sh
# Deploys the pass service with the certificates in .secrets/ (pass-cert.pem, pass-key.pem, wwdr.pem, api-key.txt,
# and optionally google-service-account.json).
# Requires `npx wrangler login` first. Safe to re-run: it updates the secrets and redeploys.
set -e
cd "$(dirname "$0")/.."
secrets=.secrets
for f in pass-cert.pem pass-key.pem wwdr.pem api-key.txt; do
  [ -f "$secrets/$f" ] || { echo "Missing $secrets/$f" >&2; exit 1; }
done

npx wrangler whoami >/dev/null
# Create the banner KV namespace once and write its id into wrangler.toml.
if grep -q '^id = "CREATED_BY_DEPLOY_SCRIPT"' wrangler.toml; then
  id="$(npx wrangler kv namespace create walletka-banners 2>&1 | grep -oE '[0-9a-f]{32}' | head -1)"
  [ -n "$id" ] || { echo "Could not create the KV namespace" >&2; exit 1; }
  sed -i.bak "s/^id = \"CREATED_BY_DEPLOY_SCRIPT\"/id = \"$id\"/" wrangler.toml && rm wrangler.toml.bak
  echo "Created KV namespace $id"
fi

# Builds the Kotlin code; the first deploy also creates the Worker, so secrets are set afterwards.
npm run deploy
npx wrangler secret put PASS_CERTIFICATE < "$secrets/pass-cert.pem"
npx wrangler secret put PASS_PRIVATE_KEY < "$secrets/pass-key.pem"
npx wrangler secret put WWDR_CERTIFICATE < "$secrets/wwdr.pem"
tr -d '\n' < "$secrets/api-key.txt" | npx wrangler secret put API_KEY
# Optional Google Wallet sync: the issuer's service account key (GOOGLE_ISSUER_ID is set in wrangler.toml).
if [ -f "$secrets/google-service-account.json" ]; then
  npx wrangler secret put GOOGLE_SERVICE_ACCOUNT_JSON < "$secrets/google-service-account.json"
fi
echo "Deployed. Configure the apps with the workers.dev URL above and the key in $secrets/api-key.txt."
