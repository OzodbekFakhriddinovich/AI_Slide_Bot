package uz.ai.slideshowbot.service;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PptxCreatorService {

    @Autowired
    private ImageSearchService imageSearchService;

    private static final int W   = 960;
    private static final int H   = 540;
    private static final int BM  = 2;
    private static final int BT  = 3;
    private static final int PAD = 18;


    private static final double FS_TOPIC     = 50.0;
    private static final double FS_NAME      = 40.0;
    private static final double FS_REJA_HEAD = 55.0;
    private static final double FS_REJA_ITEM = 47.0;
    private static final double FS_HEAD      = 28.0;
    private static final double FS_BODY      = 24.0;

    private static final AtomicInteger LAYOUT_COUNTER = new AtomicInteger(0);

    private final ExecutorService imagePool = Executors.newFixedThreadPool(5);

    public String createPptxFromSections(String topic, String userName, String subjectName,
                                         List<GptTextGeneratorService.Section> sections,
                                         String templateStr) throws IOException {
        File dir = new File("slides");
        if (!dir.exists()) dir.mkdirs();

        int           tmplNum = parseTemplate(templateStr);
        TemplateStyle style   = getTemplateStyle(tmplNum);
        int           layout  = LAYOUT_COUNTER.getAndIncrement() % 10;
        int           pattern = (tmplNum - 1) % 6;

        Map<Integer, byte[]> imgCache = prefetchImages(sections, topic);

        XMLSlideShow ppt = new XMLSlideShow();
        ppt.setPageSize(new Dimension(W, H));

        createTitleSlide(ppt, topic, userName, subjectName, style, pattern);
        createRejaSlide(ppt, sections, style, pattern);

        for (int i = 0; i < sections.size(); i++) {
            GptTextGeneratorService.Section sec = sections.get(i);
            List<GptTextGeneratorService.SlideContent> slides = sec.slides();
            createSA(ppt, sec.title(), slides, style, layout, pattern);
            if (!slides.isEmpty())   createSB(ppt, slides.get(0), style, layout, pattern);
            if (slides.size() > 1)   createSC(ppt, slides.get(1), style, imgCache.get(i), layout, pattern);
            if (slides.size() > 2)   createSD(ppt, slides.get(2), style, layout, pattern);
        }
        createThankYouSlide(ppt, userName, style, pattern);

        String safe     = topic.replaceAll("[^a-zA-Z0-9\\s]", "_").trim();
        String fileName = "slides/" + safe + "_" + System.currentTimeMillis() + ".pptx";
        try (FileOutputStream out = new FileOutputStream(fileName)) { ppt.write(out); }
        ppt.close();
        return fileName;
    }

    private Map<Integer, byte[]> prefetchImages(List<GptTextGeneratorService.Section> sections, String topic) {
        Map<Integer, byte[]>         result  = new ConcurrentHashMap<>();
        Map<Integer, Future<byte[]>> futures = new LinkedHashMap<>();
        for (int i = 0; i < sections.size(); i++) {
            List<GptTextGeneratorService.SlideContent> sls = sections.get(i).slides();
            if (sls.size() <= 1) continue;
            final int idx = i;
            final String prompt = sls.get(1).imageQuery;
            futures.put(idx, imagePool.submit(() -> {
                try {
                    byte[] bytes = imageSearchService.findImageBytes(prompt, topic);
                    return (bytes != null && bytes.length > 1000) ? bytes : null;
                } catch (Exception e) { return null; }
            }));
        }
        futures.forEach((idx, f) -> {
            try { byte[] b = f.get(90, TimeUnit.SECONDS); if (b != null) result.put(idx, b); }
            catch (Exception ignored) {}
        });
        return result;
    }

    private void drawPattern(XSLFSlide slide, TemplateStyle style, int pattern) {
        Color d1 = blend(style.titleColor, style.bgColor, 0.80f);
        Color d2 = blend(style.titleColor, style.bgColor, 0.88f);
        Color d3 = blend(style.titleColor, style.bgColor, 0.92f);

        switch (pattern) {
            case 0 -> {
                drawFilled(slide, ShapeType.TRIANGLE, W-260, H-180, 220, 180, d1);
                drawFilled(slide, ShapeType.TRIANGLE, W-160, H-120, 150, 120, d2);
                drawFilled(slide, ShapeType.TRIANGLE, W-80,  H-80,  80,  80,  d3);
            }
            case 1 -> {
                drawFilled(slide,  ShapeType.ELLIPSE, W-220, -80,  280, 280, d3);
                drawOutline(slide, ShapeType.ELLIPSE, W-170, -40,  200, 200, d2, 2.0);
                drawOutline(slide, ShapeType.ELLIPSE, W-120,   0,  120, 120, d1, 1.5);
                drawFilled(slide,  ShapeType.ELLIPSE,     0, H-100, 120, 120, d3);
                drawOutline(slide, ShapeType.ELLIPSE,   -30, H-60,   80,  80, d2, 1.5);
            }
            case 2 -> {
                for (int x = W-200; x <= W-20; x += 28)
                    for (int y = H-200; y <= H-20; y += 28)
                        drawFilled(slide, ShapeType.ELLIPSE, x, y, 9, 9, d2);
                for (int x = 20; x <= 120; x += 24)
                    for (int y = 20; y <= 120; y += 24)
                        drawFilled(slide, ShapeType.ELLIPSE, x, y, 6, 6, d3);
            }
            case 3 -> {
                for (int i = 0; i < 4; i++) {
                    int x = W - 90 + i * 22;
                    int alpha = 255 - i * 30;
                    Color lc = blend(style.titleColor, style.bgColor, 0.75f + i * 0.05f);
                    drawFilled(slide, ShapeType.RECT, x, 0, 8, H, lc);
                }
            }
            case 4 -> {
                drawOutline(slide, ShapeType.HEXAGON, W-200, -30, 160, 160, d1, 2.5);
                drawFilled(slide,  ShapeType.HEXAGON, W-130,  30,  90,  90, d2);
                drawOutline(slide, ShapeType.HEXAGON, W-90, 110,  60,  60, d3, 1.5);
                drawFilled(slide,  ShapeType.HEXAGON,    0, H-100, 70, 70, d3);
                drawOutline(slide, ShapeType.HEXAGON,   20, H-60,  40, 40, d2, 1.0);
            }
            default -> {
                drawFilled(slide,  ShapeType.RECT, W-110,  0, 50, 50, d2);
                drawFilled(slide,  ShapeType.RECT, W-60,   0, 60, 30, d1);
                drawFilled(slide,  ShapeType.RECT,   0, H-60, 60, 60, d2);
                drawFilled(slide,  ShapeType.RECT,   0, H-30, 30, 30, d1);
                drawOutline(slide, ShapeType.RECT, W-160, H-160, 130, 130, d3, 2.0);
            }
        }
    }

    private void createTitleSlide(XMLSlideShow ppt, String topic, String userName,
                                  String subjectName, TemplateStyle style, int pattern) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        drawPattern(slide, style, pattern);

        textBox(slide, topic.toUpperCase(), PAD, 45, W - PAD*2, 320,
                FS_TOPIC, true, style.titleColor,
                org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);

        String info = userName + (subjectName != null && !subjectName.isBlank() ? "  |  " + subjectName : "");
        textBox(slide, info, PAD, H - 105, W - PAD*2, 80,
                FS_NAME, false, style.titleColor,
                org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);

        addBorder(slide);
    }

    private void createRejaSlide(XMLSlideShow ppt, List<GptTextGeneratorService.Section> sections,
                                 TemplateStyle style, int pattern) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        drawPattern(slide, style, pattern);

        textBox(slide, "Reja", PAD, BM + BT + 8, W - PAD*2, 80,
                FS_REJA_HEAD, true, style.titleColor,
                org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);

        int listY = BM + BT + 95;
        int listH = H - listY - BM - BT - 12;

        XSLFTextBox listBox = slide.createTextBox();
        listBox.setAnchor(new Rectangle(PAD + 12, listY, W - PAD*2 - 24, listH));

        for (int i = 0; i < Math.min(sections.size(), 5); i++) {
            XSLFTextParagraph p = listBox.addNewTextParagraph();
            p.setSpaceBefore(0.0);
            p.setSpaceAfter(12.0);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(clean(sections.get(i).title()));
            r.setFontSize(FS_REJA_ITEM);
            r.setBold(true);
            r.setFontColor(style.titleColor);
            r.setFontFamily("Calibri");
        }

        addBorder(slide);
    }

    private void createSA(XMLSlideShow ppt, String sectionTitle,
                          List<GptTextGeneratorService.SlideContent> slides,
                          TemplateStyle style, int layout, int pattern) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        drawPattern(slide, style, pattern);

        String       title   = clean(sectionTitle);
        List<String> bullets = (!slides.isEmpty() && slides.get(0).bullets != null)
                ? slides.get(0).bullets : new ArrayList<>();

        int innerTop    = BM + BT + 4;
        int innerBottom = H - BM - BT - 4;
        int innerH      = innerBottom - innerTop;

        int titleH = Math.min(180, (int)(innerH * 0.40));
        int titleY = innerTop;
        int gapH   = 6;
        int descY  = titleY + titleH + gapH;
        int descH  = innerBottom - descY;
        double titleFs = Math.min(52.0, Math.max(22.0, titleH * 0.35));

        switch (layout) {
            case 0 -> {
                drawFilled(slide, ShapeType.RECT, BM+BT, innerTop, 10, innerH,
                        blend(style.titleColor, style.bgColor, 0.3f));
                textBox(slide, title, 28, titleY, W-38, titleH, titleFs, true, style.titleColor, null);
                if (descH > 20) fillBody(slide, bullets, 28, descY, W-38, descH, Color.BLACK); // ✅
            }
            case 1 -> {
                drawFilled(slide, ShapeType.RECT, W-BM-BT-10, innerTop, 10, innerH,
                        blend(style.titleColor, style.bgColor, 0.3f));
                textBox(slide, title, PAD, titleY, W-PAD*2-14, titleH, titleFs, true,
                        style.titleColor, org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                if (descH > 20) fillBody(slide, bullets, PAD, descY, W-PAD*2-14, descH, Color.BLACK); // ✅
            }
            case 2 -> {
                int barH = Math.min(titleH, 72);
                drawFilled(slide, ShapeType.RECT, BM, innerTop, W-BM*2, barH, style.titleColor);
                textBox(slide, title, PAD, innerTop+4, W-PAD*2, barH-8,
                        Math.min(36.0, barH*0.45), true, style.bgColor,
                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                int d2Y = innerTop + barH + 8;
                int d2H = innerBottom - d2Y;
                if (d2H > 20) fillBody(slide, bullets, PAD, d2Y, W-PAD*2, d2H, Color.BLACK); // ✅
            }
            case 3 -> {
                int panelW = W / 3;
                drawFilled(slide, ShapeType.RECT, BM, innerTop, panelW, innerH, style.titleColor);
                textBox(slide, title, BM+6, innerTop + innerH/2 - titleH/2,
                        panelW-12, titleH, Math.min(30.0, titleFs*0.7), true, style.bgColor,
                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                int dX = panelW + PAD;
                int dW = W - panelW - PAD*2 - BM;
                if (innerH > 20) fillBody(slide, bullets, dX, innerTop + 8, dW, innerH-16, Color.BLACK); // ✅
            }
            case 4 -> {
                int barH4 = 70;
                drawFilled(slide, ShapeType.RECT, BM, innerBottom-barH4, W-BM*2, barH4, style.titleColor);
                textBox(slide, title, PAD, titleY, W-PAD*2, titleH, titleFs, true, style.titleColor, null);
                int d4H = innerBottom - barH4 - titleY - titleH - gapH;
                if (d4H > 20) fillBody(slide, bullets, PAD, descY, W-PAD*2, d4H, Color.BLACK); // ✅
            }
            case 5 -> {
                int panelW5 = W / 3;
                drawFilled(slide, ShapeType.RECT, W-panelW5-BM, innerTop, panelW5, innerH, style.titleColor);
                int dW5 = W - panelW5 - PAD*2 - 10;
                textBox(slide, title, PAD, titleY, dW5, titleH, titleFs, true, style.titleColor, null);
                int d5H = innerBottom - descY;
                if (d5H > 20) fillBody(slide, bullets, PAD, descY, dW5, d5H, Color.BLACK);
            }
            case 6 -> {
                drawFilled(slide, ShapeType.RECT, BM, BM, W-BM*2, H-BM*2, style.titleColor);
                textBox(slide, title, PAD, titleY, W-PAD*2, titleH, titleFs, true, style.bgColor, null);
                if (descH > 20) fillBody_color(slide, bullets, PAD, descY, W-PAD*2, descH, style.bgColor);
            }
            case 7 -> {
                int barH7 = 8;
                drawFilled(slide, ShapeType.RECT, BM, innerTop, W-BM*2, barH7, style.titleColor);
                drawFilled(slide, ShapeType.RECT, BM, innerBottom-barH7, W-BM*2, barH7, style.titleColor);
                textBox(slide, title, PAD, innerTop+barH7+4, W-PAD*2, titleH,
                        titleFs, true, style.titleColor,
                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                int d7Y = innerTop+barH7+titleH+gapH+4;
                int d7H = innerBottom-barH7-d7Y;
                if (d7H > 20) fillBody(slide, bullets, PAD, d7Y, W-PAD*2, d7H, Color.BLACK);
            }
            case 8 -> {
                textBox(slide, title, PAD, titleY, W-PAD*2, titleH, titleFs, true,
                        style.titleColor, org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                if (descH > 20) fillBody_center(slide, bullets, PAD, descY, W-PAD*2, descH, Color.BLACK);
            }
            default -> {
                textBox(slide, title, PAD, titleY, W-PAD*2-60, titleH, titleFs, true, style.titleColor, null);
                int d9H = innerBottom - descY;
                if (d9H > 20) fillBody(slide, bullets, PAD, descY, W-PAD*2-60, d9H, Color.BLACK);
            }
        }

        addBorder(slide);
    }


    private void createSB(XMLSlideShow ppt, GptTextGeneratorService.SlideContent content,
                          TemplateStyle style, int layout, int pattern) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        drawPattern(slide, style, pattern);

        List<String> bullets  = content.bullets != null ? content.bullets : new ArrayList<>();
        int          cnt      = Math.min(bullets.size(), 4);

        int innerTop    = BM + BT + 2;
        int innerBottom = H - BM - BT - 4;

        int titleH = 44;
        int titleY = innerTop;
        int bodyY  = titleY + titleH + 4;
        int bodyH  = innerBottom - bodyY;

        switch (layout) {
            case 0 -> {
                addBoldTitle(slide, content.title, style, titleH, titleY);
                if (bodyH > 20) fillBody(slide, bullets, PAD, bodyY, W-PAD*2, bodyH, style.textColor);
            }
            case 1 -> {
                drawFilled(slide, ShapeType.RECT, BM, innerTop, W-BM*2, titleH+4, style.titleColor);
                textBox(slide, clean(content.title), PAD, innerTop+3, W-PAD*2, titleH, FS_HEAD, true,
                        style.bgColor, org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                if (bodyH > 20) fillBody(slide, bullets, PAD, bodyY, W-PAD*2, bodyH, style.textColor);
            }
            case 2 -> {
                addBoldTitle(slide, content.title, style, titleH, titleY);
                if (cnt > 0 && bodyH > 20) {
                    int half = cnt / 2 + cnt % 2;
                    int w2   = (W - PAD*2 - 14) / 2;
                    fillBody(slide, bullets.subList(0, half), PAD, bodyY, w2, bodyH, style.textColor);
                    if (cnt > half)
                        fillBody(slide, bullets.subList(half, cnt), PAD+w2+14, bodyY, w2, bodyH, style.textColor);
                }
            }
            case 3 -> {
                addBoldTitle(slide, content.title, style, titleH, titleY);
                if (cnt > 0 && bodyH > 20) {
                    int iH = bodyH / cnt;
                    Color cf = blend(style.bgColor, style.titleColor, 0.10f);
                    Color cb = blend(style.titleColor, style.bgColor, 0.40f);
                    for (int i = 0; i < cnt; i++) {
                        int cy  = bodyY + i * iH;
                        int ch  = iH - 4;
                        if (cy + ch > innerBottom) ch = innerBottom - cy;
                        if (ch < 10) break;
                        XSLFAutoShape box = slide.createAutoShape();
                        box.setShapeType(ShapeType.RECT);
                        box.setAnchor(new Rectangle(PAD, cy, W-PAD*2, ch));
                        box.setFillColor(cf); box.setLineColor(cb); box.setLineWidth(1.0);
                        drawFilled(slide, ShapeType.RECT, PAD, cy, 6, ch, style.titleColor);
                        textBox(slide, clean(bullets.get(i)), PAD+14, cy+3, W-PAD*2-18, ch-6,
                                FS_BODY, false, style.titleColor, null);
                    }
                }
            }
            case 4 -> {
                addBoldTitle(slide, content.title, style, titleH, titleY);
                if (cnt > 0 && bodyH > 20) {
                    int iH = bodyH / cnt;
                    for (int i = 0; i < cnt; i++) {
                        int cy = bodyY + i * iH;
                        if (cy + iH > innerBottom) break;
                        textBox(slide, String.valueOf(i+1), PAD, cy, 36, iH-2, 28.0, true, style.titleColor, null);
                        textBox(slide, clean(bullets.get(i)), PAD+42, cy+2, W-PAD*2-46, iH-4,
                                FS_BODY, false, style.textColor, null);
                    }
                }
            }
            case 5 -> {
                int tW = W / 3;
                textBox(slide, clean(content.title), W-tW-PAD, innerTop, tW, innerBottom-innerTop,
                        FS_HEAD, true, style.titleColor, org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                int lW = W - tW - PAD*2 - 12;
                if (lW > 20 && innerBottom-innerTop > 20)
                    fillBody(slide, bullets, PAD, innerTop+4, lW, innerBottom-innerTop-8, style.textColor);
            }
            case 6 -> {
                drawFilled(slide, ShapeType.RECT, BM, BM, W-BM*2, H-BM*2, style.titleColor);
                addBoldTitle_color(slide, content.title, titleH, titleY, style.bgColor);
                if (bodyH > 20) fillBody_color(slide, bullets, PAD, bodyY, W-PAD*2, bodyH, style.bgColor);
            }
            case 7 -> {
                addBoldTitle(slide, content.title, style, titleH, titleY);
                if (cnt > 0 && bodyH > 20) {
                    int iH = bodyH / cnt;
                    for (int i = 0; i < cnt; i++) {
                        int cy = bodyY + i * iH;
                        if (cy + iH > innerBottom) break;
                        if (i > 0) drawFilled(slide, ShapeType.RECT, PAD, cy-1, W-PAD*2, 1,
                                blend(style.titleColor, style.bgColor, 0.5f));
                        textBox(slide, clean(bullets.get(i)), PAD, cy+3, W-PAD*2, iH-5,
                                FS_BODY, false, style.textColor, null);
                    }
                }
            }
            case 8 -> {
                textBox(slide, clean(content.title), PAD, innerTop, W-PAD*2, titleH, FS_HEAD, true,
                        style.titleColor, org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                if (bodyH > 20) fillBody_center(slide, bullets, PAD, bodyY, W-PAD*2, bodyH, style.textColor);
            }
            default -> {
                addBoldTitle(slide, content.title, style, titleH, titleY);
                if (cnt > 0 && bodyH > 20) {
                    int firstH = Math.min(80, bodyH / 2);
                    textBox(slide, clean(bullets.get(0)), PAD, bodyY, W-PAD*2, firstH,
                            FS_BODY+4, true, style.titleColor, null);
                    int remY = bodyY + firstH + 4;
                    int remH = innerBottom - remY;
                    int rem  = cnt - 1;
                    if (rem > 0 && remH > 20)
                        fillBody(slide, bullets.subList(1, cnt), PAD+16, remY, W-PAD*2-20, remH, style.textColor);
                }
            }
        }

        addBorder(slide);
    }

    private void createSC(XMLSlideShow ppt, GptTextGeneratorService.SlideContent content,
                          TemplateStyle style, byte[] imgBytes, int layout, int pattern) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        drawPattern(slide, style, pattern);

        List<String> bullets = content.bullets != null ? content.bullets : new ArrayList<>();
        int cnt = Math.min(bullets.size(), 3);

        int innerTop    = BM + BT + 2;
        int innerBottom = H - BM - BT - 4;
        int titleH = 44;
        int titleY = innerTop;
        int bodyY  = titleY + titleH + 4;
        int bodyH  = innerBottom - bodyY;

        addBoldTitle(slide, content.title, style, titleH, titleY);

        Rectangle txtRect, imgRect;
        if (layout % 2 == 0) {
            txtRect = new Rectangle(PAD,    bodyY, W/2-16,       bodyH);
            imgRect = new Rectangle(W/2+8,  bodyY, W/2-8-BM-BT,  bodyH);
        } else {
            imgRect = new Rectangle(BM+BT,  bodyY, W/2-16,       bodyH);
            txtRect = new Rectangle(W/2+8,  bodyY, W/2-26,        bodyH);
        }

        if (cnt > 0 && bodyH > 20) {
            int iH = txtRect.height / cnt;
            for (int i = 0; i < cnt; i++) {
                int cy = txtRect.y + i * iH;
                if (cy + iH > innerBottom) break;
                textBox(slide, clean(bullets.get(i)), txtRect.x, cy, txtRect.width, iH-4,
                        FS_BODY, false, style.textColor, null);
            }
        }
        placeImage(ppt, slide, imgBytes, imgRect, style);
        addBorder(slide);
    }

    private void createSD(XMLSlideShow ppt, GptTextGeneratorService.SlideContent content,
                          TemplateStyle style, int layout, int pattern) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        drawPattern(slide, style, pattern);

        List<String> bullets = content.bullets != null ? content.bullets : new ArrayList<>();

        int innerTop    = BM + BT + 2;
        int innerBottom = H - BM - BT - 4;
        int titleH = 44;

        XSLFTextBox tb = slide.createTextBox();
        tb.setAnchor(new Rectangle(PAD, innerTop, W-PAD*2, titleH));
        XSLFTextParagraph tp = tb.addNewTextParagraph();
        tp.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        XSLFTextRun tr = tp.addNewTextRun();
        tr.setText(content.title != null ? clean(content.title).toUpperCase() : "");
        tr.setFontSize(18.0); tr.setBold(true);
        tr.setFontColor(style.titleColor); tr.setFontFamily("Calibri");

        int colY = innerTop + titleH + 4;
        int colH = innerBottom - colY;
        int gap  = 10;
        int colW = (W - PAD*2 - gap*2) / 3;

        for (int i = 0; i < 3; i++) {
            int    colX = PAD + i * (colW + gap);
            String text = i < bullets.size() ? clean(bullets.get(i)) : "";

            switch (layout) {
                case 0, 3, 6 -> {
                    card(slide, colX, colY, colW, colH, style);
                    drawFilled(slide, ShapeType.RECT, colX, colY, colW, 7, style.titleColor);
                    if (!text.isBlank())
                        textBox(slide, text, colX+8, colY+11, colW-16, colH-14, FS_BODY, false, Color.BLACK, null);
                }
                case 1, 4, 7 -> {
                    Color cf = blend(style.bgColor, style.titleColor, 0.18f);
                    drawFilled(slide, ShapeType.RECT, colX, colY, colW, colH, cf);
                    drawFilled(slide, ShapeType.RECT, colX, colY, colW, 6, style.titleColor);
                    if (!text.isBlank())
                        textBox(slide, text, colX+8, colY+10, colW-16, colH-14, FS_BODY, false, Color.BLACK, null);
                }
                case 2, 5, 8 -> {
                    drawFilled(slide, ShapeType.RECT, colX, colY, 4, colH, style.titleColor);
                    if (!text.isBlank())
                        textBox(slide, text, colX+12, colY+4, colW-14, colH-8, FS_BODY, false, Color.BLACK, null);
                }
                default -> {
                    textBox(slide, String.valueOf(i+1), colX, colY, 40, 44, 28.0, true, style.titleColor, null);
                    drawFilled(slide, ShapeType.RECT, colX, colY+48, colW, 3,
                            blend(style.titleColor, style.bgColor, 0.4f));
                    if (!text.isBlank())
                        textBox(slide, text, colX, colY+56, colW, colH-60, FS_BODY, false, Color.BLACK, null);
                }
            }
        }
        addBorder(slide);
    }


    private void createThankYouSlide(XMLSlideShow ppt, String userName,
                                     TemplateStyle style, int pattern) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        drawPattern(slide, style, pattern);

        textBox(slide, "E'tiboringiz uchun rahmat!", PAD, 110, W-PAD*2, 220,
                46.0, true, style.titleColor,
                org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        textBox(slide, userName, PAD, H-110, W-PAD*2, 72,
                FS_NAME, false, style.titleColor,
                org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        addBorder(slide);
    }

    private void fillBody(XSLFSlide slide, List<String> bullets, int x, int y, int w, int h, Color color) {
        if (bullets == null || bullets.isEmpty() || h <= 10) return;
        XSLFTextBox lb = slide.createTextBox();
        lb.setAnchor(new Rectangle(x, y, w, Math.min(h, H - y - BM - BT - 8)));

        for (String text : bullets) {
            XSLFTextParagraph p = lb.addNewTextParagraph();
            p.setSpaceBefore(4.0);
            p.setSpaceAfter(4.0);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(clean(text));
            r.setFontSize(FS_BODY);
            r.setFontColor(color);
            r.setFontFamily("Calibri");
        }
    }

    private void fillBody_color(XSLFSlide slide, List<String> bullets,
                                int x, int y, int w, int h, Color color) {
        fillBody(slide, bullets, x, y, w, h, color);
    }

    private void fillBody_center(XSLFSlide slide, List<String> bullets,
                                 int x, int y, int w, int h, Color color) {
        if (bullets == null || bullets.isEmpty() || h <= 4 || w <= 4) return;
        int cnt = Math.min(bullets.size(), 4);
        XSLFTextBox lb = slide.createTextBox();
        lb.setAnchor(new Rectangle(x, y, w, Math.min(h, H - y - BM - BT - 4)));
        for (int i = 0; i < cnt; i++) {
            XSLFTextParagraph p = lb.addNewTextParagraph();
            p.setSpaceBefore(0.0); p.setSpaceAfter(0.0);
            p.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(clean(bullets.get(i)));
            r.setFontSize(FS_BODY); r.setFontColor(color); r.setFontFamily("Calibri");
        }
    }

    private void addBoldTitle(XSLFSlide slide, String title, TemplateStyle style, int h, int y) {
        textBox(slide, clean(title), PAD, y, W - PAD*2, h,
                FS_HEAD, true, style.titleColor, null);
    }

    private void addBoldTitle_color(XSLFSlide slide, String title, int h, int y, Color color) {
        textBox(slide, clean(title!=null?title:""), PAD, y, W-PAD*2, h,
                FS_HEAD, true, color, null);
    }

    private void textBox(XSLFSlide slide, String text, int x, int y, int w, int h,
                         double fontSize, boolean bold, Color color,
                         org.apache.poi.sl.usermodel.TextParagraph.TextAlign align) {
        if (w <= 0 || h <= 0) return;
        int safeH = Math.min(h, H - y - BM - BT - 6);
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(x, y, w, safeH));
        XSLFTextParagraph p = box.addNewTextParagraph();
        if (align != null) p.setTextAlign(align);
        XSLFTextRun r = p.addNewTextRun();
        r.setText(text);
        r.setFontSize(fontSize);
        r.setBold(bold);
        r.setFontColor(color);
        r.setFontFamily("Calibri");
    }

    private void card(XSLFSlide slide, int x, int y, int w, int h, TemplateStyle style) {
        XSLFAutoShape s = slide.createAutoShape();
        s.setShapeType(ShapeType.RECT);
        s.setAnchor(new Rectangle(x, y, w, h));
        s.setFillColor(blend(style.bgColor, style.titleColor, 0.12f));
        s.setLineColor(blend(style.titleColor, style.bgColor, 0.40f));
        s.setLineWidth(1.0);
    }

    private void placeImage(XMLSlideShow ppt, XSLFSlide slide, byte[] imgBytes,
                            Rectangle anchor, TemplateStyle style) {
        if (imgBytes != null && imgBytes.length > 500) {
            try {
                XSLFPictureData pd;
                try { pd = ppt.addPicture(imgBytes, PictureData.PictureType.PNG); }
                catch (Exception e2) { pd = ppt.addPicture(imgBytes, PictureData.PictureType.JPEG); }
                XSLFPictureShape pic = slide.createPicture(pd);
                pic.setAnchor(anchor);
                return;
            } catch (Exception ignored) {}
        }
        Color c1 = blend(style.titleColor, style.bgColor, 0.4f);
        Color c2 = blend(style.titleColor, style.bgColor, 0.6f);
        int cx = anchor.x + anchor.width/2;
        int cy = anchor.y + anchor.height/2;
        int r  = Math.min(anchor.width, anchor.height) / 3;
        XSLFAutoShape bg = slide.createAutoShape();
        bg.setShapeType(ShapeType.RECT); bg.setAnchor(anchor);
        bg.setFillColor(blend(style.bgColor, style.titleColor, 0.06f));
        bg.setLineColor(c2); bg.setLineWidth(1.0);
        drawOutline(slide, ShapeType.HEXAGON, cx-r, cy-r, r*2, r*2, c1, 3.0);
        drawFilled(slide, ShapeType.ELLIPSE, cx-r/2, cy-r/2, r, r, c1);
    }

    private void addBorder(XSLFSlide slide) {
        Color bc = Color.BLACK;
        drawFilled(slide, ShapeType.RECT, BM,       BM,       W-BM*2, BT,    bc);
        drawFilled(slide, ShapeType.RECT, BM,       H-BM-BT,  W-BM*2, BT,    bc);
        drawFilled(slide, ShapeType.RECT, BM,       BM,       BT,     H-BM*2, bc);
        drawFilled(slide, ShapeType.RECT, W-BM-BT,  BM,       BT,     H-BM*2, bc);
    }

    private void drawFilled(XSLFSlide slide, ShapeType type, int x, int y, int w, int h, Color fill) {
        if (w <= 0 || h <= 0) return;
        XSLFAutoShape s = slide.createAutoShape();
        s.setShapeType(type); s.setAnchor(new Rectangle(x, y, w, h));
        s.setFillColor(fill); s.setLineColor(fill);
    }

    private void drawOutline(XSLFSlide slide, ShapeType type, int x, int y, int w, int h,
                             Color lc, double lw) {
        if (w <= 0 || h <= 0) return;
        XSLFAutoShape s = slide.createAutoShape();
        s.setShapeType(type); s.setAnchor(new Rectangle(x, y, w, h));
        s.setFillColor(new Color(0, 0, 0, 0));
        s.setLineColor(lc); s.setLineWidth(lw);
    }

    private String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("\\*\\*([^*]*)\\*\\*", "$1")
                .replaceAll("\\*([^*]*)\\*", "$1")
                .replaceAll("__([^_]*)__", "$1")
                .replaceAll("_([^_]*)_", "$1").trim();
    }

    private Color blend(Color a, Color b, float ratio) {
        float r = Math.min(1f, Math.max(0f, ratio));
        return new Color((int)(a.getRed()*(1-r)+b.getRed()*r),
                (int)(a.getGreen()*(1-r)+b.getGreen()*r),
                (int)(a.getBlue()*(1-r)+b.getBlue()*r));
    }

    private int parseTemplate(String t) {
        try { return Integer.parseInt(t != null ? t.trim() : "1"); }
        catch (Exception e) { return 1; }
    }

    private TemplateStyle getTemplateStyle(int template) {
        return switch (template) {
            case 1  -> new TemplateStyle(Color.WHITE,           new Color(110, 83, 49),  Color.BLACK); // ✅
            case 2  -> new TemplateStyle(new Color(40, 40, 40), new Color(181,156,115),  Color.BLACK); // ✅
            case 3  -> new TemplateStyle(new Color(60, 40, 20), new Color(160,120, 80),  Color.BLACK); // ✅
            case 4  -> new TemplateStyle(Color.WHITE,           new Color( 46, 99, 58),  Color.BLACK); // ✅
            case 5  -> new TemplateStyle(Color.WHITE,           new Color( 74, 60,117),  Color.BLACK); // ✅
            case 6  -> new TemplateStyle(Color.WHITE,           new Color( 34, 91,145),  Color.BLACK); // ✅
            case 7  -> new TemplateStyle(Color.WHITE,           new Color( 14, 48,102),  Color.BLACK); // ✅
            case 8  -> new TemplateStyle(new Color(30, 30, 30), new Color(197,211,224),  Color.BLACK); // ✅
            case 9  -> new TemplateStyle(Color.WHITE,           new Color( 28, 66, 33),  Color.BLACK); // ✅
            case 10 -> new TemplateStyle(new Color(20, 20, 20), new Color(180,195,210),  Color.BLACK); // ✅
            default -> new TemplateStyle(Color.BLACK,           Color.WHITE,             Color.BLACK);
        };
    }

    private static class TemplateStyle {
        final Color titleColor, bgColor, textColor;
        TemplateStyle(Color t, Color b, Color tx) { titleColor=t; bgColor=b; textColor=tx; }
    }
}