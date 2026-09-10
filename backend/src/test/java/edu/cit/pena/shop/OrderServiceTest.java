package edu.cit.pena.shop;

import edu.cit.pena.inventory.InventoryItem;
import edu.cit.pena.inventory.InventoryService;
import edu.cit.pena.inventory.InventoryTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
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
    @DisplayName("Should confirm order and decrease inventory when stock is sufficient")
    void testPlaceOrderConfirmed() {
        InventoryItem beforeItem = inventoryService.getItem("P100");
        assertNotNull(beforeItem);
        int stockBefore = beforeItem.getStock();
        assertEquals(25, stockBefore);

        OrderRequest request = new OrderRequest("P100", 2);
        OrderResponse response = orderService.placeOrder(request);

        assertEquals("CONFIRMED", response.status());
        assertNotNull(response.reason());
        assertNotNull(response.inventory());
        assertEquals("P100", response.inventory().productId());
        assertEquals(23, response.inventory().stock());

        // Verify order persisted in database
        List<Order> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        Order latestOrder = orders.get(0);
        assertEquals("P100", latestOrder.getProductId());
        assertEquals(2, latestOrder.getQuantity());
        assertEquals("CONFIRMED", latestOrder.getStatus());
    }

    @Test
    @DisplayName("Should reject order when stock is insufficient (P300 with 0 stock)")
    void testPlaceOrderRejected() {
        OrderRequest request = new OrderRequest("P300", 1);
        OrderResponse response = orderService.placeOrder(request);

        assertEquals("REJECTED", response.status());
        assertTrue(response.reason().toLowerCase().contains("insufficient"));
        assertNotNull(response.inventory());
        assertEquals("P300", response.inventory().productId());
        assertEquals(0, response.inventory().stock());

        // Verify rejected order is also logged in database
        List<Order> orders = orderRepository.findAll();
        assertEquals(1, orders.size());
        Order latestOrder = orders.get(0);
        assertEquals("REJECTED", latestOrder.getStatus());
    }
}
