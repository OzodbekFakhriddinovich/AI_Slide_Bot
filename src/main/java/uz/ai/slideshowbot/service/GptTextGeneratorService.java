package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class GptTextGeneratorService {

    @Value("${gemini.api.keys:}")
    private String apiKeysRaw;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=";

    private final ObjectMapper mapper = new ObjectMapper();

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
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

    public String callGemini(String prompt, boolean isJson) throws Exception {
        List<String> keys = getApiKeys();
        if (keys.isEmpty()) {
            throw new RuntimeException("Gemini API key topilmadi! application.yml ni tekshiring.");
        }

        String key = keys.get(0);
        String url = GEMINI_URL + key;

        Map<String, Object> reqMap = new HashMap<>();
        reqMap.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        if (isJson) {
            reqMap.put("generationConfig", Map.of("response_mime_type", "application/json"));
        }
        String jsonReq = mapper.writeValueAsString(reqMap);

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            System.out.println("🔑 Gemini 3.5 Flash urinish " + attempt + "/" + MAX_ATTEMPTS);

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
            case 3000 -> "Use classic well-established information up to 2024.";
            case 5000 -> "Include recent research and data up to 2025.";
            case 7000 -> "Use the most current 2025-2026 statistics, breakthroughs and innovations.";
            default   -> "Use accurate and comprehensive information relevant to the topic.";
        };

        String langNote = switch (lang) {
            case "RU"  -> "Write ALL content in RUSSIAN language.";
            case "ENG" -> "Write ALL content in ENGLISH language.";
            default    -> "Write ALL content in UZBEK language (use Latin script, NOT Cyrillic).";
        };

        String prompt = String.format("""
        You are a professional academic presentation writer creating a high-quality slide deck.
        Topic: '%s'
        Output language: %s
        Content quality note: %s

        Create EXACTLY %d sections. Each section MUST contain REAL, SPECIFIC and ACCURATE information:
        - Use CONCRETE statistics with actual numbers (e.g., "GDP grew by 3.2%% in 2023", not "economy grew")
        - Use SPECIFIC dates, years, names of scientists/countries/organizations
        - Use REAL facts that are verifiable and academically accurate
        - Every bullet point must be a complete, informative sentence with specific data
        - NO generic or vague statements like "important factor" or "plays a key role"

        Return ONLY a pure JSON array with EXACTLY %d objects. No markdown, no ```, no explanations.

        Each object MUST follow this exact structure:
        {
          "sectionTitle": "Section heading",
          "slide1": {
            "title": "Specific slide title",
            "bullets": [
              "Specific fact with real data, year or statistic",
              "Another concrete fact with name or number",
              "Third specific fact with measurable result",
              "Fourth fact with real-world example"
            ],
            "imageQuery": "specific professional photo description in english, 4k realistic"
          },
          "slide2": { same structure },
          "slide3": { same structure }
        }

        CRITICAL RULES:
        - Return ONLY the JSON array: [ {section1}, {section2}, ... ]
        - Each section has exactly slide1, slide2, slide3
        - Each slide has exactly 4 bullets with REAL specific facts
        - imageQuery must be in English, detailed and descriptive
        - NO placeholder text, NO generic phrases
        """, topic, langNote, yearNote, sectionCount, sectionCount);

        try {
            String rb = callGemini(prompt, true);

            JsonNode root = mapper.readTree(rb);
            String rawText = root.path("candidates").get(0)
                    .path("content").path("parts").get(0).path("text").asText();

            String clean = rawText.trim()
                    .replaceAll("(?s)<thinking>.*?</thinking>", "")
                    .trim();

            if (clean.startsWith("```json")) {
                clean = clean.substring(7);
            } else if (clean.startsWith("```")) {
                clean = clean.substring(3);
            }
            if (clean.endsWith("```")) {
                clean = clean.substring(0, clean.length() - 3);
            }
            clean = clean.trim();

            int s = clean.indexOf('['), e = clean.lastIndexOf(']');
            if (s >= 0 && e > s) clean = clean.substring(s, e + 1);

            ObjectMapper flexMapper = new ObjectMapper();
            flexMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS, true);
            flexMapper.configure(com.fasterxml.jackson.core.JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
            flexMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

            JsonNode arr = flexMapper.readTree(clean);
            List<Section> sections = new ArrayList<>();

            for (JsonNode node : arr) {
                String secTitle = node.path("sectionTitle").asText("Bo'lim " + (sections.size() + 1));

                List<SlideContent> contents = new ArrayList<>();
                for (String key : new String[]{"slide1", "slide2", "slide3"}) {
                    JsonNode sn = node.path(key);
                    SlideContent sc = new SlideContent();

                    sc.title = sn.path("title").asText(secTitle);

                    sc.bullets = new ArrayList<>();
                    JsonNode bn = sn.path("bullets");
                    if (bn.isArray() && bn.size() >= 3) {
                        for (JsonNode b : bn) sc.bullets.add(b.asText());
                    } else {
                        sc.bullets.add(secTitle + " — asosiy yo'nalishlar va ta'rif");
                        sc.bullets.add("Ilmiy tadqiqotlar va zamonaviy ma'lumotlar");
                        sc.bullets.add("Amaliy qo'llanish sohalari va real misollar");
                        sc.bullets.add("Natijalar, xulosalar va perspektivalar");
                    }

                    sc.imageQuery = sn.path("imageQuery").asText(topic + " professional presentation, high quality, cinematic lighting, detailed");

                    contents.add(sc);
                }
                sections.add(new Section(secTitle, contents));
            }
            return sections;

        } catch (Exception ex) {
            System.err.println("generateSections xato: " + ex.getMessage());
            ex.printStackTrace();

            List<Section> fallbackSections = new ArrayList<>();
            for (int i = 1; i <= sectionCount; i++) {
                String secTitle = topic + " — " + i + "-qism";
                List<SlideContent> slides = new ArrayList<>();
                for (int j = 1; j <= 3; j++) {
                    SlideContent sc = new SlideContent();
                    sc.title = topic + ": " + i + "." + j + "-slayd";
                    sc.bullets = List.of(
                            topic + " bo'yicha asosiy tushunchalar va ta'riflar",
                            "Zamonaviy yondashuvlar va sohadagi so'nggi tadqiqotlar",
                            "Amaliy qo'llanish sohalari va texnik imkoniyatlar",
                            "Xulosalar, erishilgan natijalar va kelajak istiqbollari"
                    );
                    sc.imageQuery = topic + " presentation background, 4k realistic";
                    slides.add(sc);
                }
                fallbackSections.add(new Section(secTitle, slides));
            }
            return fallbackSections;
        }
    }

    public record Section(String title, List<SlideContent> slides) {}

    public static class SlideContent {
        public String title;
        public List<String> bullets;
        public String imageQuery;
    }
}
