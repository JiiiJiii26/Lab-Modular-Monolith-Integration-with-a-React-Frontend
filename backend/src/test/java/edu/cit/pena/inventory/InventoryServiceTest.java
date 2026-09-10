package edu.cit.pena.inventory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class InventoryServiceTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryTestFixture inventoryTestFixture;

    @BeforeEach
    void setUp() {
        inventoryTestFixture.resetData();
    }

    @Test
    @DisplayName("Should successfully reserve stock when sufficient inventory is available")
    void testReserveSuccess() {
        ReservationResult result = inventoryService.reserve("P100", 5);

        assertTrue(result.isSuccess());
        assertEquals(20, result.getRemainingStock());
        assertEquals(20, inventoryService.getItem("P100").getStock());
    }

    @Test
    @DisplayName("Should reject reservation when requested quantity exceeds available stock")
    void testReserveInsufficientStock() {
        ReservationResult result = inventoryService.reserve("P200", 15);

        assertFalse(result.isSuccess());
        assertTrue(result.getReason().contains("Insufficient stock"));
        assertEquals(10, result.getRemainingStock());
        assertEquals(10, inventoryService.getItem("P200").getStock());
    }

    @Test
    @DisplayName("Should reject reservation when product stock is 0 (P300)")
    void testReserveZeroStock() {
        ReservationResult result = inventoryService.reserve("P300", 1);

        assertFalse(result.isSuccess());
        assertEquals(0, result.getRemainingStock());
        assertEquals(0, inventoryService.getItem("P300").getStock());
    }

    @Test
    @DisplayName("Should reject reservation for non-existent product")
    void testReserveProductNotFound() {
        ReservationResult result = inventoryService.reserve("P999", 2);

        assertFalse(result.isSuccess());
        assertTrue(result.getReason().contains("Product not found"));
    }
}
