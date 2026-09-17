package edu.cit.pena.inventory;

import edu.cit.pena.shared.events.LowStockEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
 * code outside of the 'edu.cit.pena.inventory' package (such as 'edu.cit.pena.shop' or
 * 'edu.cit.pena.notification') cannot directly instantiate, reference, or cast to this class.
 *
 * External modules are compelled to interact solely with the public 'InventoryService' interface.
 */
@Service
@Transactional
class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final int lowStockThreshold;

    public InventoryServiceImpl(
            InventoryRepository inventoryRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.inventory.low-stock-threshold:5}") int lowStockThreshold) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
        this.lowStockThreshold = lowStockThreshold;
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

        // LowStock rule: emit LowStockEvent at most once per reserve() call if remaining stock < threshold
        if (updatedStock < lowStockThreshold) {
            eventPublisher.publishEvent(new LowStockEvent(
                    item.getProductId(),
                    item.getName(),
                    updatedStock,
                    lowStockThreshold
            ));
        }

        return ReservationResult.success(
                "Successfully reserved " + quantity + " unit(s) of " + item.getName() + ".",
                updatedStock,
                item
        );
    }

    @Override
    public RestockResult restock(String productId, int quantity) {
        if (quantity <= 0) {
            return RestockResult.failure(
                    "Invalid restock quantity: " + quantity + ". Quantity must be positive.",
                    0,
                    null
            );
        }

        InventoryItem item = inventoryRepository.findById(productId).orElse(null);
        if (item == null) {
            return RestockResult.failure(
                    "Product not found: " + productId,
                    0,
                    null
            );
        }

        int updatedStock = item.getStock() + quantity;
        item.setStock(updatedStock);
        inventoryRepository.save(item);

        return RestockResult.success(
                "Successfully restocked " + quantity + " unit(s) of " + item.getName() + ".",
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
