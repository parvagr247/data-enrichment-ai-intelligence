package com.subdual.research_service.extraction.document;

import com.subdual.research_service.extraction.ai.document.ContentExtractor;
import com.subdual.research_service.extraction.ai.document.ExtractedDocument;
import com.subdual.research_service.integration.web.FetchedContent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentExtractorTest {

    private ContentExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ContentExtractor();
    }

    @Test
    @DisplayName("Should extract title, meta description, and clean text while stripping boilerplate")
    void shouldExtractMetadataAndCleanText() {
        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <title>Spring Boot Reference</title>
                    <meta name="description" content="Official documentation for Spring Boot framework." />
                    <meta property="og:site_name" content="Spring Docs" />
                    <script>console.log("tracking script");</script>
                    <style>body { font-size: 14px; }</style>
                </head>
                <body>
                    <nav><a href="/home">Home</a></nav>
                    <header><h1>Header Navigation</h1></header>
                    <main>
                        <h2>Core Features</h2>
                        <p>Spring Boot makes it easy to build production-ready applications quickly.</p>
                    </main>
                    <footer><p>Copyright 2026</p></footer>
                </body>
                </html>
                """;

        FetchedContent fetched = FetchedContent.success("https://docs.spring.io/spring-boot", 200, "text/html", html);
        ExtractedDocument doc = extractor.extract(fetched, 10000);

        assertThat(doc.title()).isEqualTo("Spring Boot Reference");
        assertThat(doc.metaDescription()).isEqualTo("Official documentation for Spring Boot framework.");
        assertThat(doc.siteName()).isEqualTo("Spring Docs");
        assertThat(doc.cleanText()).contains("Spring Boot makes it easy to build production-ready applications quickly.");
        assertThat(doc.cleanText()).doesNotContain("tracking script");
        assertThat(doc.cleanText()).doesNotContain("font-size");
        assertThat(doc.cleanText()).doesNotContain("Copyright 2026");
    }

    @Test
    @DisplayName("Should enforce maxContentLength bound on body text")
    void shouldTruncateBodyTextToMaxContentLength() {
        String html = "<html><body><p>" + "A".repeat(500) + "</p></body></html>";
        FetchedContent fetched = FetchedContent.success("https://example.com", 200, "text/html", html);

        ExtractedDocument doc = extractor.extract(fetched, 100);
        assertThat(doc.cleanText()).hasSize(100);
    }

    @Test
    @DisplayName("Should handle failed or empty FetchedContent gracefully")
    void shouldHandleFailedFetchedContent() {
        FetchedContent failed = FetchedContent.failed("https://example.com/fail", 500, "Connection refused");
        ExtractedDocument doc = extractor.extract(failed, 5000);

        assertThat(doc.url()).isEqualTo("https://example.com/fail");
        assertThat(doc.title()).isNull();
        assertThat(doc.cleanText()).isEmpty();
    }
}
