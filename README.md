# Modular Monolith Integration (Spring Boot + React + Supabase)

A modular monolith architecture demonstrating in-process integration between an **Order Module** (`edu.cit.pena.shop`) and an **Inventory Module** (`edu.cit.pena.inventory`), backed by **Supabase (PostgreSQL)** and a modern **React (Vite)** single-page frontend.

---

## 1. Architectural Overview & Repo Layout

The repository enforces modular monolith boundaries at compile-time using Java's package-private encapsulation and interface-based dependency injection.

```
/
├── backend/
│   ├── mvnw / mvnw.cmd                     # Maven wrapper scripts
│   ├── pom.xml                             # Spring Boot & dependencies
│   └── src/
│       ├── main/
│       │   ├── java/edu/cit/pena/
│       │   │   ├── ModularMonolithApplication.java # @SpringBootApplication root scanner
│       │   │   ├── config/
│       │   │   │   └── CorsConfig.java     # CORS configuration
│       │   │   ├── inventory/              # INVENTORY MODULE
│       │   │   │   ├── InventoryItem.java  # JPA entity mapped to 'inventory' table
│       │   │   │   ├── InventoryRepository.java # Package-private JPA repository
│       │   │   │   ├── InventoryService.java    # Public contract interface
│       │   │   │   ├── InventoryServiceImpl.java# Package-private implementation
│       │   │   │   └── ReservationResult.java   # Record carrying outcome & snapshot
│       │   │   └── shop/                   # ORDER MODULE
│       │   │       ├── InventorySnapshot.java # Response snapshot DTO
│       │   │       ├── Order.java             # JPA entity mapped to 'orders' table
│       │   │       ├── OrderController.java   # REST Controller (/api/orders, /api/inventory)
│       │   │       ├── OrderRepository.java   # JPA repository for orders
│       │   │       ├── OrderRequest.java      # Order request payload DTO
│       │   │       ├── OrderResponse.java     # Order response payload DTO
│       │   │       └── OrderService.java      # Constructor-injects InventoryService only
│       │   └── resources/
│       │       └── application.properties  # Database credentials via env vars only
│       └── test/                           # Automated integration & unit tests
├── frontend/                               # Vite + React single-page frontend
│   ├── src/
│   │   ├── App.jsx                         # Order form, dropdown, live result area
│   │   ├── App.css                         # Clean responsive UI styling
│   │   ├── index.css
│   │   └── main.jsx
│   ├── index.html
│   ├── package.json
│   └── vite.config.js                      # Vite dev proxy configuration
├── sql/
│   └── schema.sql                          # Database schema DDL & seed data
├── .env.example                            # Template for environment variables
├── .gitignore                              # Excludes credentials, build artifacts, node_modules
└── README.md
```

---

## 2. Supabase Setup Steps

1. **Create a Supabase Project**:
   - Log in to your [Supabase Dashboard](https://app.supabase.com/) and create or open your project.

2. **Run `schema.sql`**:
   - In your Supabase dashboard, navigate to the **SQL Editor** tab from the left sidebar.
   - Click **"New query"**.
   - Copy the entire contents of [`sql/schema.sql`](file:///c:/Users/l23y19w34/Downloads/Lab_monolith/sql/schema.sql) and paste them into the SQL editor.
   - Click **"Run"** (or press `Ctrl + Enter`).
   - Navigate to the **Table Editor** to confirm that both `inventory` and `orders` tables are created, and `inventory` contains the 3 seeded products:
     - `P100` | Wireless Mouse | 25
     - `P200` | Mechanical Keyboard | 10
     - `P300` | USB-C Hub | 0

3. **Get the Session Pooler Connection String**:
   - In your Supabase dashboard, go to **Project Settings** (gear icon) -> **Database**.
   - Scroll down to the **Connection string** section.
   - Select the **Session pooler** tab (Port 5432).
   - Notice the connection parameters:
     - Host: `aws-0-ap-southeast-2.pooler.supabase.com`
     - Port: `5432`
     - Database: `postgres`
     - Username: `postgres.xamtsuyooxcjpwbkhklf`
   - These are pre-configured in `backend/src/main/resources/application.properties`.

---

## 3. Environment Variable Setup for Backend

The backend reads your database password securely from the `SUPABASE_DB_PASSWORD` environment variable (never hardcoded):

### On Windows (PowerShell):
```powershell
$env:SUPABASE_DB_PASSWORD = "YOUR_ACTUAL_SUPABASE_PASSWORD"
```

### On macOS / Linux (Bash / Zsh):
```bash
export SUPABASE_DB_PASSWORD="YOUR_ACTUAL_SUPABASE_PASSWORD"
```

A template is provided in [`.env.example`](file:///c:/Users/l23y19w34/Downloads/Lab_monolith/.env.example).


---

## 4. How to Run Backend and Frontend

### Step A: Run the Backend (Spring Boot)

Navigate to the `backend/` directory and execute with Maven:

```bash
cd backend
mvn spring-boot:run
```

*(Alternatively, if you prefer the bundled wrapper: `./mvnw spring-boot:run` on Linux/macOS or `.\mvnw.cmd spring-boot:run` on Windows).*

The backend starts on port **8080** and scans both `edu.cit.pena.shop` and `edu.cit.pena.inventory`.

To run backend automated integration tests:
```bash
mvn test
```

### Step B: Run the Frontend (Vite + React)

In a separate terminal, navigate to the `frontend/` directory:

```bash
cd frontend
npm install
npm run dev
```

The frontend will start at **`http://localhost:5173`**. Open this URL in your web browser.

---

## 5. Architectural Decision: Vite Proxy vs. Full URLs

In `frontend/vite.config.js`, we configured a development reverse proxy:

```javascript
server: {
  port: 5173,
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
      secure: false
    }
  }
}
```

### Why Choose a Vite Proxy?
1. **Zero Hardcoded Hostnames**: Frontend code issues relative HTTP requests (e.g. `fetch('/api/orders')` and `fetch('/api/inventory')`). It does not need to know or hardcode `http://localhost:8080`.
2. **Eliminates Browser Pre-flight Latency**: Because the browser communicates directly with the Vite origin (`http://localhost:5173`), calls to `/api/...` are treated as same-origin requests by the browser. This eliminates OPTIONS pre-flight checks during development.
3. **Production Parity**: In a production deployment, a web client and backend API are usually hosted behind an ingress controller, reverse proxy (Nginx, Caddy), or API Gateway on the same domain. Using relative paths via a dev proxy mirrors the production architecture perfectly.
4. **CORS Flexibility**: The backend also includes an explicit Spring `@CrossOrigin` and `WebMvcConfigurer` allowing `http://localhost:5173`, ensuring that if a developer ever switches to full absolute URLs, requests continue to succeed without CORS blocks.

---

## 6. Console Output & Curl Commands for Verification

You can verify the backend endpoints directly using `curl` or PowerShell `Invoke-RestMethod`:

### 1. View Initial Inventory (`GET /api/inventory`)
```bash
curl -X GET http://localhost:8080/api/inventory
```
**Console Response:**
```json
[
  {
    "productId": "P100",
    "name": "Wireless Mouse",
    "stock": 25
  },
  {
    "productId": "P200",
    "name": "Mechanical Keyboard",
    "stock": 10
  },
  {
    "productId": "P300",
    "name": "USB-C Hub",
    "stock": 0
  }
]
```

### 2. Confirmed Order Path (`POST /api/orders`)
Submit an order for 1 unit of `P100` (Wireless Mouse has 25 initial stock):
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d "{\"productId\": \"P100\", \"quantity\": 1}"
```
**Console Response (CONFIRMED):**
```json
{
  "status": "CONFIRMED",
  "reason": "Successfully reserved 1 unit(s) of Wireless Mouse.",
  "inventory": {
    "productId": "P100",
    "name": "Wireless Mouse",
    "stock": 24
  }
}
```

### 3. Rejected Order Path (`POST /api/orders`)
Submit an order for 1 unit of `P300` (USB-C Hub has 0 stock):
```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d "{\"productId\": \"P300\", \"quantity\": 1}"
```
**Console Response (REJECTED):**
```json
{
  "status": "REJECTED",
  "reason": "Insufficient stock for USB-C Hub (requested: 1, available: 0).",
  "inventory": {
    "productId": "P300",
    "name": "USB-C Hub",
    "stock": 0
  }
}
```

---

## 7. Network Tab Evidence

### Confirmed Order
![Confirmed Order Network Tab Evidence](evidence/Screenshot%202026-09-10%20201317.png)

### Rejected Order
![Rejected Order Network Tab Evidence](evidence/Screenshot%202026-09-10%20201228.png)

---

## 8. Reflection

### 1. In-process vs. microservices for Order/Inventory
When both modules run in one Spring Boot process, OrderService calls InventoryService as a plain Java method. The JVM gives me typed synchronous calls, one @Transactional boundary covering both the stock decrement and the order insert, full stack traces on failure, and no serialization, timeouts, retries, or service discovery. I can debug the whole flow in one IDE session. If Inventory became a separate microservice, I would have to add all of that back: an HTTP or gRPC client, DTOs and JSON at the wire, timeouts, retry and backoff, circuit breakers, service discovery, service-to-service auth, and distributed tracing. Most importantly, one local transaction would no longer span both writes, so I would need a saga or outbox pattern, or compensating logic for partial failures. Splitting trades a simple synchronous call for a network hop plus a pile of reliability plumbing.

### 2. Why package-private on InventoryServiceImpl matters
InventoryServiceImpl is package-private, so it is only visible inside edu.cit.pena.inventory. The Order module lives in edu.cit.pena.shop and cannot reference it — the compiler enforces this. Order can only see the InventoryService interface, which is the module's public contract. That matters because the interface is the seam: as long as Order depends only on it, Inventory can swap JPA for JDBC, add caching, or rename internals without touching Order. If the class were public, Order could inject the implementation and couple to it directly, and worse, could bypass reserve() — the single place the stock check lives — and confirm orders that should be rejected. Package-private visibility turns the module boundary from a convention into something the compiler enforces.

### 3. When to extract Inventory as its own microservice
I would extract Inventory when its operational profile diverges from Order's: when many services read and write it, when it needs independent scaling or release cadence, or when separate teams own each module. If only Order talks to Inventory, keeping it in-process is cheaper. To extract it I would move edu.cit.pena.inventory into its own Spring Boot project with its own main and database connection; expose getItem and reserve as REST or gRPC endpoints; and replace the injected InventoryService bean in Order with an HTTP client that implements the same interface, so OrderService itself does not change. I would also add service auth, a resilient client, and a saga or outbox pattern, because the single local transaction no longer spans both writes.

