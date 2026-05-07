package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.*;

@Service
public class ReferatDocxService {

    @Autowired
    private GptTextGeneratorService gptService;

    private final ObjectMapper mapper = new ObjectMapper();

    private static final Map<Integer, Integer> SAHIFA_BOB = Map.of(15, 3, 20, 4, 25, 5);

    private String[][] buildBobNames(int count, String lang) {
        String[][] all = switch (lang) {
            case "RU" -> new String[][]{
                    {"Глава I. Ключевые понятия, определения и терминология",
                            "ГЛАВА I. КЛЮЧЕВЫЕ ПОНЯТИЯ, ОПРЕДЕЛЕНИЯ И ТЕРМИНОЛОГИЯ"},
                    {"Глава II. Анализ текущего состояния (статистика, факты, данные)",
                            "ГЛАВА II. АНАЛИЗ ТЕКУЩЕГО СОСТОЯНИЯ (СТАТИСТИКА, ФАКТЫ, ДАННЫЕ)"},
                    {"Глава III. Практическое применение и реальные примеры",
                            "ГЛАВА III. ПРАКТИЧЕСКОЕ ПРИМЕНЕНИЕ И РЕАЛЬНЫЕ ПРИМЕРЫ"},
                    {"Глава IV. Анализ проблем и пути их решения",
                            "ГЛАВА IV. АНАЛИЗ ПРОБЛЕМ И ПУТИ ИХ РЕШЕНИЯ"},
                    {"Глава V. Перспективы развития и рекомендации",
                            "ГЛАВА V. ПЕРСПЕКТИВЫ РАЗВИТИЯ И РЕКОМЕНДАЦИИ"}
            };
            case "ENG" -> new String[][]{
                    {"Chapter I. Key Concepts, Definitions and Terminology",
                            "CHAPTER I. KEY CONCEPTS, DEFINITIONS AND TERMINOLOGY"},
                    {"Chapter II. Current State Analysis (Statistics, Facts, Data)",
                            "CHAPTER II. CURRENT STATE ANALYSIS (STATISTICS, FACTS, DATA)"},
                    {"Chapter III. Practical Applications and Real-World Examples",
                            "CHAPTER III. PRACTICAL APPLICATIONS AND REAL-WORLD EXAMPLES"},
                    {"Chapter IV. Problem Analysis and Solutions",
                            "CHAPTER IV. PROBLEM ANALYSIS AND SOLUTIONS"},
                    {"Chapter V. Future Perspectives and Recommendations",
                            "CHAPTER V. FUTURE PERSPECTIVES AND RECOMMENDATIONS"}
            };
            default -> new String[][]{
                    {"I BOB. Kalit tushunchalar, ta'riflar va terminologiya",
                            "I BOB. KALIT TUSHUNCHALAR, TA'RIFLAR VA TERMINOLOGIYA"},
                    {"II BOB. Hozirgi holat tahlili (Statistika, faktlar, ma'lumotlar)",
                            "II BOB. HOZIRGI HOLAT TAHLILI (STATISTIKA, FAKTLAR, MA'LUMOTLAR)"},
                    {"III BOB. Amaliy qo'llanmalari va real hayot misollari",
                            "III BOB. AMALIY QO'LLANMALARI VA REAL HAYOT MISOLLARI"},
                    {"IV BOB. Muammolar va yechimlar tahlili",
                            "IV BOB. MUAMMOLAR VA YECHIMLAR TAHLILI"},
                    {"V BOB. Kelajak istiqbollari va tavsiyalar",
                            "V BOB. KELAJAK ISTIQBOLLARI VA TAVSIYALAR"}
            };
        };
        return Arrays.copyOf(all, count);
    }

    private String lbl(String key, String lang) {
        return switch (key) {
            case "REFERAT"   -> switch (lang) { case "RU" -> "РЕФЕРАТ"; case "ENG" -> "ESSAY"; default -> "REFERAT"; };
            case "MAVZU"     -> switch (lang) { case "RU" -> "Тема:"; case "ENG" -> "Topic:"; default -> "Mavzu:"; };
            case "BAJARDI"   -> switch (lang) { case "RU" -> "Выполнил(а):"; case "ENG" -> "Completed by:"; default -> "Bajardi:"; };
            case "TEKSHIRDI" -> switch (lang) { case "RU" -> "Проверил(а):"; case "ENG" -> "Checked by:"; default -> "Tekshirdi:"; };
            case "REJA"      -> switch (lang) { case "RU" -> "ПЛАН"; case "ENG" -> "OUTLINE"; default -> "REJA"; };
            case "KIRISH"    -> switch (lang) { case "RU" -> "ВВЕДЕНИЕ"; case "ENG" -> "INTRODUCTION"; default -> "KIRISH"; };
            case "XULOSA_H"  -> switch (lang) { case "RU" -> "ЗАКЛЮЧЕНИЕ"; case "ENG" -> "CONCLUSION"; default -> "XULOSA"; };
            case "XULOSA_R"  -> switch (lang) { case "RU" -> "Заключение"; case "ENG" -> "Conclusion"; default -> "Xulosa"; };
            case "ADAT_H"    -> switch (lang) { case "RU" -> "СПИСОК ЛИТЕРАТУРЫ"; case "ENG" -> "REFERENCES"; default -> "FOYDALANILGAN ADABIYOTLAR"; };
            case "ADAT_R"    -> switch (lang) { case "RU" -> "Список литературы"; case "ENG" -> "References"; default -> "Foydalanilgan adabiyotlar"; };
            case "YIL"       -> switch (lang) { case "RU" -> "Узбекистан — 2026"; case "ENG" -> "Uzbekistan — 2026"; default -> "O\u2019zbekiston \u2014 2026"; };
            case "VAZIRLIGI" -> switch (lang) {
                case "RU"  -> "МИНИСТЕРСТВО ВЫСШЕГО ОБРАЗОВАНИЯ, НАУКИ И ИННОВАЦИЙ РЕСПУБЛИКИ УЗБЕКИСТАН";
                case "ENG" -> "MINISTRY OF HIGHER EDUCATION, SCIENCE AND INNOVATIONS OF THE REPUBLIC OF UZBEKISTAN";
                default    -> "O'ZBEKISTON RESPUBLIKASI OLIY TA'LIM, FAN VA INNOVATSIYALAR VAZIRLIGI";
            };
            default -> key;
        };
    }

    public byte[] generate(String mavzu, String ism, int sahifa, String lang) throws Exception {
        int bobCount = SAHIFA_BOB.getOrDefault(sahifa, 3);
        String[][] bobNames = buildBobNames(bobCount, lang);
        Map<String, Object> content = fetchContent(mavzu, bobCount, bobNames, lang);
        return buildDocument(mavzu, ism, lang, bobNames, content);
    }

    private Map<String, Object> fetchContent(String mavzu, int bobCount, String[][] bobNames, String lang) throws Exception {
        String langNote = switch (lang) {
            case "RU"  -> "Write ALL text in RUSSIAN language.";
            case "ENG" -> "Write ALL text in ENGLISH language.";
            default    -> "Write ALL text in UZBEK language (Latin script, not Cyrillic).";
        };

        StringBuilder bobDesc = new StringBuilder();
        for (int i = 0; i < bobCount; i++) {
            bobDesc.append("chapters[").append(i).append("].chapter_name = \"").append(bobNames[i][0]).append("\"\n");
        }

        String prompt = String.format("""
You are a professional academic essay writer with deep expertise in the given topic.
Topic: "%s". %s
Number of chapters: %d

Your essay MUST contain REAL, SPECIFIC and VERIFIABLE information:
- Use CONCRETE statistics with actual numbers, percentages, dates (e.g., "In 2023, global renewable energy capacity reached 3,372 GW according to IRENA")
- Cite SPECIFIC researchers, scientists, organizations, countries by NAME
- Use REAL historical events with exact years
- Every paragraph must contain at least one specific fact, statistic or example
- Write academically: formal tone, logical structure, evidence-based arguments
- Introduction must analyze the topic deeply — historical background, current significance, research gaps
- Each subsection must build a complete argument with evidence
- Conclusion must synthesize all chapters with implications for future research

Return ONLY pure JSON, no markdown, no ```json, no explanations, nothing else.

Use EXACTLY this JSON structure with EXACTLY these English key names:
{
  "introduction": "<MINIMUM 700 words. Write 6+ paragraphs. Include: topic background, historical context with dates, current relevance, research significance, methodology, essay structure overview. Use specific facts and statistics>",
  "chapters": [
    {
      "chapter_name": "<chapter title>",
      "subsections": [
        {"title": "1.1. <specific subsection name>", "text": "<MINIMUM 350 words with specific facts, statistics, named researchers, dates. 3+ paragraphs separated by \\n\\n>"},
        {"title": "1.2. <specific subsection name>", "text": "<MINIMUM 350 words with concrete examples and real data. 3+ paragraphs separated by \\n\\n>"},
        {"title": "1.3. <specific subsection name>", "text": "<MINIMUM 350 words with comparative analysis and evidence. 3+ paragraphs separated by \\n\\n>"}
      ]
    }
  ],
  "conclusion": "<MINIMUM 500 words. Summarize key findings per chapter, discuss broader implications, suggest future research directions. Use specific references to chapter content. 5+ paragraphs separated by \\n\\n>",
  "references": [
    "1. Lastname F.N. Book Title. — City: Publisher, 2023. — 320 p.",
    "2. Lastname F.N. Article title // Journal name. — 2022. — Vol. 15, No. 3. — P. 45-67.",
    "3. Organization name. Report title [Electronic resource]. — URL: https://example.org (accessed: 2024)",
    "4. ...", "5. ...", "6. ...", "7. ...", "8. ...", "9. ...", "10. ...", "11. ...", "12. ..."
  ]
}

Chapter names must be:
%s

ABSOLUTE RULES:
- Return ONLY the JSON object starting with { and ending with }
- Use EXACTLY these key names: introduction, chapters, subsections, title, text, conclusion, references
- Each chapter must have EXACTLY 3 subsections
- Paragraphs within text fields separated by \\n\\n
- NO placeholder text, NO generic phrases like "plays an important role"
- Every claim must be supported with a specific fact, name, date or number
""", mavzu, langNote, bobCount, bobDesc.toString());

        String responseJson = gptService.callGemini(prompt);
        JsonNode root = mapper.readTree(responseJson);
        String raw = root.path("candidates").get(0)
                .path("content").path("parts").get(0).path("text").asText().trim();

        raw = raw.replaceAll("(?s)<thinking>.*?</thinking>", "").trim();
        raw = raw.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();

        int s = raw.indexOf('{');
        int e = raw.lastIndexOf('}');
        if (s >= 0 && e > s) raw = raw.substring(s, e + 1);

        Map<String, Object> result = mapper.readValue(raw, Map.class);
        System.out.println("✅ Referat JSON parse qilindi. Chapters: " +
                ((result.get("chapters") instanceof List<?> l) ? l.size() : 0));
        return result;
    }

    @SuppressWarnings("unchecked")
    private byte[] buildDocument(String mavzu, String ism, String lang,
                                 String[][] bobNames, Map<String, Object> content) throws Exception {
        XWPFDocument doc = new XWPFDocument();
        applyPageSetup(doc);
        addPageNumbers(doc);

        buildTitlePage(doc, mavzu, ism, lang);
        pageBreak(doc);

        buildRejaPage(doc, lang, bobNames);
        pageBreak(doc);

        String kirishText = getStr(content, "introduction", "kirish", "Kirish");
        buildSection(doc, lbl("KIRISH", lang), kirishText);

        List<Map<String, Object>> chapters = (List<Map<String, Object>>) content.getOrDefault("chapters", new ArrayList<>());
        for (int i = 0; i < bobNames.length; i++) {
            pageBreak(doc);
            Map<String, Object> bob = i < chapters.size() ? chapters.get(i) : new HashMap<>();
            buildBob(doc, bobNames[i][1], bob);
        }

        pageBreak(doc);
        String xulosaTxt = getStr(content, "conclusion", "xulosa", "Xulosa", "Заключение", "Conclusion");
        buildSection(doc, lbl("XULOSA_H", lang), xulosaTxt);

        pageBreak(doc);
        Object adatRaw = content.getOrDefault("references",
                content.getOrDefault("adabiyotlar",
                        content.getOrDefault("Список литературы", new ArrayList<>())));
        List<String> adatList = new ArrayList<>();
        if (adatRaw instanceof List<?> lst) {
            for (Object o : lst) if (o != null) adatList.add(o.toString());
        }
        buildAdabiyotlar(doc, lang, adatList);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.write(baos);
        doc.close();
        return baos.toByteArray();
    }

    private String getStr(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return "Matn tayyorlanmoqda...";
    }

    private void applyPageSetup(XWPFDocument doc) {
        CTSectPr sp = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();

        CTPageSz sz = sp.isSetPgSz() ? sp.getPgSz() : sp.addNewPgSz();
        sz.setW(BigInteger.valueOf(11906));
        sz.setH(BigInteger.valueOf(16838));

        CTPageMar m = sp.isSetPgMar() ? sp.getPgMar() : sp.addNewPgMar();
        m.setLeft(BigInteger.valueOf(1800));
        m.setRight(BigInteger.valueOf(1000));
        m.setTop(BigInteger.valueOf(1134));
        m.setBottom(BigInteger.valueOf(1420));
    }

    private void addPageNumbers(XWPFDocument doc) {
        try {
            XWPFFooter footer = doc.createFooter(HeaderFooterType.DEFAULT);
            XWPFParagraph p = footer.getParagraphArray(0);
            if (p == null) p = footer.createParagraph();
            p.setAlignment(ParagraphAlignment.RIGHT);

            CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
            CTSpacing sp = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
            sp.setBefore(BigInteger.ZERO);
            sp.setAfter(BigInteger.ZERO);

            CTR run = p.getCTP().addNewR();
            CTRPr rPr = run.addNewRPr();
            CTHpsMeasure sz = rPr.addNewSz();
            sz.setVal(BigInteger.valueOf(24));
            CTHpsMeasure szCs = rPr.addNewSzCs();
            szCs.setVal(BigInteger.valueOf(24));

            org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts fonts =
                    rPr.addNewRFonts();
            fonts.setAscii("Times New Roman");
            fonts.setHAnsi("Times New Roman");
            fonts.setCs("Times New Roman");

            run.addNewFldChar().setFldCharType(
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.BEGIN);
            CTR run2 = p.getCTP().addNewR();
            run2.addNewInstrText().setStringValue("PAGE");
            CTR run3 = p.getCTP().addNewR();
            run3.addNewFldChar().setFldCharType(
                    org.openxmlformats.schemas.wordprocessingml.x2006.main.STFldCharType.END);
        } catch (Exception e) {
            System.err.println("Page number qo'shishda xato: " + e.getMessage());
        }
    }

    private void buildTitlePage(XWPFDocument doc, String mavzu, String ism, String lang) {
        para(doc, lbl("VAZIRLIGI", lang), 14, true,  ParagraphAlignment.CENTER);
        emptyLines(doc, 7);
        para(doc, lbl("REFERAT", lang),   24, true,  ParagraphAlignment.CENTER);
        emptyLine(doc);
        para(doc, lbl("MAVZU", lang),     20, true,  ParagraphAlignment.CENTER);
        para(doc, mavzu.toUpperCase(),    16, false, ParagraphAlignment.CENTER);
        emptyLines(doc, 9);
        para(doc, lbl("BAJARDI", lang) + "  " + ism,         14, false, ParagraphAlignment.CENTER);
        emptyLine(doc);
        para(doc, lbl("TEKSHIRDI", lang) + "  _________________", 14, false, ParagraphAlignment.CENTER);
        emptyLines(doc, 7);
        para(doc, lbl("YIL", lang), 14, false, ParagraphAlignment.CENTER);
    }

    private void buildRejaPage(XWPFDocument doc, String lang, String[][] bobNames) {
        para(doc, lbl("REJA", lang), 18, true, ParagraphAlignment.CENTER);
        emptyLine(doc);
        for (String[] b : bobNames) para(doc, b[0], 16, true, ParagraphAlignment.LEFT);
        para(doc, lbl("XULOSA_R", lang), 16, true, ParagraphAlignment.LEFT);
        para(doc, lbl("ADAT_R", lang),   16, true, ParagraphAlignment.LEFT);
    }

    private void buildSection(XWPFDocument doc, String heading, String text) {
        para(doc, heading, 16, true, ParagraphAlignment.CENTER);
        emptyLine(doc);
        bodyText(doc, text);
    }

    @SuppressWarnings("unchecked")
    private void buildBob(XWPFDocument doc, String headingText, Map<String, Object> bob) {
        para(doc, headingText, 16, true, ParagraphAlignment.CENTER);
        emptyLine(doc);

        List<Map<String, Object>> subsections = (List<Map<String, Object>>)
                bob.getOrDefault("subsections",
                        bob.getOrDefault("kichik_boblar", new ArrayList<>()));

        for (Map<String, Object> k : subsections) {
            String nom  = k.getOrDefault("title", k.getOrDefault("nom", "")).toString();
            String matn = k.getOrDefault("text",  k.getOrDefault("matn", "")).toString();
            if (!nom.isBlank()) {
                XWPFParagraph p = doc.createParagraph();
                p.setAlignment(ParagraphAlignment.LEFT);
                spacing(p);
                XWPFRun r = p.createRun();
                r.setFontFamily("Times New Roman");
                r.setFontSize(14);
                r.setBold(true);
                r.setText(nom);
            }
            bodyText(doc, matn);
        }
    }

    private void buildAdabiyotlar(XWPFDocument doc, String lang, List<String> items) {
        para(doc, lbl("ADAT_H", lang), 16, true, ParagraphAlignment.CENTER);
        emptyLine(doc);
        for (String item : items) {
            if (item == null || item.isBlank()) continue;
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.LEFT);
            spacing(p);
            XWPFRun r = p.createRun();
            r.setFontFamily("Times New Roman");
            r.setFontSize(14);
            r.setBold(false);
            r.setText(item);
        }
    }

    private void bodyText(XWPFDocument doc, String text) {
        if (text == null || text.isBlank()) return;
        String[] blocks = text.split("\\n\\n");
        for (String block : blocks) {
            String clean = block.replaceAll("\\n", " ").trim();
            if (clean.isBlank()) continue;
            XWPFParagraph p = doc.createParagraph();
            p.setAlignment(ParagraphAlignment.BOTH);
            spacing(p);
            firstIndent(p);
            XWPFRun r = p.createRun();
            r.setFontFamily("Times New Roman");
            r.setFontSize(14);
            r.setBold(false);
            r.setText(clean);
        }
    }

    private void para(XWPFDocument doc, String text, int size, boolean bold, ParagraphAlignment align) {
        XWPFParagraph p = doc.createParagraph();
        p.setAlignment(align);
        spacing(p);
        XWPFRun r = p.createRun();
        r.setFontFamily("Times New Roman");
        r.setFontSize(size);
        r.setBold(bold);
        r.setText(text);
    }

    private void emptyLine(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        spacing(p);
        XWPFRun r = p.createRun();
        r.setFontFamily("Times New Roman");
        r.setFontSize(14);
        r.setText("");
    }

    private void emptyLines(XWPFDocument doc, int n) {
        for (int i = 0; i < n; i++) emptyLine(doc);
    }

    private void pageBreak(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.createRun().addBreak(BreakType.PAGE);
    }

    private void spacing(XWPFParagraph p) {
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        CTSpacing sp = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
        sp.setLine(BigInteger.valueOf(360));
        sp.setLineRule(STLineSpacingRule.AUTO);
        sp.setBefore(BigInteger.ZERO);
        sp.setAfter(BigInteger.ZERO);
    }

    private void firstIndent(XWPFParagraph p) {
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
        ind.setFirstLine(BigInteger.valueOf(720));
    }
}