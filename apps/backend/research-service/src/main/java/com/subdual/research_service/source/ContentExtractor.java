package com.subdual.research_service.source;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Extracts structured textual content, semantic metadata, and titles from raw HTML/JSON fetched pages.
 * Strips script tags, navigation boilerplate, headers, and footers.
 */
@Component
public class ContentExtractor {

    public ExtractedDocument extract(FetchedContent fetched, int maxContentLength) {
        if (fetched == null || !fetched.success() || fetched.rawBody() == null || fetched.rawBody().isBlank()) {
            return new ExtractedDocument(
                    fetched != null ? fetched.url() : "",
                    null,
                    "",
                    null,
                    null
            );
        }

        try {
            Document doc = Jsoup.parse(fetched.rawBody(), fetched.url());

            // Extract semantic metadata tags
            String title = extractTitle(doc);
            String metaDescription = extractMetaTag(doc, "name", "description");
            if (metaDescription == null || metaDescription.isBlank()) {
                metaDescription = extractMetaTag(doc, "property", "og:description");
            }

            String siteName = extractMetaTag(doc, "property", "og:site_name");

            // Strip noise and boilerplate tags before extracting clean body text
            doc.select("script, style, nav, header, footer, noscript, svg, form").remove();

            Element body = doc.body();
            String rawText = body != null ? body.text() : doc.text();
            String cleanText = rawText.replaceAll("\\s+", " ").trim();

            if (maxContentLength > 0 && cleanText.length() > maxContentLength) {
                cleanText = cleanText.substring(0, maxContentLength);
            }

            return new ExtractedDocument(fetched.url(), title, metaDescription, siteName, cleanText, Instant.now());
        } catch (Exception ex) {
            return new ExtractedDocument(fetched.url(), null, null, null, "", Instant.now());
        }
    }

    private String extractTitle(Document doc) {
        String ogTitle = extractMetaTag(doc, "property", "og:title");
        if (ogTitle != null && !ogTitle.isBlank()) {
            return ogTitle.trim();
        }
        String docTitle = doc.title();
        if (docTitle != null && !docTitle.isBlank()) {
            return docTitle.trim();
        }
        Element h1 = doc.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            return h1.text().trim();
        }
        return null;
    }

    private String extractMetaTag(Document doc, String attrKey, String attrValue) {
        Element meta = doc.selectFirst("meta[" + attrKey + "='" + attrValue + "']");
        if (meta == null) {
            meta = doc.selectFirst("meta[" + attrKey + "=\"" + attrValue + "\"]");
        }
        if (meta != null && meta.hasAttr("content")) {
            String content = meta.attr("content").trim();
            return content.isBlank() ? null : content;
        }
        return null;
    }
}
