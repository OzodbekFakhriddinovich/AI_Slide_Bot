package uz.ai.slideshowbot.service;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.List;

@Service
public class PptxCreatorService {

    @Autowired
    private ImageSearchService imageSearchService;

    public String createPptx(String topic, String userName, String subjectName,
                             List<GptTextGeneratorService.SlideData> slides, String templateStr) throws IOException {

        File dir = new File("slides");
        if (!dir.exists()) dir.mkdirs();

        XMLSlideShow ppt = new XMLSlideShow();

        int template = parseTemplate(templateStr);
        TemplateStyle style = getTemplateStyle(template);

        // 1. Title slide
        createTitleSlide(ppt, topic, userName, subjectName, style);

        // 2. Reja (Plan) slide
        if (slides != null && !slides.isEmpty()) {
            createPlanSlide(ppt, slides, style);
        }

        // 3. Asosiy slaydlar
        for (GptTextGeneratorService.SlideData data : slides) {
            createContentSlide(ppt, data, topic, style);
        }

        String fileName = "slides/" + topic.replaceAll("[^a-zA-Z0-9\\s]", "_") + "_" + System.currentTimeMillis() + ".pptx";

        try (FileOutputStream out = new FileOutputStream(fileName)) {
            ppt.write(out);
        }
        ppt.close();

        return fileName;
    }

    private int parseTemplate(String t) {
        try {
            return Integer.parseInt(t != null ? t : "1");
        } catch (Exception e) {
            return 1;
        }
    }

    private TemplateStyle getTemplateStyle(int template) {
        return switch (template) {
            case 1 -> new TemplateStyle(new Color(139, 69, 19), new Color(245, 235, 220), new Color(60, 40, 20), 380, 90, 320, 240); // Brown History 3
            case 2 -> new TemplateStyle(new Color(139, 69, 19), new Color(240, 230, 210), new Color(50, 30, 10), 410, 100, 290, 260); // Brown History 2
            case 3 -> new TemplateStyle(new Color(199, 21, 133), new Color(255, 240, 245), new Color(80, 20, 50), 50, 110, 310, 270); // Flowers 3
            case 4 -> new TemplateStyle(new Color(34, 139, 34), new Color(230, 245, 230), new Color(20, 80, 20), 50, 100, 300, 280); // Green World
            case 5 -> new TemplateStyle(new Color(75, 0, 130), new Color(45, 45, 70), Color.WHITE, 410, 80, 290, 270); // Biome
            case 6 -> new TemplateStyle(new Color(0, 128, 128), new Color(240, 248, 255), new Color(30, 60, 100), 410, 100, 290, 260); // Green Abstract
            case 7 -> new TemplateStyle(new Color(139, 69, 19), new Color(30, 30, 40), Color.WHITE, 50, 120, 300, 260); // Brown History dark
            case 8 -> new TemplateStyle(new Color(70, 130, 180), new Color(248, 248, 255), new Color(40, 60, 90), 410, 90, 290, 260); // White Blue
            case 9 -> new TemplateStyle(new Color(199, 21, 133), new Color(255, 240, 245), new Color(80, 20, 50), 50, 110, 310, 270); // Flowers 1
            case 10 -> new TemplateStyle(new Color(25, 25, 112), new Color(240, 248, 255), new Color(40, 40, 80), 410, 80, 290, 270); // Snow
            default -> new TemplateStyle(new Color(44, 62, 80), Color.WHITE, Color.BLACK, 410, 100, 290, 260);
        };
    }

    private void createTitleSlide(XMLSlideShow ppt, String topic, String userName, String subjectName, TemplateStyle style) throws IOException {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);

        // Sarlavha
        XSLFTextBox titleBox = slide.createTextBox();
        titleBox.setAnchor(new Rectangle(50, 80, 620, 120));
        XSLFTextRun titleRun = titleBox.addNewTextParagraph().addNewTextRun();
        titleRun.setText(topic.toUpperCase());
        titleRun.setFontSize(36.0);
        titleRun.setBold(true);
        titleRun.setFontColor(style.titleColor);

        // Tayyorladi
        XSLFTextBox infoBox = slide.createTextBox();
        infoBox.setAnchor(new Rectangle(50, 420, 620, 50));
        XSLFTextRun infoRun = infoBox.addNewTextParagraph().addNewTextRun();
        infoRun.setText("Tayyorladi: " + userName + (subjectName.isEmpty() ? "" : " | " + subjectName));
        infoRun.setFontSize(16.0);
        infoRun.setItalic(true);
        infoRun.setFontColor(style.textColor);

        addWebImageToSlide(ppt, slide, topic, new Rectangle(150, 180, 420, 220));
    }

    private void createPlanSlide(XMLSlideShow ppt, List<GptTextGeneratorService.SlideData> slides, TemplateStyle style) {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);

        XSLFTextBox title = slide.createTextBox();
        title.setAnchor(new Rectangle(50, 30, 600, 60));
        XSLFTextRun tr = title.addNewTextParagraph().addNewTextRun();
        tr.setText("TAQDIMOT REJASI");
        tr.setFontSize(28.0);
        tr.setBold(true);
        tr.setFontColor(style.titleColor);

        XSLFTextBox content = slide.createTextBox();
        content.setAnchor(new Rectangle(70, 100, 580, 380));
        for (GptTextGeneratorService.SlideData s : slides) {
            XSLFTextParagraph p = content.addNewTextParagraph();
            p.setBullet(true);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(s.title);
            r.setFontSize(19.0);
            r.setFontColor(style.textColor);
        }
    }

    private void createContentSlide(XMLSlideShow ppt, GptTextGeneratorService.SlideData data, String topic, TemplateStyle style) throws IOException {
        XSLFSlide slide = ppt.createSlide();
        slide.getBackground().setFillColor(style.bgColor);

        // Sarlavha
        XSLFTextBox head = slide.createTextBox();
        head.setAnchor(new Rectangle(50, 30, 620, 70));
        XSLFTextRun hr = head.addNewTextParagraph().addNewTextRun();
        hr.setText(data.title);
        hr.setFontSize(26.0);
        hr.setBold(true);
        hr.setFontColor(style.titleColor);

        // Matn
        XSLFTextBox body = slide.createTextBox();
        body.setAnchor(new Rectangle(50, 110, 360, 340));
        if (data.bullets != null) {
            for (String bullet : data.bullets) {
                XSLFTextParagraph p = body.addNewTextParagraph();
                p.setBullet(true);
                XSLFTextRun br = p.addNewTextRun();
                br.setText(bullet);
                br.setFontSize(17.5);
                br.setFontColor(style.textColor);
            }
        }

        // Rasm
        String query = (data.imageQuery != null && !data.imageQuery.isEmpty()) ? data.imageQuery : topic + " " + data.title;
        Rectangle rect = new Rectangle(style.imageX, style.imageY, style.imageW, style.imageH);
        addWebImageToSlide(ppt, slide, query, rect);
    }

    private void addWebImageToSlide(XMLSlideShow ppt, XSLFSlide slide, String query, Rectangle anchor) {
        try {
            String imageUrl = imageSearchService.findImageUrl(query);
            try (InputStream is = new URL(imageUrl).openStream()) {
                byte[] bytes = is.readAllBytes();
                XSLFPictureData pd = ppt.addPicture(bytes, PictureData.PictureType.PNG);
                XSLFPictureShape pic = slide.createPicture(pd);
                pic.setAnchor(anchor);
            }
        } catch (Exception e) {
            System.err.println("Rasm yuklash xatosi (" + query + "): " + e.getMessage());
        }
    }

    // ==================== YORDAMCHI KLASS ====================
    private static class TemplateStyle {
        Color titleColor, bgColor, textColor;
        int imageX, imageY, imageW, imageH;

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