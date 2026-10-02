package edu.cit.pena.channel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import java.util.function.Supplier;

/**
 * PACKAGE-PRIVATE Spring configuration for the channel module.
 * Exposes a Supplier<String> bean that returns the current instance ID,
 * so XmlHttpClient (in the supplier package) can include X-Client-Instance
 * on every outbound call without importing channel-specific types.
 */
@Configuration
class TianggeChannelConfig {

    /**
     * Provides the instance ID supplier to be injected into XmlHttpClient.
     * TianggeInstanceManager is injected lazily to avoid circular dependency
     * during context initialization.
     */
    @Bean
    Supplier<String> tianggeInstanceIdSupplier(@Lazy TianggeInstanceManager instanceManager) {
        return instanceManager::getInstanceId;
    }
}
