package uz.ai.slideshowbot.slides;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SlideRequestTest {
    private final ObjectMapper json = new ObjectMapper();
    private ObjectNode payload() {
        ObjectNode data = json.createObjectNode();
        data.put("topic", "Tarmoq xavfsizligi"); data.put("name", "Ozodbek Jalilov");
        data.put("language", "UZ"); data.put("slideCount", "10"); data.put("template", "auto");
        data.put("tariff", "5000"); data.put("format", "pptx"); data.put("requestId", "request_12345678");
        return data;
    }

    @Test void acceptsOnlyRequestedBodyCountsAndPowerPoint() {
        for (int count : new int[]{10, 15, 20}) {
            SlideRequest request = SlideRequest.from(payload().put("slideCount", count).put("language", "TUR").put("template", "24"), "Fallback", "legacy_request_123");
            assertEquals(count, request.slideCount()); assertEquals("TUR", request.language());
            assertEquals("24", request.template()); assertEquals("pptx", request.format());
        }
        for (int count : new int[]{5, 9, 11, 25, 30})
            assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(payload().put("slideCount", count), "Name", "legacy_request_123"));
        assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(payload().put("format", "pdf"), "Name", "legacy_request_123"));
    }

    @Test void refusesForgedPricesAndFractionalNumbers() {
        for (String price : new String[]{"0", "-3000", "3000", "1", "5000.0", "2147483647"})
            assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(payload().put("tariff", price), "Name", "legacy_request_123"));
        assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(payload().put("slideCount", 10.7), "Name", "legacy_request_123"));
    }

    @Test void rejectsUnsupportedOptionsAndOversizedText() {
        assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(payload().put("slideCount", "31"), "Name", "legacy_request_123"));
        assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(payload().put("template", "25"), "Name", "legacy_request_123"));
        assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(payload().put("language", "XX"), "Name", "legacy_request_123"));
        assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(payload().put("format", "exe"), "Name", "legacy_request_123"));
        assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(payload().put("topic", "a".repeat(151)), "Name", "legacy_request_123"));
    }

    @Test void supportsLegacyRequestIdsButNeverAutofillsRequiredName() {
        ObjectNode data = payload(); data.remove("requestId"); data.putNull("name");
        assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(data, "Telegram Name", "legacy_123_456"));
        data.put("name", "Ozodbek Jalilov");
        SlideRequest request = SlideRequest.from(data, "User", "legacy_123_456");
        assertEquals("Ozodbek Jalilov", request.name()); assertEquals("legacy_123_456", request.requestId());
        assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(payload().putNull("topic"), "Name", "legacy_request_123"));
    }

    @Test void fullNameAndLanguageRequireAnExplicitUserChoice() {
        for (String name : new String[]{"", "   ", "Ozodbek", "12 34"})
            assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(payload().put("name", name), "Ozodbek Jalilov", "legacy_request_123"));
        ObjectNode withoutLanguage = payload(); withoutLanguage.remove("language");
        assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(withoutLanguage, "Ozodbek Jalilov", "legacy_request_123"));
        assertThrows(IllegalArgumentException.class, () -> SlideRequest.from(payload().put("language", ""), "Name", "legacy_request_123"));
    }
}
