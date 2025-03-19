import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A multithreaded web crawler that collects all unique links from a given website.
 * It uses an ExecutorService with a work-stealing pool to handle concurrency.
 */
public class Main {

    private final ExecutorService executor = Executors.newWorkStealingPool();
    private final PageFetcher pageFetcher;
    private static final boolean debugMode = Boolean.getBoolean("debug");

    /**
     * Main entry point for the crawler.
     *
     * @param args Command line arguments, expects a URL as the first argument.
     */
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java Main <url>");
            System.exit(1);
        }

        String webSite = args[0];
        System.out.println("Collecting links for " + webSite + " ...");

        long startTime = System.currentTimeMillis();

        Main crawler = new Main(new DefaultPageFetcher());
        Set<String> links = crawler.collectLinks(webSite);
        links.stream().sorted().forEach(System.out::println);

        System.out.println("Crawling finished in " + (System.currentTimeMillis() - startTime) / 1000.0 + " seconds");
    }

    /**
     * Initializes the crawler with a given PageFetcher implementation.
     *
     * @param pageFetcher The page fetcher used to retrieve page content.
     */
    public Main(PageFetcher pageFetcher) {
        this.pageFetcher = pageFetcher;
    }

    /**
     * Recursively collects all links starting from the given website.
     *
     * @param webSite The starting URL.
     * @return A set of unique links collected from the site.
     */
    protected Set<String> collectLinks(String webSite) {
        URI baseUri = URI.create(webSite);

        Set<String> visitedLinks = ConcurrentHashMap.newKeySet();
        collectLinks(baseUri.getHost(), baseUri.toString(), visitedLinks);
        shutdownExecutor();
        return visitedLinks;
    }

    /**
     * Interface for fetching page content.
     */
    interface PageFetcher {
        String fetchPageContent(String url) throws Exception;
    }

    /**
     * Default implementation of the PageFetcher that retrieves content from a URL.
     */
    static class DefaultPageFetcher implements PageFetcher {
        @Override
        public String fetchPageContent(String url) throws Exception {
            long start = System.currentTimeMillis();

            HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            try (InputStream inputStream = connection.getInputStream()) {
                if (debugMode) {
                    System.out.println("Fetched " + url + " in " + (System.currentTimeMillis() - start) + " ms");
                }
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    /**
     * Recursively crawls a page, extracts links, and submits new pages to the executor.
     *
     * @param baseUrl      The base URL for the website.
     * @param url          The current page URL being crawled.
     * @param visitedLinks A set of already visited links to avoid cycles.
     * @return A set of newly discovered links from the current page.
     */
    private Set<String> collectLinks(String baseUrl, String url, Set<String> visitedLinks) {
        Set<String> localVisitedLinks = new HashSet<>();
        try {
            String content = pageFetcher.fetchPageContent(url);
            Set<String> links = extractLinks(baseUrl, content);
            List<Future<Set<String>>> futures = new ArrayList<>();

            for (String link : links) {
                if (visitedLinks.add(link)) {
                    localVisitedLinks.add(link);
                    futures.add(executor.submit(() -> collectLinks(baseUrl, link, visitedLinks)));
                }
            }

            for (Future<Set<String>> future : futures) {
                try {
                    visitedLinks.addAll(future.get(10, TimeUnit.SECONDS));
                } catch (Exception e) {
                    if (debugMode) System.err.println("Failed to get result for: " + url);
                }
            }
        } catch (Exception e) {
            if (debugMode) System.err.println("Failed to crawl: " + url + " with exception : \n" + e.getMessage());
        }
        return localVisitedLinks;
    }

    /**
     * Extracts all valid hyperlinks from the HTML content.
     *
     * @param domain  The base URL to ensure links belong to the same domain.
     * @param content The HTML content to extract links from.
     * @return A set of unique links found in the content.
     */
    private Set<String> extractLinks(String domain, String content) {
        Set<String> links = new HashSet<>();
        Pattern pattern = Pattern.compile("<a\\s+[^>]*?href=\"(https?://[^\"]+)\"", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String link = matcher.group(1);
            if (link.matches(".*\\.(jpg|jpeg|png|gif|pdf|mp4|zip|tar|exe|docx|download|upload)$")) continue;
            if (link.contains(domain)) links.add(link);
        }
        return links;
    }

    /**
     * Shuts down the executor service gracefully, waiting for tasks to complete.
     */
    private void shutdownExecutor() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}

