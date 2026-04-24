package uz.ai.slideshowbot.service;

import okhttp3.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.FileEntity;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.FileRepository;
import uz.ai.slideshowbot.repository.UserRepository;

import com.aspose.words.Document;
import com.aspose.words.SaveFormat;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

@Service
public class WordPdfService {

    @Autowired private UserRepository userRepository;
    @Autowired private FileRepository fileRepository;
    @Autowired private TelegramRawSender rawSender;
    @Autowired private MenuService menuService;

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();

    public void handleMenu(SlideBot bot, UserEntity user, Long chatId) {
        resetFlow(user);
        user.setInWordPdfFlow(true);
        userRepository.save(user);

        String lang = lang(user);
        String text = switch (lang) {
            case "RU"  -> "📄 Отправьте Word (.docx) или PDF файл — я конвертирую его.\n\n✅ Принимаю: .docx, .pdf\n❌ Другие файлы не принимаются.";
            case "ENG" -> "📄 Send me a Word (.docx) or PDF file — I will convert it.\n\n✅ Accepted: .docx, .pdf\n❌ Other files are not accepted.";
            default    -> "📄 Word (.docx) yoki PDF fayl yuboring — men uni aylantiraman.\n\n✅ Qabul qilaman: .docx, .pdf\n❌ Boshqa fayl turlari qabul qilinmaydi.";
        };
        rawSender.sendColoredSingleMenu(bot, chatId.toString(), text, backBtnText(lang), "danger");
    }

    public boolean handleFileInput(SlideBot bot, UserEntity user, org.telegram.telegrambots.meta.api.objects.Message message) {
        if (!Boolean.TRUE.equals(user.getInWordPdfFlow())) return false;

        Long chatId = message.getChatId();
        String lang = lang(user);

        if (!message.hasDocument()) {
            rawSender.sendColoredSingleMenu(bot, chatId.toString(), switch (lang) {
                case "RU"  -> "❌ Я принимаю только .docx и .pdf файлы! Отправьте документ.";
                case "ENG" -> "❌ I only accept .docx and .pdf files! Send a document.";
                default    -> "❌ Faqat .docx va .pdf fayllarni qabul qilaman! Hujjat yuboring.";
            }, backBtnText(lang), "danger");
            return true;
        }

        String fileName = message.getDocument().getFileName();
        if (fileName == null) fileName = "";
        String lowerName = fileName.toLowerCase();

        boolean isDocx = lowerName.endsWith(".docx");
        boolean isPdf  = lowerName.endsWith(".pdf");

        if (!isDocx && !isPdf) {
            rawSender.sendColoredSingleMenu(bot, chatId.toString(), switch (lang) {
                case "RU"  -> "❌ Файл не поддерживается!\n\nОтправьте .docx (Word) или .pdf файл.";
                case "ENG" -> "❌ File type not supported!\n\nSend a .docx (Word) or .pdf file.";
                default    -> "❌ Fayl turi qabul qilinmaydi!\n\n.docx (Word) yoki .pdf fayl yuboring.";
            }, backBtnText(lang), "danger");
            return true;
        }

        try {
            String fileId = message.getDocument().getFileId();
            GetFile getFileMethod = new GetFile(fileId);
            org.telegram.telegrambots.meta.api.objects.File tgFile = bot.execute(getFileMethod);
            String fileUrl = tgFile.getFileUrl(bot.getBotToken());

            byte[] fileBytes;
            try (InputStream in = new URL(fileUrl).openStream()) {
                fileBytes = in.readAllBytes();
            }

            FileEntity fe = new FileEntity();
            fe.setChatId(chatId);
            fe.setFileName(fileName);
            fe.setFileType(isDocx ? "WORD_PDF_DOCX" : "WORD_PDF_PDF");
            fe.setData(fileBytes);
            FileEntity saved = fileRepository.save(fe);

            user.setWordPdfPendingFileId(saved.getId());
            userRepository.save(user);

            String confirmMsg = switch (lang) {
                case "RU"  -> isDocx
                        ? "✅ Word файл получен! Нажмите «Подтвердить» — конвертирую в PDF."
                        : "✅ PDF файл получен! Нажмите «Подтвердить» — конвертирую в Word.";
                case "ENG" -> isDocx
                        ? "✅ Word file received! Press \"Confirm\" — I will convert it to PDF."
                        : "✅ PDF file received! Press \"Confirm\" — I will convert it to Word.";
                default    -> isDocx
                        ? "✅ Word fayl qabul qilindi! «Tasdiqlash» ni bosing — PDF ga aylantiraman."
                        : "✅ PDF fayl qabul qilindi! «Tasdiqlash» ni bosing — Word ga aylantiraman.";
            };

            String confirmBtn = switch (lang) { case "RU" -> "✅ Подтвердить"; case "ENG" -> "✅ Confirm"; default -> "✅ Tasdiqlash"; };
            rawSender.sendColoredBinaryMenu(bot, chatId.toString(), confirmMsg,
                    confirmBtn, "success", backBtnText(lang), "danger");

        } catch (Exception e) {
            e.printStackTrace();
            rawSender.sendColoredSingleMenu(bot, chatId.toString(), switch (lang) {
                case "RU"  -> "❌ Ошибка при загрузке файла. Попробуйте снова.";
                case "ENG" -> "❌ Error loading file. Please try again.";
                default    -> "❌ Fayl yuklashda xatolik. Qayta urinib ko'ring.";
            }, backBtnText(lang), "danger");
        }
        return true;
    }

    public boolean handleConfirm(SlideBot bot, UserEntity user, Long chatId) {
        if (!Boolean.TRUE.equals(user.getInWordPdfFlow())) return false;
        if (user.getWordPdfPendingFileId() == null) return false;

        String lang = lang(user);
        FileEntity fe = fileRepository.findById(user.getWordPdfPendingFileId()).orElse(null);
        if (fe == null) {
            rawSender.sendColoredSingleMenu(bot, chatId.toString(), switch (lang) {
                case "RU" -> "❌ Файл не найден. Отправьте снова.";
                case "ENG" -> "❌ File not found. Send again.";
                default -> "❌ Fayl topilmadi. Qayta yuboring.";
            }, backBtnText(lang), "danger");
            return true;
        }

        resetFlow(user);

        Integer progressMsgId = rawSender.sendSimpleMessage(bot, chatId.toString(), switch (lang) {
            case "RU"  -> "⏳ Конвертирую файл...\n" + buildBar(0);
            case "ENG" -> "⏳ Converting file...\n" + buildBar(0);
            default    -> "⏳ Fayl aylantirilmoqda...\n" + buildBar(0);
        });

        FileEntity finalFe = fe;
        new Thread(() -> doConvert(bot, user, chatId, finalFe, progressMsgId, lang)).start();
        return true;
    }

    private void doConvert(SlideBot bot, UserEntity user, Long chatId, FileEntity fe, Integer progressMsgId, String lang) {
        try {
            String label = switch (lang) {
                case "RU" -> "Конвертирую...";
                case "ENG" -> "Converting...";
                default -> "Aylantirilmoqda...";
            };

            for (int i = 1; i <= 3; i++) {
                if (progressMsgId != null) rawSender.editMessageText(bot, chatId.toString(), progressMsgId, label + "\n" + buildBar(i * 33));
                Thread.sleep(400);
            }

            boolean isDocx = "WORD_PDF_DOCX".equals(fe.getFileType());
            String outputExt = isDocx ? ".pdf" : ".docx";
            byte[] resultBytes;

            try (InputStream inputStream = new ByteArrayInputStream(fe.getData());
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {

                if (isDocx) {
                    com.aspose.words.Document doc = new com.aspose.words.Document(inputStream);
                    doc.save(outputStream, com.aspose.words.SaveFormat.PDF);
                } else {
                    com.aspose.pdf.Document pdfDocument = new com.aspose.pdf.Document(inputStream);
                    pdfDocument.save(outputStream, com.aspose.pdf.SaveFormat.DocX);
                }
                resultBytes = outputStream.toByteArray();
            }

            if (progressMsgId != null) rawSender.deleteMessageById(bot, chatId.toString(), progressMsgId);

            String origName = fe.getFileName() != null ? fe.getFileName() : "file";
            String resultFileName = origName.replaceAll("(?i)\\.(docx|pdf)$", "") + outputExt;
            String mimeType = isDocx ? "application/pdf" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

            String caption = switch (lang) {
                case "RU" -> isDocx ? "✅ Word → PDF готово!" : "✅ PDF → Word готово!";
                case "ENG" -> isDocx ? "✅ Word → PDF is ready!" : "✅ PDF → Word is ready!";
                default -> isDocx ? "✅ Word → PDF tayyor!" : "✅ PDF → Word tayyor!";
            };

            String shareUrl = "https://t.me/share/url?url=" + URLEncoder.encode("https://t.me/AI_Slide_Assistant_Bot", StandardCharsets.UTF_8);
            String shareBtn = switch (lang) {
                case "RU" -> "📤 Поделиться";
                case "ENG" -> "📤 Share";
                default -> "📤 Ulashish";
            };

            sendConvertedFile(bot, chatId.toString(), resultBytes, resultFileName, caption, mimeType, shareBtn, shareUrl);

            try { fileRepository.deleteById(fe.getId()); } catch (Exception ignored) {}
            menuService.sendSimpleMenu(bot, chatId, lang);

        } catch (Exception e) {
            e.printStackTrace();
            if (progressMsgId != null) rawSender.deleteMessageById(bot, chatId.toString(), progressMsgId);
            try { fileRepository.deleteById(fe.getId()); } catch (Exception ignored) {}
            rawSender.sendSimpleMessage(bot, chatId.toString(), "❌ Xatolik: " + e.getMessage());
        }
    }

    private void sendConvertedFile(SlideBot bot, String chatId, byte[] bytes,
                                   String fileName, String caption,
                                   String mimeType, String btnText, String btnUrl) {
        try {
            String markup = """
                    {"inline_keyboard":[[{"text":"%s","url":"%s","style":"success"}]]}
                    """.formatted(rawSender.esc(btnText), btnUrl).strip();

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart("caption", caption)
                    .addFormDataPart("parse_mode", "HTML")
                    .addFormDataPart("reply_markup", markup)
                    .addFormDataPart("document", fileName,
                            RequestBody.create(bytes, MediaType.get(mimeType)))
                    .build();

            String url = "https://api.telegram.org/bot" + bot.getBotToken() + "/sendDocument";
            Request request = new Request.Builder().url(url).post(requestBody).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) System.err.println("sendConvertedFile xato: " + response.code());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String buildBar(int pct) {
        pct = Math.min(100, Math.max(0, pct));
        int filled = pct / 10;
        return "█".repeat(filled) + "░".repeat(10 - filled) + " " + pct + "%";
    }

    public void resetFlow(UserEntity user) {
        if (user == null) return;
        if (user.getWordPdfPendingFileId() != null) {
            try { fileRepository.deleteById(user.getWordPdfPendingFileId()); } catch (Exception ignored) {}
            user.setWordPdfPendingFileId(null);
        }
        user.setInWordPdfFlow(false);
        userRepository.save(user);
    }

    private String lang(UserEntity user) { return (user.getLanguage() != null) ? user.getLanguage() : "UZ"; }

    private String backBtnText(String lang) {
        return switch (lang) { case "RU" -> "◀️ Назад"; case "ENG" -> "◀️ Back"; default -> "◀️ Orqaga"; };
    }
}