package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
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
        Long chatId = message.getChatId();
        String lang = (user.getLanguage() != null) ? user.getLanguage() : "UZ";

        try {
            String jsonData = message.getWebAppData().getData();
            Map<String, Object> data = objectMapper.readValue(jsonData, Map.class);

            String topic         = String.valueOf(data.getOrDefault("topic", "")).trim();
            String name          = String.valueOf(data.getOrDefault("name", user.getFirstName() != null ? user.getFirstName() : "Foydalanuvchi")).trim();
            String slideLanguage = String.valueOf(data.getOrDefault("language", "UZ"));
            int slideCount       = parseIntSafe(data.get("slideCount"), 10);
            String templateNum   = String.valueOf(data.getOrDefault("template", "1"));
            int cost             = parseIntSafe(data.get("tariff"), 3000);

            if (topic.isEmpty()) {
                sendError(bot, chatId, lang, "Mavzu kiritilishi shart!");
                return;
            }

            if (user.getBalance() == null || user.getBalance() < cost) {
                String errorMsg = switch (lang) {
                    case "RU" -> "Недостаточно средств на балансе! /buy";
                    case "ENG" -> "Insufficient balance! /buy";
                    default -> "Balansingizda mablag' yetarli emas! To'ldirish uchun: /buy";
                };
                sendError(bot, chatId, lang, errorMsg);
                return;
            }

            user.setTopic(topic);
            user.setUserInfo(name);
            user.setSlideCount(String.valueOf(slideCount));
            user.setTemplate(slideLanguage);
            user.setLastSelectedTemplateNumber(parseIntSafe(templateNum, 1));
            user.setBalance(user.getBalance() - cost);
            user.setState(UserState.CREATING_SLIDES);
            userRepository.save(user);

            sendWaitingMessage(bot, chatId, lang);

            new Thread(() -> {
                try {
                    List<GptTextGeneratorService.SlideData> slideDataList =
                            gptService.generateSlides(topic, slideCount, slideLanguage);

                    String pptxPath = pptxService.createPptx(
                            topic, name, "", slideDataList, templateNum
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
            sendError(bot, chatId, lang, "Ma'lumotlarni qayta ishlashda xatolik yuz berdi.");
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
        Long chatId = message.getChatId();
        String text = message.getText();

        if (user.getState() == UserState.WAITING_TOPIC) {
            user.setTopic(text);
            user.setState(UserState.WAITING_INFO);
            userRepository.save(user);

            send(bot, chatId, "Mavzu qabul qilindi. Endi qo'shimcha ma'lumot yuboring.");
        }
    }

    private void sendWaitingMessage(SlideBot bot, Long chatId, String lang) {
        String text = switch (lang) {
            case "RU" -> "🧠 ИИ создает презентацию с картинками... ⏳\nЭто займет около 1-2 минут.";
            case "ENG" -> "🧠 AI is generating your presentation with images... ⏳\nIt will take about 1-2 minutes.";
            default -> "🧠 Sun'iy intellekt rasmli taqdimot tayyorlamoqda... ⏳\n1-2 daqiqa vaqt olishi mumkin.";
        };
        send(bot, chatId, text);
    }

    private String getSuccessCaption(String lang, String topic, Integer balance) {
        return switch (lang) {
            case "RU" -> "✅ Готово!\n📊 Тема: " + topic + "\n💰 Баланс: " + balance + " сум";
            case "ENG" -> "✅ Finished!\n📊 Topic: " + topic + "\n💰 Balance: " + balance + " UZS";
            default -> "✅ Tayyor!\n📊 Mavzu: " + topic + "\n💰 Qolgan balans: " + balance + " so'm";
        };
    }

    private void handleCreationError(SlideBot bot, Long chatId, String lang, int cost, UserEntity user) {
        String msg = switch (lang) {
            case "RU" -> "Xatolik yuz berdi. Mablag' qaytarildi.";
            case "ENG" -> "Error occurred. Funds returned.";
            default -> "Slayd yaratishda xatolik yuz berdi. Mablag' balansingizga qaytarildi.";
        };
        sendError(bot, chatId, lang, msg);
        try {
            user.setBalance(user.getBalance() + cost);
            user.setState(UserState.START);
            userRepository.save(user);
        } catch (Exception ignored) {}
    }

    private void sendError(SlideBot bot, Long chatId, String lang, String msg) {
        send(bot, chatId, "❌ " + msg);
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
}