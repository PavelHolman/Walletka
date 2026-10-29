#!/bin/sh
# Writes .dev.vars for `wrangler dev`.
#   scripts/dev-vars.sh             throwaway test certificates (PassKit rejects their passes)
#   scripts/dev-vars.sh .secrets    real pass-cert.pem / pass-key.pem / wwdr.pem (+ api-key.txt) from that folder
set -e
here="$(cd "$(dirname "$0")/.." && pwd)"
certs="${1:-$here/../src/jvmTest/resources/test-certs}"
pem() { awk 'NF { print }' "$1"; }
subject="$(openssl x509 -in "$certs/pass-cert.pem" -noout -subject -nameopt sep_multiline,utf8)"
field() { echo "$subject" | sed -n "s/^ *$1=//p"; }
{
  echo "PASS_TYPE_IDENTIFIER=$(field UID)"
  echo "TEAM_IDENTIFIER=$(field OU)"
  echo "PASS_CERTIFICATE=\"$(pem "$certs/pass-cert.pem")\""
  echo "PASS_PRIVATE_KEY=\"$(pem "$certs/pass-key.pem")\""
  echo "WWDR_CERTIFICATE=\"$(pem "$certs/wwdr.pem")\""
  echo "API_KEY=$(cat "$certs/api-key.txt" 2>/dev/null || echo dev-key)"
} > "$here/.dev.vars"
echo "Wrote .dev.vars ($(field UID), team $(field OU))"
