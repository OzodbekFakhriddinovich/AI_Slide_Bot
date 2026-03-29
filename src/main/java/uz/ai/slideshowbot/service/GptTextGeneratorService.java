package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    // ─────────────────────────────────────────────────────────────────────────
    //  Asosiy public metod: 4 ta reja bo'limi + slideCount ta kontent slayd
    // ─────────────────────────────────────────────────────────────────────────
    public GeneratedContent generatePresentation(String topic, int slideCount, String language, int tariff) {

        String yearInstruction = switch (tariff) {
            case 3000 -> "Faqat 2024-yilgacha bo'lgan ma'lumotlardan foydalangin.";
            case 5000 -> "2024-yildan keyingi yangi ma'lumotlar, so'nggi yangiliklar va tadqiqotlarni qo'shgin.";
            case 7000 -> "2025-2026-yillardagi eng so'nggi ma'lumotlar, statistikalar va yangiliklar asosida tayyorla.";
            default   -> "Mavzuga mos umumiy ma'lumotlar yetarli.";
        };

        // Til bo'yicha yo'riqnoma
        String langNote = switch (language) {
            case "RU"  -> "Barcha matnlar RUS tilida bo'lsin.";
            case "ENG" -> "All text must be in ENGLISH.";
            default    -> "Barcha matnlar O'ZBEK tilida bo'lsin.";
        };

        String prompt = String.format("""
            Sen professional prezentatsiya yaratuvchi AI assistantsan.
            Mavzu: '%s'. Jami %d ta kontent slayd tayyorla. %s %s
            
            QOIDALAR:
            1. sections - bu Reja sahifasi uchun FAQAT 4 ta asosiy bo'lim nomi (qisqa, aniq).
            2. slides - bu %d ta kontent slayd. Har birida:
               - title: slayd sarlavhasi (qisqa)
               - bullets: 3-4 ta muhim punkt (har biri 1-2 gap)
               - imageQuery: inglizcha rasm qidiruv so'zi (aniq, mavzuga mos, 3-5 so'z)
            3. imageQuery matematik yoki geometrik dizaynlar bo'lsin:
               masalan: "hexagon network diagram", "circle infographic technology",
               "triangle data visualization", "pentagon security chart"
            
            JAVOBNI FAQAT TOZA JSON formatida qaytar, hech qanday kod bloki yo'q:
            {
              "sections": ["1-bo'lim", "2-bo'lim", "3-bo'lim", "4-bo'lim"],
              "slides": [
                {"title": "...", "bullets": ["...", "...", "..."], "imageQuery": "..."},
                ...
              ]
            }
            """,
                topic, slideCount, langNote, yearInstruction, slideCount);

        try {
            String jsonRequest = mapper.writeValueAsString(Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", prompt))
                    ))
            ));

            RequestBody body = RequestBody.create(jsonRequest, MediaType.get("application/json"));
            Request request = new Request.Builder()
                    .url(GEMINI_URL + apiKey)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";

                if (!response.isSuccessful()) {
                    System.err.println("Gemini API xatosi: " + responseBody);
                    throw new RuntimeException("API xatosi kodi: " + response.code());
                }

                JsonNode root = mapper.readTree(responseBody);
                JsonNode candidates = root.path("candidates");

                if (candidates.isMissingNode() || candidates.size() == 0) {
                    throw new RuntimeException("Gemini mos javob qaytarmadi.");
                }

                String content = candidates.get(0)
                        .path("content").path("parts").get(0)
                        .path("text").asText();

                // JSON tozalash
                String cleanJson = content.trim()
                        .replaceAll("(?s)```json\\s*", "")
                        .replaceAll("```", "")
                        .trim();

                // Faqat {...} qismini olish
                int start = cleanJson.indexOf('{');
                int end   = cleanJson.lastIndexOf('}');
                if (start >= 0 && end > start) {
                    cleanJson = cleanJson.substring(start, end + 1);
                }

                JsonNode parsed = mapper.readTree(cleanJson);

                // sections (4 ta)
                List<String> sections = new ArrayList<>();
                JsonNode sectionsNode = parsed.path("sections");
                if (sectionsNode.isArray()) {
                    for (JsonNode s : sectionsNode) {
                        sections.add(s.asText());
                        if (sections.size() == 4) break;
                    }
                }
                // Agar kamroq kelsa to'ldirish
                while (sections.size() < 4) {
                    sections.add("Bo'lim " + (sections.size() + 1));
                }

                // slides
                List<SlideData> slides = new ArrayList<>();
                JsonNode slidesNode = parsed.path("slides");
                if (slidesNode.isArray()) {
                    for (JsonNode s : slidesNode) {
                        SlideData sd = new SlideData();
                        sd.title      = s.path("title").asText("Sarlavha");
                        sd.imageQuery = s.path("imageQuery").asText(topic);
                        sd.bullets    = new ArrayList<>();
                        JsonNode bulletsNode = s.path("bullets");
                        if (bulletsNode.isArray()) {
                            for (JsonNode b : bulletsNode) {
                                sd.bullets.add(b.asText());
                            }
                        }
                        slides.add(sd);
                    }
                }

                return new GeneratedContent(sections, slides);
            }
        } catch (Exception e) {
            System.err.println("Prezentatsiya yaratishda xatolik: " + e.getMessage());
            throw new RuntimeException("Prezentatsiya yaratishda xatolik: " + e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Orqaga moslik uchun (eski kod uchun) - endi generatePresentation ishlatilsin
    // ─────────────────────────────────────────────────────────────────────────
    public List<SlideData> generateSlides(String topic, int slideCount, String language, int tariff) {
        return generatePresentation(topic, slideCount, language, tariff).slides();
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Record: Gemini javobini saqlash
    // ─────────────────────────────────────────────────────────────────────────
    public record GeneratedContent(List<String> sections, List<SlideData> slides) {}

    // ─────────────────────────────────────────────────────────────────────────
    //  SlideData - har bir kontent slayd ma'lumoti
    // ─────────────────────────────────────────────────────────────────────────
    public static class SlideData {
        public String       title;
        public List<String> bullets;
        public String       imageQuery;
    }
}