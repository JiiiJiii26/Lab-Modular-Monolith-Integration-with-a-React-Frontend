package edu.cit.pena.inventory;

import java.util.List;

/**
 * Public service interface defining the contract for inventory operations.
 * External modules (like edu.cit.pena.shop) must interact ONLY through this interface.
 */
public interface InventoryService {

    /**
     * Retrieves an inventory item by its product ID.
     *
     * @param productId product identifier
     * @return InventoryItem if found, or null
     */
    InventoryItem getItem(String productId);

    /**
     * Reserves requested quantity of a product.
     * Decrements stock if sufficient inventory is available, or rejects if stock is insufficient.
     *
     * @param productId product identifier
     * @param quantity  requested quantity
     * @return ReservationResult carrying success/failure flag, reason, and remaining stock
     */
    ReservationResult reserve(String productId, int quantity);

    /**
     * Restocks requested quantity of a product.
     * Increments stock by the given quantity.
     *
     * @param productId product identifier
     * @param quantity  quantity to add back to stock
     * @return RestockResult carrying success/failure flag, reason, and updated stock
     */
    RestockResult restock(String productId, int quantity);

    /**
     * Retrieves all inventory items to populate product catalogs/dropdowns.
     *
     * @return list of all InventoryItems
     */
    List<InventoryItem> getAllItems();
}
