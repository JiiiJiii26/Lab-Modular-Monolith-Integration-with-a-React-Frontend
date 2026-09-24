package edu.cit.pena.shop;

import edu.cit.pena.inventory.InventoryItem;
import edu.cit.pena.inventory.InventoryService;
import edu.cit.pena.inventory.InventoryTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryTestFixture inventoryTestFixture;

    @BeforeEach
    void setUp() {
        inventoryTestFixture.resetData();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("Should confirm multi-item order and decrease inventory when all items have sufficient stock")
    void testPlaceMultiItemOrderConfirmed() {
        // Initial inventory: P100=25, P200=10
        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest("P100", 2),
                new OrderItemRequest("P200", 1)
        ));

        OrderResponse response = orderService.placeOrder(request);

        assertEquals("CONFIRMED", response.status());
        assertNotNull(response.reason());
        assertEquals(2, response.items().size());
        assertEquals("RESERVED", response.items().get(0).outcome());
        assertEquals("RESERVED", response.items().get(1).outcome());

        // Check inventory decreased
        assertEquals(23, inventoryService.getItem("P100").getStock());
        assertEquals(9, inventoryService.getItem("P200").getStock());

        // Verify order persisted in database with order_items rows
        List<Order> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        Order savedOrder = orders.get(0);
        assertEquals("CONFIRMED", savedOrder.getStatus());
        assertEquals(2, savedOrder.getItems().size());
    }

    @Test
    @DisplayName("Should reject multi-item order if ANY item exceeds stock and write NO order_items rows")
    void testPlaceMultiItemOrderRejected() {
        // P100=25 (sufficient), P300=0 (insufficient)
        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest("P100", 1),
                new OrderItemRequest("P300", 1)
        ));

        OrderResponse response = orderService.placeOrder(request);

        assertEquals("REJECTED", response.status());
        assertTrue(response.reason().toLowerCase().contains("insufficient"));
        assertEquals(2, response.items().size());
        assertEquals("REJECTED", response.items().get(0).outcome());
        assertEquals("REJECTED", response.items().get(1).outcome());

        // Critical all-or-nothing check: P100 stock must NOT be decremented!
        assertEquals(25, inventoryService.getItem("P100").getStock());
        assertEquals(0, inventoryService.getItem("P300").getStock());

        // Critical rule: REJECTED order persisted but NO order_items rows written
        List<Order> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        Order rejectedOrder = orders.get(0);
        assertEquals("REJECTED", rejectedOrder.getStatus());
        assertTrue(rejectedOrder.getItems().isEmpty(), "Rejected order must have NO order_items rows persisted");
    }

    @Test
    @DisplayName("Should cancel order, restock inventory, and return 409 if cancelled again")
    void testCancelOrderAndRestock() {
        // Place an initial order of 5 units of P200 (starts at 10 -> drops to 5)
        OrderRequest request = new OrderRequest(List.of(new OrderItemRequest("P200", 5)));
        OrderResponse placeResponse = orderService.placeOrder(request);
        assertEquals("CONFIRMED", placeResponse.status());
        assertEquals(5, inventoryService.getItem("P200").getStock());

        List<Order> orders = orderRepository.findAll();
        Long orderId = orders.get(0).getOrderId();

        // Cancel order
        CancelOrderResult cancelResult = orderService.cancelOrder(orderId);
        assertEquals(CancelOrderResult.Status.SUCCESS, cancelResult.status());
        assertEquals("CANCELLED", cancelResult.order().status());

        // Verify stock restocked back to 10
        assertEquals(10, inventoryService.getItem("P200").getStock());

        // Attempt to cancel again -> must throw ResponseStatusException CONFLICT (409)
        ResponseStatusException conflictEx = assertThrows(ResponseStatusException.class, () -> orderService.cancelOrder(orderId));
        assertEquals(HttpStatus.CONFLICT, conflictEx.getStatusCode());

        // Non-existent order cancellation -> NOT_FOUND (404)
        ResponseStatusException notFoundEx = assertThrows(ResponseStatusException.class, () -> orderService.cancelOrder(9999L));
        assertEquals(HttpStatus.NOT_FOUND, notFoundEx.getStatusCode());
    }
}
