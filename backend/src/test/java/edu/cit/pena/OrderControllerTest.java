package edu.cit.pena;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.pena.inventory.InventoryItem;
import edu.cit.pena.inventory.InventoryRepository;
import edu.cit.pena.notification.NotificationRepository;
import edu.cit.pena.shop.OrderRepository;
import edu.cit.pena.shop.dto.OrderItemRequest;
import edu.cit.pena.shop.dto.OrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

        inventoryRepository.saveAll(List.of(
                new InventoryItem("P100", "Wireless Mouse", 25),
                new InventoryItem("P200", "Mechanical Keyboard", 10),
                new InventoryItem("P300", "USB-C Hub", 0)
        ));
    }

    @Test
    @DisplayName("REST POST /api/orders -> Multi-item 200 OK CONFIRMED")
    void testPostMultiItemOrderConfirmed() throws Exception {
        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest("P100", 3),
                new OrderItemRequest("P200", 2)
        ));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.status", is("CONFIRMED")))
                .andExpect(jsonPath("$.reason", nullValue()))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].productId", is("P100")))
                .andExpect(jsonPath("$.items[0].outcome", is("RESERVED")))
                .andExpect(jsonPath("$.items[1].productId", is("P200")))
                .andExpect(jsonPath("$.items[1].outcome", is("RESERVED")));

        assertEquals(22, inventoryRepository.findById("P100").orElseThrow().getStock());
        assertEquals(8, inventoryRepository.findById("P200").orElseThrow().getStock());
    }

    @Test
    @DisplayName("REST POST /api/orders -> Multi-item REJECTED (all-or-nothing rollback)")
    void testPostMultiItemOrderRejectedAllOrNothing() throws Exception {
        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest("P100", 2),
                new OrderItemRequest("P300", 1) // P300 has 0 stock -> entire order rejected
        ));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId", notNullValue()))
                .andExpect(jsonPath("$.status", is("REJECTED")))
                .andExpect(jsonPath("$.reason", containsString("Insufficient stock")));

        // Verify P100 stock was untouched
        assertEquals(25, inventoryRepository.findById("P100").orElseThrow().getStock());
        assertEquals(0, inventoryRepository.findById("P300").orElseThrow().getStock());
    }

    @Test
    @DisplayName("REST POST /api/orders/{orderId}/cancel -> Cancels order and restocks inventory")
    void testCancelOrderSuccess() throws Exception {
        // First place order
        OrderRequest request = new OrderRequest(List.of(
                new OrderItemRequest("P100", 5),
                new OrderItemRequest("P200", 3)
        ));

        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        String orderId = objectMapper.readTree(responseBody).get("orderId").asText();

        // Verify stock deducted
        assertEquals(20, inventoryRepository.findById("P100").orElseThrow().getStock());
        assertEquals(7, inventoryRepository.findById("P200").orElseThrow().getStock());

        // Cancel order
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")))
                .andExpect(jsonPath("$.orderId", is(orderId)));

        // Verify stock restored
        assertEquals(25, inventoryRepository.findById("P100").orElseThrow().getStock());
        assertEquals(10, inventoryRepository.findById("P200").orElseThrow().getStock());
    }

    @Test
    @DisplayName("REST POST /api/orders/{orderId}/cancel -> 404 NOT FOUND for nonexistent order")
    void testCancelOrderNotFound() throws Exception {
        mockMvc.perform(post("/api/orders/ORD-NONEXISTENT/cancel"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("REST POST /api/orders/{orderId}/cancel -> 409 CONFLICT for already cancelled order")
    void testCancelOrderDuplicateConflict() throws Exception {
        OrderRequest request = new OrderRequest("P100", 2);
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String orderId = objectMapper.readTree(result.getResponse().getContentAsString()).get("orderId").asText();

        // First cancel succeeds
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isOk());

        // Second cancel returns 409 Conflict
        mockMvc.perform(post("/api/orders/" + orderId + "/cancel"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("REST GET /api/inventory -> Returns all seeded items")
    void testGetInventory() throws Exception {
        mockMvc.perform(get("/api/inventory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].productId", notNullValue()))
                .andExpect(jsonPath("$[0].name", notNullValue()));
    }

    @Test
    @DisplayName("REST GET /api/orders -> Returns order history")
    void testGetOrders() throws Exception {
        OrderRequest request = new OrderRequest("P100", 2);
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].orderId", notNullValue()))
                .andExpect(jsonPath("$[0].items", hasSize(1)));
    }

    @Test
    @DisplayName("REST GET /api/notifications -> Returns notification log including low stock")
    void testGetNotifications() throws Exception {
        // Place order for 21 units of P100 (initial stock 25 -> drops to 4 <= 5, triggering LowStockEvent)
        OrderRequest request = new OrderRequest("P100", 21);
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(2))))
                .andExpect(jsonPath("$[*].message", hasItem(containsString("confirmed"))))
                .andExpect(jsonPath("$[*].message", hasItem(containsString("reorder needed"))));
    }
}

