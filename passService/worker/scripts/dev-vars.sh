#!/bin/sh
# Writes .dev.vars for `wrangler dev` using the throwaway test certificates (not accepted by Apple Wallet).
set -e
certs="$(dirname "$0")/../../src/jvmTest/resources/test-certs"
pem() { awk 'NF { printf "%s\\n", $0 }' "$1"; }
{
  echo "PASS_TYPE_IDENTIFIER=pass.cz.poqet.walletka.test"
  echo "TEAM_IDENTIFIER=TESTTEAM01"
  echo "PASS_CERTIFICATE=\"$(pem "$certs/pass-cert.pem")\""
  echo "PASS_PRIVATE_KEY=\"$(pem "$certs/pass-key.pem")\""
  echo "WWDR_CERTIFICATE=\"$(pem "$certs/wwdr.pem")\""
  echo "API_KEY=dev-key"
} > "$(dirname "$0")/../.dev.vars"
echo "Wrote .dev.vars"
