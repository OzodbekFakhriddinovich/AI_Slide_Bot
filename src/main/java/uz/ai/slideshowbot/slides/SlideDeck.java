package uz.ai.slideshowbot.slides;

import java.util.List;

/** A complete, ordered presentation. One plan item becomes exactly one output slide. */
public record SlideDeck(String title, String language, List<Slide> slides) {
    public SlideDeck {
        slides = List.copyOf(slides);
    }

    public record Slide(String title, String subtitle, List<String> bullets, Layout layout,
                        String imageQuery, List<Source> sources) {
        public Slide {
            title = title == null ? "" : title;
            subtitle = subtitle == null ? "" : subtitle;
            bullets = bullets == null ? List.of() : List.copyOf(bullets);
            layout = layout == null ? Layout.EDITORIAL : layout;
            imageQuery = imageQuery == null ? "" : imageQuery;
            sources = sources == null ? List.of() : List.copyOf(sources);
        }
    }

    public record Source(String title, String url) {}

    public enum Layout {
        COVER, AGENDA, EDITORIAL, CARDS, SPLIT, PROCESS, COMPARISON, TIMELINE, SUMMARY, SOURCES
    }
}
