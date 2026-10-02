package edu.cit.pena.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

@Repository
public interface InventoryRepository extends JpaRepository<InventoryItem, String> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select item from InventoryItem item where item.productId = :productId")
	java.util.Optional<InventoryItem> findByIdForUpdate(@Param("productId") String productId);
}
