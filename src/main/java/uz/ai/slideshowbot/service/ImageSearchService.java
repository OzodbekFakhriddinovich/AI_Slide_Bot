package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Key-free image search. Only reusable Commons images, never generated or random images. */
@Service
public class ImageSearchService {
    static final int MAX_IMAGE_BYTES = 6 * 1024 * 1024;
    private static final String COMMONS_API = "https://commons.wikimedia.org/w/api.php";
    private static final String USER_AGENT = "SlideBot/1.0 (educational presentations; Wikimedia Commons client)";
    private final OkHttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public record ImageAsset(byte[] data, String title, String url, String attribution) {}

    public ImageSearchService() {
        this(new OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS).callTimeout(6, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).build());
    }

    ImageSearchService(OkHttpClient client) { this.client = client; }

    public byte[] findImageBytes(String query, String topic) {
        ImageAsset image = findImage(query, topic, Set.of());
        return image == null ? null : image.data();
    }

    public ImageAsset findImage(String query, String topic, Set<String> excludedUrls) {
        if (Thread.currentThread().isInterrupted()) return null;
        String search = WebResearchService.cleanQuery(query == null || query.isBlank() ? topic : query);
        if (search.isBlank()) return null;
        Set<String> excluded = excludedUrls == null ? Set.of() : excludedUrls;
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(9);
        try {
            JsonNode pages = api(COMMONS_API, Map.of("generator", "search", "gsrsearch", search,
                    "gsrnamespace", "6", "gsrlimit", "8", "prop", "imageinfo", "iiprop",
                    "url|mime|size|extmetadata", "iiurlwidth", "1280", "iiextmetadatafilter",
                    "Artist|Credit|LicenseShortName|Attribution|ObjectName|ImageDescription"), deadline)
                    .path("query").path("pages");
            for (JsonNode page : pages) {
                if (expired(deadline)) return null;
                ImageAsset image = fromPage(page, search, excluded, deadline, false);
                if (image != null) return image;
            }
            // When an abstract security term returns only old charts, look for the network
            // equipment it concerns. Never use an unrelated random photograph.
            String lower=search.toLowerCase(Locale.ROOT);
            String context=lower.matches(".*(information security|network security|cyberattack|computer security).*")
                    ? "server rack network" : "";
            if(!context.isEmpty() && !expired(deadline)) {
                JsonNode contextual=api(COMMONS_API,Map.of("generator","search","gsrsearch",context,
                        "gsrnamespace","6","gsrlimit","6","prop","imageinfo","iiprop","url|mime|size|extmetadata",
                        "iiurlwidth","1280","iiextmetadatafilter","Artist|Credit|LicenseShortName|Attribution|ObjectName|ImageDescription"),deadline)
                        .path("query").path("pages");
                for(JsonNode page:contextual) {
                    if(expired(deadline)) return null;
                    ImageAsset image=fromPage(page,context,excluded,deadline,false);
                    if(image!=null) return image;
                }
            }
            // A localized article can identify a Commons file even when the file name is English.
            String wiki = search.matches(".*[А-Яа-яЁё].*") ? "ru"
                    : search.matches("(?iu).*(o['‘’ʻ]|g['‘’ʻ]|tarix|quyosh|energiya|O.zbekiston|hujayra|fotosintez).*") ? "uz" : "en";
            JsonNode wikiPages = api("https://" + wiki + ".wikipedia.org/w/api.php", Map.of(
                    "generator", "search", "gsrsearch", search, "gsrnamespace", "0", "gsrlimit", "2",
                    "prop", "pageimages", "piprop", "name", "pilicense", "free"), deadline)
                    .path("query").path("pages");
            for (JsonNode page : wikiPages) {
                String fileName = page.path("pageimage").asText();
                if (fileName.isBlank() || expired(deadline)) continue;
                JsonNode filePages = api(COMMONS_API, Map.of("titles", "File:" + fileName,
                        "prop", "imageinfo", "iiprop", "url|mime|size|extmetadata", "iiurlwidth", "1280",
                        "iiextmetadatafilter", "Artist|Credit|LicenseShortName|Attribution|ObjectName"), deadline)
                        .path("query").path("pages");
                for (JsonNode filePage : filePages) {
                    ImageAsset image = fromPage(filePage, search, excluded, deadline, true);
                    if (image != null) return image;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            // Missing topical imagery is ordinary; the renderer uses its designed text layout.
        }
        return null;
    }

    private ImageAsset fromPage(JsonNode page, String search, Set<String> excluded, long deadline,
                                boolean articleMatched) throws IOException {
        JsonNode info = page.path("imageinfo").path(0);
        if (info.isMissingNode()) return null;
        JsonNode meta = info.path("extmetadata");
        String license = plain(meta.path("LicenseShortName").path("value").asText());
        if (license.isBlank() || license.toLowerCase(Locale.ROOT).contains("non-free")) return null;
        String title = plain(meta.path("ObjectName").path("value").asText(page.path("title").asText()));
        String description = plain(meta.path("ImageDescription").path("value").asText());
        String descriptionKey=(page.path("title").asText()+" "+title+" "+description).toLowerCase(Locale.ROOT);
        // A historic chart or a screenshot is not a photograph illustrating a general topic.
        // Only use those assets when the slide explicitly requests that kind of evidence.
        if(!search.toLowerCase(Locale.ROOT).matches(".*(chart|statistic|graph|screenshot|logo|statistics).*")) {
            if(descriptionKey.matches("(?s).*(statistics|pie chart|bar chart|screenshot|screen shot|e-mail|email|mail message|logo|book cover|poster|word cloud|infographic).*")) return null;
        }
        if (!articleMatched && !relevant(search, title + " " + description)) return null;
        String pageUrl = info.path("descriptionurl").asText();
        if (!trustedDescription(pageUrl)) return null;
        String imageUrl = info.path("thumburl").asText(info.path("url").asText());
        if (excluded.contains(pageUrl) || excluded.contains(imageUrl) || !trustedImageUrl(imageUrl)) return null;
        String artist = plain(meta.path("Artist").path("value").asText());
        String credit = plain(meta.path("Attribution").path("value").asText());
        if (credit.isBlank()) credit = artist.isBlank()
                ? plain(meta.path("Credit").path("value").asText()) : artist;
        if (credit.isBlank()) credit = "Wikimedia Commons contributors";
        byte[] bytes = download(imageUrl, deadline);
        return bytes == null ? null : new ImageAsset(bytes, title, pageUrl,
                credit + " · " + license + " · Wikimedia Commons");
    }

    private JsonNode api(String endpoint, Map<String, String> params, long deadline) throws IOException {
        if (expired(deadline)) throw new IOException("Image lookup timed out");
        HttpUrl.Builder url = Objects.requireNonNull(HttpUrl.parse(endpoint)).newBuilder()
                .addQueryParameter("action", "query").addQueryParameter("format", "json")
                .addQueryParameter("formatversion", "2");
        params.forEach(url::addQueryParameter);
        try (Response response = execute(new Request.Builder().url(url.build())
                .header("User-Agent", USER_AGENT).build(), deadline)) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Image lookup unavailable");
            return mapper.readTree(readBounded(response.body(), 768 * 1024));
        }
    }

    private byte[] download(String imageUrl, long deadline) throws IOException {
        if (!trustedImageUrl(imageUrl) || expired(deadline)) return null;
        try (Response response = execute(new Request.Builder().url(imageUrl)
                .header("User-Agent", USER_AGENT).build(), deadline)) {
            if (!response.isSuccessful() || response.body() == null) return null;
            String type = response.header("Content-Type", "").toLowerCase(Locale.ROOT).split(";", 2)[0];
            if (!Set.of("image/jpeg", "image/png", "image/gif").contains(type)) return null;
            byte[] data = readBounded(response.body(), MAX_IMAGE_BYTES);
            return validImage(data) ? data : null;
        }
    }

    private Response execute(Request request, long deadline) throws IOException {
        Call call = client.newCall(request);
        long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (remaining <= 0 || Thread.currentThread().isInterrupted()) throw new IOException("Image lookup cancelled");
        call.timeout().timeout(Math.min(remaining, 5_000), TimeUnit.MILLISECONDS);
        return call.execute();
    }

    static byte[] readBounded(ResponseBody body, int maximum) throws IOException {
        if (body.contentLength() > maximum) throw new IOException("Response too large");
        try (var input = body.byteStream()) {
            byte[] data = input.readNBytes(maximum + 1);
            if (data.length > maximum) throw new IOException("Response too large");
            return data;
        }
    }

    static boolean validImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 240 || height < 140 || width > 16000 || height > 16000
                        || (long) width * height > 32_000_000L) return false;
                return reader.read(0) != null;
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException ignored) { return false; }
    }

    static boolean trustedImageUrl(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443)
                    && "upload.wikimedia.org".equalsIgnoreCase(uri.getHost())
                    && uri.getRawPath().startsWith("/wikipedia/commons/");
        } catch (RuntimeException ignored) { return false; }
    }

    private static boolean trustedDescription(String value) {
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null
                    && "commons.wikimedia.org".equalsIgnoreCase(uri.getHost())
                    && uri.getPath().startsWith("/wiki/File:");
        } catch (RuntimeException ignored) { return false; }
    }

    private static boolean relevant(String query, String candidate) {
        Set<String> words = WebResearchService.keywords(query);
        String lower = candidate.toLowerCase(Locale.ROOT);
        return words.stream().anyMatch(lower::contains);
    }

    static String plain(String html) {
        return html.replaceAll("(?s)<[^>]*>", " ").replace("&amp;", "&").replace("&quot;", "\"")
                .replace("&#39;", "'").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
    }

    private static boolean expired(long deadline) {
        return Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline;
    }
}
