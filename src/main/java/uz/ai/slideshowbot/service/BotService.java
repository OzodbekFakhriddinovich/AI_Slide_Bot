package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.*;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.enums.UserState;
import uz.ai.slideshowbot.repository.UserRepository;

import java.util.*;

@Service
public class BotService {

    private static final String WEB_APP_URL =
            "https://ozodbekfakhriddinovich.github.io/AI_Slide_Bot/index.html";

    @Autowired private ReferralService referralService;
    @Autowired private ZipService zipService;
    @Autowired private UserRepository userRepository;
    @Autowired private PdfService pdfService;
    @Autowired private BalanceService balanceService;
    @Autowired private AdminCheckService adminCheckService;
    @Autowired private BroadcastService broadcastService;
    @Autowired private MenuService menuService;
    @Autowired private SlideFlowSingleService slideFlowSingleService;
    @Autowired private PptxCreatorService pptxCreatorService;
    @Autowired private GptTextGeneratorService gptTextGeneratorService;

    public boolean handleUpdate(Update update, SlideBot bot) {
        try {
            if (adminCheckService.handleAdminCallback(update, bot)) {
                return true;
            }

            if (update.hasCallbackQuery()) {
                CallbackQuery query = update.getCallbackQuery();
                String data = query.getData();
                Message callbackMsg = (Message) query.getMessage();
                Long chatId = callbackMsg.getChatId();

                UserEntity user = userRepository.findByChatId(chatId).orElse(null);
                if (user == null) return false;

                try {
                    if (data.startsWith("SLIDE_LANG_") || data.startsWith("SLIDE_NUM_")
                            || data.startsWith("SLIDE_TPL_") || data.startsWith("SLIDE_TARIFF_")) {
                        slideFlowSingleService.handleCallback(user, query, bot);
                        return true;
                    }

                    switch (data) {
                        case "CHECK_JOIN" -> {
                            if (isUserInChannel(bot, chatId)) {
                                deleteMessage(bot, callbackMsg);
                                sendMessage(bot, chatId, getText("MAIN_MENU", user.getLanguage()),
                                        mainMenuKeyboard(user.getLanguage()), false);
                            } else {
                                sendAlert(bot, query.getId(),
                                        switch (user.getLanguage()) {
                                            case "RU" -> "❌ Вы не подписались на канал!";
                                            case "ENG" -> "❌ You haven't joined the channel!";
                                            default -> "❌ Siz kanalimizga a'zo bo'lmagansiz!";
                                        });
                            }
                        }
                        case "ZIP_SAVE" -> zipService.handleZipConfirmReply(update, bot, user);
                        case "ZIP_BACK" -> {
                            user.setInZipFlow(false);
                            userRepository.save(user);
                            deleteMessage(bot, callbackMsg);
                            sendMessage(bot, chatId, getText("MAIN_MENU", user.getLanguage()),
                                    mainMenuKeyboard(user.getLanguage()), false);
                        }
                        case "BALANCE_PAY" -> balanceService.handleBuy(bot, user, chatId, null);
                        case "SEND_CHECK" -> {
                            try {
                                org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup r =
                                        new org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup();
                                r.setChatId(chatId.toString());
                                r.setMessageId(callbackMsg.getMessageId());
                                r.setReplyMarkup(new InlineKeyboardMarkup(List.of()));
                                bot.execute(r);
                            } catch (Exception ignored) {}
                            balanceService.handleCheckFlow(bot, user, chatId);
                            return true;
                        }
                        default -> {}
                    }

                    AnswerCallbackQuery answer = new AnswerCallbackQuery();
                    answer.setCallbackQueryId(query.getId());
                    answer.setShowAlert(false);
                    bot.execute(answer);

                } catch (Exception e) {
                    e.printStackTrace();
                    sendAlert(bot, query.getId(), "⚠️ Xatolik yuz berdi!");
                }
                return true;
            }

            if (!update.hasMessage()) return false;
            Message message = update.getMessage();
            Long chatId = message.getChatId();

            UserEntity user = userRepository.findByChatId(chatId).orElseGet(() -> {
                UserEntity u = new UserEntity();
                u.setChatId(chatId);
                u.setBalance(4000);
                userRepository.save(u);
                return u;
            });

            if (message.getFrom() != null) {
                if (user.getFirstName() == null || !Objects.equals(user.getFirstName(), message.getFrom().getFirstName())) {
                    user.setFirstName(message.getFrom().getFirstName());
                }
                if (user.getUsername() == null || !Objects.equals(user.getUsername(), message.getFrom().getUserName())) {
                    user.setUsername(message.getFrom().getUserName());
                }
            }
            userRepository.save(user);

            if (message.getFrom() != null && message.getFrom().getUserName() != null) {
                if (message.getFrom().getUserName().equals("slides_admin1")) {
                    broadcastService.broadcastToAll(bot, message);
                    bot.execute(new SendMessage(chatId.toString(), "✅ Xabar barcha foydalanuvchilarga yuborildi."));
                    return true;
                }
            }

            if (message.getWebAppData() != null) {
                slideFlowSingleService.handleWebAppData(user, message, bot);
                return true;
            }

            String text = message.hasText() ? message.getText() : "";

            if (Boolean.TRUE.equals(user.getInCheckFlow())
                    && message.hasText()
                    && !text.equals("/check")
                    && !text.equals("/chek")) {
                user.setInCheckFlow(false);
                userRepository.save(user);
            }

            if (Boolean.TRUE.equals(user.getInCheckFlow()) && (message.hasPhoto() || message.hasDocument())) {
                balanceService.handleCheckFile(bot, user, message);
                adminCheckService.sendCheckToAdmin(message, user, bot);
                user.setInCheckFlow(false);
                userRepository.save(user);
                return true;
            }

            if (Boolean.TRUE.equals(user.getInZipFlow())) {
                if (message.hasPhoto() || message.hasDocument()) {
                    zipService.handleFile(update, bot, user);
                    return true;
                }
                if (text.equals("📦 ZIP yaratish") || text.equals("📦 Create ZIP") || text.equals("📦 Создать ZIP")) {
                    zipService.handleZipConfirmReply(update, bot, user);
                    return true;
                }
                if (text.equals("◀️ Orqaga") || text.equals("◀️ Back") || text.equals("◀️ Назад")) {
                    user.setInZipFlow(false);
                    userRepository.save(user);
                    bot.execute(menuService.mainMenu(chatId, user.getLanguage()));
                    return true;
                }
                if (message.hasAudio() || message.hasVideo() || message.hasVoice()) {
                    sendMessage(bot, chatId,
                            switch (user.getLanguage()) {
                                case "RU" -> "❌ Только фото и документы!";
                                case "ENG" -> "❌ Only photos and documents!";
                                default -> "❌ Faqat rasm va fayllar qabul qilinadi!";
                            },
                            zipService.zipBackKeyboard(user.getLanguage()), false);
                    return true;
                }
                if (!text.isEmpty()) return true;
            }

            if (pdfService.handlePdfFlow(update, bot, user)) return true;

            if (text.equals("/start") || text.startsWith("/start ")) {
                if (Boolean.TRUE.equals(user.getInCheckFlow())) {
                    user.setInCheckFlow(false);
                    userRepository.save(user);
                }
                if (user.getState() != null && user.getState() != UserState.START) {
                    user.setState(UserState.START);
                    userRepository.save(user);
                }

                String refCode = null;
                if (message.hasText()) {
                    String[] parts = message.getText().trim().split("\\s+");
                    if (parts.length > 1 && parts[1].startsWith("ref_")) {
                        refCode = parts[1];
                    }
                }
                if (refCode != null) {
                    referralService.handleReferralJoin(bot, user, refCode);
                }

                if (user.getLanguage() == null || user.getLanguage().isEmpty()) {
                    sendMessage(bot, chatId,
                            "🇺🇿 Tilni tanlang\n🇷🇺 Выберите язык\n🇬🇧 Choose language:",
                            languageKeyboard(), true);
                    return true;
                }

                if (!isUserInChannel(bot, chatId)) {
                    sendJoinRequest(bot, chatId, user.getLanguage());
                    return true;
                }

                sendMessage(bot, chatId, getText("MAIN_MENU", user.getLanguage()),
                        mainMenuKeyboard(user.getLanguage()), false);
                return true;
            }

            if (update.hasMessage() && update.getMessage().hasText()) {
                Message message1 = update.getMessage();
                Long chatId1 = message1.getChatId();
                String text1 = message1.getText();

                UserEntity user1 = userRepository.findByChatId(chatId1).orElseGet(() -> {
                    UserEntity u = new UserEntity();
                    u.setChatId(chatId1);
                    u.setBalance(4000);
                    u.setState(UserState.START);
                    userRepository.save(u);
                    return u;
                });

                if (message1.getFrom() != null) {
                    if (user1.getFirstName() == null || !Objects.equals(user1.getFirstName(), message1.getFrom().getFirstName())) {
                        user1.setFirstName(message1.getFrom().getFirstName());
                    }
                    if (user1.getUsername() == null || !Objects.equals(user1.getUsername(), message1.getFrom().getUserName())) {
                        user1.setUsername(message1.getFrom().getUserName());
                    }
                }
                userRepository.save(user1);

                String lang = (user1.getLanguage() != null) ? user1.getLanguage() : "UZ";

                switch (text1) {

                    case "🎞️ Taqdimot yaratish", "🎞️ Создать презентацию", "🎞️ Create Presentation" -> {
                        if (Boolean.TRUE.equals(user1.getInCheckFlow())) {
                            user1.setInCheckFlow(false);
                            userRepository.save(user1);
                        }

                        KeyboardButton webAppBtn = new KeyboardButton(switch (lang) {
                            case "RU" -> "⚙️ Открыть настройки презентации";
                            case "ENG" -> "⚙️ Open presentation settings";
                            default -> "⚙️ Taqdimot sozlamalarini kiritish";
                        });
                        webAppBtn.setWebApp(new WebAppInfo(WEB_APP_URL));

                        KeyboardRow webRow = new KeyboardRow();
                        webRow.add(webAppBtn);

                        ReplyKeyboardMarkup webMarkup = new ReplyKeyboardMarkup(List.of(webRow));
                        webMarkup.setResizeKeyboard(true);

                        SendMessage webMsg = new SendMessage(chatId1.toString(), switch (lang) {
                            case "RU" -> "O'zingizga kerakli sozlamlarni tanlang!";
                            case "ENG" -> "Choose your presentation settings!";
                            default -> "O'zingizga kerakli sozlamlarni tanlang!";
                        });
                        webMsg.setReplyMarkup(webMarkup);
                        bot.execute(webMsg);
                        return true;
                    }

                    case "🇺🇿 O'zbekcha", "🇷🇺 Русский", "🇬🇧 English" -> {
                        switch (text1) {
                            case "🇺🇿 O'zbekcha" -> user1.setLanguage("UZ");
                            case "🇷🇺 Русский" -> user1.setLanguage("RU");
                            case "🇬🇧 English" -> user1.setLanguage("ENG");
                        }
                        userRepository.save(user1);
                        deleteUserMessage(bot, message1);

                        if (user1.getLastLanguageMessageId() != null) {
                            deleteMessage(bot, chatId1, user1.getLastLanguageMessageId());
                            user1.setLastLanguageMessageId(null);
                            userRepository.save(user1);
                        }

                        if (!isUserInChannel(bot, chatId1)) {
                            sendJoinRequest(bot, chatId1, user1.getLanguage());
                        } else {
                            sendMessage(bot, chatId1, getText("MAIN_MENU", user1.getLanguage()),
                                    mainMenuKeyboard(user1.getLanguage()), false);
                        }
                        return true;
                    }

                    case "💰 Balans", "💰 Баланс", "💰 Balance", "/balance" -> {
                        if (Boolean.TRUE.equals(user1.getInCheckFlow())) {
                            user1.setInCheckFlow(false);
                            userRepository.save(user1);
                        }
                        balanceService.showBalance(bot, user1, chatId1, null);
                        return true;
                    }

                    case "/check", "/chek" -> {
                        balanceService.handleCheckFlow(bot, user1, chatId1);
                        return true;
                    }

                    case "/buy" -> {
                        if (Boolean.TRUE.equals(user1.getInCheckFlow())) {
                            user1.setInCheckFlow(false);
                            userRepository.save(user1);
                        }
                        balanceService.handleBuy(bot, user1, chatId1, null);
                        return true;
                    }

                    case "/referal", "Referral", "Реферал" -> {
                        if (Boolean.TRUE.equals(user1.getInCheckFlow())) {
                            user1.setInCheckFlow(false);
                            userRepository.save(user1);
                        }
                        referralService.handleReferralCommand(bot, user1, chatId1);
                        return true;
                    }

                    case "📦 Fayllarni Zip qilish", "📦 Zip Files", "📦 Архивировать файлы (Zip)" -> {
                        if (Boolean.TRUE.equals(user1.getInCheckFlow())) {
                            user1.setInCheckFlow(false);
                        }
                        if (!isUserInChannel(bot, chatId1)) {
                            sendJoinRequest(bot, chatId1, user1.getLanguage());
                            return true;
                        }
                        user1.setInZipFlow(true);
                        userRepository.save(user1);
                        sendMessage(bot, chatId1,
                                switch (user1.getLanguage()) {
                                    case "RU" -> "📂 Пожалуйста, отправьте файл для создания ZIP.";
                                    case "ENG" -> "📂 Please send any file to create ZIP.";
                                    default -> "📂 ZIP yaratish uchun fayl yuboring.";
                                },
                                zipService.zipBackKeyboard(user1.getLanguage()), false);
                        return true;
                    }

                    case "🌐 Til Sozlamalari", "🌐 Язык", "🌐 Language Settings" -> {
                        if (Boolean.TRUE.equals(user1.getInCheckFlow())) {
                            user1.setInCheckFlow(false);
                            userRepository.save(user1);
                        }
                        sendMessage(bot, chatId1,
                                "🇺🇿 Tilni tanlang\n🇷🇺 Выберите язык\n🇬🇧 Choose language:",
                                languageKeyboard(), true);
                        return true;
                    }

                    case "📄 Word <--> PDF" -> {
                        String msg = switch (user1.getLanguage()) {
                            case "RU" -> "🚧 Этот раздел находится в разработке. Скоро будет доступен!";
                            case "ENG" -> "🚧 This section is under development. Coming soon!";
                            default -> "🚧 Bu bo'lim hali ishlab chiqilmoqda. Tez orada ishga tushadi!";
                        };
                        sendMessage(bot, chatId1, msg, mainMenuKeyboard(user1.getLanguage()), false);
                        return true;
                    }

                    default -> {
                        if (user1.getState() == UserState.WAITING_TOPIC
                                || user1.getState() == UserState.WAITING_INFO) {
                            slideFlowSingleService.handleMessage(user1, message1, bot);
                            return true;
                        }

                        if (user1.getState() == UserState.START || user1.getState() == null) {
                            sendMessage(bot, chatId1,
                                    switch (user1.getLanguage() != null ? user1.getLanguage() : "UZ") {
                                        case "RU" -> "❗️ Неверная команда. Нажмите /start.";
                                        case "ENG" -> "❗️ Invalid command. Press /start.";
                                        default -> "❗️ Noto'g'ri buyruq. /start ni bosing.";
                                    },
                                    mainMenuKeyboard(user1.getLanguage()), false);
                            return true;
                        }

                        sendMessage(bot, chatId1,
                                "Iltimos, menyudan foydalaning.",
                                mainMenuKeyboard(user1.getLanguage()), false);
                        return true;
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return false;
    }

    private void deleteMessage(SlideBot bot, Long chatId, Integer msgId) {
        try {
            if (msgId != null) bot.execute(new DeleteMessage(chatId.toString(), msgId));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void deleteMessage(SlideBot bot, Message msg) {
        try {
            if (msg != null) bot.execute(new DeleteMessage(msg.getChatId().toString(), msg.getMessageId()));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void deleteUserMessage(SlideBot bot, Message msg) {
        try {
            if (msg != null) bot.execute(new DeleteMessage(msg.getChatId().toString(), msg.getMessageId()));
        } catch (Exception e) { e.printStackTrace(); }
    }

    private boolean isUserInChannel(SlideBot bot, Long chatId) {
        try {
            GetChatMember getChatMember = new GetChatMember("@pptx_pdf_mustaqil_ish", chatId);
            ChatMember member = bot.execute(getChatMember);
            if (member != null && member.getStatus() != null) {
                String status = member.getStatus();
                return status.equals("member") || status.equals("administrator") || status.equals("creator");
            }
        } catch (TelegramApiException ignored) {}
        return false;
    }

    private void sendJoinRequest(SlideBot bot, Long chatId, String lang) {
        String text = switch (lang) {
            case "RU" -> "📢 Пожалуйста, подпишитесь на наш канал и нажмите кнопку ниже:";
            case "ENG" -> "📢 Please subscribe to our official channel and press the button below:";
            default -> "📢 Botdan foydalanish uchun kanalimizga obuna bo'ling va tugmani bosing:";
        };

        InlineKeyboardButton joinBtn = new InlineKeyboardButton();
        joinBtn.setText(switch (lang) {
            case "RU" -> "📢 Перейти к каналу";
            case "ENG" -> "📢 Go to the official channel";
            default -> "📢 Rasmiy kanalga o'tish";
        });
        joinBtn.setUrl("https://t.me/pptx_pdf_mustaqil_ish");

        InlineKeyboardButton checkBtn = new InlineKeyboardButton();
        checkBtn.setText(switch (lang) {
            case "RU" -> "✅ Проверить";
            case "ENG" -> "✅ Check";
            default -> "✅ Tekshirish";
        });
        checkBtn.setCallbackData("CHECK_JOIN");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(joinBtn), List.of(checkBtn)));
        sendMessage(bot, chatId, text, markup, false);
    }

    private Message sendMessage(SlideBot bot, Long chatId, String text, ReplyKeyboard keyboard, boolean isLanguageMsg) {
        try {
            SendMessage msg = new SendMessage(chatId.toString(), text);
            if (keyboard != null) msg.setReplyMarkup(keyboard);
            Message sentMsg = bot.execute(msg);
            UserEntity user = userRepository.findByChatId(chatId).orElse(null);
            if (user != null) {
                if (isLanguageMsg) user.setLastLanguageMessageId(sentMsg.getMessageId());
                else user.setLastBotMessageId(sentMsg.getMessageId());
                userRepository.save(user);
            }
            return sentMsg;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Message sendMessage(SlideBot bot, Long chatId, String text, InlineKeyboardMarkup inlineKeyboard, boolean isLanguageMsg) {
        try {
            SendMessage msg = new SendMessage(chatId.toString(), text);
            if (inlineKeyboard != null) msg.setReplyMarkup(inlineKeyboard);
            Message sentMsg = bot.execute(msg);
            UserEntity user = userRepository.findByChatId(chatId).orElse(null);
            if (user != null) {
                if (isLanguageMsg) user.setLastLanguageMessageId(sentMsg.getMessageId());
                else user.setLastBotMessageId(sentMsg.getMessageId());
                userRepository.save(user);
            }
            return sentMsg;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private ReplyKeyboard languageKeyboard() {
        KeyboardRow row = new KeyboardRow();
        row.add("🇺🇿 O'zbekcha");
        row.add("🇷🇺 Русский");
        row.add("🇬🇧 English");
        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup(List.of(row));
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);
        return markup;
    }

    private ReplyKeyboard mainMenuKeyboard(String lang) {
        List<KeyboardRow> rows = new ArrayList<>();
        KeyboardRow r1 = new KeyboardRow();
        KeyboardRow r2 = new KeyboardRow();
        KeyboardRow r3 = new KeyboardRow();

        switch (lang != null ? lang : "UZ") {
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

    private void sendAlert(SlideBot bot, String callbackId, String text) {
        try {
            AnswerCallbackQuery alert = new AnswerCallbackQuery();
            alert.setCallbackQueryId(callbackId);
            alert.setText(text);
            alert.setShowAlert(true);
            bot.execute(alert);
        } catch (Exception ignored) {}
    }

    private String getText(String key, String lang) {
        switch (key) {
            case "MAIN_MENU":
                if ("RU".equals(lang)) return "Привет, ИИ-презентация | Слайд | PPT | PDF-бот. Добро пожаловать!\n\n" +
                        "Я — интеллектуальный бот, который поможет вам подготовить презентации, визуально представить документы и управлять файлами. Я предоставляю следующие услуги:\n\n" +
                        "1. Создание слайдов — подготовка профессиональных презентаций в PowerPoint или других форматах.\n" +
                        "2. PDF-документы — простая конвертация изображений в PDF.\n" +
                        "3. Идеи для презентаций — советы по эффективной и понятной организации контента.\n" +
                        "4. Дизайн презентаций — выбор подходящего дизайна для профессионально выглядящих слайдов.\n" +
                        "5. Zip-файлы — простая конвертация файлов в ZIP-формат.\n\n" +
                        "/balance — ваш баланс в кошельке бота. Если вы новый участник, вам доступен бонус в размере 4000 сумов.\n" +
                        "/buy — раздел покупки";

                if ("ENG".equals(lang)) return "Hello AI Presentation | Slide | PPT | PDF Bot Welcome\n\n" +
                        "I am an intelligent bot that helps you prepare presentations, visually present documents, and manage files. I provide you with the following services:\n\n" +
                        "1. Create Slides – Prepare professional presentations in PowerPoint or other formats.\n" +
                        "2. PDF Documents – Easily convert images to PDF.\n" +
                        "3. Presentation Ideas – Tips for organizing content in an effective and understandable way.\n" +
                        "4. Presentation Design – Choose a suitable design for professional-looking slides.\n" +
                        "5. Zip Files – Easily convert files to zip format.\n\n" +
                        "/balance is your balance in the bot wallet. If you are a new member, you have a bonus of 4000 soums.\n" +
                        "/buy purchase section";

                return "Salom AI Taqdimot | Slayd | PPT | PDF Bot ga xush kelibsiz\n\n" +
                        "Men taqdimot tayyorlash, hujjatlarni vizual tarzda taqdim etish va fayllarni boshqarish bo'yicha yordam beradigan aqlli botman. Sizga quyidagi xizmatlarni taqdim etaman:\n\n" +
                        "1. Slaydlar yaratish – PowerPoint yoki boshqa formatlarda professional taqdimotlar tayyorlash.\n" +
                        "2. PDF hujjatlar – Rasmlarni osongina PDF formatiga o'zgartirish.\n" +
                        "3. Taqdimot g'oyalari – Kontentni samarali va tushunarli tarzda tashkil qilish bo'yicha maslahatlar.\n" +
                        "4. Taqdimot dizayni – Professional ko'rinishga ega slaydlar uchun mos dizayn tanlash.\n" +
                        "5. Fayllarni zip qilish – Fayllarni osongina zip formatiga o'zgartirish imkoniyati.\n\n" +
                        "/balance sizning bot hamyonidagi balansingiz. Agar yangi a'zo bo'lgan bo'lsangiz sizda 4000 so'm bonus bor.\n" +
                        "/buy xarid bo'limi";
        }
        return "Iltimos, menyudan foydalaning.";
    }
}