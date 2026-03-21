package uz.ai.slideshowbot.service;

import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.UserRepository;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

@Service
public class PdfService {
    private final UserRepository userRepository;
    private final Map<Long, List<File>> tempUserImages = new HashMap<>();
    private final Map<Long, Boolean> waitingForConfirmation = new HashMap<>();
    private static final String SAVE_DIR = "C:/Users/User/Desktop/saqlash/saqlash";

    public PdfService(UserRepository userRepository) {
        this.userRepository = userRepository;
        File dir = new File(SAVE_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    public boolean handlePdfFlow(Update update, SlideBot bot, UserEntity user) throws TelegramApiException {
        if (update == null || user == null) return false;
        Message msg = update.hasMessage() ? update.getMessage() : null;
        if (msg == null) return false;
        Long chatId = msg.getChatId();

        if (msg.hasText()) {
            String text = msg.getText();
            if (text.equals("📄 PDF yaratish") || text.equals("📄 Создать PDF") || text.equals("📄 Create PDF")) {
                tempUserImages.remove(chatId);
                waitingForConfirmation.put(chatId, false);
                sendMessage(bot, chatId,
                        getText("SEND_IMAGES", user.getLanguage()),
                        pdfControlKeyboard(user.getLanguage()));
                return true;
            }
            if (text.equals("✅ Tasdiqlash") || text.equals("✅ Подтвердить") || text.equals("✅ Confirm")) {
                List<File> images = tempUserImages.get(chatId);
                if (images == null || images.isEmpty()) {
                    sendMessage(bot, chatId,
                            switch (user.getLanguage()) {
                                case "RU" -> "⚠️ Вы еще не отправили изображение!";
                                case "ENG" -> "⚠️ You haven't sent an image yet!";
                                default -> "⚠️ Siz hali rasm yubormadingiz!";
                            },
                            pdfControlKeyboard(user.getLanguage()));
                    return true;
                }
                File pdf = createPdf(images, chatId);
                tempUserImages.remove(chatId);
                waitingForConfirmation.remove(chatId);
                showProgressAndSendPdf(bot, chatId, pdf, user);
                return true;
            }
            if (text.equals("◀️ Orqaga") || text.equals("◀️ Назад") || text.equals("◀️ Back")) {
                clearTempFiles(chatId);
                waitingForConfirmation.remove(chatId);
                tempUserImages.remove(chatId);
                sendMessage(bot, chatId, getText("MAIN_MENU", user.getLanguage()), mainMenuKeyboard(user.getLanguage()));
                return true;
            }
        }

        if (msg.hasPhoto()) {
            waitingForConfirmation.put(chatId, true);
            try {
                String fileId = msg.getPhoto().get(msg.getPhoto().size() - 1).getFileId();
                org.telegram.telegrambots.meta.api.objects.File tgFile = bot.execute(new GetFile(fileId));
                File downloaded = bot.downloadFile(tgFile.getFilePath());
                File localCopy = new File(SAVE_DIR, "chat_" + chatId + "_" + System.currentTimeMillis() + ".jpg");
                Files.copy(downloaded.toPath(), localCopy.toPath());
                downloaded.delete();
                tempUserImages.computeIfAbsent(chatId, k -> new ArrayList<>()).add(localCopy);

                SendMessage replyMsg = new SendMessage(chatId.toString(), getText("IMAGE_SAVED", user.getLanguage()));
                replyMsg.setReplyToMessageId(msg.getMessageId());
                replyMsg.setReplyMarkup(pdfControlKeyboard(user.getLanguage()));
                bot.execute(replyMsg);

            } catch (Exception e) {
                e.printStackTrace();
                SendMessage errorMsg = new SendMessage(chatId.toString(), getText("ERROR", user.getLanguage()));
                errorMsg.setReplyToMessageId(msg.getMessageId());
                errorMsg.setReplyMarkup(pdfControlKeyboard(user.getLanguage()));
                bot.execute(errorMsg);
            }
            return true;
        }

        if (msg.hasDocument() || msg.hasAudio() || msg.hasVideo() || msg.hasVoice() || msg.hasSticker()) {
            String warnText = switch (user.getLanguage()) {
                case "RU" -> "❌ Только изображения принимаются для PDF!\nОтправьте фото, а не другие файлы!";
                case "ENG" -> "❌ Only images are accepted for PDF!\nPlease send a photo, not other files!";
                default -> "❌ Faqat rasm yuboring!\nBoshqa fayllar PDF uchun qabul qilinmaydi!";
            };

            SendMessage warnMsg = new SendMessage(chatId.toString(), warnText);
            warnMsg.setReplyToMessageId(msg.getMessageId());
            warnMsg.setReplyMarkup(pdfControlKeyboard(user.getLanguage()));
            try {
                bot.execute(warnMsg);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        }
        return false;
    }

    private File createPdf(List<File> images, Long chatId) {
        File output = new File(SAVE_DIR, "user_" + chatId + ".pdf");
        try (FileOutputStream fos = new FileOutputStream(output)) {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, fos);
            document.open();
            for (File imgFile : images) {
                Image img = Image.getInstance(imgFile.getAbsolutePath());
                img.scaleToFit(PageSize.A4.getWidth(), PageSize.A4.getHeight());
                img.setAlignment(Image.ALIGN_CENTER);
                document.add(img);
                document.newPage();
            }
            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return output;
    }

    private void showProgressAndSendPdf(SlideBot bot, Long chatId, File pdf, UserEntity user) {
        try {
            SendMessage emojiMsg = new SendMessage(chatId.toString(), "📝");
            bot.execute(emojiMsg);

            SendMessage progressMsg = new SendMessage(chatId.toString(),
                    getText("PLEASE_WAIT", user.getLanguage()) + "\n⬜⬜⬜⬜⬜⬜⬜⬜⬜⬜");
            Message sentProgress = bot.execute(progressMsg);

            int totalSteps = 4;
            for (int i = 1; i <= totalSteps; i++) {
                StringBuilder bar = new StringBuilder();
                for (int j = 0; j < totalSteps; j++) {
                    bar.append(j < i ? "🟩" : "⬜");
                }
                EditMessageText edit = new EditMessageText();
                edit.setChatId(chatId.toString());
                edit.setMessageId(sentProgress.getMessageId());
                edit.setText(getText("PLEASE_WAIT", user.getLanguage()) + "\n" + bar);
                bot.execute(edit);
                Thread.sleep(60);
            }

            bot.execute(new DeleteMessage(chatId.toString(), sentProgress.getMessageId()));

            String botLink = "https://t.me/AI_Slide_Assistant_Bot";
            String encodedUrl = URLEncoder.encode(botLink, StandardCharsets.UTF_8);
            String fullShareUrl = "https://t.me/share/url?url=" + encodedUrl;

            InlineKeyboardButton shareBtn = new InlineKeyboardButton();
            shareBtn.setText(
                    switch (user.getLanguage()) {
                        case "RU" -> "📤 Поделиться";
                        case "ENG" -> "📤 Share";
                        default -> "📤 Ulashish";
                    }
            );
            shareBtn.setUrl(fullShareUrl);
            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(List.of(List.of(shareBtn)));

            SendDocument sendDoc = new SendDocument();
            sendDoc.setChatId(chatId.toString());
            sendDoc.setDocument(new InputFile(pdf));
            sendDoc.setCaption(getText("PDF_READY", user.getLanguage()));
            sendDoc.setReplyMarkup(markup);
            bot.execute(sendDoc);

            clearTempFiles(chatId);
            if (pdf.exists()) pdf.delete();

            sendMessage(bot, chatId, getText("MAIN_MENU", user.getLanguage()), mainMenuKeyboard(user.getLanguage()));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendMessage(SlideBot bot, Long chatId, String text, ReplyKeyboard keyboard) {
        try {
            SendMessage msg = new SendMessage(chatId.toString(), text);
            if (keyboard != null) msg.setReplyMarkup(keyboard);
            bot.execute(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void clearTempFiles(Long chatId) {
        File folder = new File(SAVE_DIR);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.exists() && f.getName().contains("chat_" + chatId)) {
                    f.delete();
                }
            }
        }
    }

    private ReplyKeyboard pdfControlKeyboard(String lang) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow r1 = new KeyboardRow();
        KeyboardRow r2 = new KeyboardRow();
        switch (lang) {
            case "RU" -> {
                r1.add("✅ Подтвердить");
                r2.add("◀️ Назад");
            }
            case "ENG" -> {
                r1.add("✅ Confirm");
                r2.add("◀️ Back");
            }
            default -> {
                r1.add("✅ Tasdiqlash");
                r2.add("◀️ Orqaga");
            }
        }
        rows.add(r1);
        rows.add(r2);
        markup.setKeyboard(rows);
        markup.setResizeKeyboard(true);
        return markup;
    }

    private ReplyKeyboard mainMenuKeyboard(String lang) {
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow r1 = new KeyboardRow();
        KeyboardRow r2 = new KeyboardRow();
        KeyboardRow r3 = new KeyboardRow();
        switch (lang) {
            case "RU" -> {
                r1.add("📄 Создать PDF");
                r1.add("📦 Архивировать файлы (Zip)");
                r2.add("🎞️ Создать презентацию");
                r2.add("🌐 Язык");
                r3.add("📄 Word <--> PDF");
                r3.add("💰 Баланс");
            }
            case "ENG" -> {
                r1.add("📄 Create PDF");
                r1.add("📦 Zip Files");
                r2.add("🎞️ Create Presentation");
                r2.add("🌐 Language Settings");
                r3.add("📄 Word <--> PDF");
                r3.add("💰 Balance");
            }
            default -> {
                r1.add("📄 PDF yaratish");
                r1.add("📦 Fayllarni Zip qilish");
                r2.add("🎞️ Taqdimot yaratish");
                r2.add("🌐 Til Sozlamalari");
                r3.add("📄 Word <--> PDF");
                r3.add("💰 Balans");
            }
        }
        rows.add(r1);
        rows.add(r2);
        rows.add(r3);
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(rows);
        markup.setResizeKeyboard(true);
        return markup;
    }

    private String getText(String key, String lang) {
        return switch (key) {
            case "SEND_IMAGES" -> switch (lang) {
                case "RU" -> "📸 Отправьте изображения для создания PDF.";
                case "ENG" -> "📸 Please send images to create a PDF.";
                default -> "📸 Iltimos, PDF yaratish uchun rasm yuboring.";
            };
            case "IMAGE_SAVED" -> switch (lang) {
                case "RU" -> "✅ Изображение сохранено! Отправьте еще или подтвердите.";
                case "ENG" -> "✅ Image saved! Send more or confirm.";
                default -> "✅ Rasm saqlandi! Yana yuboring yoki tasdiqlang.";
            };
            case "ERROR" -> switch (lang) {
                case "RU" -> "❌ Произошла ошибка при сохранении изображения!";
                case "ENG" -> "❌ Error saving image!";
                default -> "❌ Rasm saqlashda xatolik yuz berdi!";
            };
            case "PLEASE_WAIT" -> switch (lang) {
                case "RU" -> "⏳ Пожалуйста, подождите...";
                case "ENG" -> "⏳ Please wait...";
                default -> "⏳ Iltimos, kuting...";
            };
            case "PDF_READY" -> switch (lang) {
                case "RU" -> "✅ Ваш PDF готов!\nПоделитесь нашим ботом со своими друзьями тоже.";
                case "ENG" -> "✅ Your PDF is ready!\nShare our bot with your friends as well.";
                default -> "✅ PDF faylingiz tayyor!\nBotimizni do`stlaringizga ham ulashing.";
            };
            case "MAIN_MENU" -> switch (lang) {
                case "RU" -> "🏠 Главное меню";
                case "ENG" -> "🏠 Main menu";
                default -> "🏠 Asosiy menyu";
            };
            default -> "";
        };
    }
}