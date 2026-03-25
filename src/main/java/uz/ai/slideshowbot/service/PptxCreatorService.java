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
                             List<GptTextGeneratorService.SlideData> slides, String template) throws IOException {

        File dir = new File("slides");
        if (!dir.exists()) dir.mkdirs();

        XMLSlideShow ppt = new XMLSlideShow();

        Color bgColor = switch (template != null ? template : "1") {
            case "2" -> new Color(240, 248, 255);
            case "3" -> new Color(255, 250, 240);
            default -> Color.WHITE;
        };

        XSLFSlide titleSlide = ppt.createSlide();
        titleSlide.getBackground().setFillColor(bgColor);

        XSLFTextBox titleBox = titleSlide.createTextBox();
        titleBox.setAnchor(new Rectangle(50, 50, 620, 100));
        XSLFTextParagraph p1 = titleBox.addNewTextParagraph();
        p1.setTextAlign(TextParagraph.TextAlign.CENTER);     // ✅ TO'G'RI ishlatish
        XSLFTextRun r1 = p1.addNewTextRun();
        r1.setText(topic.toUpperCase());
        r1.setFontSize(32.0);
        r1.setBold(true);
        r1.setFontColor(new Color(44, 62, 80));

        XSLFTextBox infoBox = titleSlide.createTextBox();
        infoBox.setAnchor(new Rectangle(50, 400, 620, 50));
        XSLFTextParagraph p2 = infoBox.addNewTextParagraph();
        p2.setTextAlign(TextParagraph.TextAlign.RIGHT);      // ✅ TO'G'RI ishlatish
        XSLFTextRun r2 = p2.addNewTextRun();
        r2.setText("Tayyorladi: " + userName + (subjectName.isEmpty() ? "" : " | " + subjectName));
        r2.setFontSize(18.0);
        r2.setItalic(true);

        addWebImageToSlide(ppt, titleSlide, topic, new Rectangle(160, 150, 400, 230));

        if (slides != null && !slides.isEmpty()) {
            XSLFSlide planSlide = ppt.createSlide();
            planSlide.getBackground().setFillColor(bgColor);

            XSLFTextBox planTitle = planSlide.createTextBox();
            planTitle.setAnchor(new Rectangle(50, 20, 600, 50));
            XSLFTextRun ptr = planTitle.addNewTextParagraph().addNewTextRun();
            ptr.setText("TAQDIMOT REJASI:");
            ptr.setFontSize(24.0);
            ptr.setBold(true);

            XSLFTextBox planContent = planSlide.createTextBox();
            planContent.setAnchor(new Rectangle(70, 80, 580, 350));
            for (int i = 0; i < slides.size(); i++) {
                XSLFTextParagraph p = planContent.addNewTextParagraph();
                p.setBullet(true);
                XSLFTextRun r = p.addNewTextRun();
                r.setText(slides.get(i).title);
                r.setFontSize(20.0);
            }
        }

        for (GptTextGeneratorService.SlideData data : slides) {
            XSLFSlide slide = ppt.createSlide();
            slide.getBackground().setFillColor(bgColor);

            XSLFTextBox head = slide.createTextBox();
            head.setAnchor(new Rectangle(50, 20, 620, 50));
            XSLFTextRun hr = head.addNewTextParagraph().addNewTextRun();
            hr.setText(data.title);
            hr.setFontSize(26.0);
            hr.setBold(true);
            hr.setFontColor(new Color(22, 160, 133));

            XSLFTextBox body = slide.createTextBox();
            body.setAnchor(new Rectangle(50, 80, 340, 350));
            if (data.bullets != null) {
                for (String b : data.bullets) {
                    XSLFTextParagraph bp = body.addNewTextParagraph();
                    bp.setBullet(true);
                    XSLFTextRun br = bp.addNewTextRun();
                    br.setText(b);
                    br.setFontSize(18.0);
                }
            }

            String query = (data.imageQuery != null && !data.imageQuery.isEmpty()) ? data.imageQuery : topic + " " + data.title;
            addWebImageToSlide(ppt, slide, query, new Rectangle(410, 100, 290, 260));
        }

        String fileName = "slides/" + topic.replaceAll("[^a-zA-Z0-9]", "_") + "_" + System.currentTimeMillis() + ".pptx";
        try (FileOutputStream out = new FileOutputStream(fileName)) {
            ppt.write(out);
        }
        ppt.close();

        return fileName;
    }

    private void addWebImageToSlide(XMLSlideShow ppt, XSLFSlide slide, String query, Rectangle anchor) {
        try {
            String imageUrl = imageSearchService.findImageUrl(query);
            if (imageUrl != null && !imageUrl.isEmpty()) {
                try (InputStream is = new URL(imageUrl).openStream()) {
                    byte[] imageBytes = is.readAllBytes();
                    XSLFPictureData pd = ppt.addPicture(imageBytes, PictureData.PictureType.PNG);
                    XSLFPictureShape pic = slide.createPicture(pd);
                    pic.setAnchor(anchor);
                }
            }
        } catch (Exception e) {
            System.err.println("Rasm yuklashda xatolik (" + query + "): " + e.getMessage());
        }
    }
}