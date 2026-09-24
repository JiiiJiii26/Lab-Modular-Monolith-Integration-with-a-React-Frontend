# Reflection

## Question 1
**PO-100211 (BuyerRef "RO-3") ended with StatusCode 90, which is not in the documentation. How did you work out what it means, and what does your system now do with the stock that will never arrive?**

**Answer:**
The interface manual documents four order status codes — 10 (Accepted), 20 (Picking),
30 (Shipped), and 40 (Delivered) — so when our tracking job received StatusCode 90 on
PO-100211, the `SupplierTranslator.mapStatus` switch fell through to
`SupplierOrderStatus.UNKNOWN`, logged a WARN with the raw code and PO number, and
updated the row without emitting a delivery event. Because UNKNOWN is not in the
"open orders" set used by `SupplierTrackingJob`, the row stopped being polled and the
order was treated as terminal. That means no units were ever added to Inventory for
RO-3, which is the correct behaviour: LegacySupply will not physically ship the 2 cases
of P200, so there is no stock to add. The self-check page later confirmed the
interpretation — it now shows "1 cancelled orders seen" — which tells us that 90 is
LegacySupply's undocumented "cancelled/abandoned" code, and validates the terminal-
UNKNOWN decision we documented in INTEGRATION.md section 5.

## Question 2
**LegacySupply never tells you how long a session lasts. Measure your session lifetime from your own logs, state the number, and explain how your adapter decides when to sign in again.**

**Answer:**
The auth response from LegacySupply contains only `<SessionToken>` and `<IssuedAt>` — no
expiry field and no `Set-Cookie` header. The manual only says sessions are "short-lived"
and that partners should obtain a new session when theirs is no longer accepted. I
measured the lifetime from my own backend logs: the self-check record shows 3 requests
that were rejected with HTTP 401 `E-AUTH-07 token expired` out of 13 sign-ins, and the
gap between a fresh `auth ok` entry and the next `token expired` was consistently
between 60 and 90 seconds. My adapter never tries to predict this expiry. It assumes
the token is valid, sends it in the `X-LS-Session` header, and only re-authenticates
when LegacySupply replies 401. When that happens, `SessionManager` signs in once, caches
the new token, and retries the original request with the same `X-Request-Id`, so the
retry cannot create a duplicate. The self-check page confirms this works end-to-end:
"Renews expired sessions — 13 sign-ins, 3 requests with an expired session."

## Question 3
**The catalog reports PackSize and orders report Uom "CS". Using one of your own orders, show the arithmetic from "units your Inventory needed" to the Qty you sent, and to the units your Inventory received on delivery.**

**Answer:**
When P100 (Wireless Mouse) dropped below the low-stock threshold of 5, the low-stock rule
calculated that 16 units were needed to restore stock. P100 maps to SupplierSku
`GSF-1861` with `PackSize = 20`, so `SupplierTranslator` computed
`cases = ceil(16 / 20) = 1` and sent `<Qty>1</Qty>` with the Uom that LegacySupply uses
for the item (`CS`, a case). My `supplier_orders` row records both numbers: `units = 16`
(what I asked for, in my units) and `cases = 1` (what I sent, in LegacySupply's units).
LegacySupply created PO-100210 and later moved it to StatusCode 40 (Delivered). One case
× 20 units per case = 20 physical units shipped — 4 more than the 16 I requested, because
partial cases are not possible. That 20-unit figure is what the delivery event carries
into the inventory restock.