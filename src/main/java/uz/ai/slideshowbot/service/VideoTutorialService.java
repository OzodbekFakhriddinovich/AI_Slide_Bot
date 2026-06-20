package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.VideoTutorialEntity;
import uz.ai.slideshowbot.repository.VideoTutorialRepository;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class VideoTutorialService {

    public static final Long VIDEO_ADMIN_ID = 8769058760L;

    private final Map<Long, String> adminState        = new ConcurrentHashMap<>();
    private final Map<Long, String> adminTargetButton = new ConcurrentHashMap<>();
    private final Map<Long, String> adminTempVideoId  = new ConcurrentHashMap<>();
    private final Map<Long, String> adminTempName     = new ConcurrentHashMap<>();

    @Autowired private VideoTutorialRepository videoRepository;
    @Autowired private TelegramRawSender rawSender;



    public boolean isVideoAdmin(Long chatId) {
        return VIDEO_ADMIN_ID.equals(chatId);
    }

    public boolean isAdminInFlow(Long chatId) {
        return !adminState.getOrDefault(chatId, "IDLE").equals("IDLE");
    }



    public void showButtonSelector(SlideBot bot, Long chatId) {
        String markup = """
            {"inline_keyboard":[
              [{"text":"📄 PDF yaratish","callback_data":"VID_BTN_PDF"},
               {"text":"🎞️ Taqdimot","callback_data":"VID_BTN_TAQDIMOT"}],
              [{"text":"📦 ZIP","callback_data":"VID_BTN_ZIP"},
               {"text":"📄 Word↔PDF","callback_data":"VID_BTN_WORD_PDF"}],
              [{"text":"📋 Test","callback_data":"VID_BTN_TEST"},
               {"text":"📝 Referat","callback_data":"VID_BTN_REFERAT"}],
              [{"text":"💰 Balans","callback_data":"VID_BTN_BALANS"}],
              [{"text":"❌ Bekor qilish","callback_data":"VID_CANCEL"}]
            ]}""";
        rawSender.sendInlineMessage(bot, chatId.toString(),
                "📹 Qaysi tugma uchun video qo'llanma qo'shmoqchisiz?", markup);
    }


    public boolean handleAdminCallback(SlideBot bot, Long chatId, Integer msgId, String data) {
        if (!isVideoAdmin(chatId)) return false;
        if (!data.startsWith("VID_")) return false;

        if (data.startsWith("VID_BTN_")) {
            String buttonKey = data.substring(8);
            rawSender.deleteMessageById(bot, chatId.toString(), msgId);
            adminState.put(chatId, "WAITING_VIDEO");
            adminTargetButton.put(chatId, buttonKey);
            adminTempVideoId.remove(chatId);
            adminTempName.remove(chatId);

            String label = buttonKeyToLabel(buttonKey);
            rawSender.sendWithColoredBtn(bot, chatId.toString(),
                    "📹 <b>" + label + "</b> uchun video qo'llanmani yuboring:\n\n⬇️ Video faylni yuboring.",
                    "❌ Bekor qilish", "danger");
            return true;
        }

        if (data.equals("VID_CANCEL")) {
            rawSender.deleteMessageById(bot, chatId.toString(), msgId);
            resetAdminFlow(chatId);
            rawSender.sendSimpleMessage(bot, chatId.toString(), "❌ Bekor qilindi.");
            return true;
        }

        String state = adminState.getOrDefault(chatId, "IDLE");

        if (data.equals("VID_VIDEO_YES") && state.equals("WAITING_VIDEO_CONFIRM")) {
            rawSender.deleteMessageById(bot, chatId.toString(), msgId);
            adminState.put(chatId, "WAITING_NAME");
            rawSender.sendWithColoredBtn(bot, chatId.toString(),
                    "✅ Video qabul qilindi!\n\n📝 Endi video nomini yozing:\n<i>Masalan: PDF yaratish qo'llanmasi</i>",
                    "❌ Bekor qilish", "danger");
            return true;
        }

        if (data.equals("VID_VIDEO_NO") && state.equals("WAITING_VIDEO_CONFIRM")) {
            rawSender.deleteMessageById(bot, chatId.toString(), msgId);
            adminState.put(chatId, "WAITING_VIDEO");
            adminTempVideoId.remove(chatId);
            rawSender.sendWithColoredBtn(bot, chatId.toString(),
                    "🔄 Qayta video yuboring:", "❌ Bekor qilish", "danger");
            return true;
        }

        if (data.equals("VID_NAME_YES") && state.equals("WAITING_NAME_CONFIRM")) {
            rawSender.deleteMessageById(bot, chatId.toString(), msgId);
            adminState.put(chatId, "WAITING_PUBLISH");
            String btnLabel = buttonKeyToLabel(adminTargetButton.getOrDefault(chatId, ""));
            String name     = adminTempName.getOrDefault(chatId, "");
            String markup = """
                {"inline_keyboard":[[
                  {"text":"✅ Ha, chiqarish","callback_data":"VID_PUBLISH_YES"},
                  {"text":"❌ Yo'q","callback_data":"VID_PUBLISH_NO"}
                ]]}""";
            rawSender.sendInlineMessage(bot, chatId.toString(),
                    "🚀 <b>" + btnLabel + "</b> uchun video qo'llanma productionga chiqsinmi?\n\n📝 Nom: <b>" + name + "</b>",
                    markup);
            return true;
        }

        if (data.equals("VID_NAME_NO") && state.equals("WAITING_NAME_CONFIRM")) {
            rawSender.deleteMessageById(bot, chatId.toString(), msgId);
            adminState.put(chatId, "WAITING_NAME");
            adminTempName.remove(chatId);
            rawSender.sendWithColoredBtn(bot, chatId.toString(),
                    "🔄 Qayta video nomini yozing:", "❌ Bekor qilish", "danger");
            return true;
        }

        if (data.equals("VID_PUBLISH_YES") && state.equals("WAITING_PUBLISH")) {
            rawSender.deleteMessageById(bot, chatId.toString(), msgId);
            String buttonKey  = adminTargetButton.getOrDefault(chatId, "");
            String videoFileId = adminTempVideoId.getOrDefault(chatId, "");
            String videoName   = adminTempName.getOrDefault(chatId, "");
            try {
                VideoTutorialEntity entity = videoRepository.findByButtonKey(buttonKey)
                        .orElse(new VideoTutorialEntity());
                entity.setButtonKey(buttonKey);
                entity.setVideoFileId(videoFileId);
                entity.setVideoName(videoName);
                entity.setActive(true);
                videoRepository.save(entity);

                rawSender.sendSimpleMessage(bot, chatId.toString(),
                        "✅ Video qo'llanma muvaffaqiyatli saqlandi!\n\n" +
                                "📹 Nom: <b>" + videoName + "</b>\n" +
                                "🎯 Tugma: <b>" + buttonKeyToLabel(buttonKey) + "</b>");
            } catch (Exception e) {
                rawSender.sendSimpleMessage(bot, chatId.toString(), "❌ Xatolik: " + e.getMessage());
            }
            resetAdminFlow(chatId);
            return true;
        }

        if (data.equals("VID_PUBLISH_NO") && state.equals("WAITING_PUBLISH")) {
            rawSender.deleteMessageById(bot, chatId.toString(), msgId);
            resetAdminFlow(chatId);
            rawSender.sendSimpleMessage(bot, chatId.toString(), "❌ Bekor qilindi. Video saqlanmadi.");
            return true;
        }

        return false;
    }


    public boolean handleAdminMessage(SlideBot bot, Long chatId, Message message) {
        String state = adminState.getOrDefault(chatId, "IDLE");

        if (state.equals("WAITING_VIDEO")) {
            if (message.hasVideo()) {
                String fileId = message.getVideo().getFileId();
                adminTempVideoId.put(chatId, fileId);
                adminState.put(chatId, "WAITING_VIDEO_CONFIRM");

                try {
                    SendVideo sv = new SendVideo();
                    sv.setChatId(chatId.toString());
                    sv.setVideo(new InputFile(fileId));
                    sv.setCaption("☝️ Shu video qo'llanmami?");
                    bot.execute(sv);
                } catch (Exception ignored) {}

                String markup = """
                    {"inline_keyboard":[[
                      {"text":"✅ Ha","callback_data":"VID_VIDEO_YES"},
                      {"text":"❌ Yo'q","callback_data":"VID_VIDEO_NO"}
                    ]]}""";
                rawSender.sendInlineMessage(bot, chatId.toString(), "Tasdiqlaysizmi?", markup);
            } else {
                rawSender.sendWithColoredBtn(bot, chatId.toString(),
                        "❌ Faqat video fayl yuboring!", "❌ Bekor qilish", "danger");
            }
            return true;
        }

        if (state.equals("WAITING_NAME")) {
            if (message.hasText()) {
                String name = message.getText().trim();
                if (name.length() < 3) {
                    rawSender.sendWithColoredBtn(bot, chatId.toString(),
                            "❌ Nom juda qisqa! Kamida 3 belgi kiriting.",
                            "❌ Bekor qilish", "danger");
                    return true;
                }
                adminTempName.put(chatId, name);
                adminState.put(chatId, "WAITING_NAME_CONFIRM");

                String markup = """
                    {"inline_keyboard":[[
                      {"text":"✅ Ha","callback_data":"VID_NAME_YES"},
                      {"text":"❌ Yo'q","callback_data":"VID_NAME_NO"}
                    ]]}""";
                rawSender.sendInlineMessage(bot, chatId.toString(),
                        "📝 Video nomi: <b>" + name + "</b>\n\nTasdiqlaysizmi?", markup);
            } else {
                rawSender.sendWithColoredBtn(bot, chatId.toString(),
                        "❌ Matn yozing!", "❌ Bekor qilish", "danger");
            }
            return true;
        }

        return false;
    }


    public void sendTutorialIfExists(SlideBot bot, Long chatId, String buttonKey, String lang) {
        try {
            VideoTutorialEntity tutorial = videoRepository
                    .findByButtonKeyAndActive(buttonKey, true).orElse(null);
            if (tutorial == null || tutorial.getVideoFileId() == null) return;

            SendVideo sv = new SendVideo();
            sv.setChatId(chatId.toString());
            sv.setVideo(new InputFile(tutorial.getVideoFileId()));
            sv.setCaption("📹 <b>" + tutorial.getVideoName() + "</b>");
            sv.setParseMode("HTML");
            bot.execute(sv);
        } catch (Exception e) {
            System.err.println("Tutorial video yuborishda xato: " + e.getMessage());
        }
    }

    public String resolveButtonKey(String text) {
        if (text == null) return null;
        return switch (text) {
            case "📄 PDF yaratish", "📄 Создать PDF", "📄 Create PDF"                      -> "PDF";
            case "🎞️ Taqdimot yaratish", "🎞️ Создать презентацию", "🎞️ Create Presentation" -> "TAQDIMOT";
            case "📦 Fayllarni Zip qilish", "📦 Zip Files", "📦 Архивировать файлы (Zip)"   -> "ZIP";
            case "📄 Word <--> PDF"                                                          -> "WORD_PDF";
            case "📋 Test tayyorlash", "📋 Создать тест", "📋 Create Test"                  -> "TEST";
            case "📝 Referat tayyorlash", "📝 Создать реферат", "📝 Create Essay"           -> "REFERAT";
            case "💰 Balans", "💰 Баланс", "💰 Balance", "/balance"                          -> "BALANS";
            default -> null;
        };
    }

    private String buttonKeyToLabel(String key) {
        return switch (key) {
            case "PDF"      -> "📄 PDF yaratish";
            case "TAQDIMOT" -> "🎞️ Taqdimot yaratish";
            case "ZIP"      -> "📦 Fayllarni Zip qilish";
            case "WORD_PDF" -> "📄 Word ↔ PDF";
            case "TEST"     -> "📋 Test tayyorlash";
            case "REFERAT"  -> "📝 Referat tayyorlash";
            case "BALANS"   -> "💰 Balans";
            default         -> key;
        };
    }

    private void resetAdminFlow(Long chatId) {
        adminState.put(chatId, "IDLE");
        adminTargetButton.remove(chatId);
        adminTempVideoId.remove(chatId);
        adminTempName.remove(chatId);
    }
}