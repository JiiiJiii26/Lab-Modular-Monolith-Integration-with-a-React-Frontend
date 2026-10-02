package edu.cit.pena.channel;

import edu.cit.pena.inventory.InventoryService;
import edu.cit.pena.inventory.dto.InventoryItemDto;
import edu.cit.pena.shop.OrderService;
import edu.cit.pena.shop.OrderStatus;
import edu.cit.pena.supplier.SupplierGateway;
import edu.cit.pena.supplier.event.SupplierOrderDeliveredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ChannelIntegrationTest {

    @Autowired
    private ChannelGatewayImpl channelGateway;

    @Autowired
    private TianggeOrderProcessor orderProcessor;

    @Autowired
    private ChannelOrderRepository channelOrderRepository;

    @Autowired
    private FeedCursorRepository feedCursorRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private OrderService orderService;

    @MockBean
    private TianggeClient httpClient;

    @BeforeEach
    void setUp() {
        channelGateway.setLive(true);
        channelOrderRepository.deleteAll();
        feedCursorRepository.deleteAll();
    }

    @Test
    @DisplayName("Task 1: ChannelGateway generates non-null UUID instance ID")
    void testInstanceIdGeneration() {
        assertNotNull(channelGateway.getInstanceId());
        assertDoesNotThrow(() -> UUID.fromString(channelGateway.getInstanceId()));
    }

    @Test
    @DisplayName("Task 4: Decides ACCEPTED when inventory is sufficient, avoids duplicate on redelivery")
    void testOrderPlacedAcceptedAndDeduplicated() {
        String orderId = "TG-TEST-" + UUID.randomUUID().toString().substring(0, 6);
        Models.FeedLine line = new Models.FeedLine("P100", 2);
        Models.FeedEvent event = new Models.FeedEvent(
                101, "evt_101", "ORDER_PLACED", orderId,
                "2026-10-01T01:06:00.000Z", "2026-10-01T01:07:00.000Z",
                null, null, List.of(line), new Models.FeedBuyer("Test Buyer", "Cebu City")
        );

        int initialStock = inventoryService.getItem("P100").getStock();

        // 1. First delivery of order
        orderProcessor.processOrderPlaced(event);

        verify(httpClient, times(1)).sendDecision(eq(orderId), argThat(d -> "ACCEPTED".equals(d.decision())));

        ChannelOrder savedOrder = channelOrderRepository.findById(orderId).orElse(null);
        assertNotNull(savedOrder);
        assertEquals("ACCEPTED", savedOrder.getStatus());
        assertNotNull(savedOrder.getShopOrderId());

        int stockAfterAccept = inventoryService.getItem("P100").getStock();
        assertEquals(initialStock - 2, stockAfterAccept);

        // 2. Redelivery of identical order (must NOT process again or deduct stock twice)
        orderProcessor.processOrderPlaced(event);

        // Stock must remain unchanged
        assertEquals(stockAfterAccept, inventoryService.getItem("P100").getStock());
        // No second decision sent
        verify(httpClient, times(1)).sendDecision(eq(orderId), any());
    }

    @Test
    @DisplayName("Task 5: Customer cancellation restocks inventory and confirms to Tiangge")
    void testOrderCancelledRestocksAndConfirms() {
        String orderId = "TG-CANCEL-" + UUID.randomUUID().toString().substring(0, 6);
        Models.FeedLine line = new Models.FeedLine("P100", 3);
        Models.FeedEvent placeEvent = new Models.FeedEvent(
                102, "evt_102", "ORDER_PLACED", orderId,
                "2026-10-01T01:06:00.000Z", "2026-10-01T01:07:00.000Z",
                null, null, List.of(line), new Models.FeedBuyer("Test Buyer", "Manila")
        );

        int initialStock = inventoryService.getItem("P100").getStock();
        orderProcessor.processOrderPlaced(placeEvent);
        assertEquals(initialStock - 3, inventoryService.getItem("P100").getStock());

        Models.FeedEvent cancelEvent = new Models.FeedEvent(
                103, "evt_103", "ORDER_CANCELLED", orderId,
                null, null, "2026-10-01T01:06:20.000Z", "2026-10-01T01:07:20.000Z",
                null, null
        );

        orderProcessor.processOrderCancelled(cancelEvent);

        verify(httpClient, times(1)).sendCancellationConfirmation(eq(orderId), any());
        assertEquals(initialStock, inventoryService.getItem("P100").getStock());

        ChannelOrder order = channelOrderRepository.findById(orderId).orElse(null);
        assertNotNull(order);
        assertEquals("CANCELLED_BY_CUSTOMER", order.getStatus());
    }

    @Test
    @DisplayName("Task 6: Backorders when stock is 0 and restock in-flight, resolves ACCEPTED on delivery")
    void testBackorderAndResolveOnDelivery() {
        // P300 starts with 0 stock in seeder
        InventoryItemDto p300 = inventoryService.getItem("P300");
        assertEquals(0, p300.getStock());

        String orderId = "TG-BO-" + UUID.randomUUID().toString().substring(0, 6);
        Models.FeedLine line = new Models.FeedLine("P300", 5);
        Models.FeedEvent event = new Models.FeedEvent(
                104, "evt_104", "ORDER_PLACED", orderId,
                "2026-10-01T01:06:00.000Z", "2026-10-01T01:07:00.000Z",
                null, null, List.of(line), new Models.FeedBuyer("Backorder Buyer", "Davao")
        );

        orderProcessor.processOrderPlaced(event);

        verify(httpClient, times(1)).sendDecision(eq(orderId), argThat(d -> "BACKORDERED".equals(d.decision())));
        ChannelOrder boOrder = channelOrderRepository.findById(orderId).orElse(null);
        assertNotNull(boOrder);
        assertEquals("BACKORDERED", boOrder.getStatus());

        // Now simulate delivery from LegacySupply: stock arrives
        inventoryService.restock("P300", 10);
        orderProcessor.resolveBackordersOnDelivery(new SupplierOrderDeliveredEvent("PO-TEST-1", "P300", 10, LocalDateTime.now()));

        verify(httpClient, times(1)).sendResolution(eq(orderId), argThat(r -> "ACCEPTED".equals(r.status())));
        ChannelOrder resolvedOrder = channelOrderRepository.findById(orderId).orElse(null);
        assertNotNull(resolvedOrder);
        assertEquals("RESOLVED", resolvedOrder.getStatus());
        assertEquals("ACCEPTED", resolvedOrder.getResolution());
    }

    @Test
    @DisplayName("Task 4: Durable cursor stores sequence numbers without resetting")
    void testCursorDurableStorage() {
        FeedCursor cursor = feedCursorRepository.findById("TIANGGE_FEED_CURSOR")
                .orElseGet(() -> new FeedCursor("TIANGGE_FEED_CURSOR", 0L, LocalDateTime.now()));

        cursor.setCursorValue(75L);
        feedCursorRepository.save(cursor);

        FeedCursor loaded = feedCursorRepository.findById("TIANGGE_FEED_CURSOR").orElse(null);
        assertNotNull(loaded);
        assertEquals(75L, loaded.getCursorValue());
    }
}
