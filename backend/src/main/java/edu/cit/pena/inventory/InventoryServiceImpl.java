package edu.cit.pena.inventory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.pena.inventory.dto.InventoryItemDto;
import edu.cit.pena.inventory.dto.ReservationResult;
import edu.cit.pena.inventory.event.LowStockEvent;

/**
 * Package-private implementation of InventoryService.
 * Enforces architectural boundary: outside packages (such as shop) can only
 * access the public InventoryService interface.
 */
@Service
@Transactional
class InventoryServiceImpl implements InventoryService {

    private static final int DEFAULT_LOW_STOCK_THRESHOLD = 5;

    private final InventoryRepository inventoryRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${inventory.low-stock-threshold:5}")
    private int lowStockThreshold = DEFAULT_LOW_STOCK_THRESHOLD;

    InventoryServiceImpl(InventoryRepository inventoryRepository, ApplicationEventPublisher eventPublisher) {
        this.inventoryRepository = inventoryRepository;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryItemDto getItem(String productId) {
        return inventoryRepository.findById(productId)
                .map(this::toDto)
                .orElse(null);
    }

    @Override
    public ReservationResult reserve(String productId, int quantity) {
        if (quantity <= 0) {
            return ReservationResult.builder()
                    .success(false)
                    .message("Quantity must be greater than zero.")
                    .remainingStock(getItemStockOrZero(productId))
                    .build();
        }

        Optional<InventoryItem> optionalItem = inventoryRepository.findByIdForUpdate(productId);
        if (optionalItem.isEmpty()) {
            return ReservationResult.builder()
                    .success(false)
                    .message(String.format("Product with ID '%s' not found.", productId))
                    .remainingStock(0)
                    .build();
        }

        InventoryItem item = optionalItem.get();
        if (item.getStock() < quantity) {
            return ReservationResult.builder()
                    .success(false)
                    .message(String.format("Insufficient stock for %s (%s): requested %d, available %d",
                            item.getName(), productId, quantity, item.getStock()))
                    .remainingStock(item.getStock())
                    .build();
        }

        // Deduct stock and persist
        int newStock = item.getStock() - quantity;
        item.setStock(newStock);
        inventoryRepository.save(item);

        // Check low-stock threshold rule and publish event
        if (newStock <= lowStockThreshold) {
            eventPublisher.publishEvent(new LowStockEvent(
                    item.getProductId(),
                    item.getName(),
                    newStock,
                    lowStockThreshold,
                    LocalDateTime.now()
            ));
        }

        return ReservationResult.builder()
                .success(true)
                .message(String.format("Successfully reserved %d unit(s) of %s (%s).",
                        quantity, item.getName(), productId))
                .remainingStock(item.getStock())
                .build();
    }

    @Override
    public void restock(String productId, int quantity) {
        if (quantity <= 0) {
            return;
        }

        inventoryRepository.findById(productId).ifPresent(item -> {
            item.setStock(item.getStock() + quantity);
            inventoryRepository.save(item);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryItemDto> getAllItems() {
        return inventoryRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    private InventoryItemDto toDto(InventoryItem item) {
        return new InventoryItemDto(item.getProductId(), item.getName(), item.getStock());
    }

    private int getItemStockOrZero(String productId) {
        return inventoryRepository.findById(productId)
                .map(InventoryItem::getStock)
                .orElse(0);
    }
}

