import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CrawlerTest {

    @Test
    void testCrawlPageShouldIgnoreExternalLinks() throws Exception {

        Main.PageFetcher mockFetcher = mock(Main.PageFetcher.class);
        when(mockFetcher.fetchPageContent(anyString())).thenReturn("""
                <html>
                <body>
                <a class="some_class "href="https://example.com/page1">Page 1</a>
                <a href="https://example.com/page2">Page 2</a>
                <a href="https://external.com/page3">External Link</a>
                </body>
                </html>
                """);

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
    void testCrawlPageWithMultipleSubpages() throws Exception {
        Main.PageFetcher mockFetcher = mock(Main.PageFetcher.class);

        Map<String, String> mockResponses = Map.of(
                "https://example.com", """
                        <html><body>
                        <a href="https://example.com/page1">Page 1</a>
                        <a href="https://example.com/page2">Page 2</a>
                        </body></html>
                        """,
                "https://example.com/page1", """
                        <html><body>
                        <a href="https://example.com/page3">Page 3</a>
                        </body></html>
                        """,
                "https://example.com/page2", """
                        <html><body>
                        <a href="https://example.com/page4">Page 4</a>
                        </body></html>
                        """
        );

        when(mockFetcher.fetchPageContent(anyString()))
                .thenAnswer(invocation -> mockResponses.getOrDefault((String) invocation.getArgument(0), "<html><body>404 Not Found</body></html>"));

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
    void testCollectLinksIncludesSubdomain() throws Exception {
        Main.PageFetcher mockFetcher = mock(Main.PageFetcher.class);

        when(mockFetcher.fetchPageContent(anyString())).thenReturn("""
                <html>
                <body>
                <a href="https://orf.at/news">News</a>
                <a href="https://kids.orf.at/story">Kids Story</a>
                <a href="https://external.com/page">External Link</a>
                </body>
                </html>
                """);

        Main crawler = new Main(mockFetcher);

        Set<String> res = crawler.collectLinks("https://orf.at");

        Set<String> expectedLinks = Set.of(
                "https://orf.at/news",
                "https://kids.orf.at/story"
        );

        assertTrue(res.containsAll(expectedLinks), "Crawler should visit subdomains!");
    }

    @Test
    void testCollectLinksExcludesMedia() throws Exception {
        Main.PageFetcher mockFetcher = mock(Main.PageFetcher.class);

        when(mockFetcher.fetchPageContent(anyString())).thenReturn("""
                <html>
                <body>
                <a href="https://orf.at/news">News</a>
                <a href="https://orf.at/media/file.mp3">Mp3 Link</a>
                <a href="https://orf.at/download/file.jpeg">Download Link</a>
                <a href="https://gitlab.com">Gitlab Link</a>
                </body>
                </html>
                """);

        Main crawler = new Main(mockFetcher);

        Set<String> res = crawler.collectLinks("https://orf.at");

        Set<String> expectedLinks = Set.of("https://orf.at/news");

        assertEquals(res, expectedLinks, "Crawler should exclude media, git or download!");
    }

    @Test
    void testCollectLinksIncludeRelativeUrl() throws Exception {
        Main.PageFetcher mockFetcher = mock(Main.PageFetcher.class);

        when(mockFetcher.fetchPageContent(anyString())).thenReturn("""
                <html>
                <body>
                <a href="https://orf.at/news">News</a>
                <a href="/doc/">External Link</a>
                </body>
                </html>
                """);


        Main crawler = new Main(mockFetcher);

        Set<String> res = crawler.collectLinks("https://orf.at");

        Set<String> expectedLinks = Set.of("https://orf.at/news", "https://orf.at/doc/");

        assertTrue(res.containsAll(expectedLinks), "Crawler should visit relative urls!");
    }
}