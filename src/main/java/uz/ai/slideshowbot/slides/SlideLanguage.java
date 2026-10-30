package uz.ai.slideshowbot.slides;

import java.util.Locale;
import java.util.Set;

/** Language rules shared by the WebApp order and slide renderer. */
public final class SlideLanguage {
    private static final Set<String> BOT_LANGUAGES = Set.of("UZ", "RU", "ENG");
    private SlideLanguage() {}

    public static String normalize(String language) {
        String value = language == null ? "UZ" : language.toUpperCase(Locale.ROOT);
        return BOT_LANGUAGES.contains(value) ? value : "UZ";
    }

}
