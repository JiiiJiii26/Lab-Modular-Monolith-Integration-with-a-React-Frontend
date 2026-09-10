package edu.cit.pena.inventory;

import org.springframework.stereotype.Component;

/**
 * Test fixture component in the inventory package to reliably reset test data across tests.
 */
@Component
public class InventoryTestFixture {

    private final InventoryRepository inventoryRepository;

    public InventoryTestFixture(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    public void resetData() {
        inventoryRepository.deleteAll();
        inventoryRepository.save(new InventoryItem("P100", "Wireless Mouse", 25));
        inventoryRepository.save(new InventoryItem("P200", "Mechanical Keyboard", 10));
        inventoryRepository.save(new InventoryItem("P300", "USB-C Hub", 0));
    }
}
