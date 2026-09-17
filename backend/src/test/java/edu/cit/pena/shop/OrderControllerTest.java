package edu.cit.pena.shop;

import edu.cit.pena.inventory.InventoryTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private InventoryTestFixture inventoryTestFixture;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        inventoryTestFixture.resetData();
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("GET /api/inventory should return inventory list")
    void testGetInventory() throws Exception {
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("POST /api/orders should return CONFIRMED for valid multi-item order")
    void testPlaceMultiItemOrderConfirmed() throws Exception {
        String jsonPayload = """
                {
                    "items": [
                        { "productId": "P100", "quantity": 2 },
                        { "productId": "P200", "quantity": 1 }
                    ]
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].outcome").value("RESERVED"))
                .andExpect(jsonPath("$.inventory").isArray());
    }

    @Test
    @DisplayName("POST /api/orders should return REJECTED when exceeding stock")
    void testPlaceMultiItemOrderRejected() throws Exception {
        String jsonPayload = """
                {
                    "items": [
                        { "productId": "P100", "quantity": 1 },
                        { "productId": "P300", "quantity": 1 }
                    ]
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.items[0].outcome").value("REJECTED"))
                .andExpect(jsonPath("$.items[1].outcome").value("REJECTED"));
    }

    @Test
    @DisplayName("GET /api/orders should return all orders with line items")
    void testGetOrders() throws Exception {
        // Place one order first
        String jsonPayload = """
                {
                    "items": [
                        { "productId": "P100", "quantity": 1 }
                    ]
                }
                """;
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].items").isArray());
    }

    @Test
    @DisplayName("POST /api/orders/{id}/cancel should cancel order or return 404/409")
    void testCancelOrderEndpoints() throws Exception {
        // 1. Create an order
        String jsonPayload = """
                {
                    "items": [
                        { "productId": "P100", "quantity": 2 }
                    ]
                }
                """;
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk());

        Order saved = orderRepository.findAll().get(0);
        Long orderId = saved.getOrderId();

        // 2. Cancel order -> 200 OK
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 3. Cancel again -> 409 Conflict
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isConflict());

        // 4. Cancel non-existent order -> 404 Not Found
        mockMvc.perform(post("/api/orders/999999/cancel"))
                .andExpect(status().isNotFound());
    }
}
