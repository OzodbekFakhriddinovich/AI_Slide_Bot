package uz.ai.slideshowbot.service;

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
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.UserRepository;
import org.telegram.telegrambots.meta.api.objects.InputFile;

import java.io.*;
import java.io.File;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ZipService {

    @Autowired
    private UserRepository userRepository;

    private final Map<Long, List<File>> userFiles = new HashMap<>();

    private static final String SAVE_DIR = "C:\\Users\\User\\Desktop\\saqlash\\saqlash";

    public ZipService() {
        File dir = new File(SAVE_DIR);
        if (!dir.exists()) dir.mkdirs();
    }

    public void handleFile(Update update, SlideBot bot, UserEntity user) {
        if (update == null || !update.hasMessage() || user == null) return;

        Message msg = update.getMessage();
        Long chatId = msg.getChatId();
        String fileId;
        String fileName;

        if (msg.hasPhoto()) {
            List<PhotoSize> photos = msg.getPhoto();
            fileId = photos.get(photos.size() - 1).getFileId();
            fileName = "photo_" + System.currentTimeMillis() + ".jpg";
        } else if (msg.hasDocument()) {
            Document doc = msg.getDocument();
            fileId = doc.getFileId();
            fileName = doc.getFileName() != null ? doc.getFileName() : "file_" + System.currentTimeMillis();
        } else {
            sendReply(bot, chatId, msg.getMessageId(),
                    getInvalidFileText(user.getLanguage()),
                    zipBackKeyboard(user.getLanguage()));
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                GetFile getFile = new GetFile(fileId);
                org.telegram.telegrambots.meta.api.objects.File tgFile = bot.execute(getFile);
                if (tgFile == null) return;

                String fileUrl = tgFile.getFileUrl(bot.getBotToken());
                File downloaded = new File(SAVE_DIR, "chat_" + chatId + "_" + System.currentTimeMillis() + "_" + fileName);

                try (InputStream in = new URL(fileUrl).openStream();
                     FileOutputStream out = new FileOutputStream(downloaded)) {
                    in.transferTo(out);
                }

                userFiles.computeIfAbsent(chatId, k -> new ArrayList<>()).add(downloaded);

                sendReply(bot, chatId, msg.getMessageId(),
                        getFileSavedText(user.getLanguage()),
                        zipButtonKeyboard(user.getLanguage()));

            } catch (Exception e) {
                e.printStackTrace();
                sendReply(bot, chatId, msg.getMessageId(),
                        getErrorText(user.getLanguage()),
                        zipBackKeyboard(user.getLanguage()));
            }
        });
    }

    public void handleZipConfirmReply(Update update, SlideBot bot, UserEntity user) {
        Long chatId = getChatId(update);
        if (chatId == null || user == null) return;

        List<File> files = userFiles.get(chatId);
        if (files == null || files.isEmpty()) {
            send(bot, chatId, getNoFilesText(user), zipBackKeyboard(user.getLanguage()));
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                SendMessage emojiMsg = new SendMessage(chatId.toString(), "⏳");
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
                    Thread.sleep(300);
                }

                File zipFile = createZip(chatId, files);

                bot.execute(new DeleteMessage(chatId.toString(), sentProgress.getMessageId()));

                String fullShareUrl = "https://t.me/share/url?url=" +
                        URLEncoder.encode("https://t.me/AI_Slide_Assistant_Bot", StandardCharsets.UTF_8);

                InlineKeyboardButton shareBtn = new InlineKeyboardButton();
                shareBtn.setText(switch (user.getLanguage()) {
                    case "RU" -> "📤 Поделиться";
                    case "ENG" -> "📤 Share";
                    default -> "📤 Ulashish";
                });
                shareBtn.setUrl(fullShareUrl);

                InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(List.of(shareBtn)));

                SendDocument sendDoc = new SendDocument();
                sendDoc.setChatId(chatId.toString());
                sendDoc.setDocument(new InputFile(zipFile, zipFile.getName()));
                sendDoc.setCaption(getText("ZIP_READY", user.getLanguage()));
                sendDoc.setReplyMarkup(markup);
                bot.execute(sendDoc);

                clearUserZipData(chatId);
                if (zipFile.exists()) zipFile.delete();

                send(bot, chatId, getText("MAIN_MENU", user.getLanguage()), mainMenuKeyboard(user.getLanguage()));

                user.setInZipFlow(false);
                userRepository.save(user);

            } catch (Exception e) {
                e.printStackTrace();
                send(bot, chatId, getErrorText(user.getLanguage()), zipBackKeyboard(user.getLanguage()));
            }
        });
    }

    private File createZip(Long chatId, List<File> files) throws IOException {
        File zipFile = new File(SAVE_DIR, "chat_" + chatId + "_archive_" + System.currentTimeMillis() + ".zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File f : files) {
                if (f == null || !f.exists()) continue;
                zos.putNextEntry(new ZipEntry(f.getName()));
                try (FileInputStream fis = new FileInputStream(f)) {
                    fis.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
        return zipFile;
    }

    public void clearUserZipData(Long chatId) {
        if (chatId == null) return;
        clearTempFiles(chatId);
        userFiles.remove(chatId);
    }

    private void clearTempFiles(Long chatId) {
        File folder = new File(SAVE_DIR);
        File[] files = folder.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().contains("chat_" + chatId + "_") && f.exists()) {
                    f.delete();
                }
            }
        }
    }

    private void sendReply(SlideBot bot, Long chatId, Integer messageId, String text, ReplyKeyboard keyboard) {
        try {
            SendMessage msg = new SendMessage(chatId.toString(), text);
            msg.setReplyToMessageId(messageId);
            if (keyboard != null) msg.setReplyMarkup(keyboard);
            bot.execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void send(SlideBot bot, Long chatId, String text, ReplyKeyboard keyboard) {
        try {
            SendMessage msg = new SendMessage(chatId.toString(), text);
            if (keyboard != null) msg.setReplyMarkup(keyboard);
            bot.execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private Long getChatId(Update update) {
        if (update.hasMessage()) return update.getMessage().getChatId();
        if (update.hasCallbackQuery()) return update.getCallbackQuery().getMessage().getChatId();
        return null;
    }

    private String getFileSavedText(String lang) {
        return switch (lang) {
            case "RU" -> "✅ Файл сохранён!";
            case "ENG" -> "✅ File saved!";
            default -> "✅ Fayl saqlandi!";
        };
    }

    private String getInvalidFileText(String lang) {
        return switch (lang) {
            case "RU" -> "❌ Принимаются только фото и документы!";
            case "ENG" -> "❌ Only photos and documents are accepted!";
            default -> "❌ Faqat rasm va dokumentlar qabul qilinadi!";
        };
    }

    private String getErrorText(String lang) {
        return switch (lang) {
            case "RU" -> "❌ Ошибка при загрузке!";
            case "ENG" -> "❌ Error while uploading!";
            default -> "❌ Yuklashda xatolik yuz berdi!";
        };
    }

    public ReplyKeyboard zipButtonKeyboard(String lang) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        KeyboardRow row = new KeyboardRow();
        row.add(switch (lang) {
            case "RU" -> "📦 Создать ZIP";
            case "ENG" -> "📦 Create ZIP";
            default -> "📦 ZIP yaratish";
        });
        markup.setKeyboard(List.of(row));
        markup.setResizeKeyboard(true);
        return markup;
    }

    public ReplyKeyboard zipBackKeyboard(String lang) {
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        KeyboardRow row = new KeyboardRow();
        row.add(switch (lang) {
            case "RU" -> "◀️ Назад";
            case "ENG" -> "◀️ Back";
            default -> "◀️ Orqaga";
        });
        markup.setKeyboard(List.of(row));
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
                r1.add("📄 Создать PDF"); r1.add("📦 Архивировать файлы (Zip)");
                r2.add("🎞️ Создать презентацию"); r2.add("🌐 Язык");
                r3.add("📄 Word <--> PDF"); r3.add("💰 Баланс");
            }
            case "ENG" -> {
                r1.add("📄 Create PDF"); r1.add("📦 Zip Files");
                r2.add("🎞️ Create Presentation"); r2.add("🌐 Language Settings");
                r3.add("📄 Word <--> PDF"); r3.add("💰 Balance");
            }
            default -> {
                r1.add("📄 PDF yaratish"); r1.add("📦 Fayllarni Zip qilish");
                r2.add("🎞️ Taqdimot yaratish"); r2.add("🌐 Til Sozlamalari");
                r3.add("📄 Word <--> PDF"); r3.add("💰 Balans");
            }
        }
        rows.add(r1); rows.add(r2); rows.add(r3);
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(rows);
        markup.setResizeKeyboard(true);
        return markup;
    }

    public String getNoFilesText(UserEntity user) {
        return switch (user.getLanguage()) {
            case "RU" -> "⚠️ Нет файлов для создания ZIP.";
            case "ENG" -> "⚠️ No files to create ZIP.";
            default -> "⚠️ ZIP qilish uchun fayl mavjud emas.";
        };
    }

    private String getText(String key, String lang) {
        return switch (key) {
            case "PLEASE_WAIT" -> switch (lang) {
                case "RU" -> "⏳ Пожалуйста, подождите...";
                case "ENG" -> "⏳ Please wait...";
                default -> "⏳ Iltimos, kuting...";
            };
            case "ZIP_READY" -> switch (lang) {
                case "RU" -> "📦 ZIP готов!\nПоделитесь нашим ботом со своими друзьями тоже.";
                case "ENG" -> "📦 ZIP is ready!\nShare our bot with your friends as well.";
                default -> "📦 ZIP tayyor!\nBotimizni do`stlaringizga ham ulashing.";
            };
            case "MAIN_MENU" -> switch (lang) {
                case "RU" -> "🏠 Главное меню";
                case "ENG" -> "🏠 Main menu";
                default -> "🏠 Asosiy menyu";
            };
            default -> key;
        };
    }
}