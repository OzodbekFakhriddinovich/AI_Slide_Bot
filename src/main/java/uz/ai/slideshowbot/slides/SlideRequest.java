package uz.ai.slideshowbot.slides;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Locale;
import java.util.Arrays;
import java.util.Set;

/** Validate every value again on the server; the WebApp payload is client controlled. */
public record SlideRequest(String topic, String name, String language, int slideCount,
                           String template, int tariff, String format, String requestId) {
    private static final Set<String> LANGUAGES = Set.of("UZ", "ENG", "RU");

    public static SlideRequest from(JsonNode data, String defaultName, String legacyRequestId) {
        return from(data, defaultName, legacyRequestId, null);
    }

    /** The bot profile is authoritative; a WebApp payload cannot switch the deck language. */
    public static SlideRequest from(JsonNode data, String defaultName, String legacyRequestId, String botLanguage) {
        String interfaceLanguage = normalizeLanguage(botLanguage);
        if (data == null || !data.isObject()) throw invalid("Forma ma'lumoti noto'g'ri.");
        String topic = text(data, "topic", "").strip();
        String name = text(data, "name", "").strip().replaceAll("(?U)\\s+", " ");
        String language = botLanguage == null
                ? text(data, "language", "").toUpperCase(Locale.ROOT)
                : interfaceLanguage;
        String template = text(data, "template", "auto").toLowerCase(Locale.ROOT);
        String format = text(data, "format", "pptx").toLowerCase(Locale.ROOT);
        String requestId = text(data, "requestId", legacyRequestId);
        int count = integer(data, "slideCount", 10);
        int tariff = integer(data, "tariff", 5000);

        if (topic.length() < 3 || topic.length() > 150 || topic.codePoints().anyMatch(Character::isISOControl))
            throw invalid("Mavzu 3–150 belgidan iborat bo'lsin.");
        if (name.length() < 3 || name.length() > 80 || name.codePoints().anyMatch(Character::isISOControl)
                || name.split("(?U)\\s+").length < 2 || name.split("(?U)\\s+").length > 8
                || Arrays.stream(name.split("(?U)\\s+")).anyMatch(part -> part.codePoints().noneMatch(Character::isLetter))
                || name.codePoints().filter(Character::isLetter).count() < 3)
            throw invalid("Ism va familiyangizni to'liq kiriting (80 belgigacha).");
        if (!LANGUAGES.contains(language)) throw invalid(interfaceLanguage, "Web appni qayta oching.", "Откройте веб-приложение заново.", "Reopen the WebApp.");
        if (!Set.of(10, 15, 20).contains(count)) throw invalid("10, 15 yoki 20 ta asosiy slaydni tanlang.");
        if (!template.equals("auto")) {
            if (!template.matches("[1-9][0-9]?")) throw invalid("Dizayn tanlovi noto'g'ri.");
            int n = Integer.parseInt(template);
            if (n > 24) throw invalid("Dizayn tanlovi noto'g'ri.");
        }
        // These identifiers map to fixed server prices; arbitrary amounts are never accepted.
        int price = switch (tariff) {
            case 5000 -> 5000;
            case 7000 -> 7000;
            default -> throw invalid("Tarif tanlovi noto'g'ri. Web appni yangidan oching.");
        };
        if (!format.equals("pptx")) throw invalid("Taqdimot faqat PowerPoint (PPTX) formatida tayyorlanadi. Web appni yangidan oching.");
        if (requestId == null || !requestId.matches("[A-Za-z0-9_-]{8,80}"))
            throw invalid("Buyurtma raqami noto'g'ri. Web appni yangidan oching.");
        return new SlideRequest(topic, name, language, count, template, price, format, requestId);
    }

    private static String normalizeLanguage(String language) {
        String normalized = language == null ? "UZ" : language.toUpperCase(Locale.ROOT);
        return LANGUAGES.contains(normalized) ? normalized : "UZ";
    }

    private static IllegalArgumentException invalid(String language, String uz, String ru, String en) {
        return invalid("RU".equals(language) ? ru : "ENG".equals(language) ? en : uz);
    }

    private static String text(JsonNode data, String key, String fallback) {
        JsonNode value = data.get(key);
        if (value == null || value.isNull()) return fallback;
        if (!value.isTextual()) throw invalid("Forma maydoni noto'g'ri: " + key);
        return value.textValue();
    }

    private static int integer(JsonNode data, String key, int fallback) {
        JsonNode value = data.get(key);
        if (value == null || value.isNull()) return fallback;
        String raw = value.isIntegralNumber() || value.isTextual() ? value.asText() : "";
        if (!raw.matches("[0-9]{1,5}")) throw invalid("Raqamli maydon noto'g'ri: " + key);
        try { return Integer.parseInt(raw); }
        catch (NumberFormatException e) { throw invalid("Raqamli maydon noto'g'ri: " + key); }
    }

    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
