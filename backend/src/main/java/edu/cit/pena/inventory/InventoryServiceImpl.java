package edu.cit.pena.inventory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * ARCHITECTURAL MODULE BOUNDARY ENFORCEMENT:
 *
 * This implementation class is intentionally PACKAGE-PRIVATE (default visibility, no 'public' modifier).
 *
 * In a modular monolith architecture, logical boundaries between modules must be strictly enforced
 * at compile time rather than merely by convention. By declaring InventoryServiceImpl package-private,
 * code outside of the 'edu.cit.pena.inventory' package (such as 'edu.cit.pena.shop') cannot directly
 * instantiate, reference, or cast to this implementation class.
 *
 * External modules are compelled to interact solely with the public 'InventoryService' interface.
 * This guarantees loose coupling, prevents leaky abstractions (e.g. leaking JPA transaction mechanics
 * or internal repository access), and makes it trivial to replace or extract this module into an
 * independent microservice in the future without breaking callers.
 */
@Service
@Transactional
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;

    public InventoryServiceImpl(InventoryRepository inventoryRepository) {
        this.inventoryRepository = inventoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItem getItem(String productId) {
        return inventoryRepository.findById(productId).orElse(null);
    }

    @Override
    public ReservationResult reserve(String productId, int quantity) {
        if (quantity <= 0) {
            return ReservationResult.failure(
                    "Invalid reservation quantity: " + quantity + ". Quantity must be positive.",
                    0,
                    null
            );
        }

        InventoryItem item = inventoryRepository.findById(productId).orElse(null);
        if (item == null) {
            return ReservationResult.failure(
                    "Product not found: " + productId,
                    0,
                    null
            );
        }

        if (item.getStock() < quantity) {
            return ReservationResult.failure(
                    "Insufficient stock for " + item.getName() + " (requested: " + quantity + ", available: " + item.getStock() + ").",
                    item.getStock(),
                    item
            );
        }

        // Decrement stock and persist update
        int updatedStock = item.getStock() - quantity;
        item.setStock(updatedStock);
        inventoryRepository.save(item);

        return ReservationResult.success(
                "Successfully reserved " + quantity + " unit(s) of " + item.getName() + ".",
                updatedStock,
                item
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItem> getAllItems() {
        return inventoryRepository.findAll();
    }
}
