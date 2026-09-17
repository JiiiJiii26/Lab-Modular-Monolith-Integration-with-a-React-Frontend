package edu.cit.pena.notification;

import edu.cit.pena.shared.events.LowStockEvent;
import edu.cit.pena.shared.events.OrderCancelledEvent;
import edu.cit.pena.shared.events.OrderItemDto;
import edu.cit.pena.shared.events.OrderPlacedEvent;
import edu.cit.pena.shared.events.OrderRejectedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @BeforeEach
    void setUp() {
        notificationRepository.deleteAll();
    }

    @Test
    @DisplayName("Should capture and return all kinds of notifications via GET /api/notifications")
    void testNotificationLifecycle() throws Exception {
        // Publish events
        eventPublisher.publishEvent(new OrderPlacedEvent(101L, List.of(new OrderItemDto("P100", 2))));
        eventPublisher.publishEvent(new OrderRejectedEvent(102L, "Insufficient stock", List.of()));
        eventPublisher.publishEvent(new OrderCancelledEvent(101L, List.of(new OrderItemDto("P100", 2))));
        eventPublisher.publishEvent(new LowStockEvent("P200", "Mechanical Keyboard", 3, 5));

        // Verify GET /api/notifications returns 4 notifications
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(4));
    }
}
