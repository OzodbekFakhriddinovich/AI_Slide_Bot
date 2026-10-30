package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort, key-free translation of attributed source excerpts. This public web
 * endpoint is unofficial: bounded failures propagate instead of inventing text or
 * silently delivering slides in a different language. Native sources are preferred.
 */
@Service
public class FreeTranslationService {
    private static final int MAX_BATCH_CHARACTERS = 3200;
    private static final int MAX_BATCHES = 24;
    private static final long CACHE_TTL = TimeUnit.HOURS.toMillis(24);
    private static final Pattern MARKER = Pattern.compile("\\[\\[\\s*SB\\s*(\\d{4})\\s*\\]\\]");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)*");
    private final OkHttpClient client;
    private final ObjectMapper json = new ObjectMapper();
    private record Cached(String value, long expires) {}
    private final Map<String, Cached> cache = Collections.synchronizedMap(new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) { return size() > 3000; }
    });

    public FreeTranslationService() {
        this(new OkHttpClient.Builder().connectTimeout(4, TimeUnit.SECONDS).readTimeout(7, TimeUnit.SECONDS)
                .callTimeout(9, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build());
    }
    FreeTranslationService(OkHttpClient client) { this.client = client; }

    public String translateQuery(String query, String target, long deadline) throws IOException {
        return translate(List.of(query), "auto", target, deadline).get(0);
    }

    public List<String> translate(List<String> texts, String source, String target, long deadline) throws IOException {
        if (source.equals(target) || texts.isEmpty()) return List.copyOf(texts);
        if (!Set.of("auto", "uz", "en", "ru", "de", "es", "tr", "tg").contains(source)
                || !Set.of("uz", "en", "ru", "de", "es", "tr", "tg").contains(target))
            throw new IOException("Unsupported translation language");
        List<String> result = new ArrayList<>(Collections.nCopies(texts.size(), null));
        List<Integer> missing = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            if (text == null || text.isBlank()) { result.set(i, ""); continue; }
            Cached saved = cache.get(key(source, target, text));
            if (saved != null && saved.expires() > now) result.set(i, saved.value());
            else missing.add(i);
        }
        int requests = 0;
        for (int at = 0; at < missing.size();) {
            checkDeadline(deadline);
            List<Integer> batch = new ArrayList<>();
            StringBuilder input = new StringBuilder();
            int encodedLength=0;
            while (at < missing.size()) {
                int index = missing.get(at);
                String line = "[[SB" + String.format(Locale.ROOT, "%04d", batch.size()) + "]] " + texts.get(index) + "\n";
                int encoded=URLEncoder.encode(line,StandardCharsets.UTF_8).length();
                if (!batch.isEmpty() && (input.length() + line.length() > MAX_BATCH_CHARACTERS || encodedLength+encoded>6500)) break;
                if (line.length() > MAX_BATCH_CHARACTERS) throw new IOException("Source excerpt is too long to translate safely");
                batch.add(index); input.append(line); encodedLength+=encoded; at++;
            }
            if (++requests > MAX_BATCHES) throw new IOException("Translation request budget exhausted");
            String translated = request(input.toString(), source, target, deadline);
            List<String> lines = parseBatch(translated, batch.size());
            if (lines.size() != batch.size()) throw new IOException("Translation did not preserve source excerpt boundaries");
            for (int n = 0; n < batch.size(); n++) {
                int index = batch.get(n);
                String output = lines.get(n).replaceAll("\\s+", " ").strip();
                if (output.isBlank() || output.length() > Math.max(600, texts.get(index).length() * 5)
                        || !preservesNumbers(texts.get(index), output) || !plausibleScript(output,target))
                    throw new IOException("Translation did not preserve the source excerpt");
                result.set(index, output);
                cache.put(key(source, target, texts.get(index)), new Cached(output, System.currentTimeMillis() + CACHE_TTL));
            }
        }
        return List.copyOf(result);
    }

    private String request(String text, String source, String target, long deadline) throws IOException {
        checkDeadline(deadline);
        HttpUrl url = Objects.requireNonNull(HttpUrl.parse("https://translate.googleapis.com/translate_a/single")).newBuilder()
                .addQueryParameter("client", "gtx").addQueryParameter("sl", source).addQueryParameter("tl", target)
                .addQueryParameter("dt", "t").addQueryParameter("q", text).build();
        Call call = client.newCall(new Request.Builder().url(url).header("User-Agent", "SlideBot/1.0 source translation").build());
        call.timeout().timeout(Math.min(9000, Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime()))), TimeUnit.MILLISECONDS);
        try (Response response = call.execute()) {
            if (!response.isSuccessful() || response.body() == null) throw new IOException("Free translation is temporarily unavailable");
            JsonNode root = json.readTree(ImageSearchService.readBounded(response.body(), 256 * 1024));
            if (!root.isArray() || !root.path(0).isArray()) throw new IOException("Invalid translation response");
            StringBuilder out = new StringBuilder();
            for (JsonNode segment : root.path(0)) if (segment.path(0).isTextual()) out.append(segment.path(0).asText());
            if (out.isEmpty()) throw new IOException("Empty translation response");
            return out.toString();
        }
    }

    static List<String> parseBatch(String output, int expected) {
        Matcher matcher = MARKER.matcher(output);
        List<Integer> ids = new ArrayList<>(), starts = new ArrayList<>(), ends = new ArrayList<>();
        while (matcher.find()) { ids.add(Integer.parseInt(matcher.group(1))); starts.add(matcher.start()); ends.add(matcher.end()); }
        if (ids.size() != expected || !output.substring(0, starts.isEmpty() ? 0 : starts.get(0)).isBlank()) return List.of();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            if (ids.get(i) != i) return List.of();
            result.add(output.substring(ends.get(i), i + 1 < ids.size() ? starts.get(i + 1) : output.length()).strip());
        }
        return result;
    }

    static boolean preservesNumbers(String source, String translation) {
        return numbers(source).equals(numbers(translation));
    }
    private static boolean plausibleScript(String value,String target) {
        long letters=value.codePoints().filter(Character::isLetter).count();
        if(letters<5) return true; // Symbols and proper initials are valid headings.
        Character.UnicodeScript expected=Set.of("ru","tg").contains(target)
                ?Character.UnicodeScript.CYRILLIC:Character.UnicodeScript.LATIN;
        long matching=value.codePoints().filter(c->Character.isLetter(c) && Character.UnicodeScript.of(c)==expected).count();
        return matching>=letters*.55;
    }
    private static List<String> numbers(String value) {
        List<String> result = new ArrayList<>(); Matcher number = NUMBER.matcher(value);
        while (number.find()) result.add(number.group().replaceAll("[.,]", ""));
        Collections.sort(result); return result;
    }
    private static String key(String source, String target, String text) { return source + ":" + target + ":" + text; }
    private static void checkDeadline(long deadline) throws IOException {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) throw new IOException("Source translation timed out");
    }
}
