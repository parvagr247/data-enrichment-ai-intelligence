package com.subdual.research_service.extraction;

import com.subdual.research_service.integration.web.FetchedContent;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class ContentExtractor {

    public ExtractedDocument extract(FetchedContent fetched, int maxContentLength) {
        if (!isValidFetch(fetched)) {
            return createEmptyDocument(fetched);
        }

        try {
            Document doc = Jsoup.parse(fetched.rawBody(), fetched.url());
            String title = extractTitle(doc);
            String metaDescription = extractMetaDescription(doc);
            String siteName = extractMetaTag(doc, "property", "og:site_name");
            String cleanText = extractCleanBodyText(doc, maxContentLength);

            return new ExtractedDocument(fetched.url(), title, metaDescription, siteName, cleanText, Instant.now());
        } catch (Exception ex) {
            return createEmptyDocument(fetched);
        }
    }

    private boolean isValidFetch(FetchedContent fetched) {
        return fetched != null
                && fetched.success()
                && fetched.rawBody() != null
                && !fetched.rawBody().isBlank();
    }

    private ExtractedDocument createEmptyDocument(FetchedContent fetched) {
        String url = fetched != null ? fetched.url() : "";
        return new ExtractedDocument(url, null, null, null, "", Instant.now());
    }

    private String extractCleanBodyText(Document doc, int maxContentLength) {
        stripNoiseTags(doc);

        Element body = doc.body();
        String rawText = body != null ? body.text() : doc.text();
        String cleanText = rawText.replaceAll("\\s+", " ").trim();

        if (maxContentLength > 0 && cleanText.length() > maxContentLength) {
            return cleanText.substring(0, maxContentLength);
        }
        return cleanText;
    }

    private void stripNoiseTags(Document doc) {
        doc.select("script, style, nav, header, footer, noscript, svg, form, aside, " +
                "[role='navigation'], [role='banner'], [role='contentinfo'], " +
                "[class*='cookie'], [id*='cookie'], [class*='consent'], [id*='consent'], " +
                "[class*='advertisement'], [id*='advertisement'], [class*='ads'], [id*='ads'], " +
                "[class*='sidebar'], [id*='sidebar'], [class*='people-also-viewed'], " +
                "[class*='related-profiles'], [id*='related-profiles'], [class*='recommended']").remove();
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

    private String extractMetaDescription(Document doc) {
        String metaDescription = extractMetaTag(doc, "name", "description");
        if (metaDescription == null || metaDescription.isBlank()) {
            metaDescription = extractMetaTag(doc, "property", "og:description");
        }
        return metaDescription;
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
