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

        String fileId = null;
        String fileName = null;
        long fileSize = 0;

        if (msg.hasPhoto()) {
            List<PhotoSize> photos = msg.getPhoto();
            PhotoSize largest = photos.get(photos.size() - 1);
            fileId = largest.getFileId();
            fileSize = largest.getFileSize();
            fileName = "photo_" + System.currentTimeMillis() + ".jpg";
        } else if (msg.hasDocument()) {
            Document doc = msg.getDocument();
            fileId = doc.getFileId();
            fileSize = doc.getFileSize();
            fileName = doc.getFileName() != null ? doc.getFileName() : "file_" + System.currentTimeMillis();
        } else {
            rawSender.sendReplyWithColoredBtn(bot, chatId.toString(), msg.getMessageId(),
                    getInvalidFileText(user.getLanguage()), getBackBtnText(user.getLanguage()), "danger");
            return;
        }

        final long MAX_SIZE = 20L * 1024 * 1024;

        if (fileSize > MAX_SIZE) {
            String errorText = switch (user.getLanguage() != null ? user.getLanguage() : "UZ") {
                case "RU" -> "❌ Размер файла превышает 20 МБ!\n Не принимается из-за ограничений Telegram.";
                case "ENG" -> "❌ File size exceeds 20 MB!\nNot accepted due to Telegram limit.";
                default -> "❌ Fayl hajmi 20 MB dan oshmasligi kerak!\nTelegram cheklovi tufayli qabul qilinmadi.";
            };

            rawSender.sendSimpleMessage(bot, chatId.toString(), errorText);
            return;
        }

        final String finalFileId = fileId;
        final String finalFileName = fileName;

        CompletableFuture.runAsync(() -> {
            try {
                GetFile getFile = new GetFile(finalFileId);
                org.telegram.telegrambots.meta.api.objects.File tgFile = bot.execute(getFile);
                if (tgFile == null) return;

                String fileUrl = tgFile.getFileUrl(bot.getBotToken());
                byte[] fileBytes = new URL(fileUrl).openStream().readAllBytes();

                FileEntity fileEntity = new FileEntity();
                fileEntity.setChatId(chatId);
                fileEntity.setFileName(finalFileName);
                fileEntity.setFileType("ZIP");
                fileEntity.setData(fileBytes);
                fileRepository.save(fileEntity);

                String zipBtnText = switch (user.getLanguage()) {
                    case "RU" -> "📦 Создать ZIP";
                    case "ENG" -> "📦 Create ZIP";
                    default -> "📦 ZIP yaratish";
                };

                rawSender.sendReplyWithColoredBtn(bot, chatId.toString(), msg.getMessageId(),
                        getFileSavedText(user.getLanguage()), zipBtnText, "success");

            } catch (Exception e) {
                e.printStackTrace();
                rawSender.sendReplyWithColoredBtn(bot, chatId.toString(), msg.getMessageId(),
                        getErrorText(user.getLanguage()), getBackBtnText(user.getLanguage()), "danger");
            }
        });
    }

    public void handleZipConfirmReply(Update update, SlideBot bot, UserEntity user) {
        Long chatId = getChatId(update);
        if (chatId == null || user == null) return;

        final long MAX_SIZE = 200L * 1024 * 1024;

        List<FileEntity> validFiles = fileRepository.findAllByChatId(chatId)
                .stream()
                .filter(f -> "ZIP".equals(f.getFileType()))
                .filter(f -> f.getData() != null && f.getData().length <= MAX_SIZE)
                .toList();

        if (validFiles.isEmpty()) {
            rawSender.sendWithColoredBtn(bot, chatId.toString(), getNoFilesText(user),
                    getBackBtnText(user.getLanguage()), "danger");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // 3 soniyalik progress
                SendMessage progressMsg = new SendMessage(chatId.toString(),
                        getText("PLEASE_WAIT", user.getLanguage()) + "\n⬜⬜⬜⬜");
                Message sentProgress = bot.execute(progressMsg);

                for (int i = 1; i <= 3; i++) {
                    String bar = "🟩".repeat(i) + "⬜".repeat(3 - i);
                    EditMessageText edit = new EditMessageText();
                    edit.setChatId(chatId.toString());
                    edit.setMessageId(sentProgress.getMessageId());
                    edit.setText(getText("PLEASE_WAIT", user.getLanguage()) + "\n" + bar);
                    bot.execute(edit);
                    Thread.sleep(1000);
                }

                byte[] zipBytes = createZipFromEntities(validFiles);
                bot.execute(new DeleteMessage(chatId.toString(), sentProgress.getMessageId()));

                String zipName = "archive_" + chatId + "_" + System.currentTimeMillis() + ".zip";
                String caption = getText("ZIP_READY", user.getLanguage());

                String shareUrl = "https://t.me/share/url?url=" +
                        URLEncoder.encode("https://t.me/AI_Slide_Assistant_Bot", StandardCharsets.UTF_8);

                String btnText = switch (user.getLanguage() != null ? user.getLanguage() : "UZ") {
                    case "RU" -> "📤 Поделиться";
                    case "ENG" -> "📤 Share";
                    default -> "📤 Ulashish";
                };

                rawSender.sendDocumentWithStyledBtn(bot, chatId.toString(), zipBytes, zipName,
                        caption, btnText, shareUrl, "primary");

                clearUserZipData(chatId);
                menuService.sendSimpleMenu(bot, chatId, user.getLanguage());
                user.setInZipFlow(false);
                userRepository.save(user);

            } catch (Exception e) {
                e.printStackTrace();
                rawSender.sendWithColoredBtn(bot, chatId.toString(), getErrorText(user.getLanguage()),
                        getBackBtnText(user.getLanguage()), "danger");
            }
        });
    }

    private byte[] createZipFromEntities(List<FileEntity> files) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {

            Map<String, Integer> nameCounter = new HashMap<>();

            for (FileEntity file : files) {
                if (file.getData() == null) continue;

                String originalName = file.getFileName() != null ? file.getFileName() : "file.bin";
                String entryName = originalName;

                if (nameCounter.containsKey(originalName)) {
                    int count = nameCounter.get(originalName) + 1;
                    nameCounter.put(originalName, count);

                    int dotIndex = originalName.lastIndexOf('.');
                    if (dotIndex > 0) {
                        String name = originalName.substring(0, dotIndex);
                        String ext = originalName.substring(dotIndex);
                        entryName = name + "_" + count + ext;
                    } else {
                        entryName = originalName + "_" + count;
                    }
                } else {
                    nameCounter.put(originalName, 1);
                }

                zos.putNextEntry(new ZipEntry(entryName));
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