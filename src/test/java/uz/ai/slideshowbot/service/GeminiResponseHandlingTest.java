package uz.ai.slideshowbot.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeminiResponseHandlingTest {
    private final GptTextGeneratorService generator = new GptTextGeneratorService();

    @Test void joinsTextPartsFromTheSuccessfulCandidate() throws Exception {
        String response = """
                {"candidates":[{"finishReason":"STOP","content":{"parts":[{"text":"Hello "},{"text":"world"}]}}]}
                """;
        assertEquals("Hello world", generator.extractGeneratedText(response));
    }

    @Test void explainsMissingOrTruncatedCandidatesInsteadOfThrowingIndexErrors() {
        assertTrue(assertThrows(IllegalStateException.class,
                () -> generator.extractGeneratedText("{\"candidates\":[]}"))
                .getMessage().contains("no text candidate"));
        assertTrue(assertThrows(IllegalStateException.class,
                () -> generator.extractGeneratedText("{\"candidates\":[{\"finishReason\":\"MAX_TOKENS\"}]}"))
                .getMessage().contains("output limit"));
    }
}
