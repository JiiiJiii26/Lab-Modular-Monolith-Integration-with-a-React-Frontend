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
    @DisplayName("POST /api/orders should return CONFIRMED for valid order")
    void testPlaceOrderConfirmed() throws Exception {
        String jsonPayload = """
                {
                    "productId": "P100",
                    "quantity": 1
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.inventory.productId").value("P100"))
                .andExpect(jsonPath("$.inventory.stock").value(24));
    }

    @Test
    @DisplayName("POST /api/orders should return REJECTED when exceeding stock")
    void testPlaceOrderRejected() throws Exception {
        String jsonPayload = """
                {
                    "productId": "P300",
                    "quantity": 5
                }
                """;

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.inventory.productId").value("P300"))
                .andExpect(jsonPath("$.inventory.stock").value(0));
    }
}
