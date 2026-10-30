package uz.ai.slideshowbot.slides;

import java.awt.Color;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/** Shared, stable theme IDs used by the Telegram WebApp and slide renderer. */
public record SlideTheme(String id, String name, Color background, Color surface,
                         Color text, Color muted, Color accent, Decoration decoration) {
    public enum Decoration { NETWORK, WAVES, BOTANICAL, ORBITS, GRID, FRAME, DOTS, LINES }

    private static SlideTheme dark(int id, String name, String bg, String accent, Decoration motif) {
        Color background = Color.decode(bg);
        return new SlideTheme(String.valueOf(id), name, background,
                mix(background, Color.WHITE, .055), new Color(244, 248, 253),
                mix(background, Color.WHITE, .66), Color.decode(accent), motif);
    }

    private static SlideTheme light(int id, String name, String bg, String accent, Decoration motif) {
        Color background = Color.decode(bg);
        return new SlideTheme(String.valueOf(id), name, background,
                mix(background, Color.WHITE, .62), new Color(27, 40, 51),
                new Color(83, 99, 111), Color.decode(accent), motif);
    }

    public static final List<SlideTheme> ALL = List.of(
            dark(1, "Cyber Navy", "#0b1425", "#36d5f3", Decoration.NETWORK),
            dark(2, "Ocean", "#062c46", "#38bdf8", Decoration.WAVES),
            dark(3, "Emerald", "#082e2a", "#34d399", Decoration.BOTANICAL),
            dark(4, "Violet", "#24183f", "#a78bfa", Decoration.ORBITS),
            dark(5, "Amber", "#302315", "#fbbf24", Decoration.DOTS),
            dark(6, "Rose", "#351b2b", "#fb8fa8", Decoration.ORBITS),
            dark(7, "Slate", "#202b38", "#94b8d8", Decoration.LINES),
            light(8, "Arctic", "#eef5fa", "#2586b7", Decoration.GRID),
            light(9, "Sand", "#f1e8d8", "#a8743b", Decoration.FRAME),
            dark(10, "Forest", "#102d24", "#a3d88a", Decoration.BOTANICAL),
            dark(11, "Midnight", "#0c1025", "#7dd3fc", Decoration.DOTS),
            light(12, "Coral", "#fff1eb", "#dc674d", Decoration.WAVES),
            light(13, "Medical", "#eef7f6", "#098b86", Decoration.NETWORK),
            dark(14, "Blueprint", "#12376a", "#80d8ff", Decoration.GRID),
            light(15, "History", "#f3ead9", "#8f603b", Decoration.FRAME),
            dark(16, "Science", "#0d2538", "#59e2d0", Decoration.ORBITS),
            light(17, "Education", "#f4f0ff", "#7152c7", Decoration.DOTS),
            dark(18, "Finance", "#122724", "#b5d46e", Decoration.LINES),
            light(19, "Nature", "#eaf1e4", "#4e7d42", Decoration.BOTANICAL),
            light(20, "Editorial", "#f6f2ed", "#be553b", Decoration.LINES),
            dark(21, "Indigo", "#1c2151", "#a3a9ff", Decoration.NETWORK),
            dark(22, "Copper", "#2d201d", "#e0a879", Decoration.FRAME),
            light(23, "Lavender", "#f0eafb", "#8b64ba", Decoration.ORBITS),
            dark(24, "Monochrome", "#17191d", "#f5f5f5", Decoration.GRID));

    public static SlideTheme resolve(String id, String topic) {
        if (id != null && !id.isBlank() && !"auto".equalsIgnoreCase(id.trim())) {
            return ALL.stream().filter(t -> t.id.equals(id.trim())).findFirst().orElse(ALL.get(0));
        }
        String key = Normalizer.normalize(topic == null ? "" : topic, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replace('‘', '\'').replace('’', '\'').replace('ʻ', '\'').replace('ʼ', '\'');
        int theme = matches(key,"oziq-ovqat","oziq ovqat","food security","agricultur","qishloq xo'jal","сельскохозяй") ? 19
                : ((matches(key,"kompyuter","computer","компьютер") && matches(key,"virus","malware","вирус"))) ? 1
                : matches(key, "xavfsiz", "cyber", "security", "tarmoq", "network", "кибер", "безопас", "сетев") ? 1
                : matches(key, "tibb", "sog'liq", "kasal", "medic", "health", "virus", "медиц", "здоров", "болез") ? 13
                : matches(key, "moliya", "iqtisod", "bank", "biznes", "finance", "econom", "business", "инвест", "эконом", "финанс") ? 18
                : matches(key, "tarix", "qadim", "history", "histor", "ancient", "истори", "древн") ? 15
                : matches(key, "okean", "dengiz", "ocean", "marine", "water", "suv", "океан", "морск", "водн") ? 2
                : matches(key, "tabiat", "ekolog", "o'simlik", "nature", "environment", "ecolog", "climat", "природ", "эколог") ? 19
                : matches(key, "ta'lim", "maktab", "pedagog", "education", "school", "learning", "образован", "школ", "обучен") ? 17
                : matches(key, "kosmos", "astronom", "space", "universe", "космос", "астрон") ? 11
                : matches(key, "fizika", "kimyo", "biolog", "science", "physics", "chemi", "ilm", "наук", "физик", "хими", "биолог") ? 16
                : matches(key, "qurilish", "arxitekt", "muhandis", "engineer", "architect", "construct", "архитект", "строит", "инженер") ? 14
                : matches(key, "adabiyot", "san'at", "madani", "literature", "culture", "design", "искусств", "культур", "литератур") ? 20
                : matches(key, "texnolog", "sun'iy", "intellekt", "technology", "artificial", "software", "технолог", "интеллект") ? 21 : 1;
        return ALL.get(theme - 1);
    }

    private static boolean matches(String topic, String... keywords) {
        for (String keyword : keywords) if (topic.contains(keyword)) return true;
        return false;
    }

    public static Color mix(Color from, Color to, double ratio) {
        return new Color((int) Math.round(from.getRed() * (1-ratio) + to.getRed() * ratio),
                (int) Math.round(from.getGreen() * (1-ratio) + to.getGreen() * ratio),
                (int) Math.round(from.getBlue() * (1-ratio) + to.getBlue() * ratio));
    }
}
