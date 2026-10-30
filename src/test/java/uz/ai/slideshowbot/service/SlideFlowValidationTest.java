package uz.ai.slideshowbot.service;

import org.junit.jupiter.api.Test;
import uz.ai.slideshowbot.slides.SlideDeck;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SlideFlowValidationTest {
    private SlideDeck.Slide slide(String title, String point) {
        return new SlideDeck.Slide(title, "", List.of(point), SlideDeck.Layout.EDITORIAL, "",
                List.of(new SlideDeck.Source("Source", "https://en.wikipedia.org/wiki/Computer_security")));
    }

    @Test void rejectsWrongSlideCountAndRepeatedContent() {
        SlideDeck deck = new SlideDeck("Topic", "ENG", List.of(slide("Heading A", "First fact"), slide("Heading B", "Second fact")));
        assertDoesNotThrow(() -> SlideFlowSingleService.validateDeck(deck, 2));
        assertThrows(IOException.class, () -> SlideFlowSingleService.validateDeck(deck, 10));
        SlideDeck repeated = new SlideDeck("Topic", "ENG", List.of(slide("Heading A", "Same fact."), slide("Heading B", "SAME fact!")));
        assertThrows(IOException.class, () -> SlideFlowSingleService.validateDeck(repeated, 2));
    }

    @Test void rejectsUncitedContentAndRepeatedTitles() {
        SlideDeck uncited = new SlideDeck("Topic", "ENG", List.of(new SlideDeck.Slide("A", "", List.of("Fact"), SlideDeck.Layout.EDITORIAL, "", List.of())));
        assertThrows(IOException.class, () -> SlideFlowSingleService.validateDeck(uncited, 1));
        SlideDeck sameTitles = new SlideDeck("Topic", "ENG", List.of(slide("Same title", "One fact"), slide("Same title", "Another fact")));
        assertThrows(IOException.class, () -> SlideFlowSingleService.validateDeck(sameTitles, 2));
    }
    @Test void insufficientFundsShowsExactShortfallAndOpensAutomaticPayment() {
        var message = SlideFlowSingleService.insufficientBalanceMessage(42L, "UZ", 1200, 5000);
        assertTrue(message.getText().contains("1200"));
        assertTrue(message.getText().contains("3800"));
        var keyboard = (org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup)message.getReplyMarkup();
        assertEquals("SLIDE_TOPUP_5000", keyboard.getKeyboard().get(0).get(0).getCallbackData());
    }

    @Test void toolbarFinishesOnlyAfterTheFileIsReady() {
        assertTrue(SlideFlowSingleService.progressText("UZ", 0, false, "Manbalar").contains("0%"));
        assertTrue(SlideFlowSingleService.progressText("UZ", 30, false, "Manbalar").contains("30 soniya"));
        String stillWorking = SlideFlowSingleService.progressText("UZ", 180, false, "Rasmlar");
        assertTrue(stillWorking.contains("95%")); assertFalse(stillWorking.contains("100%"));
        assertTrue(SlideFlowSingleService.progressText("UZ", 60, true, "").contains("100%"));
    }
}
