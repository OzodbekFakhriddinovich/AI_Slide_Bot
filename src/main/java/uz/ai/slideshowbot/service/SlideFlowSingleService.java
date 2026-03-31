package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.enums.UserState;
import uz.ai.slideshowbot.repository.UserRepository;

import java.io.File;
import java.util.List;
import java.util.Map;

@Service
public class SlideFlowSingleService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GptTextGeneratorService gptService;

    @Autowired
    private PptxCreatorService pptxService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public void handleWebAppData(UserEntity user, Message message, SlideBot bot) {
        Long   chatId = message.getChatId();
        String lang   = (user.getLanguage() != null) ? user.getLanguage() : "UZ";

        try {
            String              jsonData = message.getWebAppData().getData();
            Map<String, Object> data     = objectMapper.readValue(jsonData, Map.class);

            String topic         = String.valueOf(data.getOrDefault("topic", "")).trim();
            String name          = String.valueOf(data.getOrDefault("name",
                    user.getFirstName() != null ? user.getFirstName() : "Foydalanuvchi")).trim();
            String slideLanguage = String.valueOf(data.getOrDefault("language", "UZ"));
            int    slideCount    = parseIntSafe(data.get("slideCount"), 10);
            String templateNum   = String.valueOf(data.getOrDefault("template", "1"));
            int    cost          = parseIntSafe(data.get("tariff"), 3000);

            if (topic.isEmpty()) {
                sendError(bot, chatId, "Mavzu kiritilishi shart!");
                return;
            }

            if (user.getBalance() == null || user.getBalance() < cost) {
                String errorMsg = switch (lang) {
                    case "RU"  -> "Недостаточно средств на балансе! /buy";
                    case "ENG" -> "Insufficient balance! /buy";
                    default    -> "Balansingizda mablag' yetarli emas! /buy";
                };
                sendError(bot, chatId, errorMsg);
                return;
            }

            // ==================== BALANS VA PROGRESS BOSQICHI ====================
            user.setTopic(topic);
            user.setUserInfo(name);
            user.setSlideCount(String.valueOf(slideCount));
            user.setTemplate(slideLanguage);
            user.setLastSelectedTemplateNumber(parseIntSafe(templateNum, 1));
            user.setBalance(user.getBalance() - cost);
            user.setState(UserState.CREATING_SLIDES);
            userRepository.save(user);

            String balanceMsg = "💰 Balansingizdan " + cost + " so'm mablag' yechildi.";
            sendMessage(bot, chatId, balanceMsg);

            String waitingText = switch (lang) {
                case "RU"  -> "⏳ Пожалуйста, подождите, мы готовим вашу презентацию!";
                case "ENG" -> "⏳ Please wait, we are preparing your presentation!";
                default    -> "⏳ Iltimos kuting, biz sizning taqdimotingizni tayyorlamoqdamiz!";
            };
            sendMessage(bot, chatId, waitingText);

            Message progressMessage = sendProgressBar(bot, chatId);

            new Thread(() -> {
                try {
                    simulateProgress(bot, progressMessage, 120);

                    List<GptTextGeneratorService.Section> sections =
                            gptService.generateSections(topic, slideCount, slideLanguage, cost);

                    String pptxPath = pptxService.createPptxFromSections(
                            topic, name, "", sections, templateNum
                    );

                    File file = new File(pptxPath);
                    if (file.exists()) {
                        SendDocument doc = new SendDocument();
                        doc.setChatId(chatId.toString());
                        doc.setDocument(new InputFile(file));
                        doc.setCaption(getSuccessCaption(lang, topic, user.getBalance()));
                        bot.execute(doc);
                        file.delete();
                    }

                    user.setState(UserState.START);
                    userRepository.save(user);

                } catch (Exception e) {
                    e.printStackTrace();
                    handleCreationError(bot, chatId, lang, cost, user);
                }
            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            sendError(bot, chatId, "Ma'lumotlarni qayta ishlashda xatolik yuz berdi.");
        }
    }


    private Message sendProgressBar(SlideBot bot, Long chatId) {
        String text = "Yuklanmoqda ... 0%";
        SendMessage msg = new SendMessage(chatId.toString(), text);
        try {
            return bot.execute(msg);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void simulateProgress(SlideBot bot, Message progressMsg, int totalSeconds) {
        if (progressMsg == null) return;

        int steps = 24;
        int delay = (totalSeconds * 1000) / steps;

        for (int i = 1; i <= steps; i++) {
            int percent = Math.min(100, i * (100 / steps));
            String bar = "█".repeat(i) + "░".repeat(steps - i);
            String text = "Yuklanmoqda ... " + percent + "%\n" + bar;

            try {
                EditMessageText edit = new EditMessageText();
                edit.setChatId(progressMsg.getChatId().toString());
                edit.setMessageId(progressMsg.getMessageId());
                edit.setText(text);
                bot.execute(edit);
            } catch (Exception ignored) {}

            try { Thread.sleep(delay); } catch (InterruptedException ignored) {}
        }

        try {
            bot.execute(new DeleteMessage(progressMsg.getChatId().toString(), progressMsg.getMessageId()));
        } catch (Exception ignored) {}
    }

    private String getSuccessCaption(String lang, String topic, Integer balance) {
        return switch (lang) {
            case "RU"  -> "📕 Ваша презентация готова!\n\n📌 Tема: " + topic + "\n\nБот для подготовки презентаций и PDF-файлов: @AI_Slide_Assistant_Bot";
            case "ENG" -> "📕 Your presentation is ready!\n\n📌 Topic: " + topic + "\n\nPresentation and PDF bot: @AI_Slide_Assistant_Bot";
            default    -> "📕 Sizning taqdimotingiz tayyor!\n\n📌 Mavzu: " + topic + "\n\nTaqdimot va pdf tayyorlovchi bot: @AI_Slide_Assistant_Bot";
        };
    }

    private void handleCreationError(SlideBot bot, Long chatId, String lang, int cost, UserEntity user) {
        String msg = switch (lang) {
            case "RU"  -> "Произошла ошибка. Средства возвращены.";
            case "ENG" -> "Error occurred. Funds returned.";
            default    -> "Xatolik yuz berdi. Mablag' balansingizga qaytarildi.";
        };
        sendError(bot, chatId, msg);

        try {
            user.setBalance(user.getBalance() + cost);
            user.setState(UserState.START);
            userRepository.save(user);
        } catch (Exception ignored) {}
    }

    private void sendError(SlideBot bot, Long chatId, String msg) {
        send(bot, chatId, "❌ " + msg);
    }

    private void sendMessage(SlideBot bot, Long chatId, String text) {
        try {
            bot.execute(new SendMessage(chatId.toString(), text));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void send(SlideBot bot, Long chatId, String text) {
        try {
            bot.execute(new SendMessage(chatId.toString(), text));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private int parseIntSafe(Object val, int defaultVal) {
        if (val == null) return defaultVal;
        try {
            return Integer.parseInt(String.valueOf(val));
        } catch (Exception e) {
            return defaultVal;
        }
    }

    public void handleCallback(UserEntity user, CallbackQuery query, SlideBot bot) {
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(query.getId());
            answer.setText("Qabul qilindi");
            bot.execute(answer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleMessage(UserEntity user, Message message, SlideBot bot) {
    }
}