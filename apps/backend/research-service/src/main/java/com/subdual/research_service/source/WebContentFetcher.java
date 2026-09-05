package com.subdual.research_service.source;

/**
 * Component interface for retrieving raw remote web resources over HTTP.
 */
public interface WebContentFetcher {

    FetchedContent fetch(String url);
}
