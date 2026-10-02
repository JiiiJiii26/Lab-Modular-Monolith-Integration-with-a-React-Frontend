package edu.cit.pena.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class ChannelGatewayImpl implements ChannelGateway {

    private static final Logger log = LoggerFactory.getLogger(ChannelGatewayImpl.class);

    private final String instanceId;
    private volatile boolean live;

    public ChannelGatewayImpl() {
        this.instanceId = UUID.randomUUID().toString();
        log.info("Initialized Channel instance with ID: {}", this.instanceId);
    }

    @Override
    public String getInstanceId() {
        return this.instanceId;
    }

    @Override
    public boolean isLive() {
        return this.live;
    }

    void setLive(boolean live) {
        this.live = live;
    }
}
