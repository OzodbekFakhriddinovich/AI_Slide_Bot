package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(45, TimeUnit.SECONDS)
            .writeTimeout(45, TimeUnit.SECONDS)
            .build();

    public List<SlideData> generateSlides(String topic, int slideCount, String language) {
        String prompt = String.format(
                "Sen prezentatsiya materiallari tayyorlaydigan mutaxassisan. Mavzu: '%s'. Slaydlar soni: %d. Til: %s. " +
                        "Har bir slayd uchun: 'title', 'bullets' va 'imageQuery' tayyorla. " +
                        "Javobni FAQAT toza JSON formatida, hech qanday qo'shimcha matnsiz qaytar. " +
                        "Format: [{\"title\": \"...\", \"bullets\": [\"...\", \"...\"], \"imageQuery\": \"...\"}]",
                topic, slideCount, language
        );

        try {
            Map<String, Object> requestBodyMap = Map.of(
                    "contents", List.of(Map.of(
                            "parts", List.of(Map.of("text", prompt))
                    ))
            );

            String jsonRequest = mapper.writeValueAsString(requestBodyMap);
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

                String cleanJson = content.trim();
                if (cleanJson.contains("[")) {
                    cleanJson = cleanJson.substring(cleanJson.indexOf("["), cleanJson.lastIndexOf("]") + 1);
                }

                return mapper.readValue(cleanJson, new TypeReference<List<SlideData>>() {});
            }
        } catch (Exception e) {
            System.err.println("Slayd yaratishda xatolik: " + e.getMessage());
            throw new RuntimeException("Slayd yaratishda xatolik: " + e.getMessage());
        }
    }

    public static class SlideData {
        public String title;
        public List<String> bullets;
        public String imageQuery;
    }
}