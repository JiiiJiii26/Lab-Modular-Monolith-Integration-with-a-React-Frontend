package edu.cit.pena.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Package-private repository interface for notifications.
 * Encapsulated strictly within the edu.cit.pena.notification package.
 */
@Repository
interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findAllByOrderByCreatedAtDesc();
}
