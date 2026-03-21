package uz.ai.slideshowbot.service;

import com.theokanning.openai.completion.CompletionChoice;
import com.theokanning.openai.completion.CompletionRequest;
import com.theokanning.openai.service.OpenAiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class GptTextGeneratorService {

    private final OpenAiService openAiService;
    private final ObjectMapper mapper = new ObjectMapper();

    public GptTextGeneratorService(@Value("${openai.api-key}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("❌ OpenAI API key not found in application.yml");
        }
        this.openAiService = new OpenAiService(apiKey, Duration.ofSeconds(60));
    }

    @PostConstruct
    public void verifyApiKey() {
        System.out.println("✅ OpenAI API key successfully loaded.");
    }

    public List<SlideData> generateSlides(String topic, int slideCount) throws Exception {
        return generateSlides(topic, slideCount, "uz");
    }

    public List<SlideData> generateSlides(String topic, int slideCount, String language) throws Exception {
        String prompt = String.format("""
            Create %d detailed slides about "%s" in %s.
            Each slide must follow these rules:
            - "title": 1 meaningful sentence summarizing the slide content (at least 5 words)
            - "bullets": 3–5 bullet points, each with real examples, statistics, or explanations
            - "image": a short description of an image illustrating the slide concept
            Return ONLY a valid JSON array WITHOUT extra text or comments:
            [{"title":"...", "bullets":["...","..."], "image":"..."}]
            """, slideCount, topic, language);

        CompletionRequest request = CompletionRequest.builder()
                .model("gpt-3.5-turbo-instruct")
                .prompt(prompt)
                .maxTokens(2000)
                .temperature(0.7)
                .build();

        List<CompletionChoice> choices = openAiService.createCompletion(request).getChoices();
        if (choices.isEmpty()) throw new RuntimeException("❌ GPT returned no response.");

        String result = choices.get(0).getText().trim()
                .replaceAll("(?m)^```json\\s*", "")
                .replaceAll("(?m)^```$", "");

        SlideData[] slides;
        try {
            slides = mapper.readValue(result, SlideData[].class);
        } catch (Exception e) {
            throw new RuntimeException("❌ JSON parse xatosi. GPT javobi:\n" + result, e);
        }

        List<SlideData> slideList = new ArrayList<>();
        for (SlideData s : slides) slideList.add(s);

        return slideList;
    }

    public static class SlideData {
        public String title;
        public List<String> bullets;
        public String image;
    }
}
