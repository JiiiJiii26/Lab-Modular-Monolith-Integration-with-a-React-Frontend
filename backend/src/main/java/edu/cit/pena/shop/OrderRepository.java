package edu.cit.pena.shop;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

import jakarta.persistence.QueryHint;
import java.util.List;

/**
 * Repository interface for Order persistence.
 *
 * Uses an explicit JOIN FETCH query so Hibernate loads all orders and their
 * order_items in a single SQL statement instead of issuing one SELECT per order.
 * This prevents both the N+1 problem and LazyInitializationException when items
 * are accessed outside a Hibernate session.
 *
 * PASS_DISTINCT_THROUGH=false tells Spring Data to apply DISTINCT at the
 * Java level after the JOIN, removing the duplicate Order rows that a
 * JOIN produces when an order has multiple items (without passing
 * DISTINCT into the SQL, which would prevent index usage on large tables).
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items ORDER BY o.createdAt DESC")
    @QueryHints(@QueryHint(name = "hibernate.query.passDistinctThrough", value = "false"))
    List<Order> findAllByOrderByCreatedAtDesc();
}
