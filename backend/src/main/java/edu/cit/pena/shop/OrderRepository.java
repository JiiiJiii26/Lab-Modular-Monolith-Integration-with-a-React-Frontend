package edu.cit.pena.shop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Order persistence.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {
}
