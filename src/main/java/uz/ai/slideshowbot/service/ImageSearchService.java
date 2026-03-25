package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class ImageSearchService {

    @Value("${google.search.api-key}")
    private String apiKey;

    @Value("${google.search.cx}")
    private String cx;

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();


    public String findImageUrl(String query) {
        try {
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);


            String url = String.format(
                    "https://www.googleapis.com/customsearch/v1?q=%s&cx=%s&key=%s&searchType=image&num=1",
                    encodedQuery, cx, apiKey
            );

            Request request = new Request.Builder()
                    .url(url)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    JsonNode root = mapper.readTree(responseBody);

                    if (root.has("items") && root.get("items").isArray() && root.get("items").size() > 0) {
                        return root.get("items").get(0).get("link").asText();
                    }
                } else {
                    System.err.println("Google API xatosi: " + response.code() + " " + response.message());
                }
            }
        } catch (Exception e) {
            System.err.println("Rasm qidirishda kutilmagan xatolik: " + e.getMessage());
        }

        return "https://picsum.photos/800/600";
    }
}