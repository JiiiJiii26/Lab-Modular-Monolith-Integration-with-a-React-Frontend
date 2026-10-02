package edu.cit.pena.channel;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.cit.pena.inventory.InventoryService;
import edu.cit.pena.inventory.dto.InventoryItemDto;
import edu.cit.pena.shop.OrderService;
import edu.cit.pena.shop.OrderStatus;
import edu.cit.pena.shop.dto.OrderItemRequest;
import edu.cit.pena.shop.dto.OrderRequest;
import edu.cit.pena.shop.dto.OrderResponse;
import edu.cit.pena.supplier.SupplierGateway;
import edu.cit.pena.supplier.event.SupplierOrderDeliveredEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Package-private processor for Tiangge orders, cancellations, and backorder resolutions.
 */
@Component
class TianggeOrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(TianggeOrderProcessor.class);

    static final ThreadLocal<Boolean> CHANNEL_PROCESSING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final SupplierGateway supplierGateway;
    private final TianggeClient httpClient;
    private final ChannelOrderRepository channelOrderRepository;
    private final TianggeStockSyncListener stockSyncListener;
    private final ObjectMapper objectMapper;
    private final ChannelGateway channelGateway;

    public TianggeOrderProcessor(
            OrderService orderService,
            InventoryService inventoryService,
            SupplierGateway supplierGateway,
            TianggeClient httpClient,
            ChannelOrderRepository channelOrderRepository,
            TianggeStockSyncListener stockSyncListener,
            ObjectMapper objectMapper,
            ChannelGateway channelGateway) {
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.supplierGateway = supplierGateway;
        this.httpClient = httpClient;
        this.channelOrderRepository = channelOrderRepository;
        this.stockSyncListener = stockSyncListener;
        this.objectMapper = objectMapper;
        this.channelGateway = channelGateway;
    }

    public synchronized void processOrderPlaced(Models.FeedEvent event) {
        String tianggeOrderId = event.orderId();
        Optional<ChannelOrder> existingOrder = channelOrderRepository.findById(tianggeOrderId);
        if (existingOrder.isPresent()) {
            ChannelOrder order = existingOrder.get();
            if ("ACCEPTED".equals(order.getStatus()) || "RESOLVED".equals(order.getStatus())) {
                // The decision may have succeeded while the stock publication failed.
                // A replay must repair the stock side effect without placing again.
                stockSyncListener.publishCurrentStock();
            }
            log.info("Order {} already processed as {}. Skipping duplicate order placement.",
                    tianggeOrderId, order.getStatus());
            return;
        }

        List<Models.FeedLine> lines = event.lines() != null ? event.lines() : List.of();
        String linesJson = serializeLines(lines);
        LocalDateTime placedAt = parseIsoDate(event.placedAt());
        LocalDateTime decisionDeadline = parseIsoDate(event.decisionDeadline());

        log.info("Processing ORDER_PLACED from Tiangge: {} with {} lines", tianggeOrderId, lines.size());

        // Aggregate quantities by product ID
        Map<String, Integer> lineTotals = new HashMap<>();
        for (Models.FeedLine line : lines) {
            lineTotals.merge(line.sellerSku(), line.qty(), Integer::sum);
        }

        // Check if all lines are currently in stock
        boolean allInStock = true;
        for (Map.Entry<String, Integer> entry : lineTotals.entrySet()) {
            InventoryItemDto item = inventoryService.getItem(entry.getKey());
            if (item == null || item.getStock() < entry.getValue()) {
                allInStock = false;
                break;
            }
        }

        List<OrderItemRequest> itemRequests = lines.stream()
                .map(l -> new OrderItemRequest(l.sellerSku(), l.qty()))
                .toList();

        if (allInStock) {
            boolean placed = false;
            String shopOrderId = null;

            CHANNEL_PROCESSING.set(true);
            try {
                OrderResponse orderResponse = orderService.placeOrder(new OrderRequest(itemRequests));
                if (orderResponse.getStatus() == OrderStatus.CONFIRMED) {
                    placed = true;
                    shopOrderId = orderResponse.getOrderId();
                }
            } catch (Exception ex) {
                log.warn("Failed placing confirmed order in OrderService for {}: {}", tianggeOrderId, ex.getMessage());
            } finally {
                CHANNEL_PROCESSING.set(false);
            }

            if (placed) {
                // 1. Send decision ACCEPTED to Tiangge first
                httpClient.sendDecision(tianggeOrderId, new Models.DecisionRequest("ACCEPTED", shopOrderId, null));

                ChannelOrder channelOrder = new ChannelOrder(
                        tianggeOrderId, shopOrderId, "ACCEPTED", "ACCEPTED",
                        linesJson, placedAt, decisionDeadline
                );
                channelOrderRepository.save(channelOrder);
                log.info("Decided Tiangge order {} as ACCEPTED (shopOrderId: {})", tianggeOrderId, shopOrderId);

                // 2. Publish updated stock immediately AFTER decision is accepted by Tiangge
                stockSyncListener.publishCurrentStock();
                return;
            }
        }

        // Check if backorder is possible (missing items have restock in-flight or triggerable)
        boolean canBackorder = true;
        for (Map.Entry<String, Integer> entry : lineTotals.entrySet()) {
            String sku = entry.getKey();
            int needed = entry.getValue();
            InventoryItemDto item = inventoryService.getItem(sku);
            int currentStock = item != null ? item.getStock() : 0;
            if (currentStock < needed) {
                boolean inFlight = supplierGateway.hasInFlightOrder(sku);
                if (!inFlight) {
                    try {
                        log.info("Stock missing for {}. Placing replenishment order to LegacySupply...", sku);
                        supplierGateway.orderReplenishment(sku, Math.max(needed, 20));
                        inFlight = supplierGateway.hasInFlightOrder(sku);
                    } catch (Exception ex) {
                        log.warn("Failed to place replenishment order for {}: {}", sku, ex.getMessage());
                        inFlight = false;
                    }
                }
                if (!inFlight) {
                    canBackorder = false;
                    break;
                }
            }
        }

        if (canBackorder) {
            String shopOrderId = "ORD-BO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            httpClient.sendDecision(tianggeOrderId, new Models.DecisionRequest("BACKORDERED", shopOrderId, "Supplier replenishment order in-flight"));

            ChannelOrder channelOrder = new ChannelOrder(
                    tianggeOrderId, shopOrderId, "BACKORDERED", "BACKORDERED",
                    linesJson, placedAt, decisionDeadline
            );
            channelOrderRepository.save(channelOrder);
            log.info("Decided Tiangge order {} as BACKORDERED (assigned shopOrderId: {})", tianggeOrderId, shopOrderId);
        } else {
            String shopOrderId = null;
            OrderResponse orderResponse = null;
            CHANNEL_PROCESSING.set(true);
            try {
                orderResponse = orderService.placeOrder(new OrderRequest(itemRequests));
                shopOrderId = orderResponse.getOrderId();
            } catch (Exception ex) {
                log.warn("Failed recording rejected order for {}: {}", tianggeOrderId, ex.getMessage());
            } finally {
                CHANNEL_PROCESSING.set(false);
            }

            boolean accepted = orderResponse != null && orderResponse.getStatus() == OrderStatus.CONFIRMED;
            String decision = accepted ? "ACCEPTED" : "REJECTED";
            String reason = accepted ? null : "Insufficient stock and no incoming supplier shipment";
            httpClient.sendDecision(tianggeOrderId, new Models.DecisionRequest(decision, shopOrderId, reason));

            ChannelOrder channelOrder = new ChannelOrder(
                    tianggeOrderId, shopOrderId, decision, decision,
                    linesJson, placedAt, decisionDeadline
            );
            channelOrderRepository.save(channelOrder);
            if (accepted) {
                stockSyncListener.publishCurrentStock();
            }
            log.info("Decided Tiangge order {} as {} (shopOrderId: {})", tianggeOrderId, decision, shopOrderId);
        }
    }

    public synchronized void processOrderCancelled(Models.FeedEvent event) {
        String tianggeOrderId = event.orderId();
        log.info("Processing ORDER_CANCELLED from Tiangge: {}", tianggeOrderId);

        Optional<ChannelOrder> optionalOrder = channelOrderRepository.findById(tianggeOrderId);
        if (optionalOrder.isPresent()) {
            ChannelOrder order = optionalOrder.get();
            if ("CANCELLED_BY_CUSTOMER".equals(order.getStatus())) {
                log.info("Tiangge order {} was already confirmed cancelled. Resending confirmation.", tianggeOrderId);
                httpClient.sendCancellationConfirmation(tianggeOrderId, new Models.CancellationRequest(true));
                return;
            }

            if (order.getShopOrderId() != null && ("ACCEPTED".equals(order.getStatus()) || "RESOLVED".equals(order.getStatus()))) {
                try {
                    log.info("Executing cancellation in Order module for shopOrderId: {}", order.getShopOrderId());
                    CHANNEL_PROCESSING.set(true);
                    try {
                        orderService.cancelOrder(order.getShopOrderId());
                    } finally {
                        CHANNEL_PROCESSING.set(false);
                    }
                } catch (Exception ex) {
                    throw new IllegalStateException(
                            "Unable to restock cancelled order " + order.getShopOrderId(), ex);
                }
            }

            order.setStatus("CANCELLED_BY_CUSTOMER");
            order.setUpdatedAt(LocalDateTime.now());
            channelOrderRepository.save(order);
        }

        // 1. Confirm cancellation to Tiangge first
        httpClient.sendCancellationConfirmation(tianggeOrderId, new Models.CancellationRequest(true));
        log.info("Confirmed cancellation for Tiangge order {}", tianggeOrderId);

        // 2. Publish stock immediately after confirmation
        stockSyncListener.publishCurrentStock();
    }

    @EventListener
    @Order(2)
    public synchronized void resolveBackordersOnDelivery(SupplierOrderDeliveredEvent event) {
        log.info("Supplier order delivered (PO: {}, product: {}, units: {}). Resolving pending backorders...",
                event.getPoNumber(), event.getProductId(), event.getUnitsRestocked());
        resolvePendingBackorders();
    }

    @Scheduled(initialDelay = 10000, fixedDelay = 3000)
    public synchronized void pollAndResolveBackorders() {
        resolvePendingBackorders();
    }

    public synchronized void resolvePendingBackorders() {
        if (!channelGateway.isLive()) {
            return;
        }
        List<ChannelOrder> backorders = channelOrderRepository.findByStatusOrderByPlacedAtAsc("BACKORDERED");
        if (backorders.isEmpty()) {
            return;
        }

        for (ChannelOrder backorder : backorders) {
            List<Models.FeedLine> lines = deserializeLines(backorder.getLinesJson());
            if (lines.isEmpty()) continue;

            Map<String, Integer> lineTotals = new HashMap<>();
            for (Models.FeedLine line : lines) {
                lineTotals.merge(line.sellerSku(), line.qty(), Integer::sum);
            }

            boolean canFulfill = true;
            for (Map.Entry<String, Integer> entry : lineTotals.entrySet()) {
                InventoryItemDto item = inventoryService.getItem(entry.getKey());
                if (item == null || item.getStock() < entry.getValue()) {
                    canFulfill = false;
                    break;
                }
            }

            if (canFulfill) {
                List<OrderItemRequest> itemRequests = lines.stream()
                        .map(l -> new OrderItemRequest(l.sellerSku(), l.qty()))
                        .toList();

                boolean placed = false;
                CHANNEL_PROCESSING.set(true);
                try {
                    OrderRequest orderRequest = new OrderRequest(backorder.getShopOrderId(), itemRequests);
                    OrderResponse orderResponse = orderService.placeOrder(orderRequest);
                    if (orderResponse.getStatus() == OrderStatus.CONFIRMED) {
                        placed = true;
                    }
                } catch (Exception ex) {
                    log.warn("Error placing confirmed order for backorder {}: {}", backorder.getTianggeOrderId(), ex.getMessage());
                } finally {
                    CHANNEL_PROCESSING.set(false);
                }

                if (placed) {
                    log.info("Reserving stock for backordered Tiangge order {} (shopOrderId: {})",
                            backorder.getTianggeOrderId(), backorder.getShopOrderId());
                    // 1. Send resolution ACCEPTED first
                    httpClient.sendResolution(backorder.getTianggeOrderId(), new Models.ResolutionRequest("ACCEPTED"));

                    backorder.setStatus("RESOLVED");
                    backorder.setResolution("ACCEPTED");
                    backorder.setUpdatedAt(LocalDateTime.now());
                    channelOrderRepository.save(backorder);
                    log.info("Successfully resolved backordered Tiangge order {} to ACCEPTED", backorder.getTianggeOrderId());

                    // 2. Publish stock immediately after resolution
                    stockSyncListener.publishCurrentStock();
                }
            } else {
                // If not fulfillable yet, verify replenishment is on the way for missing items
                for (Map.Entry<String, Integer> entry : lineTotals.entrySet()) {
                    String sku = entry.getKey();
                    int needed = entry.getValue();
                    InventoryItemDto item = inventoryService.getItem(sku);
                    int currentStock = item != null ? item.getStock() : 0;
                    if (currentStock < needed && !supplierGateway.hasInFlightOrder(sku)) {
                        try {
                            log.info("Replenishment missing for backorder {}. Placing replenishment for {}...", backorder.getTianggeOrderId(), sku);
                            supplierGateway.orderReplenishment(sku, Math.max(needed, 20));
                        } catch (Exception ex) {
                            log.warn("Failed placing replenishment for {}: {}", sku, ex.getMessage());
                        }
                    }
                }
            }
        }
    }

    private String serializeLines(List<Models.FeedLine> lines) {
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (Exception e) {
            log.error("Failed to serialize order lines", e);
            return "[]";
        }
    }

    private List<Models.FeedLine> deserializeLines(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<Models.FeedLine>>() {});
        } catch (Exception e) {
            log.error("Failed to deserialize order lines: {}", json, e);
            return List.of();
        }
    }

    private LocalDateTime parseIsoDate(String isoString) {
        if (isoString == null || isoString.isBlank()) return null;
        try {
            return LocalDateTime.ofInstant(Instant.parse(isoString), ZoneOffset.UTC);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
