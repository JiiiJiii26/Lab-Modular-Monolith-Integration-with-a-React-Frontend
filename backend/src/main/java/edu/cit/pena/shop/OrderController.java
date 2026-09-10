package edu.cit.pena.shop;

import edu.cit.pena.inventory.InventoryItem;
import edu.cit.pena.inventory.InventoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing endpoints for placing orders and fetching inventory.
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:5173}")
public class OrderController {

    private final OrderService orderService;
    private final InventoryService inventoryService;

    public OrderController(OrderService orderService, InventoryService inventoryService) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
    }

    /**
     * Endpoint to place an order.
     * Evaluates inventory reservation and records the order in the orders table.
     *
     * @param request JSON payload: { "productId": "...", "quantity": N }
     * @return JSON response: { "status": "CONFIRMED|REJECTED", "reason": "...", "inventory": { ... } }
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request) {
        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to fetch the full inventory list.
     * Used by the React frontend dropdown to populate products and current stock.
     *
     * @return List of all InventoryItems
     */
    @GetMapping("/inventory")
    public ResponseEntity<List<InventoryItem>> getInventory() {
        List<InventoryItem> items = inventoryService.getAllItems();
        return ResponseEntity.ok(items);
    }
}
