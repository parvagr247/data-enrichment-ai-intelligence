package com.subdual.research_service.research.discovery.cache;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.subdual.research_service.integration.web.FetchedContent;

/**
 * Thread-safe memoization cache for web fetch results and source queries.
 * Implements thread-safe Singleton access alongside Spring container management.
 * Prevents redundant HTTP fetches across concurrent entity research tasks.
 */
@Component
@Slf4j
public class ThreadSafeSourceCache {

    private static volatile ThreadSafeSourceCache instance;

    private final Map<String, FetchedContent> contentCache = new ConcurrentHashMap<>();

    public ThreadSafeSourceCache() {
        synchronized (ThreadSafeSourceCache.class) {
            instance = this;
        }
    }

    public static ThreadSafeSourceCache getInstance() {
        if (instance == null) {
            synchronized (ThreadSafeSourceCache.class) {
                if (instance == null) {
                    instance = new ThreadSafeSourceCache();
                }
            }
        }
        return instance;
    }

    public Optional<FetchedContent> getFetchedContent(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeUrl(url);
        FetchedContent cached = contentCache.get(normalized);
        if (cached != null) {
            log.debug("Cache hit for URL: '{}'", url);
            return Optional.of(cached);
        }
        return Optional.empty();
    }

    public void putFetchedContent(String url, FetchedContent content) {
        if (url != null && !url.isBlank() && content != null) {
            String normalized = normalizeUrl(url);
            contentCache.put(normalized, content);
        }
    }

    public void clear() {
        contentCache.clear();
    }

    public int size() {
        return contentCache.size();
    }

    private String normalizeUrl(String url) {
        return url.trim().toLowerCase().replaceAll("/+$", "");
    }
}
