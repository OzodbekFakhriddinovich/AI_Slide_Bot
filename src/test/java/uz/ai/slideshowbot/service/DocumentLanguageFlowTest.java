package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DocumentLanguageFlowTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final class FakeGemini extends GptTextGeneratorService {
        private final String text;
        private String prompt;

        private FakeGemini(String text) { this.text = text; }

        @Override public String callGemini(String prompt, boolean json) throws Exception {
            this.prompt = prompt;
            return JSON.writeValueAsString(Map.of("candidates", List.of(Map.of("content", Map.of(
                    "parts", List.of(Map.of("text", text)))))));
        }
    }

    @Test void testAndReferatDocumentsAreGeneratedInAllSupportedLanguages() throws Exception {
        for (String lang : List.of("UZ", "RU", "ENG")) {
            String testContent = switch (lang) {
                case "RU" -> "1. Как защищают данные?\nA) Шифрованием\nB) Публикацией\nC) Удалением\nD) Игнорированием\n\n--- ПРАВИЛЬНЫЕ ОТВЕТЫ ---\n1. A";
                case "ENG" -> "1. How can data be protected?\nA) Encryption\nB) Publishing\nC) Deletion\nD) Ignoring\n\n--- CORRECT ANSWERS ---\n1. A";
                default -> "1. Ma'lumot qanday himoyalanadi?\nA) Shifrlash\nB) E'lon qilish\nC) O'chirish\nD) E'tiborsiz qoldirish\n\n--- TO'G'RI JAVOBLAR ---\n1. A";
            };
            FakeGemini testAi = new FakeGemini(testContent);
            TestService testService = new TestService();
            set(testService, "gptTextGeneratorService", testAi);
            String generated = (String) invoke(testService, "generateTestContent",
                    new Class<?>[]{String.class, int.class, String.class, String.class},
                    "Data security", 1, "EASY", lang);
            byte[] testDoc = (byte[]) invoke(testService, "createWordDocument",
                    new Class<?>[]{String.class, String.class, String.class}, "Data security", generated, lang);
            String testText = documentText(testDoc);

            assertTrue(testText.contains(switch (lang) { case "RU" -> "Тема:"; case "ENG" -> "Topic:"; default -> "Mavzu:"; }));
            assertTrue(testText.contains(switch (lang) { case "RU" -> "ПРАВИЛЬНЫЕ ОТВЕТЫ"; case "ENG" -> "CORRECT ANSWERS"; default -> "TO'G'RI JAVOBLAR"; }));
            assertTrue(testAi.prompt.contains(switch (lang) { case "RU" -> "на русском языке"; case "ENG" -> "in English"; default -> "o'zbek tilida"; }));
            if (!lang.equals("UZ"))
                assertFalse(testAi.prompt.contains("Siz test generatori"), "Test prompt must not leak Uzbek into " + lang);

            String referatText = referatFixture(lang);
            FakeGemini referatAi = new FakeGemini(referatText);
            ReferatDocxService referatService = new ReferatDocxService();
            set(referatService, "gptService", referatAi);
            byte[] referatDoc = referatService.generate("Data security", "Alex Example", 15, lang);
            String actualReferat = documentText(referatDoc);
            assertEquals(18, manualPageCount(referatDoc));

            assertTrue(actualReferat.contains("DATA SECURITY"));
            assertTrue(actualReferat.contains(switch (lang) { case "RU" -> "РЕФЕРАТ"; case "ENG" -> "ESSAY"; default -> "REFERAT"; }));
            assertTrue(actualReferat.contains(switch (lang) { case "RU" -> "ВВЕДЕНИЕ"; case "ENG" -> "INTRODUCTION"; default -> "KIRISH"; }));
            assertTrue(actualReferat.contains(switch (lang) { case "RU" -> "ПЛАН"; case "ENG" -> "OUTLINE"; default -> "REJA"; }));
            assertTrue(referatAi.prompt.contains(switch (lang) { case "RU" -> "RUSSIAN"; case "ENG" -> "ENGLISH"; default -> "UZBEK"; }));

            if(lang.equals("ENG")) {
                ReferatDocxService longReferatService=new ReferatDocxService();
                FakeGemini longReferatAi=new FakeGemini(referatFixture(lang,4));
                set(longReferatService,"gptService",longReferatAi);
                byte[] twentyPageDoc=longReferatService.generate("Data security","Alex Example",20,lang);
                assertEquals(23,manualPageCount(twentyPageDoc));
            }
        }
    }

    private static String referatFixture(String lang) {
        return referatFixture(lang,3);
    }

    private static String referatFixture(String lang,int chapterCount) {
        String sentence=switch(lang) {
            case "RU" -> "\u0417\u0430\u0449\u0438\u0442\u0430 \u0434\u0430\u043d\u043d\u044b\u0445 \u0442\u0440\u0435\u0431\u0443\u0435\u0442 \u0430\u043d\u0430\u043b\u0438\u0437\u0430 \u0443\u0433\u0440\u043e\u0437, \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0438 \u0438\u0441\u0442\u043e\u0447\u043d\u0438\u043a\u043e\u0432, \u043e\u0431\u043e\u0441\u043d\u043e\u0432\u0430\u043d\u043d\u044b\u0445 \u043c\u0435\u0440 \u0438 \u0440\u0435\u0433\u0443\u043b\u044f\u0440\u043d\u043e\u0439 \u043e\u0446\u0435\u043d\u043a\u0438 \u0440\u0435\u0437\u0443\u043b\u044c\u0442\u0430\u0442\u0430.";
            case "ENG" -> "Data security requires threat analysis, verified sources, justified controls, and regular evaluation of results.";
            default -> "Axborot xavfsizligi tahdidlarni tahlil qilish, manbalarni tekshirish va natijalarni muntazam baholashni talab qiladi.";
        };
        StringBuilder out=new StringBuilder("# INTRODUCTION\n").append(repeat(sentence,45));
        for(int chapter=1;chapter<=chapterCount;chapter++) {
            out.append("\n# CHAPTER ").append(chapter).append(". Foundations and analysis\n");
            for(int section=1;section<=3;section++)
                out.append("\n## ").append(chapter).append(".").append(section).append(". Topic analysis\n").append(repeat(sentence,36));
        }
        out.append("\n# CONCLUSION\n").append(repeat(sentence,35)).append("\n# REFERENCES\n1. Verified academic source.\n2. International research publication.\n3. Official statistical report.");
        return out.toString();
    }
    private static String repeat(String sentence,int count) { return String.join(" ",java.util.Collections.nCopies(count,sentence)); }

    private static int manualPageCount(byte[] bytes) throws Exception {
        try(XWPFDocument doc=new XWPFDocument(new ByteArrayInputStream(bytes))) {
            long breaks=doc.getParagraphs().stream().flatMap(p->p.getRuns().stream())
                    .flatMap(run->run.getCTR().getBrList().stream())
                    .filter(br->br.getType()==org.openxmlformats.schemas.wordprocessingml.x2006.main.STBrType.PAGE).count();
            return Math.toIntExact(breaks+1);
        }
    }
    private static String documentText(byte[] bytes) throws Exception {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            StringBuilder text = new StringBuilder();
            for (XWPFParagraph paragraph : doc.getParagraphs()) text.append(paragraph.getText()).append('\n');
            return text.toString();
        }
    }

    private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = target.getClass().getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
