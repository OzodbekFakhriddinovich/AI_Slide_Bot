package uz.ai.slideshowbot.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageSearchServiceTest {
    @Test void downloadsOnlyPublicWikimediaImageOrigins() {
        assertTrue(ImageSearchService.trustedImageUrl("https://upload.wikimedia.org/wikipedia/commons/a/ab/Photo.jpg"));
        assertFalse(ImageSearchService.trustedImageUrl("https://upload.wikimedia.org.evil.test/wikipedia/commons/photo.jpg"));
        assertFalse(ImageSearchService.trustedImageUrl("https://localhost/wikipedia/commons/photo.jpg"));
        assertFalse(ImageSearchService.trustedImageUrl("http://upload.wikimedia.org/wikipedia/commons/photo.jpg"));
        assertFalse(ImageSearchService.trustedImageUrl("https://upload.wikimedia.org:8443/wikipedia/commons/photo.jpg"));
    }

    @Test void invalidImageDataIsNeverEmbedded() {
        assertFalse(ImageSearchService.validImage(new byte[]{1, 2, 3, 4}));
        assertEquals("Artist & license", ImageSearchService.plain("<a href='x'>Artist</a> &amp; license"));
    }
}
