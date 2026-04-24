package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class ImageSearchService {

    @Value("${fal.ai.key}")
    private String falApiKey;

    private static final String FAL_URL = "https://fal.run/fal-ai/flux/schnell";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    private final ObjectMapper mapper = new ObjectMapper();


    public byte[] findImageBytes(String imagePrompt, String topic) {
        String prompt = (imagePrompt != null && imagePrompt.trim().length() > 15)
                ? imagePrompt.trim()
                : "Professional high quality photo of " + topic
                + ", cinematic lighting, sharp detail, 4k, clean composition, realistic";

        System.out.println("🎨 FAL prompt (" + prompt.length() + " chars): "
                + prompt.substring(0, Math.min(120, prompt.length())));

        return callFal(prompt);
    }

    private byte[] callFal(String prompt) {
        try {
            Map<String, Object> input = new HashMap<>();
            input.put("prompt", prompt);
            input.put("image_size", Map.of("width", 1280, "height", 720));
            input.put("num_inference_steps", 4);
            input.put("guidance_scale", 3.5);
            input.put("num_images", 1);

            String json = mapper.writeValueAsString(input);

            Request request = new Request.Builder()
                    .url(FAL_URL)
                    .addHeader("Authorization", "Key " + falApiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(json, MediaType.get("application/json")))
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();

                    Map<String, Object> result = mapper.readValue(responseBody, Map.class);
                    @SuppressWarnings("unchecked")
                    List<Map<String, String>> images = (List<Map<String, String>>) result.get("images");

                    if (images != null && !images.isEmpty()) {
                        String imageUrl = images.get(0).get("url");

                        byte[] imageBytes = downloadImage(imageUrl);
                        if (imageBytes != null && imageBytes.length > 50_000) {
                            System.out.println("✅ FAL rasm muvaffaqiyatli yuklandi: "
                                    + imageBytes.length / 1024 + " KB");
                            return imageBytes;
                        }
                    }
                } else {
                    System.err.println("❌ FAL xato: " + response.code() + " " + response.message());
                    if (response.body() != null) {
                        System.err.println("Javob: " + response.body().string());
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ FAL exception: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private byte[] downloadImage(String imageUrl) {
        try {
            Request req = new Request.Builder().url(imageUrl).build();
            try (Response resp = client.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    return resp.body().bytes();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Rasm yuklashda xato: " + e.getMessage());
        }
        return null;
    }
}