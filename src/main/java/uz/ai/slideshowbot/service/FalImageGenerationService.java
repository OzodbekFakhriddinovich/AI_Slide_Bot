package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Server-side, topic-conditioned illustration generation through fal.ai. */
@Service
public class FalImageGenerationService {
    private static final String MODEL="fal-ai/flux/schnell";
    private static final int MAX_IMAGE_BYTES=6*1024*1024;
    private final String apiKey;
    private final OkHttpClient client=new OkHttpClient.Builder()
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(100,TimeUnit.SECONDS)
            .callTimeout(120,TimeUnit.SECONDS).followRedirects(true).build();
    private final ObjectMapper mapper=new ObjectMapper();

    public FalImageGenerationService(@Value("${fal.ai.key:${FAL_KEY:}}") String apiKey) {
        this.apiKey=apiKey==null?"":apiKey.trim();
    }

    public boolean isConfigured() { return !apiKey.isBlank(); }

    public ImageSearchService.ImageAsset generate(String topic,String title,String query,java.util.List<String> facts) throws IOException {
        if(!isConfigured()) throw new IOException("FAL_KEY is not configured");
        String detail=facts==null?"":facts.stream().limit(3).map(FalImageGenerationService::clean)
                .filter(value->!value.isBlank()).reduce((a,b)->a+"; "+b).orElse("");
        String prompt="Create one accurate educational editorial image for a PowerPoint slide. Topic: "+clean(topic)
                +". This slide explains: "+clean(title)+". Visual subject: "+clean(query)+". Relevant facts: "+detail
                +". Depict the actual subject and its real-world context in a polished, realistic 3D editorial illustration, landscape 16:9 composition, clear focal subject, detailed but uncluttered, professional academic presentation quality. No text, letters, numbers, labels, logos, watermark, collage, UI, or slide frame.";
        JsonNode body=mapper.createObjectNode().put("prompt",prompt).put("image_size","landscape_16_9")
                .put("num_images",1).put("output_format","jpeg").put("num_inference_steps",4);
        Request request=new Request.Builder().url("https://fal.run/"+MODEL)
                .header("Authorization","Key "+apiKey).header("Content-Type","application/json")
                .post(RequestBody.create(body.toString(),MediaType.get("application/json; charset=utf-8"))).build();
        String imageUrl;
        try(Response response=client.newCall(request).execute()) {
            if(!response.isSuccessful() || response.body()==null) throw new IOException("fal.ai image generation failed (HTTP "+response.code()+")");
            JsonNode images=mapper.readTree(response.body().bytes()).path("images");
            imageUrl=images.path(0).path("url").asText();
        }
        if(!trustedImageUrl(imageUrl)) throw new IOException("fal.ai returned an unsupported image URL");
        Request download=new Request.Builder().url(imageUrl).build();
        try(Response response=client.newCall(download).execute()) {
            if(!response.isSuccessful() || response.body()==null) throw new IOException("Could not download generated image");
            String type=response.header("Content-Type","").toLowerCase(Locale.ROOT);
            if(!type.startsWith("image/jpeg") && !type.startsWith("image/png")) throw new IOException("fal.ai returned a non-image result");
            byte[] bytes=response.body().source().readByteArray(MAX_IMAGE_BYTES+1L);
            if(bytes.length>MAX_IMAGE_BYTES || !ImageSearchService.validImage(bytes)) throw new IOException("Generated image is invalid or too large");
            return new ImageSearchService.ImageAsset(bytes,"AI illustration: "+clean(title),imageUrl,
                    "AI-generated with fal.ai FLUX.1 schnell");
        }
    }

    private static boolean trustedImageUrl(String value) {
        try {
            URI uri=URI.create(value); String host=uri.getHost();
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo()==null
                    && (uri.getPort()==-1 || uri.getPort()==443) && host!=null
                    && (host.equalsIgnoreCase("fal.media") || host.toLowerCase(Locale.ROOT).endsWith(".fal.media"));
        } catch(RuntimeException invalid) { return false; }
    }

    private static String clean(String value) {
        if(value==null) return "";
        return value.replaceAll("[\\p{Cntrl}]"," ").replaceAll("\\s+"," ").trim();
    }
}
