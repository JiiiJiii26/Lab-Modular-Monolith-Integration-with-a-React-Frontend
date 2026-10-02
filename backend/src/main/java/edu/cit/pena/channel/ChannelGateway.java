package edu.cit.pena.channel;

/**
 * Public entrypoint for the Channel module.
 * Provides the application instance ID and live state.
 */
public interface ChannelGateway {

    /**
     * Unique client instance ID (UUID) generated for this application run.
     * Sent in the X-Client-Instance header on every call to Tiangge and LegacySupply.
     */
    String getInstanceId();

    /**
     * Returns true if the channel has successfully registered and is online.
     */
    boolean isLive();
}
