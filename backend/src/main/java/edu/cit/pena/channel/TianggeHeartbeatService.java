package edu.cit.pena.channel;

import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically sends a heartbeat to Tiangge marketplace every 30 seconds.
 */
@Component
class TianggeHeartbeatService {

    private static final Logger log = LoggerFactory.getLogger(TianggeHeartbeatService.class);

    private final TianggeClient httpClient;
    private final ChannelGatewayImpl channelGateway;
    private final String appName;
    private final Instant startedAt;

    public TianggeHeartbeatService(
            TianggeClient httpClient,
            ChannelGatewayImpl channelGateway,
            @Value("${spring.application.name:shop-monolith}") String appName) {
        this.httpClient = httpClient;
        this.channelGateway = channelGateway;
        this.appName = appName;
        this.startedAt = Instant.now();
    }

    public synchronized boolean sendHeartbeat() {
        long uptimeSeconds = Duration.between(startedAt, Instant.now()).toSeconds();
        Models.HeartbeatRequest request = new Models.HeartbeatRequest(appName, startedAt.toString(), uptimeSeconds);

        try {
            Models.HeartbeatResponse response = httpClient.sendHeartbeat(request);
            log.info("Heartbeat sent successfully to Tiangge. Instance ID: {}, uptime: {}s, serverTime: {}",
                    channelGateway.getInstanceId(), uptimeSeconds, response != null ? response.serverTime() : "ok");
            return true;
        } catch (Exception ex) {
            channelGateway.setLive(false);
            log.warn("Failed sending heartbeat to Tiangge: {}", ex.getMessage());
            return false;
        }
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 30000)
    public void scheduledHeartbeat() {
        sendHeartbeat();
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
