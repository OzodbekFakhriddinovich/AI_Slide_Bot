package uz.ai.slideshowbot.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.awt.Dimension;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class SlideExportServiceTest {
    @TempDir Path directory;

    @Test void exportsTheSameNumberOfSlidesWithoutExternalPrograms() throws Exception {
        Path file = directory.resolve("sample.pptx");
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.setPageSize(new Dimension(960, 540));
            ppt.getProperties().getCoreProperties().setTitle("Network security");
            ppt.createSlide().createTextBox().setText("Network security");
            ppt.createSlide().createTextBox().setText("Different content on slide two");
            try (var out = Files.newOutputStream(file)) { ppt.write(out); }
        }
        Path pdf = Path.of(new SlideExportService().toPdf(file.toString()));
        assertTrue(Files.size(pdf) > 1000);
        try (PDDocument document = PDDocument.load(pdf.toFile())) {
            assertEquals(2, document.getNumberOfPages());
            assertEquals(960, document.getPage(0).getMediaBox().getWidth());
            assertEquals("Network security", document.getDocumentInformation().getTitle());
        }
    }
}
