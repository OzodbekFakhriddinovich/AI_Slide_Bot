package uz.ai.slideshowbot.service;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.*;
import java.net.URI;
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

    private static final double FS_TOPIC     = 40.0;
    private static final double FS_NAME      = 36.0;
    private static final double FS_REJA_HEAD = 55.0;
    private static final double FS_REJA_ITEM = 45.0;
    private static final double FS_HEAD      = 26.0;
    private static final double FS_BODY      = 24.0;

    private static final AtomicInteger LAYOUT_COUNTER = new AtomicInteger(0);
    private final ExecutorService imagePool = Executors.newFixedThreadPool(5);

    public String createPptxFromSections(String topic, String userName, String subjectName,
                                         List<GptTextGeneratorService.Section> sections,
                                         String templateStr) throws IOException {
        File dir = new File("slides");
        if (!dir.exists()) dir.mkdirs();

        int           template = parseTemplate(templateStr);
        TemplateStyle style    = getTemplateStyle(template);
        int layout = LAYOUT_COUNTER.getAndIncrement() % 10;

        Map<Integer, byte[]> imgCache = prefetchImages(sections, topic);

        XMLSlideShow ppt = new XMLSlideShow();
        ppt.setPageSize(new Dimension(W, H));

        createTitleSlide(ppt, topic, userName, subjectName, style);
        createRejaSlide(ppt, sections, style);

        for (int i = 0; i < sections.size(); i++) {
            GptTextGeneratorService.Section sec = sections.get(i);
            List<GptTextGeneratorService.SlideContent> slides = sec.slides();

            createSA(ppt, sec.title(), slides, style, layout);

            if (!slides.isEmpty())
                createSB(ppt, slides.get(0), style, layout);

            if (slides.size() > 1)
                createSC(ppt, slides.get(1), style, imgCache.get(i), layout);

            if (slides.size() > 2)
                createSD(ppt, slides.get(2), style, layout);
        }

        createThankYouSlide(ppt, userName, style);

        String safe     = topic.replaceAll("[^a-zA-Z0-9\\s]", "_").trim();
        String fileName = "slides/" + safe + "_" + System.currentTimeMillis() + ".pptx";
        try (FileOutputStream out = new FileOutputStream(fileName)) { ppt.write(out); }
        ppt.close();
        return fileName;
    }

    public String createPptx(String topic, String userName, String subjectName,
                             List<GptTextGeneratorService.SlideData> slides,
                             String templateStr) throws IOException {
        List<GptTextGeneratorService.Section> sections = new ArrayList<>();
        for (int i = 0; i < slides.size(); i += 3) {
            List<GptTextGeneratorService.SlideContent> contents = new ArrayList<>();
            for (int j = i; j < Math.min(i + 3, slides.size()); j++) {
                GptTextGeneratorService.SlideData    sd = slides.get(j);
                GptTextGeneratorService.SlideContent sc = new GptTextGeneratorService.SlideContent();
                sc.title = sd.title; sc.bullets = sd.bullets; sc.imageQuery = sd.imageQuery;
                contents.add(sc);
            }
            while (contents.size() < 3) {
                GptTextGeneratorService.SlideContent e = new GptTextGeneratorService.SlideContent();
                e.title = "Ma'lumot"; e.bullets = new ArrayList<>(List.of("Qo'shimcha")); e.imageQuery = topic;
                contents.add(e);
            }
            sections.add(new GptTextGeneratorService.Section(contents.get(0).title, contents));
        }
        return createPptxFromSections(topic, userName, subjectName, sections, templateStr);
    }

    private Map<Integer, byte[]> prefetchImages(List<GptTextGeneratorService.Section> sections, String topic) {
        Map<Integer, byte[]>         result  = new ConcurrentHashMap<>();
        Map<Integer, Future<byte[]>> futures = new LinkedHashMap<>();
        for (int i = 0; i < sections.size(); i++) {
            List<GptTextGeneratorService.SlideContent> sls = sections.get(i).slides();
            if (sls.size() <= 1) continue;
            final int idx = i; final String q = sls.get(1).imageQuery;
            futures.put(idx, imagePool.submit(() -> {
                try {
                    String url = imageSearchService.findImageUrl(q, topic);
                    if (url == null || url.isBlank()) return null;
                    try (InputStream is = URI.create(url).toURL().openStream()) {
                        byte[] b = is.readAllBytes(); return b.length > 500 ? b : null;
                    }
                } catch (Exception e) { return null; }
            }));
        }
        futures.forEach((idx, f) -> {
            try { byte[] b = f.get(20, TimeUnit.SECONDS); if (b != null) result.put(idx, b); }
            catch (Exception ignored) {}
        });
        return result;
    }

    private void createTitleSlide(XMLSlideShow ppt, String topic, String userName,
                                  String subjectName, TemplateStyle style) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        textBox(slide, topic.toUpperCase(), PAD, 50, W-PAD*2, 330, FS_TOPIC, true,
                style.titleColor, org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        String info = userName + (subjectName != null && !subjectName.isBlank() ? "  |  "+subjectName : "");
        textBox(slide, info, PAD, H-110, W-PAD*2, 80, FS_NAME, false,
                style.titleColor, org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        addBorder(slide);
    }

    private void createRejaSlide(XMLSlideShow ppt, List<GptTextGeneratorService.Section> sections,
                                 TemplateStyle style) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        int count = Math.min(sections.size(), 5);
        int titleH = 60, titleY = BM+BT+4;
        textBox(slide, "Reja", PAD, titleY, W-PAD*2, titleH, FS_REJA_HEAD, true, style.titleColor, null);
        int listY = titleY+titleH+2, listH = H-listY-BM-BT-4;
        double fs = FS_REJA_ITEM;
        if (count * fs * 1.15 > listH) fs = Math.max(18.0, (listH/count)/1.15);
        XSLFTextBox lb = slide.createTextBox();
        lb.setAnchor(new Rectangle(PAD, listY, W-PAD*2, listH));
        for (int i = 0; i < count; i++) {
            XSLFTextParagraph p = lb.addNewTextParagraph();
            p.setSpaceBefore(0.0); p.setSpaceAfter(0.0);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(clean(sections.get(i).title()));
            r.setFontSize(fs); r.setBold(true);
            r.setFontColor(style.titleColor); r.setFontFamily("Calibri");
        }
        addBorder(slide);
    }

    private void createSA(XMLSlideShow ppt, String sectionTitle,
                          List<GptTextGeneratorService.SlideContent> slides,
                          TemplateStyle style, int layout) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        String title = clean(sectionTitle);
        List<String> bullets = (!slides.isEmpty() && slides.get(0).bullets != null)
                ? slides.get(0).bullets : new ArrayList<>();

        switch (layout) {
            case 0 -> {
                drawFilled(slide, ShapeType.RECT, BM+BT, BM+BT, 12, H-(BM+BT)*2, blend(style.titleColor,style.bgColor,0.3f));
                textBox(slide, title, 32, BM+BT+8, W-46, 100, 64.0, true, style.titleColor, null);
                bulletsBlock(slide, bullets, 32, BM+BT+118, W-46, H-BM-BT-126, FS_BODY, style);
            }
            case 1 -> {
                drawFilled(slide, ShapeType.RECT, W-BM-BT-12, BM+BT, 12, H-(BM+BT)*2, blend(style.titleColor,style.bgColor,0.3f));
                textBox(slide, title, PAD, 30, W-PAD*2-16, 180, 60.0, true, style.titleColor,
                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                bulletsBlock(slide, bullets, PAD, 215, W-PAD*2-16, H-225-BM-BT, FS_BODY, style);
            }
            case 2 -> {
                drawFilled(slide, ShapeType.RECT, BM, BM, W-BM*2, 90, style.titleColor);
                textBox(slide, title, PAD, BM+8, W-PAD*2, 76, 44.0, true, style.bgColor,
                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                bulletsBlock(slide, bullets, PAD, 98, W-PAD*2, H-106-BM-BT, FS_BODY, style);
            }
            case 3 -> {
                drawFilled(slide, ShapeType.RECT, BM, BM, W/3, H-BM*2, style.titleColor);
                textBox(slide, title, BM+8, H/2-70, W/3-16, 140, 32.0, true, style.bgColor,
                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                bulletsBlock(slide, bullets, W/3+PAD, BM+BT+PAD, W-W/3-PAD*2-BM, H-BM*2-PAD*2, FS_BODY, style);
            }
            case 4 -> {
                drawFilled(slide, ShapeType.RECT, BM, H-BM-90, W-BM*2, 90, style.titleColor);
                textBox(slide, title, PAD, BM+BT+6, W-PAD*2, 120, 56.0, true, style.titleColor, null);
                bulletsBlock(slide, bullets, PAD, 130, W-PAD*2, H-220-BM, FS_BODY, style);
            }
            case 5 -> {
                drawFilled(slide, ShapeType.RECT, W-W/3-BM, BM, W/3, H-BM*2, style.titleColor);
                textBox(slide, title, PAD, BM+BT+10, W-W/3-PAD*2-8, 160, 52.0, true, style.titleColor, null);
                bulletsBlock(slide, bullets, PAD, 175, W-W/3-PAD*2-8, H-183-BM-BT, FS_BODY, style);
            }
            case 6 -> {
                drawFilled(slide, ShapeType.RECT, BM, BM, W-BM*2, H-BM*2, style.titleColor);
                textBox(slide, title, PAD, BM+BT+10, W-PAD*2, 120, 56.0, true, style.bgColor, null);
                bulletsBlock_color(slide, bullets, PAD, 140, W-PAD*2, H-148-BM-BT, FS_BODY, style.bgColor);
            }
            case 7 -> {
                drawFilled(slide, ShapeType.RECT, BM, BM,     W-BM*2, 8, style.titleColor);
                drawFilled(slide, ShapeType.RECT, BM, H-BM-8, W-BM*2, 8, style.titleColor);
                textBox(slide, title, PAD, 16, W-PAD*2, 130, 60.0, true, style.titleColor,
                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                bulletsBlock(slide, bullets, PAD, 154, W-PAD*2, H-162-16-BM, FS_BODY, style);
            }
            case 8 -> {
                textBox(slide, title, PAD, BM+BT+8, W-PAD*2, 130, 58.0, true, style.titleColor,
                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                bulletsBlock2col(slide, bullets, style);
            }
            default -> {
                Color dim = blend(style.titleColor, style.bgColor, 0.75f);
                drawFilled(slide, ShapeType.TRIANGLE, W-200, H-200, 200, 200, dim);
                drawFilled(slide, ShapeType.ELLIPSE,  W-60,  10,    55,  55,  dim);
                textBox(slide, title, PAD, BM+BT+8, W-PAD*2-80, 120, 58.0, true, style.titleColor, null);
                bulletsBlock(slide, bullets, PAD, 136, W-PAD*2-80, H-144-BM-BT, FS_BODY, style);
            }
        }
        addBorder(slide);
    }

    private void createSB(XMLSlideShow ppt, GptTextGeneratorService.SlideContent content,
                          TemplateStyle style, int layout) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        List<String> bullets = content.bullets != null ? content.bullets : new ArrayList<>();
        int cnt = Math.min(bullets.size(), 4);

        switch (layout) {
            case 0 -> {
                addBoldTitle(slide, content.title, style, 50);
                tightBulletList(slide, bullets, PAD, BM+BT+56, W-PAD*2, H-BM-BT-64, FS_BODY, style.textColor);
            }
            case 1 -> {
                drawFilled(slide, ShapeType.RECT, BM, BM, W-BM*2, 56, style.titleColor);
                textBox(slide, clean(content.title), PAD, BM+6, W-PAD*2, 44, FS_HEAD, true, style.bgColor,
                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                tightBulletList(slide, bullets, PAD, 62, W-PAD*2, H-70-BM-BT, FS_BODY, style.textColor);
            }
            case 2 -> {
                addBoldTitle(slide, content.title, style, 50);
                int sY = BM+BT+56, sH = H-sY-BM-BT-6;
                int half = cnt/2 + cnt%2, w2 = (W-PAD*2-12)/2;
                tightBulletList(slide, bullets.subList(0, half), PAD, sY, w2, sH, FS_BODY, style.textColor);
                if (cnt > half) tightBulletList(slide, bullets.subList(half, cnt), PAD+w2+12, sY, w2, sH, FS_BODY, style.textColor);
            }
            case 3 -> {
                addBoldTitle(slide, content.title, style, 50);
                int sY = BM+BT+56, avH = H-sY-BM-BT-6, iH = cnt > 0 ? avH/cnt : avH;
                Color cf = blend(style.bgColor, style.titleColor, 0.10f);
                Color cb = blend(style.titleColor, style.bgColor, 0.40f);
                for (int i = 0; i < cnt; i++) {
                    int cy = sY + i*(iH+4);
                    XSLFAutoShape box = slide.createAutoShape();
                    box.setShapeType(ShapeType.RECT);
                    box.setAnchor(new Rectangle(PAD, cy, W-PAD*2, iH-2));
                    box.setFillColor(cf); box.setLineColor(cb); box.setLineWidth(1.0);
                    drawFilled(slide, ShapeType.RECT, PAD, cy, 6, iH-2, style.titleColor);
                    textBox(slide, clean(bullets.get(i)), PAD+14, cy+2, W-PAD*2-18, iH-6, FS_BODY, false, style.textColor, null);
                }
            }
            case 4 -> {
                addBoldTitle(slide, content.title, style, 50);
                int sY = BM+BT+56, avH = H-sY-BM-BT-6, iH = cnt > 0 ? avH/cnt : avH;
                for (int i = 0; i < cnt; i++) {
                    int cy = sY + i*(iH+3);
                    textBox(slide, String.valueOf(i+1), PAD, cy, 40, iH, 32.0, true, style.titleColor, null);
                    textBox(slide, clean(bullets.get(i)), PAD+48, cy+2, W-PAD*2-52, iH-4, FS_BODY, false, style.textColor, null);
                }
            }
            case 5 -> {
                int tW = W/3;
                textBox(slide, clean(content.title), W-tW-PAD, BM+BT+6, tW, H-BM-BT-12, FS_HEAD, true,
                        style.titleColor, org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                tightBulletList(slide, bullets, PAD, BM+BT+6, W-tW-PAD*2-12, H-BM-BT-12, FS_BODY, style.textColor);
            }
            case 6 -> {
                drawFilled(slide, ShapeType.RECT, BM, BM, W-BM*2, H-BM*2, style.titleColor);
                textBox(slide, clean(content.title), PAD, BM+BT+6, W-PAD*2, 52, FS_HEAD, true, style.bgColor, null);
                tightBulletList(slide, bullets, PAD, BM+BT+60, W-PAD*2, H-BM-BT-68, FS_BODY, style.bgColor);
            }
            case 7 -> {
                addBoldTitle(slide, content.title, style, 50);
                int sY = BM+BT+56, avH = H-sY-BM-BT-6, iH = cnt > 0 ? avH/cnt : avH;
                for (int i = 0; i < cnt; i++) {
                    int cy = sY + i*iH;
                    if (i > 0) drawFilled(slide, ShapeType.RECT, PAD, cy-1, W-PAD*2, 1, blend(style.titleColor,style.bgColor,0.5f));
                    textBox(slide, clean(bullets.get(i)), PAD, cy+2, W-PAD*2, iH-4, FS_BODY, false, style.textColor, null);
                }
            }
            case 8 -> {
                textBox(slide, clean(content.title), PAD, BM+BT+6, W-PAD*2, 60, FS_HEAD, true, style.titleColor,
                        org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
                tightBulletList_center(slide, bullets, PAD, BM+BT+72, W-PAD*2, H-BM-BT-80, FS_BODY, style.textColor);
            }
            default -> {
                addBoldTitle(slide, content.title, style, 50);
                if (cnt > 0) textBox(slide, clean(bullets.get(0)), PAD, BM+BT+56, W-PAD*2, 100, 28.0, true, style.titleColor, null);
                int sY = BM+BT+162, avH = H-sY-BM-BT-6, rem = cnt-1;
                if (rem > 0) {
                    int iH = avH/rem;
                    for (int i = 1; i < cnt; i++) {
                        textBox(slide, clean(bullets.get(i)), PAD+20, sY+(i-1)*iH, W-PAD*2-24, iH-4, FS_BODY-2, false, style.textColor, null);
                    }
                }
            }
        }
        addBorder(slide);
    }

    private void createSC(XMLSlideShow ppt, GptTextGeneratorService.SlideContent content,
                          TemplateStyle style, byte[] imgBytes, int layout) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        List<String> bullets = content.bullets != null ? content.bullets : new ArrayList<>();
        int cnt = Math.min(bullets.size(), 3);
        int top = BM+BT+56, cH = H-top-BM-BT-6;

        addBoldTitle(slide, content.title, style, 50);

        Rectangle txtRect, imgRect;

        if (layout % 2 == 0) {
            txtRect = new Rectangle(PAD,     top, W/2 - 16,          cH);
            imgRect = new Rectangle(W/2 + 8, top, W/2 - 8 - BM - BT, cH);
        } else {
            imgRect = new Rectangle(BM + BT, top, W/2 - 16,          cH);
            txtRect = new Rectangle(W/2 + 8, top, W/2 - 26,          cH);
        }

        if (cnt > 0) {
            int iH = txtRect.height / cnt;
            for (int i = 0; i < cnt; i++) {
                textBox(slide, clean(bullets.get(i)),
                        txtRect.x, txtRect.y + i*iH, txtRect.width, iH-4,
                        FS_BODY, false, style.textColor, null);
            }
        }
        placeImage(ppt, slide, imgBytes, imgRect, style);
        addBorder(slide);
    }

    private void createSD(XMLSlideShow ppt, GptTextGeneratorService.SlideContent content,
                          TemplateStyle style, int layout) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        List<String> bullets = content.bullets != null ? content.bullets : new ArrayList<>();
        int titleH = 52;

        XSLFTextBox tb = slide.createTextBox();
        tb.setAnchor(new Rectangle(PAD, BM+BT+2, W-PAD*2, titleH));
        XSLFTextParagraph tp = tb.addNewTextParagraph();
        tp.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        XSLFTextRun tr = tp.addNewTextRun();
        tr.setText(content.title != null ? clean(content.title).toUpperCase() : "");
        tr.setFontSize(22.0); tr.setBold(true);
        tr.setFontColor(style.titleColor); tr.setFontFamily("Calibri");

        int colY = BM+BT+titleH+4, colH = H-colY-BM-BT-4;
        int gap = 12, colW = (W-PAD*2-gap*2)/3;

        for (int i = 0; i < 3; i++) {
            int colX = PAD + i*(colW+gap);
            String text = i < bullets.size() ? clean(bullets.get(i)) : "";

            switch (layout) {
                case 0, 3, 6 -> {
                    card(slide, colX, colY, colW, colH, ShapeType.RECT, style);
                    drawFilled(slide, ShapeType.RECT, colX, colY, colW, 8, style.titleColor);
                    textBox(slide, text, colX+8, colY+12, colW-16, colH-16, FS_BODY, false, style.textColor, null);
                }
                case 1, 4, 7 -> {
                    drawFilled(slide, ShapeType.RECT, colX, colY, colW, colH,
                            blend(style.titleColor, style.bgColor, i==1 ? 0.1f : i==2 ? 0.2f : 0.3f));
                    drawFilled(slide, ShapeType.RECT, colX, colY, colW, 6, style.titleColor);
                    textBox(slide, text, colX+8, colY+10, colW-16, colH-14, FS_BODY, false, style.textColor, null);
                }
                case 2, 5, 8 -> {
                    drawFilled(slide, ShapeType.RECT, colX, colY, 4, colH, style.titleColor);
                    textBox(slide, text, colX+12, colY+4, colW-16, colH-8, FS_BODY, false, style.textColor, null);
                }
                default -> {
                    textBox(slide, String.valueOf(i+1), colX, colY, 44, 50, 36.0, true, style.titleColor, null);
                    drawFilled(slide, ShapeType.RECT, colX, colY+54, colW, 3, blend(style.titleColor,style.bgColor,0.4f));
                    textBox(slide, text, colX, colY+62, colW, colH-66, FS_BODY-1, false, style.textColor, null);
                }
            }
        }
        addBorder(slide);
    }

    private void createThankYouSlide(XMLSlideShow ppt, String userName, TemplateStyle style) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        textBox(slide, "E'tiboringiz uchun rahmat!", PAD, 110, W-PAD*2, 220, 50.0, true, style.titleColor,
                org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        textBox(slide, userName, PAD, H-115, W-PAD*2, 80, FS_NAME, false, style.titleColor,
                org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
        addBorder(slide);
    }

    private void addBoldTitle(XSLFSlide slide, String title, TemplateStyle style, int h) {
        textBox(slide, clean(title != null ? title : ""), PAD, BM+BT+2, W-PAD*2, h, FS_HEAD, true, style.titleColor, null);
    }

    private void textBox(XSLFSlide slide, String text, int x, int y, int w, int h,
                         double fontSize, boolean bold, Color color,
                         org.apache.poi.sl.usermodel.TextParagraph.TextAlign align) {
        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(x, y, w, h));
        XSLFTextParagraph p = box.addNewTextParagraph();
        if (align != null) p.setTextAlign(align);
        XSLFTextRun r = p.addNewTextRun();
        r.setText(text); r.setFontSize(fontSize); r.setBold(bold);
        r.setFontColor(color); r.setFontFamily("Calibri");
    }

    private void tightBulletList(XSLFSlide slide, List<String> bullets,
                                 int x, int y, int w, int h, double fs, Color color) {
        if (bullets == null || bullets.isEmpty()) return;
        int cnt = Math.min(bullets.size(), 4);
        XSLFTextBox lb = slide.createTextBox();
        lb.setAnchor(new Rectangle(x, y, w, h));
        for (int i = 0; i < cnt; i++) {
            XSLFTextParagraph p = lb.addNewTextParagraph();
            p.setSpaceBefore(0.0); p.setSpaceAfter(0.0);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(clean(bullets.get(i)));
            r.setFontSize(fs); r.setFontColor(color); r.setFontFamily("Calibri");
        }
    }

    private void tightBulletList_center(XSLFSlide slide, List<String> bullets,
                                        int x, int y, int w, int h, double fs, Color color) {
        if (bullets == null || bullets.isEmpty()) return;
        int cnt = Math.min(bullets.size(), 4);
        XSLFTextBox lb = slide.createTextBox();
        lb.setAnchor(new Rectangle(x, y, w, h));
        for (int i = 0; i < cnt; i++) {
            XSLFTextParagraph p = lb.addNewTextParagraph();
            p.setSpaceBefore(0.0); p.setSpaceAfter(0.0);
            p.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(clean(bullets.get(i)));
            r.setFontSize(fs); r.setFontColor(color); r.setFontFamily("Calibri");
        }
    }

    private void bulletsBlock(XSLFSlide slide, List<String> bullets,
                              int x, int y, int w, int h, double fs, TemplateStyle style) {
        tightBulletList(slide, bullets, x, y, w, h, fs, style.textColor);
    }

    private void bulletsBlock_color(XSLFSlide slide, List<String> bullets,
                                    int x, int y, int w, int h, double fs, Color color) {
        tightBulletList(slide, bullets, x, y, w, h, fs, color);
    }

    private void bulletsBlock2col(XSLFSlide slide, List<String> bullets, TemplateStyle style) {
        if (bullets == null || bullets.isEmpty()) return;
        int cnt = Math.min(bullets.size(), 4);
        int sY = BM+BT+140, sH = H-sY-BM-BT-10;
        int half = cnt/2 + cnt%2, w2 = (W-PAD*2-16)/2;
        tightBulletList(slide, bullets.subList(0, half), PAD, sY, w2, sH, FS_BODY, style.textColor);
        if (cnt > half) tightBulletList(slide, bullets.subList(half, cnt), PAD+w2+16, sY, w2, sH, FS_BODY, style.textColor);
    }

    private void card(XSLFSlide slide, int x, int y, int w, int h, ShapeType type, TemplateStyle style) {
        XSLFAutoShape s = slide.createAutoShape();
        s.setShapeType(type); s.setAnchor(new Rectangle(x, y, w, h));
        s.setFillColor(blend(style.bgColor, style.titleColor, 0.12f));
        s.setLineColor(blend(style.titleColor, style.bgColor, 0.40f)); s.setLineWidth(1.0);
    }

    private void placeImage(XMLSlideShow ppt, XSLFSlide slide, byte[] imgBytes,
                            Rectangle anchor, TemplateStyle style) {
        if (imgBytes != null && imgBytes.length > 500) {
            try {
                XSLFPictureData  pd  = ppt.addPicture(imgBytes, PictureData.PictureType.JPEG);
                XSLFPictureShape pic = slide.createPicture(pd);
                pic.setAnchor(anchor); return;
            } catch (Exception e) { System.err.println("Rasm xatosi: " + e.getMessage()); }
        }
        Color c1 = blend(style.titleColor, style.bgColor, 0.4f);
        Color c2 = blend(style.titleColor, style.bgColor, 0.6f);
        int cx = anchor.x+anchor.width/2, cy = anchor.y+anchor.height/2;
        int r = Math.min(anchor.width, anchor.height)/3;
        XSLFAutoShape bg = slide.createAutoShape();
        bg.setShapeType(ShapeType.RECT); bg.setAnchor(anchor);
        bg.setFillColor(blend(style.bgColor,style.titleColor,0.06f));
        bg.setLineColor(c2); bg.setLineWidth(1.0);
        drawOutline(slide, ShapeType.HEXAGON, cx-r, cy-r, r*2, r*2, c1, 3.0);
        drawFilled(slide, ShapeType.ELLIPSE, cx-r/2, cy-r/2, r, r, c1);
        int sq=12;
        drawOutline(slide,ShapeType.RECT,anchor.x+6,anchor.y+6,sq,sq,c2,1.0);
        drawOutline(slide,ShapeType.RECT,anchor.x+anchor.width-6-sq,anchor.y+6,sq,sq,c2,1.0);
        drawOutline(slide,ShapeType.RECT,anchor.x+6,anchor.y+anchor.height-6-sq,sq,sq,c2,1.0);
        drawOutline(slide,ShapeType.RECT,anchor.x+anchor.width-6-sq,anchor.y+anchor.height-6-sq,sq,sq,c2,1.0);
    }

    private void addBorder(XSLFSlide slide) {
        Color bc = Color.BLACK;
        drawFilled(slide,ShapeType.RECT,BM,BM,W-BM*2,BT,bc);
        drawFilled(slide,ShapeType.RECT,BM,H-BM-BT,W-BM*2,BT,bc);
        drawFilled(slide,ShapeType.RECT,BM,BM,BT,H-BM*2,bc);
        drawFilled(slide,ShapeType.RECT,W-BM-BT,BM,BT,H-BM*2,bc);
    }

    private void drawFilled(XSLFSlide slide, ShapeType type, int x, int y, int w, int h, Color fill) {
        XSLFAutoShape s = slide.createAutoShape();
        s.setShapeType(type); s.setAnchor(new Rectangle(x,y,w,h));
        s.setFillColor(fill); s.setLineColor(fill);
    }

    private void drawOutline(XSLFSlide slide, ShapeType type, int x, int y, int w, int h, Color lc, double lw) {
        XSLFAutoShape s = slide.createAutoShape();
        s.setShapeType(type); s.setAnchor(new Rectangle(x,y,w,h));
        s.setFillColor(new Color(0,0,0,0)); s.setLineColor(lc); s.setLineWidth(lw);
    }

    private String clean(String text) {
        if (text == null) return "";
        return text.replaceAll("\\*\\*([^*]*)\\*\\*","$1")
                .replaceAll("\\*([^*]*)\\*","$1")
                .replaceAll("__([^_]*)__","$1")
                .replaceAll("_([^_]*)_","$1").trim();
    }

    private Color blend(Color a, Color b, float ratio) {
        float r = Math.min(1f,Math.max(0f,ratio));
        return new Color((int)(a.getRed()*(1-r)+b.getRed()*r),
                (int)(a.getGreen()*(1-r)+b.getGreen()*r),
                (int)(a.getBlue()*(1-r)+b.getBlue()*r));
    }

    private int parseTemplate(String t) {
        try { return Integer.parseInt(t!=null?t.trim():"1"); } catch(Exception e){return 1;}
    }

     private TemplateStyle getTemplateStyle(int template) {
        return switch (template) {
            case 1  -> new TemplateStyle(Color.WHITE,           new Color(110, 83, 49),  Color.WHITE,           410,100,290,260);
            case 2  -> new TemplateStyle(new Color(40, 40, 40), new Color(181,156,115),  new Color(50, 50, 50), 410,100,290,260);
            case 3  -> new TemplateStyle(new Color(60, 40, 20), new Color(160,120, 80),  Color.WHITE,           410,100,290,260);
            case 4  -> new TemplateStyle(Color.WHITE,           new Color( 46, 99, 58),  Color.WHITE,           410,100,290,260);
            case 5  -> new TemplateStyle(Color.WHITE,           new Color( 74, 60,117),  Color.WHITE,           410,100,290,260);
            case 6  -> new TemplateStyle(Color.WHITE,           new Color( 34, 91,145),  Color.WHITE,           410,100,290,260);
            case 7  -> new TemplateStyle(Color.WHITE,           new Color( 14, 48,102),  Color.WHITE,           410,100,290,260);
            case 8  -> new TemplateStyle(new Color(30, 30, 30), new Color(197,211,224),  new Color(40, 40, 40), 410,100,290,260);
            case 9  -> new TemplateStyle(Color.WHITE,           new Color( 28, 66, 33),  Color.WHITE,           410,100,290,260);
            case 10 -> new TemplateStyle(new Color(20, 20, 20), new Color(180,195,210),  new Color(50, 50, 50), 410,100,290,260);
            default -> new TemplateStyle(Color.BLACK,           Color.WHITE,             Color.DARK_GRAY,       410,100,290,260);
        };
    }

    private static class TemplateStyle {
        final Color titleColor, bgColor, textColor;
        @SuppressWarnings("unused") final int imageX, imageY, imageW, imageH;
        TemplateStyle(Color t, Color b, Color tx, int ix, int iy, int iw, int ih) {
            titleColor=t; bgColor=b; textColor=tx; imageX=ix; imageY=iy; imageW=iw; imageH=ih;
        }
    }
}