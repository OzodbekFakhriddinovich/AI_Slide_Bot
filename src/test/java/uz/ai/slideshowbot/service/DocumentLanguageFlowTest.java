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

            String referatText = switch (lang) {
                case "RU" -> "# ВВЕДЕНИЕ\nВ работе рассматривается защита данных.\n# ГЛАВА I. Основы\n## 1.1. Шифрование\nШифрование защищает данные от чтения посторонними.\n# ЗАКЛЮЧЕНИЕ\nЗащита данных требует комплексных мер.\n# СПИСОК ЛИТЕРАТУРЫ\n1. Проверенный источник.";
                case "ENG" -> "# INTRODUCTION\nThis paper examines data security.\n# CHAPTER I. Foundations\n## 1.1. Encryption\nEncryption protects data from unauthorized reading.\n# CONCLUSION\nData security requires layered controls.\n# REFERENCES\n1. A verified source.";
                default -> "# KIRISH\nUshbu ish ma'lumotlar xavfsizligini o'rganadi.\n# I BOB. Asoslar\n## 1.1. Shifrlash\nShifrlash ma'lumotlarni ruxsatsiz o'qishdan himoya qiladi.\n# XULOSA\nMa'lumotlarni himoyalash bir necha chorani talab qiladi.\n# FOYDALANILGAN ADABIYOTLAR\n1. Tekshirilgan manba.";
            };
            FakeGemini referatAi = new FakeGemini(referatText);
            ReferatDocxService referatService = new ReferatDocxService();
            set(referatService, "gptService", referatAi);
            byte[] referatDoc = referatService.generate("Data security", "Alex Example", 15, lang);
            String actualReferat = documentText(referatDoc);

            assertTrue(actualReferat.contains("DATA SECURITY"));
            assertTrue(actualReferat.contains(switch (lang) { case "RU" -> "РЕФЕРАТ"; case "ENG" -> "ESSAY"; default -> "REFERAT"; }));
            assertTrue(actualReferat.contains(switch (lang) { case "RU" -> "ВВЕДЕНИЕ"; case "ENG" -> "INTRODUCTION"; default -> "KIRISH"; }));
            assertTrue(actualReferat.contains(switch (lang) { case "RU" -> "ПЛАН"; case "ENG" -> "OUTLINE"; default -> "REJA"; }));
            assertTrue(referatAi.prompt.contains(switch (lang) { case "RU" -> "RUSSIAN"; case "ENG" -> "ENGLISH"; default -> "UZBEK"; }));
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
