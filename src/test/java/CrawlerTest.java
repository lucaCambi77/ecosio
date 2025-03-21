import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlerTest {
    @Test
    void testCrawlPageShouldIgnoreExternalLinks() {
        Main.PageFetcher mockFetcher = url -> """
                <html>
                <body>
                <a class="some_class "href="https://example.com/page1">Page 1</a>
                <a href="https://example.com/page2">Page 2</a>
                <a href="https://external.com/page3">External Link</a>
                </body>
                </html>
                """;

        Main crawler = new Main(mockFetcher);

        Set<String> res = crawler.collectLinks("https://example.com");

        Set<String> expectedLinks = Set.of(
                "https://example.com/page1",
                "https://example.com/page2"
        );

        assertTrue(res.containsAll(expectedLinks));
        assertFalse(res.contains("https://external.com/page3"), "External link should be ignored");
    }

    @Test
    void testCrawlPageWithMultipleSubpages() {
        Main.PageFetcher mockFetcher = url -> switch (url) {
            case "https://example.com" -> """
                    <html><body>
                    <a href="https://example.com/page1">Page 1</a>
                    <a href="https://example.com/page2">Page 2</a>
                    </body></html>
                    """;
            case "https://example.com/page1" -> """
                    <html><body>
                    <a href="https://example.com/page3">Page 3</a>
                    </body></html>
                    """;
            case "https://example.com/page2" -> """
                    <html><body>
                    <a href="https://example.com/page4">Page 4</a>
                    </body></html>
                    """;
            default -> "";
        };

        Main crawler = new Main(mockFetcher);

        Set<String> res = crawler.collectLinks("https://example.com");

        Set<String> expectedLinks = Set.of(
                "https://example.com/page1",
                "https://example.com/page2",
                "https://example.com/page3",
                "https://example.com/page4"
        );

        assertEquals(expectedLinks, res, "Crawler should visit all reachable subpages!");
    }

    @Test
    void testCollectLinksIncludesSubdomain() {
        Main.PageFetcher mockFetcher = url -> """
                <html>
                <body>
                <a href="https://orf.at/news">News</a>
                <a href="https://kids.orf.at/story">Kids Story</a>
                <a href="https://external.com/page">External Link</a>
                </body>
                </html>
                """;

        Main crawler = new Main(mockFetcher);

        Set<String> res = crawler.collectLinks("https://orf.at");

        Set<String> expectedLinks = Set.of(
                "https://orf.at/news",
                "https://kids.orf.at/story"
        );

        assertTrue(res.containsAll(expectedLinks), "Crawler should visit subdomains!");
    }
}