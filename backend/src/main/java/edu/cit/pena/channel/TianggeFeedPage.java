package edu.cit.pena.channel;

import java.util.List;

/**
 * Public result type for fetchFeed — one page of events plus the cursor.
 */
public record TianggeFeedPage(List<TianggeEvent> events, long nextCursor) {}