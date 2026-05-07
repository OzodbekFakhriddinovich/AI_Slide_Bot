package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.enums.UserState;
import uz.ai.slideshowbot.repository.UserRepository;

@Service
public class ReferatService {

    @Autowired private UserRepository userRepository;
    @Autowired private ReferatDocxService referatDocxService;
    @Autowired private TelegramRawSender rawSender;
    @Autowired private MenuService menuService;

    private static final int PRICE = 5_000;

    public void startFlow(SlideBot bot, UserEntity user, Long chatId) {
        resetFlow(user);
        user.setInReferatFlow(true);
        user.setState(UserState.WAITING_REFERAT_TOPIC);
        userRepository.save(user);

        String lang = lang(user);
        String text = switch (lang) {
            case "RU" -> """
                    🧪 <b>Напишите тему реферата</b>

                    Пример: <i>"Гуманистические идеи в поэзии Алишера Навои"</i>

                    💰 Цена: <b>5 000 сум</b>
                    📝 Тема: от 10 символов""";
            case "ENG" -> """
                    🧪 <b>Write the essay topic</b>

                    Example: <i>"Humanistic ideas in the poetry of Alisher Navoi"</i>

                    💰 Price: <b>5,000 UZS</b>
                    📝 Topic: from 10 characters""";
            default -> """
                    🧪 <b>Referat mavzusini yozing</b>

                    Namuna: <i>"Alisher Navoiy she'riyatida insonparvarlik g'oyalari"</i>

                    💰 Narxi: <b>5 000 so'm</b>
                    📝 Mavzu: 10 ta belgidan ko'p""";
        };
        rawSender.sendWithColoredBtn(bot, chatId.toString(), text, backBtn(lang), "danger");
    }

    public void handleTopicInput(SlideBot bot, UserEntity user, Long chatId, String text) {
        String lang = lang(user);
        if (text.length() < 10) {
            String err = switch (lang) {
                case "RU"  -> "❌ Тема слишком короткая! Минимум 10 символов. Попробуйте снова.";
                case "ENG" -> "❌ Topic is too short! Minimum 10 characters. Try again.";
                default    -> "❌ Mavzu juda qisqa! Kamida 10 ta belgi kiriting.";
            };
            rawSender.sendWithColoredBtn(bot, chatId.toString(), err, backBtn(lang), "danger");
            return;
        }
        user.setReferatTopic(text.trim());
        user.setState(UserState.WAITING_REFERAT_ISM);
        userRepository.save(user);

        String ask = switch (lang) {
            case "RU"  -> "👤 <b>Введите ваше Имя и Фамилию:</b>\n\nПример: <i>Иванов Иван Иванович</i>";
            case "ENG" -> "👤 <b>Enter your Full Name:</b>\n\nExample: <i>John Smith</i>";
            default    -> "👤 <b>Ism va familyangizni kiriting:</b>\n\nNamuna: <i>Jalilov Ozodbek Faxriddinovich</i>";
        };
        rawSender.sendWithColoredBtn(bot, chatId.toString(), ask, backBtn(lang), "danger");
    }

    public void handleIsmInput(SlideBot bot, UserEntity user, Long chatId, String text) {
        String lang = lang(user);
        if (text.trim().length() < 3) {
            String err = switch (lang) {
                case "RU"  -> "❌ Слишком короткое. Введите полное имя и фамилию.";
                case "ENG" -> "❌ Too short. Enter your full name.";
                default    -> "❌ Juda qisqa. To'liq ism familyangizni kiriting.";
            };
            rawSender.sendWithColoredBtn(bot, chatId.toString(), err, backBtn(lang), "danger");
            return;
        }
        user.setReferatIsm(text.trim());
        user.setState(UserState.WAITING_REFERAT_SAHIFA);
        userRepository.save(user);

        String title = switch (lang) {
            case "RU"  -> "📊 Выберите количество страниц:";
            case "ENG" -> "📊 Select number of pages:";
            default    -> "📊 Necha sahifalik bo'lsin?";
        };
        String markup = """
                {"inline_keyboard":[[
                  {"text":"📄 15 sahifa","callback_data":"REF_SAHIFA_15"},
                  {"text":"📄 20 sahifa","callback_data":"REF_SAHIFA_20"},
                  {"text":"📄 25 sahifa","callback_data":"REF_SAHIFA_25"}
                ]]}""";
        rawSender.sendInlineMessage(bot, chatId.toString(), title, markup);
    }

    public void handleSahifaCallback(SlideBot bot, UserEntity user, CallbackQuery query) {
        String data   = query.getData();
        Long chatId   = query.getMessage().getChatId();
        Integer msgId = query.getMessage().getMessageId();
        String lang   = lang(user);

        int sahifa = switch (data) {
            case "REF_SAHIFA_15" -> 15;
            case "REF_SAHIFA_20" -> 20;
            default              -> 25;
        };

        user.setReferatSahifa(sahifa);
        userRepository.save(user);
        rawSender.deleteMessageById(bot, chatId.toString(), msgId);

        String langLabel = switch (lang) {
            case "RU"  -> "🇷🇺 <b>русском</b>";
            case "ENG" -> "🇺🇸 <b>English</b>";
            default    -> "🇺🇿 <b>o'zbek</b>";
        };
        String confirmText = switch (lang) {
            case "RU"  -> "ℹ️ Данный реферат будет подготовлен на " + langLabel + " языке.\n\nНажмите кнопку ниже для подтверждения:";
            case "ENG" -> "ℹ️ This essay will be prepared in " + langLabel + ".\n\nPress the button below to confirm:";
            default    -> "ℹ️ Bu Referat " + langLabel + " tilida tayyorlanadi.\n\nTasdiqlash uchun quyidagi tugmani bosing:";
        };
        String confirmBtn = switch (lang) {
            case "RU"  -> "✅ Подтвердить";
            case "ENG" -> "✅ Confirm";
            default    -> "✅ Tasdiqlash";
        };
        String markup = "{\"inline_keyboard\":[[{\"text\":\"%s\",\"callback_data\":\"REF_CONFIRM\"}]]}"
                .formatted(rawSender.esc(confirmBtn));
        rawSender.sendInlineMessage(bot, chatId.toString(), confirmText, markup);
    }

    public void handleConfirmCallback(SlideBot bot, UserEntity user, CallbackQuery query) {
        Long chatId   = query.getMessage().getChatId();
        Integer msgId = query.getMessage().getMessageId();
        String lang   = lang(user);

        rawSender.deleteMessageById(bot, chatId.toString(), msgId);

        int balance = user.getBalance() != null ? user.getBalance() : 0;
        if (balance < PRICE) {
            resetFlow(user);

            String err = switch (lang) {
                case "RU"  -> "❌ Недостаточно средств!\n\n💰 Ваш баланс: <b>" + balance + " сум</b>\n💳 Нужно: <b>5 000 сум</b>";
                case "ENG" -> "❌ Insufficient balance!\n\n💰 Your balance: <b>" + balance + " UZS</b>\n💳 Required: <b>5,000 UZS</b>";
                default    -> "❌ Balansingiz yetarli emas!\n\n💰 Balansingiz: <b>" + balance + " so'm</b>\n💳 Kerak: <b>5 000 so'm</b>";
            };
            String buyBtn = switch (lang) {
                case "RU" -> "💳 Пополнить баланс"; case "ENG" -> "💳 Top up balance"; default -> "💳 Balansni to'ldirish";
            };
            String markup = "{\"inline_keyboard\":[[{\"text\":\"%s\",\"callback_data\":\"BALANCE_PAY\",\"style\":\"success\"},{\"text\":\"%s\",\"callback_data\":\"REFERAT_MENU_BACK\"}]]}"
                    .formatted(rawSender.esc(buyBtn), rawSender.esc(backBtn(lang)));
            rawSender.sendInlineMessage(bot, chatId.toString(), err, markup);
            return;
        }

        String mavzu  = user.getReferatTopic();
        String ism    = user.getReferatIsm();
        int    sahifa = user.getReferatSahifa() != null ? user.getReferatSahifa() : 15;

        user.setBalance(balance - PRICE);
        user.setInReferatFlow(false);
        user.setState(UserState.START);
        userRepository.save(user);

        String deductMsg = switch (lang) {
            case "RU"  -> "💸 С вашего счёта списано <b>5 000 сум</b>.";
            case "ENG" -> "💸 <b>5,000 UZS</b> deducted from your balance.";
            default    -> "💸 Hisobingizdan <b>5 000 so'm</b> yechildi.";
        };
        rawSender.sendSimpleMessage(bot, chatId.toString(), deductMsg);

        String progressLabel = switch (lang) {
            case "RU"  -> "⏳ Реферат готовится...";
            case "ENG" -> "⏳ Preparing your essay...";
            default    -> "⏳ Referat tayyorlanmoqda...";
        };
        Integer progressMsgId = rawSender.sendSimpleMessage(bot, chatId.toString(),
                progressLabel + "\n" + bar(0));

        new Thread(() -> doCreate(bot, chatId, lang, mavzu, ism, sahifa, progressMsgId)).start();
    }

    private void doCreate(SlideBot bot, Long chatId, String lang,
                          String mavzu, String ism, int sahifa, Integer progressMsgId) {
        Thread progressThread = startProgressThread(bot, chatId, progressMsgId, lang);
        try {
            byte[] docBytes = referatDocxService.generate(mavzu, ism, sahifa, lang);
            progressThread.interrupt();
            Thread.sleep(200);
            if (progressMsgId != null) rawSender.deleteMessageById(bot, chatId.toString(), progressMsgId);

            String caption = switch (lang) {
                case "RU"  -> "✅ <b>Реферат готов!</b>\n\n📌 Тема: " + mavzu + "\n✍️ Автор: " + ism + "\n\nБот: @AI_Slide_Assistant_Bot";
                case "ENG" -> "✅ <b>Essay is ready!</b>\n\n📌 Topic: " + mavzu + "\n✍️ Author: " + ism + "\n\nBot: @AI_Slide_Assistant_Bot";
                default    -> "✅ <b>Referat tayyor!</b>\n\n📌 Mavzu: " + mavzu + "\n✍️ Bajardi: " + ism + "\n\nBot: @AI_Slide_Assistant_Bot";
            };
            String shareUrl = "https://t.me/share/url?url=" +
                              java.net.URLEncoder.encode("https://t.me/AI_Slide_Assistant_Bot", java.nio.charset.StandardCharsets.UTF_8);
            String shareBtn = switch (lang) {
                case "RU" -> "📤 Поделиться"; case "ENG" -> "📤 Share"; default -> "📤 Ulashish";
            };
            String safeName = "Referat_" + mavzu.replaceAll("[^a-zA-Z0-9\\u0400-\\u04FF\\u0041-\\u005A]", "_")
                    .substring(0, Math.min(mavzu.length(), 30)) + ".docx";

            rawSender.sendDocxFileWithShareBtn(bot, chatId.toString(), docBytes, safeName, caption, shareBtn, shareUrl);
            menuService.sendSimpleMenu(bot, chatId, lang);

        } catch (Exception e) {
            e.printStackTrace();
            progressThread.interrupt();
            if (progressMsgId != null) rawSender.deleteMessageById(bot, chatId.toString(), progressMsgId);
            userRepository.findByChatId(chatId).ifPresent(u -> {
                u.setBalance((u.getBalance() != null ? u.getBalance() : 0) + PRICE);
                userRepository.save(u);
            });
            String errMsg = switch (lang) {
                case "RU"  -> "❌ Ошибка при создании реферата. <b>5 000 сум</b> возвращены.";
                case "ENG" -> "❌ Error creating essay. <b>5,000 UZS</b> returned to balance.";
                default    -> "❌ Referat yaratishda xatolik. <b>5 000 so'm</b> qaytarildi.";
            };
            rawSender.sendSimpleMessage(bot, chatId.toString(), errMsg);
        }
    }

    private Thread startProgressThread(SlideBot bot, Long chatId, Integer msgId, String lang) {
        if (msgId == null) return new Thread(() -> {});

        Thread t = new Thread(() -> {
            final int TOTAL_SECONDS = 45;
            final int STEPS = 30;
            final int DELAY_MS = TOTAL_SECONDS * 1000 / STEPS;

            String label = switch (lang) {
                case "RU"  -> "⏳ Реферат готовится...";
                case "ENG" -> "⏳ Creating essay...";
                default    -> "⏳ Referat tayyorlanmoqda...";
            };

            for (int i = 1; i <= STEPS; i++) {
                if (Thread.currentThread().isInterrupted()) break;

                int pct = (i * 100) / STEPS;
                String bar = "█".repeat(Math.min(20, i * 20 / STEPS))
                             + "░".repeat(20 - Math.min(20, i * 20 / STEPS));

                try {
                    rawSender.editMessageText(bot, chatId.toString(), msgId,
                            label + "\n" + bar + " " + pct + "%");
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {}
            }

            try {
                rawSender.deleteMessageById(bot, chatId.toString(), msgId);
            } catch (Exception ignored) {}
        });

        t.setDaemon(true);
        t.start();
        return t;
    }

    private String bar(int pct) {
        pct = Math.max(0, Math.min(100, pct));
        int f = pct / 5;
        return "█".repeat(f) + "░".repeat(20 - f) + " " + pct + "%";
    }

    public void resetFlow(UserEntity user) {
        if (user == null) return;
        user.setInReferatFlow(false);
        user.setReferatTopic(null);
        user.setReferatIsm(null);
        user.setReferatSahifa(null);
        if (user.getState() == UserState.WAITING_REFERAT_TOPIC
            || user.getState() == UserState.WAITING_REFERAT_ISM
            || user.getState() == UserState.WAITING_REFERAT_SAHIFA
            || user.getState() == UserState.CREATING_REFERAT) {
            user.setState(UserState.START);
        }
        userRepository.save(user);
    }

    private String lang(UserEntity u) { return u.getLanguage() != null ? u.getLanguage() : "UZ"; }
    private String backBtn(String lang) {
        return switch (lang) { case "RU" -> "◀️ Назад"; case "ENG" -> "◀️ Back"; default -> "◀️ Orqaga"; };
    }
}