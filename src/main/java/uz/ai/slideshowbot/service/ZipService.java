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
import uz.ai.slideshowbot.entity.FileEntity;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.FileRepository;
import uz.ai.slideshowbot.repository.UserRepository;

import java.io.*;
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

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private TelegramRawSender rawSender;

    @Autowired
    private MenuService menuService;

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

            if (doc.getFileSize() > 200 * 1024 * 1024) {
                rawSender.sendReplyWithColoredBtn(
                        bot, chatId.toString(), msg.getMessageId(),
                        "❌ Fayl hajmi 200 MB dan oshmasligi kerak! Maksimal 200 MB.",
                        "◀️ Orqaga", "danger"
                );
                return;
            }
        } else {
            String backBtnText = switch (user.getLanguage()) {
                case "RU" -> "◀️ Назад";
                case "ENG" -> "◀️ Back";
                default -> "◀️ Orqaga";
            };
            rawSender.sendReplyWithColoredBtn(
                    bot, chatId.toString(), msg.getMessageId(),
                    getInvalidFileText(user.getLanguage()),
                    backBtnText, "danger"
            );
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                GetFile getFile = new GetFile(fileId);
                org.telegram.telegrambots.meta.api.objects.File tgFile = bot.execute(getFile);
                if (tgFile == null) return;

                String fileUrl = tgFile.getFileUrl(bot.getBotToken());
                byte[] fileBytes;
                try (InputStream in = new URL(fileUrl).openStream()) {
                    fileBytes = in.readAllBytes();
                }

                FileEntity fileEntity = new FileEntity();
                fileEntity.setChatId(chatId);
                fileEntity.setFileName(fileName);
                fileEntity.setFileType("ZIP");
                fileEntity.setData(fileBytes);
                fileRepository.save(fileEntity);

                String zipBtnText = switch (user.getLanguage()) {
                    case "RU" -> "📦 Создать ZIP";
                    case "ENG" -> "📦 Create ZIP";
                    default -> "📦 ZIP yaratish";
                };
                rawSender.sendReplyWithColoredBtn(
                        bot, chatId.toString(), msg.getMessageId(),
                        getFileSavedText(user.getLanguage()),
                        zipBtnText, "success"
                );

            } catch (Exception e) {
                e.printStackTrace();
                String backBtnText = switch (user.getLanguage()) {
                    case "RU" -> "◀️ Назад";
                    case "ENG" -> "◀️ Back";
                    default -> "◀️ Orqaga";
                };
                rawSender.sendReplyWithColoredBtn(
                        bot, chatId.toString(), msg.getMessageId(),
                        getErrorText(user.getLanguage()),
                        backBtnText, "danger"
                );
            }
        });
    }

    public void handleZipConfirmReply(Update update, SlideBot bot, UserEntity user) {
        Long chatId = getChatId(update);
        if (chatId == null || user == null) return;

        List<FileEntity> files = fileRepository.findAllByChatId(chatId)
                .stream()
                .filter(f -> "ZIP".equals(f.getFileType()))
                .toList();

        if (files.isEmpty()) {
            rawSender.sendWithColoredBtn(bot, chatId.toString(), getNoFilesText(user), getBackBtnText(user.getLanguage()), "danger");
            return;
        }

        final List<FileEntity> finalFiles = files;
        final Long finalChatId = chatId;

        new Thread(() -> {
            try {
                SendMessage progressMsg = new SendMessage(finalChatId.toString(),
                        getText("PLEASE_WAIT", user.getLanguage()) + "\n░░░░░░░░░░ 0%");
                Message sentProgress = bot.execute(progressMsg);

                String[] bars = {
                        "██░░░░░░░░ 20%",
                        "████░░░░░░ 40%",
                        "██████░░░░ 60%",
                        "████████░░ 80%",
                        "██████████ 100%"
                };
                for (String bar : bars) {
                    Thread.sleep(800);
                    try {
                        EditMessageText edit = new EditMessageText();
                        edit.setChatId(finalChatId.toString());
                        edit.setMessageId(sentProgress.getMessageId());
                        edit.setText(getText("PLEASE_WAIT", user.getLanguage()) + "\n" + bar);
                        bot.execute(edit);
                    } catch (Exception ignored) {}
                }

                byte[] zipBytes = createZipFromEntities(finalFiles);

                bot.execute(new DeleteMessage(finalChatId.toString(), sentProgress.getMessageId()));

                String shareUrl = "https://t.me/share/url?url=" +
                        URLEncoder.encode("https://t.me/AI_Slide_Assistant_Bot", StandardCharsets.UTF_8);
                String btnText = switch (user.getLanguage() != null ? user.getLanguage() : "UZ") {
                    case "RU" -> "📤 Поделиться";
                    case "ENG" -> "📤 Share";
                    default -> "📤 Ulashish";
                };
                String zipCaption = getText("ZIP_READY", user.getLanguage());
                String zipName = "archive_" + finalChatId + ".zip";

                rawSender.sendDocumentWithStyledBtn(bot, finalChatId.toString(), zipBytes, zipName,
                        zipCaption, btnText, shareUrl, "primary");

                clearUserZipData(finalChatId);
                menuService.sendSimpleMenu(bot, finalChatId, user.getLanguage());
                user.setInZipFlow(false);
                userRepository.save(user);

            } catch (Exception e) {
                e.printStackTrace();
                rawSender.sendWithColoredBtn(bot, finalChatId.toString(),
                        getErrorText(user.getLanguage()), getBackBtnText(user.getLanguage()), "danger");
            }
        }).start();
    }

    private byte[] createZipFromEntities(List<FileEntity> files) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (FileEntity file : files) {
                if (file.getData() == null) continue;
                zos.putNextEntry(new ZipEntry(file.getFileName()));
                zos.write(file.getData());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    public void clearUserZipData(Long chatId) {
        if (chatId == null) return;
        fileRepository.deleteByChatId(chatId);
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
                case "RU" -> "📦 ZIP готов!\nПоделитесь ботом с друзьями.";
                case "ENG" -> "📦 ZIP is ready!\nShare our bot with friends.";
                default -> "📦 ZIP tayyor!\nBotimizni do'stlaringizga ulashing.";
            };
            case "MAIN_MENU" -> switch (lang) {
                case "RU" -> "🏠 Главное меню";
                case "ENG" -> "🏠 Main menu";
                default -> "🏠 Asosiy menyu";
            };
            default -> key;
        };
    }

    public String getBackBtnText(String lang) {
        return switch (lang != null ? lang : "UZ") {
            case "RU" -> "◀️ Назад";
            case "ENG" -> "◀️ Back";
            default -> "◀️ Orqaga";
        };
    }
}