package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardRemove;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.FileEntity;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.FileRepository;
import uz.ai.slideshowbot.repository.UserRepository;

import java.io.InputStream;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class BalanceService {

    @Autowired private UserRepository userRepository;
    @Autowired private FileRepository fileRepository;
    @Autowired private TelegramRawSender rawSender;

    @Value("${payment.humo-card}")
    private String humoCard;

    private static final String HUMO_CARD_OWNER = "JALILOV OZODBEK";
    private static final String UZCARD_CARD     = "5614 6887 0893 0291";
    private static final String UZCARD_OWNER    = "R. S";

    // To'lov tugmalari summalari
    private static final int[] AMOUNTS = {6000, 7000, 8000, 9000, 10000, 15000, 20000, 50000};

    private String lang(UserEntity user) {
        return (user.getLanguage() != null) ? user.getLanguage() : "UZ";
    }

    // =====================================================================
    // 1. BALANSNI KO'RSATISH — 1 ta xabar + reply keyboard summalar
    // =====================================================================
    public void showBalance(SlideBot bot, UserEntity user, Long chatId, Integer lastMessageId) {
        if (user.getBalance() == null) user.setBalance(0);
        userRepository.save(user);

        String language = lang(user);

        // Eski balance xabarini o'chirish
        if (lastMessageId != null) {
            try { bot.execute(new DeleteMessage(chatId.toString(), lastMessageId)); } catch (Exception ignored) {}
        }

        String basicText = switch (language) {
            case "RU" -> "💰 <b>Ваш баланс:</b> " + user.getBalance() + " сум\n" +
                    "/buy - пополнить баланс через оплату 💵\n" +
                    "/referal - пригласить друзей и пополнить баланс 🔗";
            case "ENG" -> "💰 <b>Your balance:</b> " + user.getBalance() + " UZS\n" +
                    "/buy - top up balance via payment 💵\n" +
                    "/referal - invite friends to top up balance 🔗";
            default -> "💰 <b>Balansingiz:</b> " + user.getBalance() + " so'm\n" +
                    "/buy - to'lov orqali balansni to'ldirish 💵\n" +
                    "/referal - do'stlarni taklif qilib balansni to'ldirish 🔗";
        };

        String btnText = switch (language) {
            case "RU" -> "💳 Оплатить";
            case "ENG" -> "💳 Pay";
            default -> "💳 To'lov qilish";
        };

        rawSender.sendMessageWithStyledCallbackBtn(
                bot,
                chatId.toString(),
                basicText,
                btnText,
                "BALANCE_PAY",
                "success"
        );
    }

    public void showPaymentAmounts(SlideBot bot, UserEntity user, Long chatId, Integer lastMessageId) {
        String language = lang(user);

        if (lastMessageId != null) {
            try { bot.execute(new DeleteMessage(chatId.toString(), lastMessageId)); } catch (Exception ignored) {}
        }

        String text = buildBalanceText(user, language);

        Integer sentMsgId = rawSender.sendInlineMessage(bot, chatId.toString(), text, buildAmountsKeyboardRaw(language));
        if (sentMsgId != null) {
            user.setLastBalanceMessageId(sentMsgId);
            userRepository.save(user);
        }
    }

    private String buildBalanceText(UserEntity user, String lang) {
        return switch (lang) {
            case "RU" -> "💰 <b>Ваш баланс:</b> " + user.getBalance() + " сум\n\n" +
                    "📕 <b>Цены:</b>\n" +
                    "• Презентация — тарифы 5 000 / 7 000 сум\n" +
                    "• 10 / 15 / 20 слайдов, PPTX\n" +
                    "• Тест — 5 000 сум\n" +
                    "• Реферат — 5 000 сум\n\n" +
                    "👇 Выберите сумму пополнения:";
            case "ENG" -> "💰 <b>Your balance:</b> " + user.getBalance() + " UZS\n\n" +
                    "📕 <b>Prices:</b>\n" +
                    "• Presentation — plans 5 000 / 7 000 UZS\n" +
                    "• 10 / 15 / 20 slides, PPTX\n" +
                    "• Test — 5 000 UZS\n" +
                    "• Essay — 5 000 UZS\n\n" +
                    "👇 Select top-up amount:";
            default -> "💰 <b>Balansingiz:</b> " + user.getBalance() + " so'm\n\n" +
                    "📕 <b>Narxlar:</b>\n" +
                    "• Taqdimot — tariflar 5 000 / 7 000 so'm\n" +
                    "• 10 / 15 / 20 slayd, PPTX\n" +
                    "• Test — 5 000 so'm\n" +
                    "• Referat — 5 000 so'm\n\n" +
                    "👇 To'ldirish summasini tanlang:";
        };
    }

    private String buildAmountsKeyboardRaw(String lang) {
        String suffix = switch (lang) {
            case "RU" -> " сум";
            case "ENG" -> " UZS";
            default -> " so'm";
        };

        return """
        {
          "keyboard": [
            [{"text": "6 000%1$s", "style": "primary"}, {"text": "7 000%1$s", "style": "primary"}, {"text": "8 000%1$s", "style": "primary"}],
            [{"text": "9 000%1$s", "style": "primary"}, {"text": "10 000%1$s", "style": "primary"}, {"text": "15 000%1$s", "style": "primary"}],
            [{"text": "20 000%1$s", "style": "primary"}, {"text": "50 000%1$s", "style": "primary"}]
          ],
          "resize_keyboard": true,
          "one_time_keyboard": true
        }
        """.formatted(suffix).strip();
    }

    // =====================================================================
    // 2. SUMMA TANLANGANDA — avto/bank tanlov inline tugmalar
    // =====================================================================
    public void handleAmountSelection(SlideBot bot, UserEntity user, Long chatId, int baseAmount, Integer userMessageId) {
        String language = lang(user);

        // 1. Foydalanuvchining reply tugma bosgandagi xabarini o'chirish
        if (userMessageId != null) {
            try { bot.execute(new DeleteMessage(chatId.toString(), userMessageId)); } catch (Exception ignored) {}
        }

        // 2. Keyboard'ni yo'qotish uchun kichik xabar, keyin o'chiramiz
        Integer removeKeyMsgId = null;
        try {
            SendMessage removeMsg = new SendMessage(chatId.toString(), "⌛");
            removeMsg.setReplyMarkup(new ReplyKeyboardRemove(true));
            Message removed = bot.execute(removeMsg);
            removeKeyMsgId = removed.getMessageId();
        } catch (Exception ignored) {}

        // 3. Keyboard'ni o'chirish xabarini ham o'chiramiz
        if (removeKeyMsgId != null) {
            try { bot.execute(new DeleteMessage(chatId.toString(), removeKeyMsgId)); } catch (Exception ignored) {}
        }

        // 4. Avto/Bank tanlov xabarini yuborish
        String amountFormatted = formatAmount(baseAmount) + " " + (language.equals("RU") ? "сум" : language.equals("ENG") ? "UZS" : "so'm");

        String mainText = switch (language) {
            case "RU" -> "💳 <b>Пополнение баланса на " + amountFormatted + "</b>\n\n" +
                    "Выберите способ оплаты:";
            case "ENG" -> "💳 <b>Top up balance: " + amountFormatted + "</b>\n\n" +
                    "Choose payment method:";
            default -> "💳 <b>" + amountFormatted + " to'lov usulini tanlang:</b>";
        };

        String quoteText = switch (language) {
            case "RU" -> "⚡ <b>Авто-оплата:</b> ваш баланс пополнится автоматически в течение 1 секунды после перевода!\n" +
                    "💳 <b>Банковская карта:</b> отправьте чек, и администратор вручную пополнит ваш баланс.";
            case "ENG" -> "⚡ <b>Auto payment:</b> your balance will be topped up automatically within 1 second after transfer!\n" +
                    "💳 <b>Bank card:</b> send the receipt, admin will top up your balance manually.";
            default -> "⚡ <b>Avto to'lov:</b> pul o'tkazilishi bilan balansingiz 1 soniyada avtomatik to'ldiriladi!\n" +
                    "💳 <b>Bank kartasi:</b> chek yuborsangiz, admin qo'lda balansni to'ldiradi.";
        };

        String fullText = mainText + "\n\n<blockquote>" + quoteText + "</blockquote>";

        String autoBtnText = switch (language) {
            case "RU" -> "⚡ Авто-оплата";
            case "ENG" -> "⚡ Auto payment";
            default -> "⚡ Avto to'lov";
        };
        String bankBtnText = switch (language) {
            case "RU" -> "💳 Оплата через банк";
            case "ENG" -> "💳 Bank card payment";
            default -> "💳 Bank kartasi orqali";
        };

        String inlineJson = """
            {"inline_keyboard":[
              [{"text":"%s","callback_data":"PAY_AUTO_%d","style":"primary"}],
              [{"text":"%s","callback_data":"PAY_BANK_%d","style":"success"}]
            ]}
            """.formatted(
                rawSender.esc(autoBtnText), baseAmount,
                rawSender.esc(bankBtnText), baseAmount
        );

        rawSender.sendInlineMessage(bot, chatId.toString(), fullText, inlineJson);
    }

    // =====================================================================
    // 3a. AVTO TO'LOV — unique summa topib ko'rsatish
    // =====================================================================
    public void handleAutoPayment(SlideBot bot, UserEntity user, Long chatId, int baseAmount, Integer callbackMsgId) {
        String language = lang(user);

        // Unique summa topish (base + 10, 20... 500 gacha, 4 daqiqa ichida band emas)
        LocalDateTime fourMinAgo = LocalDateTime.now().minusMinutes(4);
        int pendingAmount = -1;
        for (int i = 1; i <= 50; i++) {
            int candidate = baseAmount + (i * 10);
            if (!userRepository.existsByPendingAmountAndPendingTimeAfter(candidate, fourMinAgo)) {
                pendingAmount = candidate;
                break;
            }
        }

        if (pendingAmount == -1) {
            String busyMsg = switch (language) {
                case "RU" -> "⚠️ Система занята. Пожалуйста, попробуйте через 4 минуты.";
                case "ENG" -> "⚠️ System is busy. Please try again in 4 minutes.";
                default -> "⚠️ Tizim band. Iltimos, 4 daqiqadan so'ng urinib ko'ring.";
            };
            try {
                org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery ans =
                        new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
                // Note: queryId not available here, sending message instead
                bot.execute(new SendMessage(chatId.toString(), busyMsg));
            } catch (Exception ignored) {}
            return;
        }

        // Foydalanuvchi pending ma'lumotlarini saqlash
        user.setPendingAmount(pendingAmount);
        user.setPendingTime(LocalDateTime.now());
        user.setPendingBaseAmount(baseAmount);
        userRepository.save(user);

        // Xabar matni
        String sumSuffix = switch (language) {
            case "RU" -> " сум";
            case "ENG" -> " UZS";
            default -> " so'm";
        };

        String mainText = switch (language) {
            case "RU" -> "💳 <b>Пополнение баланса (Авто-оплата)</b>\n\n" +
                    "💰 <b>Сумма перевода:</b> <code>" + pendingAmount + "</code>" + sumSuffix + " <i>(нажмите чтобы скопировать)</i>\n" +
                    "🎁 Бонус: +0 сум\n\n" +
                    "🔢 <b>Номер карты:</b> <code>" + humoCard + "</code>\n" +
                    "👤 <b>Владелец:</b> <code>" + HUMO_CARD_OWNER + "</code>\n\n" +
                    "<blockquote>⚠️ <b>ВНИМАНИЕ:</b> Переведите ровно <b>" + pendingAmount + "</b>" + sumSuffix + " (ни на 1 сум меньше или больше)!\n" +
                    "⚡ После оплаты баланс пополнится автоматически за 1 секунду.\n\n" +
                    "⏳ У вас есть <b>4 минуты</b>. Если не оплатите — сумма будет отменена!</blockquote>";
            case "ENG" -> "💳 <b>Balance Top-up (Auto payment)</b>\n\n" +
                    "💰 <b>Transfer amount:</b> <code>" + pendingAmount + "</code>" + sumSuffix + " <i>(tap to copy)</i>\n" +
                    "🎁 Bonus: +0 UZS\n\n" +
                    "🔢 <b>Card number:</b> <code>" + humoCard + "</code>\n" +
                    "👤 <b>Card owner:</b> <code>" + HUMO_CARD_OWNER + "</code>\n\n" +
                    "<blockquote>⚠️ <b>ATTENTION:</b> Transfer exactly <b>" + pendingAmount + "</b>" + sumSuffix + " (not 1 UZS less or more)!\n" +
                    "⚡ Balance will be topped up automatically within 1 second after payment.\n\n" +
                    "⏳ You have <b>4 minutes</b>. If you don't pay — the amount will be cancelled!</blockquote>";
            default -> "💳 <b>Balans to'ldirish (Avto-to'lov)</b>\n\n" +
                    "💰 <b>O'tkazish summasi:</b> <code>" + pendingAmount + "</code>" + sumSuffix + " <i>(nusxalash uchun bosing)</i>\n" +
                    "🎁 Bonus: +0 so'm\n\n" +
                    "🔢 <b>Karta raqami:</b> <code>" + humoCard + "</code>\n" +
                    "👤 <b>Karta egasi:</b> <code>" + HUMO_CARD_OWNER + "</code>\n\n" +
                    "<blockquote>⚠️ <b>DIQQAT:</b> Aynan ko'rsatilgan <b>" + pendingAmount + "</b>" + sumSuffix + " o'tkazing (1 so'm kam ham, ko'p ham emas)!\n" +
                    "⚡ To'lov amalga oshishi bilan balansingiz 1 soniyada avtomatik to'ldiriladi.\n\n" +
                    "⏳ To'lov uchun <b>4 daqiqa</b> vaqtingiz bor. Shu vaqt ichida to'lov qilmasangiz summa bekor qilinadi!</blockquote>";
        };

        String checkBtnText = switch (language) {
            case "RU" -> "🔄 Проверить оплату";
            case "ENG" -> "🔄 Check payment";
            default -> "🔄 To'lovni tekshirish";
        };
        String receiptBtnText = switch (language) {
            case "RU" -> "📸 Отправить чек";
            case "ENG" -> "📸 Send receipt";
            default -> "📸 Chek yuborish";
        };

        InlineKeyboardButton checkBtn = InlineKeyboardButton.builder()
                .text(checkBtnText)
                .callbackData("CHECK_AUTO_PAYMENT")
                .build();
        InlineKeyboardButton receiptBtn = InlineKeyboardButton.builder()
                .text(receiptBtnText)
                .callbackData("SEND_CHECK")
                .build();
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(
                List.of(checkBtn),
                List.of(receiptBtn)
        ));

        // Callback xabarini inline keyboard bilan yangilash
        try {
            org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText editText =
                    org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText.builder()
                            .chatId(chatId.toString())
                            .messageId(callbackMsgId)
                            .text(mainText)
                            .parseMode("HTML")
                            .replyMarkup(markup)
                            .build();
            bot.execute(editText);
            user.setPendingMessageId(callbackMsgId);
            userRepository.save(user);
        } catch (Exception e) {
            // Edit muvaffaqiyatsiz bo'lsa yangi xabar yuboramiz
            try {
                SendMessage sm = new SendMessage(chatId.toString(), mainText);
                sm.setParseMode("HTML");
                sm.setReplyMarkup(markup);
                Message sent = bot.execute(sm);
                user.setPendingMessageId(sent.getMessageId());
                userRepository.save(user);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    // =====================================================================
    // 3b. BANK KARTASI ORQALI TO'LOV — HUMO + VISA, chek yuborish
    // =====================================================================
    public void handleBankPayment(SlideBot bot, UserEntity user, Long chatId, int baseAmount, Integer callbackMsgId) {
        String language = lang(user);

        String sumSuffix = switch (language) {
            case "RU" -> " сум";
            case "ENG" -> " UZS";
            default -> " so'm";
        };
        String formattedAmount = "<code>" + baseAmount + "</code>" + sumSuffix;

        String mainText = switch (language) {
            case "RU" -> "💳 <b>Оплата через банковскую карту</b>\n\n" +
                    "Сумма оплаты: " + formattedAmount + " <i>(нажмите чтобы скопировать)</i>\n\n" +
                    "Переведите оплату на карту ниже и сделайте скриншот чека:\n" +
                    "<i>(Нажмите на номер карты, чтобы скопировать)</i>\n\n" +
                    "<blockquote>💳 <b>HUMO Card:</b>\n<code>" + humoCard + "</code>\n" +
                    "👤 " + HUMO_CARD_OWNER + "</blockquote>\n" +
                    "❕ Если есть проблема с этой картой:\n\n" +
                    "<blockquote>💳 <b>Uzcard:</b>\n<code>" + UZCARD_CARD + "</code>\n" +
                    "👤 " + UZCARD_OWNER + "</blockquote>\n" +
                    "<blockquote>🧾 После оплаты отправьте чек 👇</blockquote>";
            case "ENG" -> "💳 <b>Bank card payment</b>\n\n" +
                    "Payment amount: " + formattedAmount + " <i>(tap to copy)</i>\n\n" +
                    "Send payment to the card below and take a screenshot:\n" +
                    "<i>(Tap on card number to copy)</i>\n\n" +
                    "<blockquote>💳 <b>HUMO Card:</b>\n<code>" + humoCard + "</code>\n" +
                    "👤 " + HUMO_CARD_OWNER + "</blockquote>\n" +
                    "❕ If there's a problem with this card:\n\n" +
                    "<blockquote>💳 <b>Uzcard:</b>\n<code>" + UZCARD_CARD + "</code>\n" +
                    "👤 " + UZCARD_OWNER + "</blockquote>\n" +
                    "<blockquote>🧾 After payment, send the receipt 👇</blockquote>";
            default -> "💳 <b>Bank kartasi orqali to'lov</b>\n\n" +
                    "To'lov summasi: " + formattedAmount + " <i>(nusxalash uchun bosing)</i>\n\n" +
                    "Quyidagi karta raqamiga to'lov qiling va chekni skreenshot qiling:\n" +
                    "<i>(COPY qilish uchun karta raqami ustiga bosing)</i>\n\n" +
                    "<blockquote>💳 <b>HUMO Card:</b>\n<code>" + humoCard + "</code>\n" +
                    "👤 " + HUMO_CARD_OWNER + "</blockquote>\n" +
                    "❕ Ushbu kartaga to'lovda muammo bo'lsa:\n\n" +
                    "<blockquote>💳 <b>Uzcard:</b>\n<code>" + UZCARD_CARD + "</code>\n" +
                    "👤 " + UZCARD_OWNER + "</blockquote>\n" +
                    "<blockquote>🧾 To'lov qilganingizdan so'ng /chek buyrug'ini yuboring yoki quyidagi tugmani bosing👇</blockquote>";
        };

        String checkBtnText = switch (language) {
            case "RU" -> "📝 Отправить чек";
            case "ENG" -> "📝 Send receipt";
            default -> "📝 Chekni yuborish";
        };

        if (callbackMsgId != null) {
            rawSender.editMessageTextWithStyledCallbackBtn(bot, chatId.toString(), callbackMsgId, mainText, checkBtnText, "SEND_CHECK", "primary");
        } else {
            rawSender.sendMessageWithStyledCallbackBtn(bot, chatId.toString(), mainText, checkBtnText, "SEND_CHECK", "primary");
        }
    }

    // =====================================================================
    // 4. AVTO TO'LOV MUVAFFAQIYATLI — balansni to'ldirish va xabar yuborish
    // =====================================================================
    public void confirmAutoPayment(SlideBot bot, UserEntity user, int paidAmount) {
        String language = lang(user);
        int baseAmount = user.getPendingBaseAmount() != null ? user.getPendingBaseAmount() : paidAmount;

        // Balansni to'ldirish (asosiy summa, tiyinlarsiz)
        int currentBalance = user.getBalance() != null ? user.getBalance() : 0;
        user.setBalance(currentBalance + baseAmount);

        // Pending xabardan inline tugmalarni o'chirish
        if (user.getPendingMessageId() != null) {
            try {
                EditMessageReplyMarkup removeMarkup = new EditMessageReplyMarkup();
                removeMarkup.setChatId(user.getChatId().toString());
                removeMarkup.setMessageId(user.getPendingMessageId());
                removeMarkup.setReplyMarkup(new InlineKeyboardMarkup(List.of()));
                bot.execute(removeMarkup);
            } catch (Exception ignored) {}
        }

        // Pending ma'lumotlarni tozalash
        user.setPendingAmount(null);
        user.setPendingTime(null);
        user.setPendingMessageId(null);
        user.setPendingBaseAmount(null);
        userRepository.save(user);

        // Foydalanuvchiga xabar
        String successMsg = switch (language) {
            case "RU" -> "✅ <b>Оплата успешно подтверждена!</b>\n\n" +
                    "💳 Поступившая сумма: " + baseAmount + " сум\n" +
                    "💰 Новый баланс: " + user.getBalance() + " сум";
            case "ENG" -> "✅ <b>Payment successfully confirmed!</b>\n\n" +
                    "💳 Amount received: " + baseAmount + " UZS\n" +
                    "💰 New balance: " + user.getBalance() + " UZS";
            default -> "✅ <b>To'lov muvaffaqiyatli tasdiqlandi!</b>\n\n" +
                    "💳 Tushgan summa: " + baseAmount + " so'm\n" +
                    "💰 Yangi balans: " + user.getBalance() + " so'm";
        };

        try {
            SendMessage sm = new SendMessage(user.getChatId().toString(), successMsg);
            sm.setParseMode("HTML");
            bot.execute(sm);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // =====================================================================
    // 5. 4 DAQIQA O'TGANDA — pending'ni bekor qilish (scheduler tomonidan chaqiriladi)
    // =====================================================================
    public void expirePendingPayment(SlideBot bot, UserEntity user) {
        String language = lang(user);

        // Inline tugmalarni xabardan o'chirish
        if (user.getPendingMessageId() != null) {
            try {
                String expiredText = switch (language) {
                    case "RU" -> "⏰ <b>Время оплаты истекло.</b>\nПопробуйте снова — /balance";
                    case "ENG" -> "⏰ <b>Payment time expired.</b>\nTry again — /balance";
                    default -> "⏰ <b>To'lov muddati tugadi.</b>\nQaytadan urinib ko'ring — /balance";
                };
                org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText editText =
                        org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText.builder()
                                .chatId(user.getChatId().toString())
                                .messageId(user.getPendingMessageId())
                                .text(expiredText)
                                .parseMode("HTML")
                                .replyMarkup(new InlineKeyboardMarkup(List.of()))
                                .build();
                bot.execute(editText);
            } catch (Exception ignored) {}
        }

        user.setPendingAmount(null);
        user.setPendingTime(null);
        user.setPendingMessageId(null);
        user.setPendingBaseAmount(null);
        userRepository.save(user);
    }

    // =====================================================================
    // 6. CHEK FLOW — eski logika (bank kartasi uchun)
    // =====================================================================
    public void handleCheckFlow(SlideBot bot, UserEntity user, Long chatId) {
        String language = lang(user);
        String text = switch (language) {
            case "RU" -> "📨 Пожалуйста, отправьте скриншот или файл чека:";
            case "ENG" -> "📨 Please send a screenshot or file of your payment receipt:";
            default -> "📨 To'lov qilganingizni tasdiqlovchi chekni skrenshotini yoki faylini yuboring:";
        };
        try {
            bot.execute(new SendMessage(chatId.toString(), text));
        } catch (Exception e) {
            e.printStackTrace();
        }
        user.setInCheckFlow(true);
        userRepository.save(user);
    }

    public void handleCheckFile(SlideBot bot, UserEntity user, Message message) {
        user.setInCheckFlow(false);
        userRepository.save(user);
        try {
            String fileId;
            String fileName;
            if (message.hasPhoto()) {
                PhotoSize photo = message.getPhoto().get(message.getPhoto().size() - 1);
                fileId = photo.getFileId();
                fileName = "check_" + user.getChatId() + ".jpg";
            } else if (message.hasDocument()) {
                fileId = message.getDocument().getFileId();
                fileName = message.getDocument().getFileName();
            } else {
                return;
            }
            GetFile getFileMethod = new GetFile(fileId);
            org.telegram.telegrambots.meta.api.objects.File tgFile = bot.execute(getFileMethod);
            String fileUrl = tgFile.getFileUrl(bot.getBotToken());
            byte[] fileBytes;
            try (InputStream in = new URL(fileUrl).openStream()) {
                fileBytes = in.readAllBytes();
            }
            FileEntity fileEntity = new FileEntity();
            fileEntity.setChatId(user.getChatId());
            fileEntity.setFileName(fileName);
            fileEntity.setFileType("CHECK");
            fileEntity.setData(fileBytes);
            FileEntity saved = fileRepository.save(fileEntity);
            user.setCheckFileId(saved.getId());
            userRepository.save(user);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteCheckFile(UserEntity user) {
        Long fileId = user.getCheckFileId();
        if (fileId != null) {
            try { fileRepository.deleteById(fileId); } catch (Exception e) { e.printStackTrace(); }
            user.setCheckFileId(null);
            userRepository.save(user);
        }
    }

    // =====================================================================
    // YORDAMCHI METHODLAR
    // =====================================================================

    /** Summani formatlaydi: 6000 → "6 000" */
    public static String formatAmount(int amount) {
        return String.format("%,d", amount).replace(',', ' ');
    }

    /**
     * Reply keyboard tugma matnidan summani aniqlaydi.
     * "6 000 so'm", "6 000 сум", "6 000 UZS" → 6000
     * Agar mos kelmasa null qaytaradi
     */
    public static Integer parseAmountFromButton(String text) {
        if (text == null) return null;
        // Raqam va bo'shliqni qoldirib, qolganini olib tashlaymiz
        String cleaned = text.replaceAll("[^0-9 ]", "").trim();
        cleaned = cleaned.replaceAll("\\s+", "");
        if (cleaned.isEmpty()) return null;
        try {
            int val = Integer.parseInt(cleaned);
            for (int amount : new int[]{6000, 7000, 8000, 9000, 10000, 15000, 20000, 50000}) {
                if (val == amount) return val;
            }
        } catch (NumberFormatException ignored) {}
        return null;
    }
}
