package uz.ai.slideshowbot.slides;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class SlideLanguageTest {
    @Test void normalizesOnlyLanguagesSupportedByTheBot() {
        assertEquals("RU", SlideLanguage.normalize("ru"));
        assertEquals("ENG", SlideLanguage.normalize("ENG"));
        assertEquals("UZ", SlideLanguage.normalize("TUR"));
        assertEquals("UZ", SlideLanguage.normalize(null));
    }

}
