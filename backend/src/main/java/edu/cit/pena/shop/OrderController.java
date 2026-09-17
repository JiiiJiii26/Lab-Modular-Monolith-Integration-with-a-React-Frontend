package edu.cit.pena.shop;

import edu.cit.pena.inventory.InventoryItem;
import edu.cit.pena.inventory.InventoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller exposing endpoints for placing orders, cancelling orders,
 * listing order history, and fetching inventory.
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
     * Endpoint to place an order (supporting multi-item requests).
     * Follows the all-or-nothing transactional rule.
     *
     * @param request JSON payload with items array
     * @return JSON response with status, reason, per-item outcomes, and full inventory snapshot
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request) {
        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint to cancel an existing order and restock its line items.
     * - 404 if order not found
     * - 409 if order status is already CANCELLED
     * - 200 OK with updated order details otherwise
     *
     * @param orderId ID of order to cancel
     * @return ResponseEntity with cancellation status
     */
    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<?> cancelOrder(@PathVariable Long orderId) {
        CancelOrderResult result = orderService.cancelOrder(orderId);

        return switch (result.status()) {
            case NOT_FOUND -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(result.message());
            case ALREADY_CANCELLED -> ResponseEntity.status(HttpStatus.CONFLICT).body(result.message());
            case SUCCESS -> ResponseEntity.ok(result.order());
        };
    }

    /**
     * Endpoint to list all orders with line items and status (newest first).
     *
     * @return List of OrderDetailsDto
     */
    @GetMapping("/orders")
    public ResponseEntity<List<OrderDetailsDto>> getOrders() {
        List<OrderDetailsDto> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }

    /**
     * Endpoint to fetch the full inventory list.
     * Used by the React frontend dropdown and inventory table.
     *
     * @return List of all InventoryItems
     */
    @GetMapping("/inventory")
    public ResponseEntity<List<InventoryItem>> getInventory() {
        List<InventoryItem> items = inventoryService.getAllItems();
        return ResponseEntity.ok(items);
    }
}
