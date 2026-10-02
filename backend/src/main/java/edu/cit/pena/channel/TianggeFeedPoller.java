package edu.cit.pena.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * PACKAGE-PRIVATE feed poller skeleton.
 * Ticks every N seconds (configured by channel.tiangge.feed-poll-seconds).
 * Processing loop is NOT yet implemented — Prompt 2.
 */
@Component
class TianggeFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(TianggeFeedPoller.class);

    @Scheduled(fixedDelayString = "${channel.tiangge.feed-poll-seconds:5}000")
    void tick() {
        log.debug("Feed poller tick");
    }
}
