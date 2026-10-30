package uz.ai.slideshowbot.service;

import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationText;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.stereotype.Service;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.stream.Collectors;

/** Key-free PDF export using the same composed slides, without an external converter. */
@Service
public class SlideExportService {
    public String toPdf(String pptxPath) throws IOException {
        Path input = Path.of(pptxPath);
        Path output = input.resolveSibling(input.getFileName().toString().replaceFirst("(?i)\\.pptx$", "") + ".pdf");
        try (InputStream in = Files.newInputStream(input);
             XMLSlideShow ppt = new XMLSlideShow(in);
             PDDocument pdf = new PDDocument()) {
            Dimension size = ppt.getPageSize();
            pdf.getDocumentInformation().setTitle(ppt.getProperties().getCoreProperties().getTitle());
            pdf.getDocumentInformation().setCreator("SlideBot");
            for (XSLFSlide slide : ppt.getSlides()) {
                // 144 dpi preserves type and photographs while keeping Telegram files reasonably sized.
                BufferedImage image = new BufferedImage(size.width * 2, size.height * 2, BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try {
                    graphics.setColor(Color.WHITE);
                    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.scale(2, 2);
                    slide.draw(graphics);
                } finally { graphics.dispose(); }
                PDPage page = new PDPage(new PDRectangle(size.width, size.height));
                pdf.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(pdf, page)) {
                    content.drawImage(JPEGFactory.createFromImage(pdf, image, 0.94f, 144), 0, 0, size.width, size.height);
                } finally { image.flush(); }
                if (slide.getNotes() != null) {
                    String sources = slide.getNotes().getShapes().stream()
                            .filter(XSLFTextShape.class::isInstance).map(XSLFTextShape.class::cast)
                            .map(XSLFTextShape::getText).filter(text -> !text.isBlank()).collect(Collectors.joining("\n"));
                    if (!sources.isBlank()) {
                        PDAnnotationText note = new PDAnnotationText();
                        note.setContents(sources);
                        note.setTitlePopup("Sources / Manbalar");
                        note.setRectangle(new PDRectangle(size.width - 22, 6, 14, 14));
                        page.getAnnotations().add(note);
                    }
                }
            }
            pdf.save(output.toFile());
            return output.toString();
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(output);
            throw e;
        }
    }
}
