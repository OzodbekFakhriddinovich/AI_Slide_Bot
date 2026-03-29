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
import java.util.concurrent.TimeUnit;

@Service
public class ImageSearchService {

    @Value("${pixabay.api-key}")
    private String apiKey;

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Mavzuga mos katta, sifatli rasm URL ni qaytaradi.
     * Prioritet: fullHDURL → largeImageURL → webformatURL → fallback
     */
    public String findImageUrl(String query, String topic) {
        // Birinchi urinish: to'liq query bilan
        String url = trySearch(query + " " + topic);
        if (url != null) return url;

        // Ikkinchi urinish: faqat topic bilan
        url = trySearch(topic + " concept");
        if (url != null) return url;

        // Uchinchi urinish: keng qidiruv
        url = trySearch(topic);
        if (url != null) return url;

        // Fallback - aniq o'lchamli placeholder (katta fayl uchun)
        return "https://picsum.photos/1920/1080";
    }

    private String trySearch(String searchText) {
        try {
            String encodedQuery = URLEncoder.encode(searchText, StandardCharsets.UTF_8);

            // per_page=5 - eng yaxshi 5 ta variantdan birini tanlaydi
            String url = String.format(
                    "https://pixabay.com/api/?key=%s&q=%s&image_type=photo" +
                            "&per_page=5&safesearch=true&min_width=1280&order=popular",
                    apiKey, encodedQuery
            );

            Request request = new Request.Builder().url(url).build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful() || response.body() == null) return null;

                String body = response.body().string();
                JsonNode root = mapper.readTree(body);
                JsonNode hits = root.path("hits");

                if (!hits.isArray() || hits.size() == 0) return null;

                // Eng katta rasmni tanlash (piksel bo'yicha)
                JsonNode best     = null;
                long    bestPixels = 0;

                for (JsonNode hit : hits) {
                    long pixels = hit.path("imageWidth").asLong(0)
                            * hit.path("imageHeight").asLong(0);
                    if (pixels > bestPixels) {
                        bestPixels = pixels;
                        best       = hit;
                    }
                }

                if (best == null) best = hits.get(0);

                // Eng katta mavjud URL ni qaytarish
                // fullHDURL → largeImageURL → webformatURL
                String fullHD = best.path("fullHDURL").asText(null);
                if (fullHD != null && !fullHD.isBlank()) return fullHD;

                String large = best.path("largeImageURL").asText(null);
                if (large != null && !large.isBlank()) return large;

                String webformat = best.path("webformatURL").asText(null);
                if (webformat != null && !webformat.isBlank()) return webformat;

                return null;
            }
        } catch (Exception e) {
            System.err.println("Pixabay xatosi (" + searchText + "): " + e.getMessage());
            return null;
        }
    }
}