package uz.ai.slideshowbot.service;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.*;
import java.net.URI;
import java.util.List;

@Service
public class PptxCreatorService {

    @Autowired
    private ImageSearchService imageSearchService;

    private static final int SLIDE_W = 720;
    private static final int SLIDE_H = 540;

    // ─────────────────────────────────────────────────────────────────────────
    //  Asosiy metod (sections + slides)
    // ─────────────────────────────────────────────────────────────────────────
    public String createPptx(String topic,
                             String userName,
                             String subjectName,
                             List<String> sections,
                             List<GptTextGeneratorService.SlideData> slides,
                             String templateStr) throws IOException {

        File dir = new File("slides");
        if (!dir.exists()) dir.mkdirs();

        XMLSlideShow ppt = new XMLSlideShow();
        ppt.setPageSize(new Dimension(SLIDE_W, SLIDE_H));

        int template = parseTemplate(templateStr);
        TemplateStyle style = getTemplateStyle(template);

        createTitleSlide(ppt, topic, userName, subjectName, style);
        createPlanSlide(ppt, sections, style);

        for (GptTextGeneratorService.SlideData data : slides) {
            createContentSlide(ppt, data, topic, style);
        }

        createConclusionSlide(ppt, topic, userName, style);

        String safe = topic.replaceAll("[^a-zA-Z0-9\\s]", "_").trim();
        String fileName = "slides/" + safe + "_" + System.currentTimeMillis() + ".pptx";

        try (FileOutputStream out = new FileOutputStream(fileName)) {
            ppt.write(out);
        }
        ppt.close();
        return fileName;
    }

    // Eski imzo (sections yo'q holat uchun)
    public String createPptx(String topic,
                             String userName,
                             String subjectName,
                             List<GptTextGeneratorService.SlideData> slides,
                             String templateStr) throws IOException {
        List<String> fallback = slides.stream().limit(4).map(s -> s.title).toList();
        return createPptx(topic, userName, subjectName, fallback, slides, templateStr);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  1. SARLAVHA SLAYD
    // ─────────────────────────────────────────────────────────────────────────
    private void createTitleSlide(XMLSlideShow ppt, String topic,
                                  String userName, String subjectName,
                                  TemplateStyle style) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);

        addDiagonalDecoration(slide, style);

        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(50, 80, 620, 220));
        XSLFTextParagraph tp = titleBox.addNewTextParagraph();
        tp.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.LEFT);
        XSLFTextRun tr = tp.addNewTextRun();
        tr.setText(topic.toUpperCase());
        tr.setFontSize(42.0);
        tr.setBold(true);
        tr.setFontColor(style.titleColor);
        tr.setFontFamily("Calibri");

        XSLFTextBox infoBox = slide.createTextBox();
        infoBox.setAnchor(new Rectangle(50, 390, 500, 45));
        XSLFTextRun ir = infoBox.addNewTextParagraph().addNewTextRun();
        String info = "Tayyorladi: " + userName;
        if (subjectName != null && !subjectName.isBlank()) info += "  |  " + subjectName;
        ir.setText(info);
        ir.setFontSize(18.0);
        ir.setFontColor(style.textColor);
        ir.setFontFamily("Calibri");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  2. REJA SLAYD — faqat 4 ta bo'lim
    // ─────────────────────────────────────────────────────────────────────────
    private void createPlanSlide(XMLSlideShow ppt, List<String> sections, TemplateStyle style) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        addDiagonalDecoration(slide, style);

        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(40, 20, 640, 70));
        XSLFTextRun titleRun = titleBox.addNewTextParagraph().addNewTextRun();
        titleRun.setText("REJA");
        titleRun.setFontSize(38.0);
        titleRun.setBold(true);
        titleRun.setFontColor(style.titleColor);
        titleRun.setFontFamily("Calibri");

        int[] xs = {40, 380, 40, 380};
        int[] ys = {115, 115, 315, 315};
        int count = Math.min(4, sections.size());

        for (int i = 0; i < count; i++) {
            int bx = xs[i], by = ys[i];

            // Raqam doirasi
            XSLFAutoShape circle = slide.createAutoShape();
            circle.setShapeType(ShapeType.ELLIPSE);
            circle.setAnchor(new Rectangle(bx, by, 42, 42));
            circle.setFillColor(style.titleColor);
            circle.setLineColor(style.titleColor);

            // Raqam matni
            XSLFTextBox numBox = slide.createTextBox();
            numBox.setAnchor(new Rectangle(bx + 11, by + 6, 22, 30));
            XSLFTextRun numRun = numBox.addNewTextParagraph().addNewTextRun();
            numRun.setText(String.valueOf(i + 1));
            numRun.setFontSize(18.0);
            numRun.setBold(true);
            numRun.setFontColor(style.bgColor);
            numRun.setFontFamily("Calibri");

            // Bo'lim nomi
            String title = sections.get(i);
            if (title.length() > 55) title = title.substring(0, 52) + "...";
            XSLFTextBox textBox = slide.createTextBox();
            textBox.setAnchor(new Rectangle(bx + 52, by + 5, 255, 55));
            XSLFTextRun textRun = textBox.addNewTextParagraph().addNewTextRun();
            textRun.setText(title);
            textRun.setFontSize(16.0);
            textRun.setBold(true);
            textRun.setFontColor(style.titleColor);
            textRun.setFontFamily("Calibri");

            // Pastki ajratuvchi chiziq
            XSLFAutoShape line = slide.createAutoShape();
            line.setShapeType(ShapeType.LINE);
            line.setAnchor(new Rectangle(bx, by + 50, 310, 1));
            line.setLineColor(blend(style.titleColor, style.bgColor, 0.4f));
            line.setLineWidth(1.0);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  3. KONTENT SLAYD — MATN CHAP | RASM O'NG (har doim)
    // ─────────────────────────────────────────────────────────────────────────
    private void createContentSlide(XMLSlideShow ppt,
                                    GptTextGeneratorService.SlideData data,
                                    String topic,
                                    TemplateStyle style) throws IOException {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);

        // Chap dekorativ vertikal bar
        XSLFAutoShape leftBar = slide.createAutoShape();
        leftBar.setShapeType(ShapeType.RECT);
        leftBar.setAnchor(new Rectangle(0, 0, 8, SLIDE_H));
        leftBar.setFillColor(style.titleColor);
        leftBar.setLineColor(style.titleColor);

        // Sarlavha
        XSLFTextBox headBox = slide.createTextBox();
        headBox.setAnchor(new Rectangle(18, 10, 690, 58));
        XSLFTextRun hr = headBox.addNewTextParagraph().addNewTextRun();
        hr.setText(data.title);
        hr.setFontSize(26.0);
        hr.setBold(true);
        hr.setFontColor(style.titleColor);
        hr.setFontFamily("Calibri");

        // Sarlavha osti chiziq
        XSLFAutoShape titleLine = slide.createAutoShape();
        titleLine.setShapeType(ShapeType.LINE);
        titleLine.setAnchor(new Rectangle(18, 68, 690, 1));
        titleLine.setLineColor(style.titleColor);
        titleLine.setLineWidth(1.5);

        // MATN — CHAP TOMON
        if (data.bullets != null) {
            int bulletCount = Math.min(data.bullets.size(), 4);
            int spacing = bulletCount > 3 ? 105 : 118;
            int startY = 82;

            for (int i = 0; i < bulletCount; i++) {
                int itemY = startY + i * spacing;

                // Raqam doirasi marker
                XSLFAutoShape marker = slide.createAutoShape();
                marker.setShapeType(ShapeType.ELLIPSE);
                marker.setAnchor(new Rectangle(20, itemY + 1, 22, 22));
                marker.setFillColor(style.titleColor);
                marker.setLineColor(style.titleColor);

                XSLFTextBox markerNum = slide.createTextBox();
                markerNum.setAnchor(new Rectangle(25, itemY + 2, 14, 18));
                XSLFTextRun mn = markerNum.addNewTextParagraph().addNewTextRun();
                mn.setText(String.valueOf(i + 1));
                mn.setFontSize(10.0);
                mn.setBold(true);
                mn.setFontColor(style.bgColor);

                // Matn
                String bullet = data.bullets.get(i);
                if (bullet.length() > 130) bullet = bullet.substring(0, 127) + "...";

                XSLFTextBox bulletBox = slide.createTextBox();
                bulletBox.setAnchor(new Rectangle(48, itemY, 295, spacing - 4));
                XSLFTextRun br = bulletBox.addNewTextParagraph().addNewTextRun();
                br.setText(bullet);
                br.setFontSize(14.0);
                br.setFontColor(style.textColor);
                br.setFontFamily("Calibri");
            }
        }

        // RASM — O'NG TOMON (har doim)
        addWebImageToSlide(ppt, slide, data.imageQuery, topic,
                new Rectangle(358, 78, 354, 450), style);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  4. XULOSA SLAYD
    // ─────────────────────────────────────────────────────────────────────────
    private void createConclusionSlide(XMLSlideShow ppt, String topic,
                                       String userName, TemplateStyle style) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);
        addDiagonalDecoration(slide, style);

        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(new Rectangle(50, 160, 620, 90));
        XSLFTextRun tr = box.addNewTextParagraph().addNewTextRun();
        tr.setText("E'tiboringiz uchun rahmat!");
        tr.setFontSize(38.0);
        tr.setBold(true);
        tr.setFontColor(style.titleColor);
        tr.setFontFamily("Calibri");

        XSLFTextBox author = slide.createTextBox();
        author.setAnchor(new Rectangle(50, 390, 450, 40));
        XSLFTextRun ar = author.addNewTextParagraph().addNewTextRun();
        ar.setText(userName);
        ar.setFontSize(20.0);
        ar.setFontColor(style.textColor);
        ar.setFontFamily("Calibri");
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Rasm qo'shish (URL deprecated fix + fallback)
    // ─────────────────────────────────────────────────────────────────────────
    private void addWebImageToSlide(XMLSlideShow ppt, XSLFSlide slide,
                                    String query, String topic,
                                    Rectangle anchor, TemplateStyle style) {
        boolean added = false;
        try {
            String imageUrl = imageSearchService.findImageUrl(query, topic);
            if (imageUrl != null && !imageUrl.isBlank()) {
                // URI.create().toURL() — deprecated URL(String) o'rniga
                try (InputStream is = URI.create(imageUrl).toURL().openStream()) {
                    byte[] bytes = is.readAllBytes();
                    if (bytes.length > 1000) {
                        PictureData.PictureType pt =
                                imageUrl.toLowerCase().contains(".png")
                                        ? PictureData.PictureType.PNG
                                        : PictureData.PictureType.JPEG;
                        XSLFPictureData pd = ppt.addPicture(bytes, pt);
                        XSLFPictureShape pic = slide.createPicture(pd);
                        pic.setAnchor(anchor);
                        added = true;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Rasm yuklashda xatolik: " + e.getMessage());
        }

        if (!added) addFallbackShape(slide, anchor, style);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Fallback: matematik shakllar (rasm yuklanmasa)
    // ─────────────────────────────────────────────────────────────────────────
    private void addFallbackShape(XSLFSlide slide, Rectangle anchor, TemplateStyle style) {
        int x = anchor.x, y = anchor.y, w = anchor.width, h = anchor.height;
        int cx = x + w / 2, cy = y + h / 2;
        Color accent = style.titleColor;
        Color dim = blend(accent, style.bgColor, 0.4f);

        // Fon to'rtburchak
        XSLFAutoShape bg = slide.createAutoShape();
        bg.setShapeType(ShapeType.RECT);
        bg.setAnchor(anchor);
        bg.setFillColor(blend(style.bgColor, accent, 0.08f));
        bg.setLineColor(dim);
        bg.setLineWidth(1.0);

        // Tashqi geksagon
        int r1 = Math.min(w, h) / 3;
        XSLFAutoShape hex = slide.createAutoShape();
        hex.setShapeType(ShapeType.HEXAGON);
        hex.setAnchor(new Rectangle(cx - r1, cy - r1, r1 * 2, r1 * 2));
        hex.setFillColor(blend(style.bgColor, accent, 0.15f));
        hex.setLineColor(accent);
        hex.setLineWidth(2.5);

        // Ichki aylana
        int r2 = r1 / 2;
        XSLFAutoShape inner = slide.createAutoShape();
        inner.setShapeType(ShapeType.ELLIPSE);
        inner.setAnchor(new Rectangle(cx - r2, cy - r2, r2 * 2, r2 * 2));
        inner.setFillColor(accent);
        inner.setLineColor(accent);

        // To'rtta burchak kvadrat
        int sq = 16;
        int[][] corners = {
                {x + 10, y + 10},
                {x + w - 10 - sq, y + 10},
                {x + 10, y + h - 10 - sq},
                {x + w - 10 - sq, y + h - 10 - sq}
        };
        for (int[] c : corners) {
            XSLFAutoShape sqShape = slide.createAutoShape();
            sqShape.setShapeType(ShapeType.RECT);
            sqShape.setAnchor(new Rectangle(c[0], c[1], sq, sq));
            sqShape.setFillColor(dim);
            sqShape.setLineColor(accent);
            sqShape.setLineWidth(1.0);
        }

        // Pastki uchburchak
        XSLFAutoShape tri = slide.createAutoShape();
        tri.setShapeType(ShapeType.TRIANGLE);
        tri.setAnchor(new Rectangle(x + w - 55, y + h - 55, 45, 45));
        tri.setFillColor(blend(accent, style.bgColor, 0.3f));
        tri.setLineColor(accent);
        tri.setLineWidth(1.0);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Dekorativ diagonal (sarlavha/reja/xulosa slaydlari)
    // ─────────────────────────────────────────────────────────────────────────
    private void addDiagonalDecoration(XSLFSlide slide, TemplateStyle style) {
        Color dark = blend(style.bgColor, Color.BLACK, 0.18f);

        XSLFAutoShape tri1 = slide.createAutoShape();
        tri1.setShapeType(ShapeType.TRIANGLE);
        tri1.setAnchor(new Rectangle(380, 370, 340, 170));
        tri1.setFillColor(dark);
        tri1.setLineColor(dark);

        XSLFAutoShape tri2 = slide.createAutoShape();
        tri2.setShapeType(ShapeType.TRIANGLE);
        tri2.setAnchor(new Rectangle(560, 430, 180, 120));
        tri2.setFillColor(blend(dark, style.titleColor, 0.2f));
        tri2.setLineColor(blend(dark, style.titleColor, 0.2f));

        XSLFAutoShape circ = slide.createAutoShape();
        circ.setShapeType(ShapeType.ELLIPSE);
        circ.setAnchor(new Rectangle(25, 435, 75, 75));
        circ.setFillColor(dark);
        circ.setLineColor(dark);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Rang aralash yordamchi
    // ─────────────────────────────────────────────────────────────────────────
    private Color blend(Color a, Color b, float ratio) {
        float r = Math.min(1f, Math.max(0f, ratio));
        return new Color(
                (int) (a.getRed() * (1 - r) + b.getRed() * r),
                (int) (a.getGreen() * (1 - r) + b.getGreen() * r),
                (int) (a.getBlue() * (1 - r) + b.getBlue() * r)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  Template parseri
    // ─────────────────────────────────────────────────────────────────────────
    private int parseTemplate(String t) {
        try {
            return Integer.parseInt(t != null ? t.trim() : "1");
        } catch (Exception e) {
            return 1;
        }
    }


    private TemplateStyle getTemplateStyle(int template) {
        return switch (template) {
            case 1 ->
                    new TemplateStyle(new Color(110, 83, 49), new Color(245, 240, 230), new Color(50, 50, 50), 410, 100, 290, 260);   // Brown History
            case 2 ->
                    new TemplateStyle(new Color(181, 156, 115), new Color(40, 35, 30), new Color(230, 220, 200), 410, 100, 290, 260); // Dark Brown
            case 3 ->
                    new TemplateStyle(new Color(160, 120, 80), new Color(255, 248, 240), new Color(60, 50, 40), 410, 100, 290, 260);   // Flowers / Warm
            case 4 ->
                    new TemplateStyle(new Color(46, 99, 58), new Color(235, 245, 235), new Color(30, 50, 30), 410, 100, 290, 260);     // Green World
            case 5 ->
                    new TemplateStyle(new Color(74, 60, 117), new Color(245, 240, 255), new Color(50, 40, 70), 410, 100, 290, 260);     // Purple / Biome
            case 6 ->
                    new TemplateStyle(new Color(34, 91, 145), new Color(230, 240, 250), new Color(30, 50, 80), 410, 100, 290, 260);     // Blue Green
            case 7 ->
                    new TemplateStyle(new Color(14, 48, 102), new Color(240, 244, 250), new Color(30, 40, 70), 410, 100, 290, 260);     // Dark Blue
            case 8 ->
                    new TemplateStyle(new Color(197, 211, 224), new Color(28, 32, 40), new Color(200, 210, 220), 410, 100, 290, 260);   // White Blue (dark bg)
            case 9 ->
                    new TemplateStyle(new Color(28, 66, 33), new Color(240, 248, 240), new Color(40, 50, 40), 410, 100, 290, 260);       // Flowers 1
            case 10 ->
                    new TemplateStyle(new Color(180, 195, 210), new Color(18, 22, 35), new Color(190, 200, 220), 410, 100, 290, 260);  // Snow / Dark
            default ->
                    new TemplateStyle(new Color(70, 130, 180), new Color(250, 250, 250), new Color(40, 40, 40), 410, 100, 290, 260);
        };
    }


    private static class TemplateStyle {
        final Color titleColor;
        final Color bgColor;
        final Color textColor;
        final int imageX, imageY, imageW, imageH;

        TemplateStyle(Color title, Color bg, Color text, int x, int y, int w, int h) {
            this.titleColor = title;
            this.bgColor = bg;
            this.textColor = text;
            this.imageX = x;
            this.imageY = y;
            this.imageW = w;
            this.imageH = h;
        }
    }
}

