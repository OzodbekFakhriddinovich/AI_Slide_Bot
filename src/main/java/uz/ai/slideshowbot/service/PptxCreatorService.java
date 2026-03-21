package uz.ai.slideshowbot.service;

import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;

@Service
public class PptxCreatorService {

    public String createPptx(String topic, String userName, String subjectName, List<GptTextGeneratorService.SlideData> slides, String template) throws IOException {
        File dir = new File("slides");
        if (!dir.exists()) dir.mkdirs();

        XMLSlideShow ppt = new XMLSlideShow();
        Random rand = new Random();

        Color bgColor = switch (template != null ? template : "") {
            case "Science" -> new Color(200, 230, 255);
            case "Art" -> new Color(255, 240, 200);
            default -> Color.WHITE;
        };

        if (!slides.isEmpty()) {
            XSLFSlide firstSlide = ppt.createSlide();
            firstSlide.getBackground().setFillColor(bgColor);

            XSLFTextBox headerBox = firstSlide.createTextBox();
            headerBox.setAnchor(new java.awt.Rectangle(50, 50, 600, 100));
            XSLFTextParagraph headerPara = headerBox.addNewTextParagraph();
            XSLFTextRun headerRun = headerPara.addNewTextRun();
            headerRun.setText(topic + "\n" + userName + "\n" + subjectName);
            headerRun.setFontSize(28.0);
            headerRun.setBold(true);

            byte[] imgBytes = getImageBytes();
            if (imgBytes != null) {
                XSLFPictureData pictureData = ppt.addPicture(imgBytes, PictureData.PictureType.PNG);
                XSLFPictureShape pic = firstSlide.createPicture(pictureData);
                pic.setAnchor(new java.awt.Rectangle(100, 200, 500, 300));
            }
        }

        XSLFSlide planSlide = ppt.createSlide();
        planSlide.getBackground().setFillColor(bgColor);
        XSLFTextBox planBox = planSlide.createTextBox();
        planBox.setAnchor(new java.awt.Rectangle(50, 50, 600, 400));

        XSLFTextParagraph planTitle = planBox.addNewTextParagraph();
        XSLFTextRun planTitleRun = planTitle.addNewTextRun();
        planTitleRun.setText("Reja:");
        planTitleRun.setFontSize(24.0);
        planTitleRun.setBold(true);

        if (!slides.isEmpty() && slides.get(0).bullets != null) {
            for (String bullet : slides.get(0).bullets) {
                if (bullet != null && !bullet.isBlank()) {
                    XSLFTextParagraph p = planBox.addNewTextParagraph();
                    p.setBullet(true);
                    XSLFTextRun r = p.addNewTextRun();
                    r.setText(bullet);
                    r.setFontSize(20.0);
                }
            }
        }

        for (int i = 1; i < slides.size(); i++) {
            GptTextGeneratorService.SlideData slideData = slides.get(i);
            if (slideData == null) continue;

            XSLFSlide slide = ppt.createSlide();
            slide.getBackground().setFillColor(bgColor);

            byte[] imgBytes = getImageBytes();
            int imgWidth = 300 + rand.nextInt(200);
            int imgHeight = 200 + rand.nextInt(100);
            int[] possibleX = {50, 250, 450};
            int imgX = possibleX[rand.nextInt(possibleX.length)];
            int imgY = 100;

            if (imgBytes != null) {
                XSLFPictureData pictureData = ppt.addPicture(imgBytes, PictureData.PictureType.PNG);
                XSLFPictureShape pic = slide.createPicture(pictureData);
                pic.setAnchor(new java.awt.Rectangle(imgX, imgY, imgWidth, imgHeight));
            }

            int textX = (imgX < 250) ? 300 : 50;
            int textWidth = 600 - imgWidth - 50;
            XSLFTextBox textBox = slide.createTextBox();
            textBox.setAnchor(new java.awt.Rectangle(textX, 100, textWidth, 400));

            XSLFTextParagraph titlePara = textBox.addNewTextParagraph();
            XSLFTextRun titleRun = titlePara.addNewTextRun();
            titleRun.setText(slideData.title != null ? slideData.title : "Slide Title");
            titleRun.setFontSize(22.0);
            titleRun.setBold(true);

            if (slideData.bullets != null) {
                for (String bullet : slideData.bullets) {
                    if (bullet == null || bullet.isBlank()) continue;
                    XSLFTextParagraph p = textBox.addNewTextParagraph();
                    p.setBullet(true);
                    XSLFTextRun run = p.addNewTextRun();
                    run.setText(bullet);
                    run.setFontSize(18.0);
                }
            }
        }

        String safeTopic = (topic == null || topic.isBlank()) ? "slides" : topic.replaceAll("\\s+", "_");
        String filePath = "slides/" + safeTopic + "_" + System.currentTimeMillis() + ".pptx";

        try (FileOutputStream out = new FileOutputStream(filePath)) {
            ppt.write(out);
        }
        ppt.close();
        return filePath;
    }

    private byte[] getImageBytes() {
        try {
            String picsumUrl = "https://picsum.photos/600/400";
            try (InputStream in = new URL(picsumUrl).openStream()) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            try {
                File fallback = new File("src/main/resources/static/no_image.png");
                if (fallback.exists()) {
                    return Files.readAllBytes(fallback.toPath());
                }
            } catch (IOException ignored) {}
        }
        return null;
    }
}
