package edu.cit.pena.shop;

import edu.cit.pena.inventory.InventoryService;
import edu.cit.pena.inventory.dto.InventoryItemDto;
import edu.cit.pena.shop.dto.OrderRequest;
import edu.cit.pena.shop.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:5173", "http://127.0.0.1:5173"})
public class OrderController {

    private final OrderService orderService;
    private final InventoryService inventoryService;

    public OrderController(OrderService orderService, InventoryService inventoryService) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
    }

    /**
     * POST /api/orders
     * Accepts: { "items": [ { "productId": "P100", "quantity": 2 }, ... ] }
     * Or single item legacy format.
     */
    @PostMapping("/orders")
    public ResponseEntity<OrderResponse> placeOrder(@Valid @RequestBody OrderRequest request) {
        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/orders/{orderId}/cancel
     * Sets order status to CANCELLED and restocks reserved inventory.
     */
    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<OrderResponse> cancelOrder(@PathVariable String orderId) {
        OrderResponse response = orderService.cancelOrder(orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/inventory
     * Returns list of inventory items with current stock.
     */
    @GetMapping("/inventory")
    public ResponseEntity<List<InventoryItemDto>> getInventory() {
        List<InventoryItemDto> items = inventoryService.getAllItems();
        return ResponseEntity.ok(items);
    }

    /**
     * GET /api/orders
     * Returns order history with status and line items.
     */
    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getOrders() {
        List<Order> orders = orderService.getAllOrders();
        return ResponseEntity.ok(orders);
    }
}

