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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SlideFlowSingleService {

    @Autowired private UserRepository userRepository;
    @Autowired private GptTextGeneratorService gptService;
    @Autowired private PptxCreatorService pptxService;

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
                sendMsg(bot, chatId, "❌ Mavzu kiritilishi shart!");
                return;
            }

            if (user.getBalance() == null || user.getBalance() < cost) {
                sendMsg(bot, chatId, switch (lang) {
                    case "RU"  -> "❌ Недостаточно средств! /buy";
                    case "ENG" -> "❌ Insufficient balance! /buy";
                    default    -> "❌ Balansingizda mablag' yetarli emas! /buy";
                });
                return;
            }

            user.setBalance(user.getBalance() - cost);
            user.setState(UserState.CREATING_SLIDES);
            userRepository.save(user);

            sendMsg(bot, chatId, "💰 Balansingizdan " + cost + " so'm yechildi.");
            sendMsg(bot, chatId, switch (lang) {
                case "RU"  -> "⏳ Подождите! Готовим презентацию...";
                case "ENG" -> "⏳ Please wait! Preparing presentation...";
                default    -> "⏳ Kuting! Taqdimot tayyorlanmoqda...";
            });

            Message progressMsg = sendProgressBar(bot, chatId);

            new Thread(() -> {
                CountDownLatch               latch      = new CountDownLatch(1);
                AtomicReference<String>      pptxRef    = new AtomicReference<>(null);
                AtomicReference<Exception>   errRef     = new AtomicReference<>(null);

                Thread progressThread = new Thread(() -> {
                    final int TOTAL_SECONDS = 60;
                    final int STEPS = 30;
                    final int DELAY_MS = TOTAL_SECONDS * 1000 / STEPS;

                    String label = switch (lang) {
                        case "RU"  -> "⏳ Taqdimot tayyorlanmoqda...";
                        case "ENG" -> "⏳ Preparing presentation...";
                        default    -> "⏳ Taqdimot tayyorlanmoqda...";
                    };

                    for (int i = 1; i <= STEPS; i++) {
                        if (latch.getCount() == 0) break;

                        int percent = (i * 100) / STEPS;
                        String bar = "█".repeat(Math.min(20, i * 20 / STEPS))
                                + "░".repeat(20 - Math.min(20, i * 20 / STEPS));

                        try {
                            EditMessageText e = new EditMessageText();
                            e.setChatId(chatId.toString());
                            e.setMessageId(progressMsg.getMessageId());
                            e.setText(label + "\n" + bar + " " + percent + "%");
                            bot.execute(e);
                        } catch (Exception ignored) {}

                        try { Thread.sleep(DELAY_MS); }
                        catch (InterruptedException ex) { break; }
                    }

                    try {
                        bot.execute(new DeleteMessage(chatId.toString(), progressMsg.getMessageId()));
                    } catch (Exception ignored) {}
                });

                Thread workThread = new Thread(() -> {
                    try {
                        List<GptTextGeneratorService.Section> sections =
                                gptService.generateSections(topic, slideCount, slideLanguage, cost);
                        String path = pptxService.createPptxFromSections(
                                topic, name, "", sections, templateNum);
                        pptxRef.set(path);
                    } catch (Exception e) {
                        errRef.set(e);
                        e.printStackTrace();
                    } finally {
                        latch.countDown();
                        progressThread.interrupt();
                    }
                });

                progressThread.setDaemon(true);
                progressThread.start();
                workThread.start();

                try { latch.await(); } catch (InterruptedException ignored) {}
                try { progressThread.join(3000); } catch (InterruptedException ignored) {}

                if (errRef.get() != null) {
                    sendMsg(bot, chatId, switch (lang) {
                        case "RU"  -> "❌ Произошла ошибка. Средства возвращены.";
                        case "ENG" -> "❌ Error occurred. Funds returned.";
                        default    -> "❌ Xatolik yuz berdi. Mablag' qaytarildi.";
                    });
                    user.setBalance(user.getBalance() + cost);
                } else {
                    String path = pptxRef.get();
                    if (path != null) {
                        File file = new File(path);
                        if (file.exists()) {
                            try {
                                SendDocument doc = new SendDocument();
                                doc.setChatId(chatId.toString());
                                doc.setDocument(new InputFile(file));
                                doc.setCaption(switch (lang) {
                                    case "RU"  -> "📕 Ваша презентация готова!\n\n📌 Тема: " + topic + "\n\nБот для презентаций и PDF: @AI_Slide_Assistant_Bot";
                                    case "ENG" -> "📕 Your presentation is ready!\n\n📌 Topic: " + topic + "\n\nPresentation and PDF bot: @AI_Slide_Assistant_Bot";
                                    default    -> "📕 Sizning taqdimotingiz tayyor!\n\n📌 Mavzu: " + topic + "\n\nTaqdimot va pdf tayyorlovchi bot: @AI_Slide_Assistant_Bot";
                                });
                                bot.execute(doc);
                                file.delete();
                            } catch (Exception e) {
                                e.printStackTrace();
                                sendMsg(bot, chatId, "❌ Fayl yuborishda xatolik.");
                                user.setBalance(user.getBalance() + cost);
                            }
                        }
                    }
                }

                user.setState(UserState.START);
                userRepository.save(user);

            }).start();

        } catch (Exception e) {
            e.printStackTrace();
            sendMsg(bot, chatId, "❌ Ma'lumotlarni qayta ishlashda xatolik.");
        }
    }

    private Message sendProgressBar(SlideBot bot, Long chatId) {
        try {
            return bot.execute(new SendMessage(chatId.toString(),
                    "⏳ Yuklanmoqda ... 0%\n░░░░░░░░░░░░░░░░░░░░"));
        } catch (Exception e) { return null; }
    }

    private void sendMsg(SlideBot bot, Long chatId, String text) {
        try { bot.execute(new SendMessage(chatId.toString(), text)); }
        catch (Exception e) { e.printStackTrace(); }
    }

    private int parseIntSafe(Object val, int def) {
        if (val==null) return def;
        try { return Integer.parseInt(String.valueOf(val)); } catch (Exception e) { return def; }
    }

    public void handleCallback(UserEntity user, CallbackQuery query, SlideBot bot) {
        try {
            AnswerCallbackQuery a = new AnswerCallbackQuery();
            a.setCallbackQueryId(query.getId()); a.setText("Qabul qilindi");
            bot.execute(a);
        } catch (Exception ignored) {}
    }

    public void handleMessage(UserEntity user, Message message, SlideBot bot) {}
}