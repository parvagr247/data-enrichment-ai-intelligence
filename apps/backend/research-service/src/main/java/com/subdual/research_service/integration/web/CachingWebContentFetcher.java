package com.subdual.research_service.integration.web;

import com.subdual.research_service.discovery.cache.ThreadSafeSourceCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * GoF Decorator Pattern: Transparently decorates a WebContentFetcher with thread-safe memoization.
 * Checks ThreadSafeSourceCache before delegating to the underlying HTTP fetcher,
 * and caches successful responses automatically.
 */
@Component
@Primary
@Slf4j
public class CachingWebContentFetcher implements WebContentFetcher {

    private final WebContentFetcher delegate;
    private final ThreadSafeSourceCache sourceCache;

    public CachingWebContentFetcher(DefaultWebContentFetcher delegate, ThreadSafeSourceCache sourceCache) {
        this.delegate = delegate;
        this.sourceCache = sourceCache;
    }

    @Override
    public FetchedContent fetch(String url) {
        if (url == null || url.isBlank()) {
            return delegate.fetch(url);
        }

        Optional<FetchedContent> cached = sourceCache.getFetchedContent(url);
        if (cached.isPresent()) {
            log.debug("[Decorator: CACHE_HIT] Returning cached content for URL: '{}'", url);
            return cached.get();
        }

        FetchedContent fetched = delegate.fetch(url);
        if (fetched != null && fetched.success()) {
            sourceCache.putFetchedContent(url, fetched);
        }
        return fetched;
    }
}
