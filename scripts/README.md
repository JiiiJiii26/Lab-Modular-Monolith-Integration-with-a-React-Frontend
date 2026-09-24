# LegacySupply Contract Discovery Scripts (Lab 3)

This directory contains diagnostic probe scripts to inspect and discover the external LegacySupply XML API contract.

- `probe-legacysupply.ps1` (PowerShell — for Windows)
- `probe-legacysupply.sh` (Bash — for macOS / Linux / WSL)

These scripts execute a strictly budgeted sequence of **5 read-only HTTP requests**:
1. **(a) Authenticate**: `POST /auth/token` with Client ID and API key. Captures session token, issued time, and cookies.
2. **(b) List Catalog**: `GET /catalog` with `X-LS-Session`. Displays raw XML and parsed product table.
3. **(c) Single Product Lookup**: `GET /catalog/{SupplierSku}` with session token for the first catalog SKU.
4. **(d) Malformed Request**: `GET /catalog/` (missing SKU) to capture the validation error format and error code.
5. **(e) Unauthenticated Request**: `GET /catalog/{SupplierSku}` without `X-LS-Session` header to capture authentication failure codes.

> **Safety Notice:** These scripts perform only read-only lookups against `/auth/token` and `/catalog`. They do **not** place orders or modify server state, and are completely safe to rerun. Any occurrence of your API key in request/response bodies is automatically masked with `<REDACTED>`.

---

## Setting Environment Variables

The scripts read credentials strictly from environment variables. Do not hardcode credentials in any file.

### Windows (PowerShell)
```powershell
$env:LS_BASE_URL  = "https://legacysupply.onrender.com/api/v1"   # Optional, defaults to this URL
$env:LS_CLIENT_ID = "YOUR_STUDENT_ID"
$env:LS_API_KEY   = "YOUR_API_KEY"
```

### macOS / Linux / WSL (Bash)
```bash
export LS_BASE_URL="https://legacysupply.onrender.com/api/v1"   # Optional, defaults to this URL
export LS_CLIENT_ID="YOUR_STUDENT_ID"
export LS_API_KEY="YOUR_API_KEY"
```

---

## Running the Probe Script

### Windows (PowerShell)
From the repo root:
```powershell
.\scripts\probe-legacysupply.ps1
```

### macOS / Linux / WSL (Bash)
From the repo root:
```bash
chmod +x scripts/probe-legacysupply.sh
./scripts/probe-legacysupply.sh
```

---

## Output
Review the output to fill in `INTEGRATION.md` and check your progress on the LegacySupply self-check page (`https://legacysupply.onrender.com/verify`).
