# Web Crawler - Multithreaded Link Collector

This is a multithreaded web crawler designed to collect all unique links from a given website. It uses Java's `ExecutorService` with a `CachedThreadPool` to handle concurrency. The application fetches pages and extracts links recursively, ensuring no link is visited more than once.

## Main Features
- **Multithreaded Crawling**: Uses an `ExecutorService` to crawl pages concurrently.
- **Link Collection**: Recursively collects all unique links from a website.
- **HTTP Requests**: Fetches pages using HTTP GET requests with a customizable `User-Agent`.
- **HTML Parsing**: Extracts only valid links (excluding media files like images, PDFs, etc.).
- **Debug Mode**: Optional debug logging to track crawling performance.

## Requirements

- Java 17 or higher. From Java 21 on there is a significant improvement in performance
- Gradle (optional if using a build tool).

## Build and Run the Application

You can build the project using Gradle or just compile and run the Main class.

### Gradle

- Build:

```bash
./gradlew build
```

This will compile the Java source file, run tests, and package the application into a JAR file.

- Run;

```bash
./gradlew run --args="https://example.com"
```

### Compile and Run the main class

- Compile

```bash
cd src/main/java
javac Main.java
```

- Run

```bash
java Main https://example.com
```

### Output example

```
Collecting links for https://example.com ...
https://example.com/page1
https://example.com/page2
...
Crawling finished in 25.3 seconds
```

### Debug option

You can enable debug mode by passing the debug system property:

```bash
java -Ddebug=true Main https://example.com
```
This will provide additional logging for each fetched page, showing the time taken for each request. This will also track errors, such read time out, page not found, etc