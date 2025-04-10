import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A multithreaded web crawler using Cached Thread Pool.
 */
public class Main {

    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final PageFetcher pageFetcher;
    private static final boolean debugMode = Boolean.getBoolean("debug");
    private final Pattern pattern = Pattern.compile(
            "<a\\s+[^>]*?href=\"(https?://[^\"]+|/[^\"]*|[^:\"][^\"]*)\"", // Added |/[^\"]* and |[^:\"][^\"]*
            Pattern.CASE_INSENSITIVE
    );
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

    public Main(PageFetcher pageFetcher) {
        this.pageFetcher = pageFetcher;
    }

    interface PageFetcher {
        String fetchPageContent(String url) throws Exception;
    }

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
                if (debugMode) System.out.println("Fetched " + url + " in " + (System.currentTimeMillis() - start) + " ms");
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    protected Set<String> collectLinks(String webSite) {
        URI baseUri = URI.create(webSite);

        Set<String> visitedLinks = ConcurrentHashMap.newKeySet();
        collectLinks(baseUri.getHost(), baseUri.toString(), visitedLinks);
        shutdownExecutor();
        return visitedLinks;
    }

    private Set<String> collectLinks(String baseUrl, String url, Set<String> visitedLinks) {
        Set<String> localVisitedLinks = new HashSet<>();
        try {
            String content = pageFetcher.fetchPageContent(url);
            Set<String> links = extractLinks(baseUrl, url, content);
            List<CompletableFuture<Set<String>>> futures = new ArrayList<>();

            for (String link : links) {
                if (visitedLinks.add(link)) {
                    localVisitedLinks.add(link);
                    futures.add(CompletableFuture.supplyAsync(() -> collectLinks(baseUrl, link, visitedLinks), executor));
                }
            }

            for (CompletableFuture<Set<String>> future : futures) {
                try {
                    visitedLinks.addAll(future.get(10, TimeUnit.SECONDS));
                } catch (Exception e) {
                    if (debugMode) System.err.println("Failed to get result for: " + url + "\nException: " + getFullErrorMessage(e));

                }
            }
        } catch (Exception e) {
            if (debugMode) System.err.println("Failed to crawl: " + url + "\nException: " + getFullErrorMessage(e));
        }
        return localVisitedLinks;
    }

    private String getFullErrorMessage(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }

    private Set<String> extractLinks(String domain, String url, String content) {

        Set<String> links = new HashSet<>();

        Matcher matcher = pattern.matcher(content);
        String link = "";

        try {
            URI baseUri = new URI(url);
            while (matcher.find()) {
                link = matcher.group(1);

                if (link.matches(".*(?:download|upload|git).*" + "|" +
                        ".*\\.(?:jpe?g|png|gif|pdf|mp[34]|zip|tar|gz|rar|exe|dmg|iso|docx?|xlsx?|pptx?|avi|mov|wmv)$")) continue;

                URI extractedUri = new URI(link);

                URI resolvedUri = baseUri.resolve(extractedUri);

                String fullUrlToCrawl = resolvedUri.toString();

                if (fullUrlToCrawl.contains(domain)) links.add(fullUrlToCrawl);
            }
        } catch (URISyntaxException e) {
            if (debugMode) System.err.println("Invalid Url to crawl: " + link + "\nException: " + getFullErrorMessage(e));
        }

        return links;
    }

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