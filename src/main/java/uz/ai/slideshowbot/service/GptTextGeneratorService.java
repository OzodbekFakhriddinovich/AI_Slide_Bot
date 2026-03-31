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

    @Value("${gemini.api-key}")
    private String apiKey;

    private final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=";

    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public List<Section> generateSections(String topic, int slideCount,
                                          String language, int tariff) {
        int sectionCount = slideCount <= 10 ? 3 : slideCount <= 15 ? 4 : 5;

        String yearNote = switch (tariff) {
            case 3000 -> "Faqat 2024-yilgacha bo'lgan klassik ma'lumotlar.";
            case 5000 -> "2025-yilgacha yangi ma'lumotlar va tadqiqotlar.";
            case 7000 -> "2025-2026-yillardagi eng so'nggi statistikalar va yangiliklar.";
            default   -> "Mavzuga mos umumiy ma'lumotlar.";
        };

        String langNote = switch (language) {
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
            
            imageQuery inglizcha 3-5 so'z, geometrik/texnologik: masalan "hexagon network security diagram"
            
            FAQAT sof JSON qaytar, ``` belgisi ISHLATMA:
            [
              {
                "sectionTitle": "...",
                "slide1": {"title":"...","bullets":["...","...","...","..."],"imageQuery":"..."},
                "slide2": {"title":"...","bullets":["...","...","..."],"imageQuery":"..."},
                "slide3": {"title":"...","bullets":["...","...","..."],"imageQuery":"..."}
              }
            ]
            """, topic, langNote, yearNote, sectionCount);

        try {
            String jsonReq = mapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
            ));
            RequestBody body = RequestBody.create(jsonReq, MediaType.get("application/json"));
            Request req = new Request.Builder().url(GEMINI_URL + apiKey).post(body).build();

            try (Response response = client.newCall(req).execute()) {
                String rb = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    System.err.println("Gemini error: " + rb);
                    throw new RuntimeException("API error: " + response.code());
                }

                JsonNode root    = mapper.readTree(rb);
                String rawText   = root.path("candidates").get(0)
                        .path("content").path("parts").get(0).path("text").asText();

                String clean = rawText.trim()
                        .replaceAll("(?s)```json\\s*", "")
                        .replaceAll("```", "").trim();
                int s = clean.indexOf('['), e = clean.lastIndexOf(']');
                if (s >= 0 && e > s) clean = clean.substring(s, e + 1);

                JsonNode arr     = mapper.readTree(clean);
                List<Section> sections = new ArrayList<>();

                for (JsonNode node : arr) {
                    String secTitle = node.path("sectionTitle").asText("Bo'lim");
                    List<SlideContent> contents = new ArrayList<>();

                    for (String key : new String[]{"slide1", "slide2", "slide3"}) {
                        JsonNode sn = node.path(key);
                        SlideContent sc = new SlideContent();
                        sc.title      = sn.path("title").asText(secTitle);
                        sc.imageQuery = sn.path("imageQuery").asText(topic + " diagram");
                        sc.bullets    = new ArrayList<>();
                        JsonNode bn   = sn.path("bullets");
                        if (bn.isArray()) for (JsonNode b : bn) sc.bullets.add(b.asText());
                        contents.add(sc);
                    }
                    sections.add(new Section(secTitle, contents));
                }
                return sections;
            }

        } catch (Exception ex) {
            System.err.println("generateSections xato: " + ex.getMessage());
            throw new RuntimeException("Section generatsiya xatosi: " + ex.getMessage());
        }
    }

    public List<SlideData> generateSlides(String topic, int slideCount,
                                          String language, int tariff) {
        List<Section> sections = generateSections(topic, slideCount, language, tariff);
        List<SlideData> result = new ArrayList<>();
        for (Section sec : sections) {
            for (SlideContent sc : sec.slides()) {
                SlideData sd = new SlideData();
                sd.title      = sc.title;
                sd.bullets    = sc.bullets;
                sd.imageQuery = sc.imageQuery;
                result.add(sd);
            }
        }
        return result;
    }

    public record Section(String title, List<SlideContent> slides) {}

    public static class SlideContent {
        public String       title;
        public List<String> bullets;
        public String       imageQuery;
    }

    public static class SlideData {
        public String       title;
        public List<String> bullets;
        public String       imageQuery;
    }
}