package uz.ai.slideshowbot.service;

import com.itextpdf.text.Document;
import com.itextpdf.text.Image;
import com.itextpdf.text.PageSize;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Autowired;
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
import uz.ai.slideshowbot.entity.FileEntity;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.FileRepository;
import uz.ai.slideshowbot.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class PdfService {

    @Autowired
    private MenuService menuService;

    private final UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private TelegramRawSender rawSender;

    private final Map<Long, List<byte[]>> tempUserImages = new HashMap<>();

    public PdfService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public boolean handlePdfFlow(Update update, SlideBot bot, UserEntity user) throws TelegramApiException {
        if (update == null || user == null) return false;
        Message msg = update.hasMessage() ? update.getMessage() : null;
        if (msg == null) return false;
        Long chatId = msg.getChatId();

        if (msg.hasText()) {
            String text = msg.getText();

            if (text.equals("📄 PDF yaratish") || text.equals("📄 Создать PDF") || text.equals("📄 Create PDF")) {
                tempUserImages.put(chatId, new ArrayList<>());
                sendColoredPdfControl(bot, chatId, getText("SEND_IMAGES", user.getLanguage()), user.getLanguage());
                return true;
            }

            if (text.equals("✅ Tasdiqlash") || text.equals("✅ Подтвердить") || text.equals("✅ Confirm")) {
                if (!tempUserImages.containsKey(chatId)) return false;
                List<byte[]> images = tempUserImages.get(chatId);
                if (images == null || images.isEmpty()) {
                    String warning = switch (user.getLanguage()) {
                        case "RU" -> "⚠️ Вы еще не отправили изображение!";
                        case "ENG" -> "⚠️ You haven't sent an image yet!";
                        default -> "⚠️ Siz hali rasm yubormadingiz!";
                    };
                    sendColoredPdfControl(bot, chatId, warning, user.getLanguage());
                    return true;
                }

                byte[] pdfBytes = createPdfFromBytes(images);
                tempUserImages.remove(chatId);
                showProgressAndSendPdf(bot, chatId, pdfBytes, user);
                return true;
            }

            if (text.equals("◀️ Orqaga") || text.equals("◀️ Наzad") || text.equals("◀️ Back") || text.equals("◀️ Назад")) {
                if (!tempUserImages.containsKey(chatId)) return false;
                tempUserImages.remove(chatId);
                menuService.sendSimpleMenu(bot, chatId, user.getLanguage());
                return true;
            }
        }

        if (msg.hasPhoto()) {
            if (!tempUserImages.containsKey(chatId)) return false;

            try {
                List<PhotoSize> photos = msg.getPhoto();
                String fileId = photos.stream()
                        .max(Comparator.comparing(PhotoSize::getFileSize))
                        .map(PhotoSize::getFileId)
                        .orElse(null);

                if (fileId != null) {
                    GetFile getFile = new GetFile();
                    getFile.setFileId(fileId);
                    org.telegram.telegrambots.meta.api.objects.File file = bot.execute(getFile);

                    URL url = new URL("https://api.telegram.org/file/bot" + bot.getBotToken() + "/" + file.getFilePath());
                    try (InputStream is = url.openStream()) {
                        byte[] imageBytes = is.readAllBytes();
                        tempUserImages.get(chatId).add(imageBytes);

                        String confirmBtnText = switch (user.getLanguage() != null ? user.getLanguage() : "UZ") {
                            case "RU" -> "✅ Подтвердить";
                            case "ENG" -> "✅ Confirm";
                            default -> "✅ Tasdiqlash";
                        };

                        rawSender.sendReplyWithColoredBtn(
                                bot,
                                chatId.toString(),
                                msg.getMessageId(),
                                getText("IMAGE_SAVED", user.getLanguage()),
                                confirmBtnText,
                                "success"
                        );
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                sendColoredPdfControl(bot, chatId, getText("ERROR", user.getLanguage()), user.getLanguage());
            }
            return true;
        }
        return false;
    }

    private void sendColoredPdfControl(SlideBot bot, Long chatId, String text, String lang) {
        String confirmBtn = switch (lang) {
            case "RU" -> "✅ Подтвердить";
            case "ENG" -> "✅ Confirm";
            default -> "✅ Tasdiqlash";
        };
        String backBtn = switch (lang) {
            case "RU" -> "◀️ Назад";
            case "ENG" -> "◀️ Back";
            default -> "◀️ Orqaga";
        };

        rawSender.sendColoredBinaryMenu(
                bot,
                chatId.toString(),
                text,
                confirmBtn, "success",
                backBtn, "danger"
        );
    }

    private byte[] createPdfFromBytes(List<byte[]> images) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();
            for (byte[] imgBytes : images) {
                Image img = Image.getInstance(imgBytes);
                img.scaleToFit(PageSize.A4.getWidth(), PageSize.A4.getHeight());
                img.setAlignment(Image.ALIGN_CENTER);
                document.add(img);
                document.newPage();
            }
            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return baos.toByteArray();
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
                case "RU" -> "✅ Ваш PDF готов!\nПоделитесь ботом с друзьями тоже.";
                case "ENG" -> "✅ Your PDF is ready!\nShare our bot with friends as well.";
                default -> "✅ PDF faylingiz tayyor!\nBotimizni do'stlaringizga ham ulashing.";
            };
            case "MAIN_MENU" -> switch (lang) {
                case "RU" -> "🏠 Главное меню";
                case "ENG" -> "🏠 Main menu";
                default -> "🏠 Asosiy menyu";
            };
            default -> "";
        };
    }

    private void showProgressAndSendPdf(SlideBot bot, Long chatId, byte[] pdfBytes, UserEntity user) {
        try {
            SendMessage progressMsg = new SendMessage(chatId.toString(),
                    getText("PLEASE_WAIT", user.getLanguage()) + "\n░░░░░░░░░░ 0%");
            Message sentProgress = bot.execute(progressMsg);

            String[] bars = { "███░░░░░░░ 30%", "██████░░░░ 60%", "██████████ 100%" };
            for (String bar : bars) {
                Thread.sleep(1000);
                try {
                    EditMessageText edit = new EditMessageText();
                    edit.setChatId(chatId.toString());
                    edit.setMessageId(sentProgress.getMessageId());
                    edit.setText(getText("PLEASE_WAIT", user.getLanguage()) + "\n" + bar);
                    bot.execute(edit);
                } catch (Exception ignored) {}
            }

            bot.execute(new DeleteMessage(chatId.toString(), sentProgress.getMessageId()));

            String botLink = "https://t.me/AI_Slide_Assistant_Bot";
            String shareUrl = "https://t.me/share/url?url=" + URLEncoder.encode(botLink, StandardCharsets.UTF_8);
            String btnText = switch (user.getLanguage() != null ? user.getLanguage() : "UZ") {
                case "RU" -> "📤 Поделиться";
                case "ENG" -> "📤 Share";
                default -> "📤 Ulashish";
            };
            String caption = getText("PDF_READY", user.getLanguage());
            String pdfName = "document_" + chatId + ".pdf";

            rawSender.sendDocumentWithStyledBtn(bot, chatId.toString(), pdfBytes, pdfName,
                    caption, btnText, shareUrl, "success");

            menuService.sendSimpleMenu(bot, chatId, user.getLanguage());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}