package edu.cit.pena;

import edu.cit.pena.inventory.InventoryItem;
import edu.cit.pena.inventory.InventoryRepository;
import edu.cit.pena.inventory.InventoryService;
import edu.cit.pena.notification.Notification;
import edu.cit.pena.notification.NotificationRepository;
import edu.cit.pena.shop.Order;
import edu.cit.pena.shop.OrderRepository;
import edu.cit.pena.shop.OrderService;
import edu.cit.pena.shop.OrderStatus;
import edu.cit.pena.shop.dto.OrderItemRequest;
import edu.cit.pena.shop.dto.OrderRequest;
import edu.cit.pena.shop.dto.OrderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class OrderServiceIntegrationTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @BeforeEach
    void setup() {
        notificationRepository.deleteAll();
        orderRepository.deleteAll();
        inventoryRepository.deleteAll();

        // Seed initial specification items
        inventoryRepository.saveAll(List.of(
                new InventoryItem("P100", "Wireless Mouse", 25),
                new InventoryItem("P200", "Mechanical Keyboard", 10),
                new InventoryItem("P300", "USB-C Hub", 0)
        ));
    }

    @Test
    @DisplayName("Architectural Boundary: InventoryServiceImpl must be package-private")
    void testInventoryServiceImplIsPackagePrivate() throws ClassNotFoundException {
        Class<?> implClass = Class.forName("edu.cit.pena.inventory.InventoryServiceImpl");
        int modifiers = implClass.getModifiers();
        assertFalse(Modifier.isPublic(modifiers), "InventoryServiceImpl MUST NOT be public (must be package-private).");
        assertFalse(Modifier.isProtected(modifiers), "InventoryServiceImpl MUST NOT be protected.");
        assertFalse(Modifier.isPrivate(modifiers), "InventoryServiceImpl MUST NOT be private.");
    }

    @Test
    @DisplayName("Architectural Boundary: NotificationEventListener must be package-private")
    void testNotificationEventListenerIsPackagePrivate() throws ClassNotFoundException {
        Class<?> listenerClass = Class.forName("edu.cit.pena.notification.NotificationEventListener");
        int modifiers = listenerClass.getModifiers();
        assertFalse(Modifier.isPublic(modifiers), "NotificationEventListener MUST NOT be public (must be package-private).");
        assertFalse(Modifier.isProtected(modifiers), "NotificationEventListener MUST NOT be protected.");
        assertFalse(Modifier.isPrivate(modifiers), "NotificationEventListener MUST NOT be private.");
    }

    @Test
    @DisplayName("Multi-Item Confirmed Order: Stock deducted for all items, status CONFIRMED, event published")
    void testPlaceMultiItemOrderConfirmed() {
        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest("P100", 2),
                new OrderItemRequest("P200", 3)
        ));
        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response.getOrderId());
        assertEquals(OrderStatus.CONFIRMED, response.getStatus());
        assertNull(response.getReason());
        assertEquals(2, response.getItems().size());

        // Verify inventory stock deducted
        assertEquals(23, inventoryRepository.findById("P100").orElseThrow().getStock());
        assertEquals(7, inventoryRepository.findById("P200").orElseThrow().getStock());

        // Verify order saved with line items
        Order savedOrder = orderRepository.findById(response.getOrderId()).orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, savedOrder.getStatus());
        assertEquals(2, savedOrder.getItems().size());

        // Verify Notification logged via event
        List<Notification> notifications = notificationRepository.findAll();
        assertTrue(notifications.stream().anyMatch(n -> n.getMessage().contains("confirmed")));
    }

    @Test
    @DisplayName("Multi-Item All-or-Nothing Rollback: If one item fails, entire order is REJECTED and zero items reserved")
    void testPlaceMultiItemOrderAllOrNothingRollback() {
        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest("P100", 5), // P100 has 25 stock (would succeed on its own)
                new OrderItemRequest("P200", 15) // P200 has 10 stock (fails)
        ));
        OrderResponse response = orderService.placeOrder(request);

        assertNotNull(response.getOrderId());
        assertEquals(OrderStatus.REJECTED, response.getStatus());
        assertNotNull(response.getReason());
        assertTrue(response.getReason().contains("Insufficient stock for Mechanical Keyboard (P200)"));

        // Critical requirement: P100 stock MUST remain untouched at 25 (all-or-nothing rollback)
        assertEquals(25, inventoryRepository.findById("P100").orElseThrow().getStock());
        assertEquals(10, inventoryRepository.findById("P200").orElseThrow().getStock());

        // Verify order saved as REJECTED
        Order savedOrder = orderRepository.findById(response.getOrderId()).orElseThrow();
        assertEquals(OrderStatus.REJECTED, savedOrder.getStatus());

        // Verify Notification logged rejection
        List<Notification> notifications = notificationRepository.findAll();
        assertTrue(notifications.stream().anyMatch(n -> n.getMessage().contains("rejected")));
    }

    @Test
    @DisplayName("Order Cancellation & Restock: Restores stock and changes order status to CANCELLED")
    void testCancelOrderAndRestock() {
        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest("P100", 4),
                new OrderItemRequest("P200", 2)
        ));
        OrderResponse response = orderService.placeOrder(request);
        String orderId = response.getOrderId();

        assertEquals(21, inventoryRepository.findById("P100").orElseThrow().getStock());
        assertEquals(8, inventoryRepository.findById("P200").orElseThrow().getStock());

        // Cancel order
        OrderResponse cancelResponse = orderService.cancelOrder(orderId);
        assertEquals(OrderStatus.CANCELLED, cancelResponse.getStatus());

        // Verify stock restocked to original values
        assertEquals(25, inventoryRepository.findById("P100").orElseThrow().getStock());
        assertEquals(10, inventoryRepository.findById("P200").orElseThrow().getStock());

        // Verify order in database is CANCELLED
        Order savedOrder = orderRepository.findById(orderId).orElseThrow();
        assertEquals(OrderStatus.CANCELLED, savedOrder.getStatus());

        // Verify cancellation notification logged
        List<Notification> notifications = notificationRepository.findAll();
        assertTrue(notifications.stream().anyMatch(n -> n.getMessage().contains("cancelled")));
    }

    @Test
    @DisplayName("Order Cancellation Error Handling: 404 for missing order, 409 for duplicate cancel")
    void testCancelOrderErrorCases() {
        assertThrows(ResponseStatusException.class, () -> orderService.cancelOrder("ORD-UNKNOWN"));

        OrderRequest request = new OrderRequest("P100", 2);
        OrderResponse response = orderService.placeOrder(request);
        orderService.cancelOrder(response.getOrderId());

        // Second cancel attempt throws conflict
        assertThrows(ResponseStatusException.class, () -> orderService.cancelOrder(response.getOrderId()));
    }

    @Test
    @DisplayName("Low-Stock Auto-Reorder Rule: Publishes LowStockEvent when remaining stock <= threshold (5)")
    void testLowStockAlertTriggered() {
        // Reserve 22 of P100 (remaining stock = 3 <= 5)
        OrderRequest request = new OrderRequest("P100", 22);
        orderService.placeOrder(request);

        assertEquals(3, inventoryRepository.findById("P100").orElseThrow().getStock());

        List<Notification> notifications = notificationRepository.findAll();
        assertTrue(notifications.stream().anyMatch(n -> n.getMessage().contains("reorder needed")),
                "Expected reorder needed notification to be recorded.");
    }
}

