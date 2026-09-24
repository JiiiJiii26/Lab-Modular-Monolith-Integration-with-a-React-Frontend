package edu.cit.pena.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Package-private repository interface for inventory data access.
 * Keeping this interface package-private strictly prevents other modules (such as shop)
 * from bypassing the InventoryService boundary and querying or mutating stock directly.
 */
@Repository
interface InventoryRepository extends JpaRepository<InventoryItem, String> {

    @Modifying
    @Query("UPDATE InventoryItem i SET i.stock = i.stock + :units WHERE i.productId = :productId")
    int incrementStock(@Param("productId") String productId, @Param("units") int units);
}
