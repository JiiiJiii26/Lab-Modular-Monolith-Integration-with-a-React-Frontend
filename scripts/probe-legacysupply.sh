#!/usr/bin/env bash
# ==============================================================================
# LegacySupply Partner Interface Probe Script (Lab 3 Contract Discovery)
#
# Diagnostic probe for contract discovery against LegacySupply API v1.
# Strictly performs 5 read-only requests:
#   1. Authenticate (POST /auth/token)
#   2. List Catalog (GET /catalog)
#   3. Single Product Lookup (GET /catalog/{SupplierSku})
#   4. Malformed Request (GET /catalog/ without SKU)
#   5. Unauthenticated Request (GET /catalog/{SupplierSku} without session)
# Safe to rerun. Does NOT modify or create orders.
# ==============================================================================

set -euo pipefail

BASE_URL="${LS_BASE_URL:-https://legacysupply.onrender.com/api/v1}"
BASE_URL="${BASE_URL%/}"
CLIENT_ID="${LS_CLIENT_ID:-}"
API_KEY="${LS_API_KEY:-}"

if [ -z "$CLIENT_ID" ]; then
  echo "Error: Missing required environment variable LS_CLIENT_ID" >&2
  echo "Please set: export LS_CLIENT_ID='your-student-id'" >&2
  exit 1
fi

if [ -z "$API_KEY" ]; then
  echo "Error: Missing required environment variable LS_API_KEY" >&2
  echo "Please set: export LS_API_KEY='your-api-key'" >&2
  exit 1
fi

echo "=========================================================="
echo " LegacySupply Contract Discovery Probe (Lab 3)"
echo " Base URL  : $BASE_URL"
echo " Client ID : $CLIENT_ID"
echo " API Key   : <REDACTED>"
echo " Budget    : 5 requests max"
echo "=========================================================="
echo ""

redact() {
  local text="$1"
  if [ -n "$API_KEY" ]; then
    echo "$text" | sed "s|${API_KEY}|<REDACTED>|g"
  else
    echo "$text"
  fi
}

# ---------------------------------------------------------------------------
# REQUEST 1 / 5: (a) AUTHENTICATE
# ---------------------------------------------------------------------------
AUTH_URL="${BASE_URL}/auth/token"
AUTH_BODY="<?xml version=\"1.0\" encoding=\"UTF-8\"?>
<AuthRequest>
  <ClientId>${CLIENT_ID}</ClientId>
  <ApiKey>${API_KEY}</ApiKey>
</AuthRequest>"

echo "================ REQUEST 1 ================"
echo "LABEL  : (a) AUTHENTICATE"
echo "METHOD POST"
echo "URL ${AUTH_URL}"
echo "BODY $(redact "$AUTH_BODY")"

TMP_RESP_HEADERS=$(mktemp)
TMP_RESP_BODY=$(mktemp)
trap 'rm -f "$TMP_RESP_HEADERS" "$TMP_RESP_BODY"' EXIT

HTTP_STATUS=$(curl -s -X POST "${AUTH_URL}" \
  -H "Content-Type: application/xml; charset=utf-8" \
  -H "Accept: application/xml" \
  --data "$AUTH_BODY" \
  -D "$TMP_RESP_HEADERS" \
  -o "$TMP_RESP_BODY" \
  -w "%{http_code}")

RAW_RESP=$(cat "$TMP_RESP_BODY")
echo "--------------- RESPONSE 1 ---------------"
echo "STATUS ${HTTP_STATUS}"
echo "BODY $(redact "$RAW_RESP")"
echo ""

SESSION_TOKEN=$(echo "$RAW_RESP" | grep -oPm1 "(?<=<SessionToken>)[^<]+" || echo "$RAW_RESP" | sed -n 's/.*<SessionToken>\(.*\)<\/SessionToken>.*/\1/p' || true)
ISSUED_AT=$(echo "$RAW_RESP" | grep -oPm1 "(?<=<IssuedAt>)[^<]+" || echo "$RAW_RESP" | sed -n 's/.*<IssuedAt>\(.*\)<\/IssuedAt>.*/\1/p' || true)
SET_COOKIE=$(grep -i '^set-cookie:' "$TMP_RESP_HEADERS" | tr -d '\r' || echo "(none)")

echo "--- Auth Extraction Details ---"
echo "Session Token : ${SESSION_TOKEN:-<NONE>}"
echo "Issued At     : ${ISSUED_AT:-<NONE>}"
echo "Set-Cookie    : ${SET_COOKIE:-<NONE>}"
echo ""

if [ -z "$SESSION_TOKEN" ]; then
  echo "Error: Failed to obtain session token from request 1." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# REQUEST 2 / 5: (b) LIST PRODUCTS
# ---------------------------------------------------------------------------
CATALOG_URL="${BASE_URL}/catalog"

echo "================ REQUEST 2 ================"
echo "LABEL  : (b) LIST PRODUCTS (GET /catalog)"
echo "METHOD GET"
echo "URL ${CATALOG_URL}"
echo "BODY (none)"

HTTP_STATUS=$(curl -s -X GET "${CATALOG_URL}" \
  -H "X-LS-Session: ${SESSION_TOKEN}" \
  -H "Accept: application/xml" \
  -D "$TMP_RESP_HEADERS" \
  -o "$TMP_RESP_BODY" \
  -w "%{http_code}")

RAW_RESP=$(cat "$TMP_RESP_BODY")
echo "--------------- RESPONSE 2 ---------------"
echo "STATUS ${HTTP_STATUS}"
echo "BODY $(redact "$RAW_RESP")"
echo ""

# Extract first SupplierSku
FIRST_SKU=$(echo "$RAW_RESP" | grep -oPm1 "(?<=<SupplierSku>)[^<]+" || echo "$RAW_RESP" | sed -n 's/.*<SupplierSku>\(.*\)<\/SupplierSku>.*/\1/p' | head -n 1 || true)
if [ -z "$FIRST_SKU" ]; then
  FIRST_SKU="ABC-1234"
  echo "Using default SKU for subsequent single-item probes: $FIRST_SKU"
else
  echo "Selected first SKU for single-item probes: $FIRST_SKU"
fi
echo ""

# ---------------------------------------------------------------------------
# REQUEST 3 / 5: (c) SINGLE PRODUCT LOOKUP
# ---------------------------------------------------------------------------
SINGLE_URL="${BASE_URL}/catalog/${FIRST_SKU}"

echo "================ REQUEST 3 ================"
echo "LABEL  : (c) SINGLE PRODUCT LOOKUP (GET /catalog/${FIRST_SKU})"
echo "METHOD GET"
echo "URL ${SINGLE_URL}"
echo "BODY (none)"

HTTP_STATUS=$(curl -s -X GET "${SINGLE_URL}" \
  -H "X-LS-Session: ${SESSION_TOKEN}" \
  -H "Accept: application/xml" \
  -D "$TMP_RESP_HEADERS" \
  -o "$TMP_RESP_BODY" \
  -w "%{http_code}")

RAW_RESP=$(cat "$TMP_RESP_BODY")
echo "--------------- RESPONSE 3 ---------------"
echo "STATUS ${HTTP_STATUS}"
echo "BODY $(redact "$RAW_RESP")"
echo ""

# ---------------------------------------------------------------------------
# REQUEST 4 / 5: (d) MALFORMED REQUEST (Omit SKU)
# ---------------------------------------------------------------------------
MALFORMED_URL="${BASE_URL}/catalog/"

echo "================ REQUEST 4 ================"
echo "LABEL  : (d) MALFORMED REQUEST (Omit SKU: GET /catalog/)"
echo "METHOD GET"
echo "URL ${MALFORMED_URL}"
echo "BODY (none)"

HTTP_STATUS=$(curl -s -X GET "${MALFORMED_URL}" \
  -H "X-LS-Session: ${SESSION_TOKEN}" \
  -H "Accept: application/xml" \
  -D "$TMP_RESP_HEADERS" \
  -o "$TMP_RESP_BODY" \
  -w "%{http_code}")

RAW_RESP=$(cat "$TMP_RESP_BODY")
echo "--------------- RESPONSE 4 ---------------"
echo "STATUS ${HTTP_STATUS}"
echo "BODY $(redact "$RAW_RESP")"
echo ""

# ---------------------------------------------------------------------------
# REQUEST 5 / 5: (e) UNAUTHENTICATED REQUEST (Omit X-LS-Session)
# ---------------------------------------------------------------------------
UNAUTH_URL="${BASE_URL}/catalog/${FIRST_SKU}"

echo "================ REQUEST 5 ================"
echo "LABEL  : (e) UNAUTHENTICATED REQUEST (No session: GET /catalog/${FIRST_SKU})"
echo "METHOD GET"
echo "URL ${UNAUTH_URL}"
echo "BODY (none)"

HTTP_STATUS=$(curl -s -X GET "${UNAUTH_URL}" \
  -H "Accept: application/xml" \
  -D "$TMP_RESP_HEADERS" \
  -o "$TMP_RESP_BODY" \
  -w "%{http_code}")

RAW_RESP=$(cat "$TMP_RESP_BODY")
echo "--------------- RESPONSE 5 ---------------"
echo "STATUS ${HTTP_STATUS}"
echo "BODY $(redact "$RAW_RESP")"
echo ""

echo "=========================================================="
echo " LegacySupply Probe Complete: 5 of 5 requests executed."
echo " Copy the captured values into INTEGRATION.md."
echo "=========================================================="
