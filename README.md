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

*Paste your browser Developer Tools Network Tab screenshots in the placeholders below:*

### Confirmed Order
<!-- Paste confirmed order network tab screenshot here -->
> _Screenshot Placeholder: Inspect the network call for `POST /api/orders` showing HTTP 200 OK and payload `{ "status": "CONFIRMED", ... }`._

### Rejected Order
<!-- Paste rejected order network tab screenshot here -->
> _Screenshot Placeholder: Inspect the network call for `POST /api/orders` showing HTTP 200 OK and payload `{ "status": "REJECTED", ... }`._

---

## 8. Architectural Reflection

<!-- Length: 300–500 words -->

### In-process vs. Microservices Integration: What You Get for Free vs. What Splits Require
In a modular monolith, in-process integration allows `OrderService` to invoke `InventoryService.reserve(...)` via direct Java method dispatch on the same JVM thread and shared memory. This provides ACID transactional guarantees completely for free: both the inventory stock reduction and the order record insertion participate within the same local database transaction boundary (`@Transactional`), ensuring strict atomicity without distributed consensus protocols. Furthermore, in-process execution delivers sub-millisecond call speeds, eliminates JSON serialization/deserialization CPU overhead, provides compile-time type verification, and maintains operational simplicity through a single build artifact and unified logging stream. 

Conversely, decomposing this interaction into microservices removes local ACID guarantees immediately. Coordinating stock reservation and order placement across network boundaries requires implementing distributed transactional patterns such as the Saga pattern (choreographed via Kafka or orchestrated via a workflow engine like Temporal) alongside compensating transactions for rollback on failure. Additionally, teams must engineer network resilience mechanisms: exponential backoff retries, circuit breakers (e.g., Resilience4j), idempotency keys to prevent duplicate inventory deductions during retried requests, distributed tracing (OpenTelemetry), API gateway routing, and distributed concurrency locks.

### Why Package-Private `InventoryServiceImpl` Matters for the Module Boundary
Declaring `InventoryServiceImpl` package-private (omitting the `public` modifier) enforces the module boundary at the compiler level rather than relying on developer discipline or linting conventions. If `InventoryServiceImpl` were public, any engineer working inside the `edu.cit.pena.shop` package could directly instantiate or inject the concrete implementation, or worse, directly access `InventoryRepository`. Such direct coupling causes architectural erosion: order routines might bypass domain invariants, alter inventory state outside transactional locks, or create cyclic package dependencies. By keeping `InventoryServiceImpl` package-private and exposing only the public `InventoryService` interface, the Java compiler forbids external packages from referencing implementation specifics. This guarantees high cohesion, low coupling, and encapsulated internal domain models.

### When to Extract Inventory into a Microservice, and Required Code Changes
Extracting the Inventory module into an autonomous microservice is justified only when specific operational thresholds are met:
1. **Independent Scalability**: Read requests for inventory browse traffic outscale order writes by orders of magnitude, requiring dedicated autoscaling.
2. **Team Autonomy**: A separate engineering team assumes sole ownership of warehouse logistics.
3. **Independent Deployment**: Changes to inventory policies must deploy without redeploying the core shop monolith.

Because `OrderService` depends solely on the `InventoryService` interface via constructor injection, the necessary code changes are remarkably minimal. `OrderService` itself requires zero modifications. The only change is replacing the in-process `InventoryServiceImpl` bean with an HTTP/gRPC client implementation (such as Spring Cloud OpenFeign or Spring 6 `RestClient`) that implements `InventoryService` and dispatches calls to the remote inventory service endpoint.
