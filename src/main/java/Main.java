import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
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
        private static final int MAX_RETRIES = 5;
        private static final int CONNECT_TIMEOUT_MS = 5000;
        private static final int READ_TIMEOUT_MS = 5000;

        @Override
        public String fetchPageContent(String url) throws Exception {
            StringBuilder content = new StringBuilder();

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    HttpURLConnection connection = (HttpURLConnection) URI.create(url).toURL().openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
                    connection.setReadTimeout(READ_TIMEOUT_MS);
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0");

                    long start = System.currentTimeMillis();

                    return bufferInputStream(url, connection, content, start, attempt);

                } catch (SocketTimeoutException e) {
                    if (debugMode)
                        System.err.println("Timeout on " + url + ", retrying... (" + attempt + "/" + MAX_RETRIES + ")");

                    if (attempt == MAX_RETRIES) {
                        throw new Exception("Failed to fetch URL after " + MAX_RETRIES + " attempts: " + url, e);
                    }

                    Thread.sleep(1000L * (1L << (attempt - 1)));
                }
            }

            throw new Exception("Unexpected error fetching: " + url);
        }

        private String bufferInputStream(String url, HttpURLConnection connection, StringBuilder content, long start, int attempt) throws IOException {
            try (InputStream inputStream = connection.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

                char[] buffer = new char[8192];
                int bytesRead;
                while ((bytesRead = reader.read(buffer)) != -1) {
                    content.append(buffer, 0, bytesRead);
                }

                if (debugMode)
                    System.out.println("Fetched " + url + " in " + (System.currentTimeMillis() - start) + " ms (Attempt " + attempt + ")");

                return content.toString();
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
            Set<String> links = extractLinks(baseUrl, content);
            List<CompletableFuture<Set<String>>> futures = new ArrayList<>();

            for (String link : links) {
                if (visitedLinks.add(link)) {
                    localVisitedLinks.add(link);
                    futures.add(CompletableFuture.supplyAsync(() -> collectLinks(baseUrl, link, visitedLinks), executor));
                }
            }

            for (CompletableFuture<Set<String>> future : futures) {
                try {
                    visitedLinks.addAll(future.get(60, TimeUnit.SECONDS));
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

    private void shutdownExecutor() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}