package uz.ai.slideshowbot.service;
 
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ai.slideshowbot.repository.FileRepository;
 
import java.time.LocalDateTime;
 
@Service
public class FileCleanupService {
 
    @Autowired
    private FileRepository fileRepository;
 
    // Har 10 daqiqada ishga tushadi
    // PDF rasmlar    → 30 daqiqadan keyin o'chirish
    // ZIP fayllar    → 30 daqiqadan keyin o'chirish
    // Word/PDF conv  → 30 daqiqadan keyin o'chirish
    // Test fayllar   → 60 daqiqadan keyin o'chirish
    // Taqdimot       → 60 daqiqadan keyin o'chirish
    // Chek fayllar   → 24 soatdan keyin o'chirish
 
    @Scheduled(fixedDelay = 10 * 60 * 1000)
    @Transactional
    public void cleanOldFiles() {
        try {
            LocalDateTime now = LocalDateTime.now();
 
            // PDF uchun yuborilgan rasmlar — 30 daqiqa
            int pdfImages = fileRepository.deleteByFileTypeAndCreatedAtBefore("PDF_IMAGE", now.minusMinutes(30));
 
            // ZIP fayllar — 30 daqiqa
            int zipFiles = fileRepository.deleteByFileTypeAndCreatedAtBefore("ZIP", now.minusMinutes(30));
 
            // Word→PDF yoki PDF→Word kirish/chiqish fayllar — 30 daqiqa
            int wordPdfIn  = fileRepository.deleteByFileTypeAndCreatedAtBefore("WORD_PDF_DOCX", now.minusMinutes(30));
            int wordPdfOut = fileRepository.deleteByFileTypeAndCreatedAtBefore("WORD_PDF_PDF", now.minusMinutes(30));
            int converted  = fileRepository.deleteByFileTypeAndCreatedAtBefore("WORD_PDF_RESULT", now.minusMinutes(30));
 
            // Test word fayllar — 60 daqiqa
            int testFiles = fileRepository.deleteByFileTypeAndCreatedAtBefore("TEST", now.minusMinutes(60));
 
            // Tolov cheklari — 24 soat
            int checkFiles = fileRepository.deleteByFileTypeAndCreatedAtBefore("CHECK", now.minusHours(24));
 
            int total = pdfImages + zipFiles + wordPdfIn + wordPdfOut + converted + testFiles + checkFiles;
            if (total > 0) {
                System.out.println("🗑️ DB tozalandi: " + total + " fayl o'chirildi "
                        + "(PDF_IMAGE=" + pdfImages
                        + ", ZIP=" + zipFiles
                        + ", WORD_PDF=" + (wordPdfIn + wordPdfOut + converted)
                        + ", TEST=" + testFiles
                        + ", CHECK=" + checkFiles + ")");
            }
        } catch (Exception e) {
            System.err.println("FileCleanupService xato: " + e.getMessage());
        }
    }
}