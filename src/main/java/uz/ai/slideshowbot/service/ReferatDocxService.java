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

    @Autowired private WebResearchService webResearchService;

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
            case "YIL"       -> switch (lang) { case "RU" -> "Узбекистан — 2026"; case "ENG" -> "Uzbekistan — 2026"; default -> "O‘zbekiston — 2026"; };
            case "VAZIRLIGI" -> switch (lang) {
                case "RU"  -> "МИНИСТЕРСТВО ВЫСШЕГО ОБРАЗОВАНИЯ, НАУКИ И ИННОВАЦИЙ РЕСПУБЛИКИ УЗБЕКИСТАН";
                case "ENG" -> "MINISTRY OF HIGHER EDUCATION, SCIENCE AND INNOVATIONS OF THE REPUBLIC OF UZBEKISTAN";
                default    -> "O'ZBEKISTON RESPUBLIKASI OLIY TA'LIM, FAN VA INNOVATSIYALAR VAZIRLIGI";
            };
            default -> key;
        };
    }

    private String localized(String lang, String uz, String ru, String en) {
        return switch (lang) { case "RU" -> ru; case "ENG" -> en; default -> uz; };
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
        String subsectionHint = localized(lang,
                "Mavzuga mos kichik bo'lim nomi", "Подходящее название подраздела", "A topic-specific subsection title");
        String lengthHint = localized(lang,
                "Kamida 400 so'z, 3 ta katta xatboshi", "Не менее 400 слов, 3 больших абзаца", "At least 400 words in 3 substantial paragraphs");

        StringBuilder bobStructure = new StringBuilder();
        for (int i = 0; i < bobCount; i++) {
            bobStructure.append("# ").append(bobNames[i][1]).append("\n\n");
            bobStructure.append("## ").append(i + 1).append(".1. <").append(subsectionHint).append(">\n");
            bobStructure.append("<").append(lengthHint).append(">\n\n");
            bobStructure.append("## ").append(i + 1).append(".2. <").append(subsectionHint).append(">\n");
            bobStructure.append("<").append(lengthHint).append(">\n\n");
            bobStructure.append("## ").append(i + 1).append(".3. <").append(subsectionHint).append(">\n");
            bobStructure.append("<").append(lengthHint).append(">\n\n");
        }

        String prompt = String.format("""
Siz professional akademik tadqiqotchi, fan doktori va oliy o'quv yurtlari uchun darslik hamda ilmiy referatlar muallifisiz.
Mavzu: "%s". %s
Boblar soni: %d ta.

SIZNING VAZIFANGIZ:
Berilgan mavzu boyicha HAQIQIY, TO'LIQ va CHUQUR ILMIY REFERAT (kamida 5,000 soz) yozish!

QAT'IY TALABLAR:
1. Hech qanday umumiy, yuzaki gaplar bolmasin. Faqat chuqur nazariy va amaliy tahlil, aniq statistika (foizlar, raqamlar, yillar), olimlar va tadqiqotchilar ismlari, kitoblar va ilmiy manbalar bolsin.
2. Har bir kichik bolim (subsection) KAMIDA 400 soz bolishi va kamida 3 ta toliq xatboshidan (paragraphs) iborat bolishi SHART!
3. KIRISH qismi KAMIDA 750 soz va kamida 6 ta toliq xatboshidan iborat bolsin (mavzuning dolzarbligi, tarixi, maqsadi, vazifalari, metodologiyasi).
4. XULOSA qismi KAMIDA 550 soz bolib, har bir bob natijalarini va kelajakdagi takliflarni qamrab olsin (5 ta katta xatboshi).
5. FOYDALANILGAN ADABIYOTLAR royxatida kamida 12 ta real kitob, monografiya va nufuzli ilmiy manbalar bolsin.
6. Matnni MARKDOWN formatida, quyidagi sarlavha strukturasi bilan yozing:

# KIRISH
<Kirish matni kamida 750 soz, 6 ta katta xatboshi>

%s
# XULOSA
<Xulosa matni kamida 550 soz, 5 ta katta xatboshi>

# FOYDALANILGAN ADABIYOTLAR
1. <Muallif. Asar nomi. - Shahar: Nashriyot, Yil. - Betlar.>
2. ...
...
12. ...

MUHIM:
- Har bir bolim (# KIRISH, # BOB, ## 1.1, # XULOSA, # FOYDALANILGAN ADABIYOTLAR) aniq korsatilgan Markdown formatida bolsin.
- Har bir xatboshi orasi bosh qator (ikkita yangi qator \\n\\n) bilan ajratilsin.
- Hech qanday JSON ishlatmang! Faqat toza matn va Markdown sarlavhalari yozing.
""", mavzu, langNote, bobCount, bobStructure.toString());

        try {
        String responseJson = gptService.callGemini(prompt, false);
        String raw = gptService.extractGeneratedText(responseJson);

        raw = raw.replaceAll("(?s)<thinking>.*?</thinking>", "").trim();
        raw = raw.replaceAll("(?s)```(markdown)?\\s*", "").replaceAll("```", "").trim();

        Map<String, Object> result = parseMarkdownContent(raw, bobCount, bobNames, mavzu, lang);
        System.out.println("✅ Referat kontenti muvaffaqiyatli parse qilindi! Chapters: " +
                ((result.get("chapters") instanceof List<?> l) ? l.size() : 0));
        if (((List<?>) result.getOrDefault("chapters", List.of())).size() >= bobCount) return result;
        throw new IllegalStateException("Generated essay is incomplete.");
        } catch (Exception modelUnavailable) {
            return sourceBasedReferat(mavzu,bobCount,bobNames,lang);
        }
    }

    private Map<String,Object> sourceBasedReferat(String topic,int bobCount,String[][] bobNames,String lang) throws Exception {
        String target=WebResearchService.wikiLanguage(lang);
        var bundle=webResearchService.research(topic,lang,bobCount*3+1,0);
        record Fact(String heading,String text,String title,String url) {}
        List<Fact> facts=new ArrayList<>();
        for(var document:bundle.documents()) {
            if(!target.equals(document.language())) continue;
            for(var section:document.sections()) for(String sentence:SlidePlanningService.sentences(section.text()))
                if(facts.stream().noneMatch(f->f.text().equalsIgnoreCase(sentence))) facts.add(new Fact(section.heading(),sentence,document.title(),document.url()));
        }
        if(facts.size()<bobCount*3) throw new java.io.IOException(WebResearchService.unavailable(lang));
        List<Map<String,Object>> chapters=new ArrayList<>(); int cursor=0;
        int perSection=Math.max(1,facts.size()/(bobCount*3));
        for(int chapterIndex=0;chapterIndex<bobCount;chapterIndex++) {
            Map<String,Object> chapter=new HashMap<>(); chapter.put("chapter_name",bobNames[chapterIndex][0]);
            List<Map<String,Object>> subsections=new ArrayList<>();
            for(int sectionIndex=0;sectionIndex<3;sectionIndex++) {
                Fact first=facts.get(cursor); StringBuilder body=new StringBuilder();
                for(int j=0;j<perSection && cursor<facts.size();j++) body.append(facts.get(cursor++).text()).append("\n\n");
                Map<String,Object> subsection=new HashMap<>();
                subsection.put("title",(chapterIndex+1)+"."+(sectionIndex+1)+". "+first.heading());
                subsection.put("text",body.toString().trim()); subsections.add(subsection);
            }
            chapter.put("subsections",subsections); chapters.add(chapter);
        }
        StringBuilder intro=new StringBuilder(),conclusion=new StringBuilder();
        facts.stream().limit(Math.min(8,facts.size())).forEach(f->intro.append(f.text()).append("\n\n"));
        facts.stream().skip(Math.max(0,facts.size()-6)).forEach(f->conclusion.append(f.text()).append("\n\n"));
        LinkedHashSet<String> references=new LinkedHashSet<>();
        for(Fact fact:facts) references.add(fact.title()+" — "+fact.url());
        Map<String,Object> result=new HashMap<>();
        result.put("introduction",intro.toString().trim()); result.put("conclusion",conclusion.toString().trim());
        result.put("chapters",chapters); result.put("references",new ArrayList<>(references));
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMarkdownContent(String raw, int bobCount, String[][] bobNames, String mavzu, String lang) {
        Map<String, Object> data = new HashMap<>();
        List<Map<String, Object>> chapters = new ArrayList<>();
        List<String> references = new ArrayList<>();

        String[] lines = raw.split("\\r?\\n");
        final String[] currentSection = {null};
        final Map<String, Object>[] currentChapter = new Map[]{null};
        final Map<String, Object>[] currentSub = new Map[]{null};
        List<String> buf = new ArrayList<>();

        Runnable flush = () -> {
            String joined = String.join("\n", buf).trim();
            buf.clear();
            if (joined.isBlank()) return;

            if (currentSub[0] != null) {
                currentSub[0].put("text", joined);
            } else if ("KIRISH".equals(currentSection[0])) {
                data.put("introduction", joined);
            } else if ("XULOSA".equals(currentSection[0])) {
                data.put("conclusion", joined);
            } else if ("ADABIYOTLAR".equals(currentSection[0])) {
                for (String l : joined.split("\\r?\\n")) {
                    String clean = l.trim();
                    if (!clean.isBlank() && (Character.isDigit(clean.charAt(0)) || clean.startsWith("-") || clean.startsWith("*"))) {
                        references.add(clean);
                    }
                }
            }
        };

        for (String line : lines) {
            String s = line.trim();
            if (s.startsWith("# ") || (s.startsWith("### ") && currentChapter[0] == null)) {
                flush.run();
                String h = s.replaceAll("^#+\\s*", "").toUpperCase();
                if (h.contains("KIRISH") || h.contains("INTRODUCTION") || h.contains("ВВЕДЕНИЕ")) {
                    currentSection[0] = "KIRISH";
                    currentChapter[0] = null;
                    currentSub[0] = null;
                } else if (h.contains("XULOSA") || h.contains("CONCLUSION") || h.contains("ЗАКЛЮЧЕНИЕ")) {
                    currentSection[0] = "XULOSA";
                    currentChapter[0] = null;
                    currentSub[0] = null;
                } else if (h.contains("ADABIYOT") || h.contains("REFERENCE") || h.contains("LITERATURE") || h.contains("ЛИТЕРАТУР")) {
                    currentSection[0] = "ADABIYOTLAR";
                    currentChapter[0] = null;
                    currentSub[0] = null;
                } else if (h.contains("BOB") || h.contains("CHAPTER") || h.contains("ГЛАВА")) {
                    currentSection[0] = "BOB";
                    String chName = s.replaceAll("^#+\\s*", "").trim();
                    currentChapter[0] = new HashMap<>();
                    currentChapter[0].put("chapter_name", chName);
                    currentChapter[0].put("subsections", new ArrayList<Map<String, Object>>());
                    chapters.add(currentChapter[0]);
                    currentSub[0] = null;
                }
            } else if (s.startsWith("## ") || (s.startsWith("### ") && currentChapter[0] != null)) {
                flush.run();
                String subTitle = s.replaceAll("^#+\\s*", "").trim();
                currentSub[0] = new HashMap<>();
                currentSub[0].put("title", subTitle);
                currentSub[0].put("text", "");
                if (currentChapter[0] != null) {
                    ((List<Map<String, Object>>) currentChapter[0].get("subsections")).add(currentSub[0]);
                }
            } else {
                buf.add(line);
            }
        }
        flush.run();

        if (!data.containsKey("introduction") || data.get("introduction").toString().isBlank()) {
            data.put("introduction", localized(lang,
                    "Ushbu referatda " + mavzu + " mavzusi nazariy va amaliy jihatdan tahlil qilinadi.",
                    "В реферате теоретически и практически рассматривается тема «" + mavzu + "».",
                    "This paper examines the topic of " + mavzu + " from theoretical and practical perspectives."));
        }
        if (!data.containsKey("conclusion") || data.get("conclusion").toString().isBlank()) {
            data.put("conclusion", localized(lang,
                    "Xulosa qilib aytganda, " + mavzu + " mavzusi bo'yicha tahlil yuqoridagi asosiy fikrlarni jamlaydi.",
                    "Таким образом, проведённый анализ темы «" + mavzu + "» объединяет основные выводы работы.",
                    "In conclusion, the analysis of " + mavzu + " brings together the paper's main findings."));
        }
        if (references.isEmpty()) {
            references.add(localized(lang,
                    "Manbalar ro'yxati model javobida berilmadi.",
                    "Список источников отсутствовал в ответе модели.",
                    "The model response did not include a reference list."));
        }

        while (chapters.size() < bobCount) {
            int idx = chapters.size();
            Map<String, Object> ch = new HashMap<>();
            ch.put("chapter_name", bobNames[idx][0]);
            List<Map<String, Object>> subs = new ArrayList<>();
            for (int j = 1; j <= 3; j++) {
                Map<String, Object> sub = new HashMap<>();
                sub.put("title", (idx + 1) + "." + j + ". " + localized(lang,
                        mavzu + " bo'yicha tahlil", "Анализ темы «" + mavzu + "»", "Analysis of " + mavzu));
                sub.put("text", localized(lang,
                        mavzu + " bo'limi bo'yicha ushbu qismda manbalar asosida tahlil beriladi.",
                        "В этом подразделе приводится анализ темы «" + mavzu + "» на основе источников.",
                        "This subsection analyzes " + mavzu + " using the cited source material."));
                subs.add(sub);
            }
            ch.put("subsections", subs);
            chapters.add(ch);
        }

        data.put("chapters", chapters);
        data.put("references", references);
        return data;
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
        String xulosaTxt = getStr(content, "conclusion", "xulosa", "Xulosa", "Conclusion");
        buildSection(doc, lbl("XULOSA_H", lang), xulosaTxt);

        pageBreak(doc);
        Object adatRaw = content.getOrDefault("references",
                content.getOrDefault("adabiyotlar", new ArrayList<>()));
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
                bob.getOrDefault("subsections", new ArrayList<>());

        for (Map<String, Object> k : subsections) {
            String nom  = k.getOrDefault("title", "").toString();
            String matn = k.getOrDefault("text", "").toString();
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
