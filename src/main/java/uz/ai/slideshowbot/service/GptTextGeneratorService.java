package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class GptTextGeneratorService {

    @Value("${gemini.api.keys}")
    private String apiKeysRaw;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-pro:generateContent?key=";

    private final ObjectMapper mapper = new ObjectMapper();

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MS = 4000;

    private List<String> getApiKeys() {
        if (apiKeysRaw == null || apiKeysRaw.isBlank()) return List.of();
        return Arrays.stream(apiKeysRaw.split(","))
                .map(String::trim)
                .filter(k -> !k.isEmpty())
                .toList();
    }

    public String callGemini(String prompt) throws Exception {
        List<String> keys = getApiKeys();
        if (keys.isEmpty()) {
            throw new RuntimeException("Gemini API key topilmadi! application.yml ni tekshiring.");
        }

        String key = keys.get(0);
        String url = GEMINI_URL + key;

        String jsonReq = mapper.writeValueAsString(Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        ));

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            System.out.println("🔑 Gemini 2.5 Pro urinish " + attempt + "/" + MAX_ATTEMPTS);

            try {
                RequestBody body = RequestBody.create(jsonReq, MediaType.get("application/json"));
                Request req = new Request.Builder().url(url).post(body).build();

                try (Response response = client.newCall(req).execute()) {
                    String rb = response.body() != null ? response.body().string() : "";
                    int code = response.code();

                    if (response.isSuccessful()) {
                        System.out.println("✅ Gemini muvaffaqiyatli javob berdi!");
                        return rb;
                    }

                    if (code == 429) {
                        System.err.println("🚫 Rate limit (429). " + attempt + " urinish...");
                    } else if (code == 503 || code == 500) {
                        System.err.println("⏳ Server xatosi (" + code + ")");
                    } else {
                        System.err.println("❌ HTTP " + code);
                    }

                    if (attempt < MAX_ATTEMPTS) {
                        Thread.sleep(RETRY_DELAY_MS * attempt);
                    }
                }
            } catch (Exception e) {
                System.err.println("❌ Attempt " + attempt + " xato: " + e.getMessage());
                if (attempt == MAX_ATTEMPTS) throw e;
                Thread.sleep(RETRY_DELAY_MS);
            }
        }

        throw new RuntimeException("Gemini " + MAX_ATTEMPTS + " marta urinib ham javob bermadi.");
    }

    public List<Section> generateSections(String topic, int slideCount,
                                          String language, int tariff) {
        int sectionCount = slideCount <= 10 ? 3 : slideCount <= 15 ? 4 : 5;

        String lang = (language != null) ? language : "UZ";

        String yearNote = switch (tariff) {
            case 3000 -> "Faqat 2024-yilgacha bo'lgan klassik ma'lumotlar.";
            case 5000 -> "2025-yilgacha yangi ma'lumotlar va tadqiqotlar.";
            case 7000 -> "2025-2026-yillardagi eng so'nggi statistikalar va yangiliklar.";
            default   -> "Mavzuga mos umumiy ma'lumotlar.";
        };

        String langNote = switch (lang) {
            case "RU"  -> "Barcha matnlar RUS tilida bo'lsin.";
            case "ENG" -> "All texts must be in ENGLISH.";
            default    -> "Barcha matnlar O'ZBEK tilida bo'lsin.";
        };

        String prompt = String.format("""
            Sen professional prezentatsiya tayyorlovchi AI assistantsan.
            Mavzu: '%s'. %s %s
     
            Aynan %d ta bo'lim tayyorla. Har bir bo'lim uchun:
            - sectionTitle: bo'lim nomi (qisqa, 3-6 so'z)
            - slide1: oddiy matn slayd → {title, bullets: aynan 4 ta punkt (har biri 1-2 gap), imageQuery}
            - slide2: rasm+matn slayd → {title, bullets: aynan 3 ta punkt (har biri 1-2 gap), imageQuery}
            - slide3: 3 ustunli slayd → {title, bullets: AYNAN 3 ta punkt (har biri 2-4 gap, to'liqroq)}
     
            imageQuery: shu slayd mavzusiga mos YUQORI SIFATLI professional PowerPoint rasmi uchun to'liq inglizcha AI text-to-image prompt.
     
            FAQAT sof JSON qaytar, ``` belgisi ISHLATMA.
            """, topic, langNote, yearNote, sectionCount);

        try {
            String rb = callGemini(prompt);

            JsonNode root = mapper.readTree(rb);
            String rawText = root.path("candidates").get(0)
                    .path("content").path("parts").get(0).path("text").asText();

            String clean = rawText.trim()
                    .replaceAll("(?s)```json\\s*", "")
                    .replaceAll("```", "").trim();

            int s = clean.indexOf('['), e = clean.lastIndexOf(']');
            if (s >= 0 && e > s) clean = clean.substring(s, e + 1);

            JsonNode arr = mapper.readTree(clean);
            List<Section> sections = new ArrayList<>();

            for (JsonNode node : arr) {
                String secTitle = node.path("sectionTitle").asText("Bo'lim");
                List<SlideContent> contents = new ArrayList<>();

                for (String key : new String[]{"slide1", "slide2", "slide3"}) {
                    JsonNode sn = node.path(key);
                    SlideContent sc = new SlideContent();
                    sc.title = sn.path("title").asText(secTitle);
                    sc.imageQuery = sn.path("imageQuery").asText(topic + " professional presentation");
                    sc.bullets = new ArrayList<>();
                    JsonNode bn = sn.path("bullets");
                    if (bn.isArray()) for (JsonNode b : bn) sc.bullets.add(b.asText());
                    contents.add(sc);
                }
                sections.add(new Section(secTitle, contents));
            }
            return sections;

        } catch (Exception ex) {
            System.err.println("generateSections xato: " + ex.getMessage());
            throw new RuntimeException("Section generatsiya xatosi: " + ex.getMessage());
        }
    }

    public record Section(String title, List<SlideContent> slides) {}

    public static class SlideContent {
        public String title;
        public List<String> bullets;
        public String imageQuery;
    }
}