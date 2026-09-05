package com.subdual.research_service.service;

import com.subdual.research_service.client.FetchedContent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ContentExtractor {

    private static final Logger log = LoggerFactory.getLogger(ContentExtractor.class);

    public ExtractedDocument extract(FetchedContent fetched, int maxContentLength) {
        if (fetched == null || !fetched.success() || fetched.rawBody() == null || fetched.rawBody().isBlank()) {
            return new ExtractedDocument(
                    fetched != null ? fetched.url() : null,
                    null,
                    null,
                    null,
                    "",
                    Instant.now()
            );
        }

        String rawBody = fetched.rawBody();
        String url = fetched.url();

        try {
            Document doc = Jsoup.parse(rawBody, url);

            // Strip noise elements
            doc.select("script, style, noscript, svg, form, iframe, header, footer, nav").remove();

            String title = doc.title();
            if (title == null || title.isBlank()) {
                Element ogTitle = doc.selectFirst("meta[property=og:title], meta[name=twitter:title]");
                if (ogTitle != null) {
                    title = ogTitle.attr("content");
                }
            }

            String metaDescription = null;
            Element descElem = doc.selectFirst("meta[name=description], meta[property=og:description], meta[name=twitter:description]");
            if (descElem != null) {
                metaDescription = descElem.attr("content");
            }

            String siteName = null;
            Element siteElem = doc.selectFirst("meta[property=og:site_name]");
            if (siteElem != null) {
                siteName = siteElem.attr("content");
            }

            String bodyText = doc.body() != null ? doc.body().text() : "";
            if (bodyText.length() > maxContentLength) {
                bodyText = bodyText.substring(0, maxContentLength);
            }

            return new ExtractedDocument(
                    url,
                    title != null && !title.isBlank() ? title.trim() : null,
                    metaDescription != null && !metaDescription.isBlank() ? metaDescription.trim() : null,
                    siteName != null && !siteName.isBlank() ? siteName.trim() : null,
                    bodyText.trim(),
                    Instant.now()
            );
        } catch (Exception ex) {
            log.warn("HTML extraction failed for URL '{}': {}", url, ex.getMessage());
            // Fallback for non-HTML or unparseable text
            String plainText = rawBody.length() > maxContentLength ? rawBody.substring(0, maxContentLength) : rawBody;
            return new ExtractedDocument(url, null, null, null, plainText.trim(), Instant.now());
        }
    }
}
