package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.*;
import org.telegram.telegrambots.meta.api.objects.webapp.WebAppInfo;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.enums.UserState;
import uz.ai.slideshowbot.repository.UserRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BotService {

    private static final String WEB_APP_URL =
            "https://ozodbekfakhriddinovich.github.io/AI_Slide_Bot/index.html";

    private static final String CHANNEL_1 = "@pptx_pdf_mustaqil_ish";
    private static final String CHANNEL_2 = "@talimxabarlari";

    private final Map<Long, long[]> channelMemberCache = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 25_000L;

    @Autowired private ReferralService referralService;
    @Autowired private ZipService zipService;
    @Autowired private UserRepository userRepository;
    @Autowired private PdfService pdfService;
    @Autowired private BalanceService balanceService;
    @Autowired private AdminCheckService adminCheckService;
    @Autowired private BroadcastService broadcastService;
    @Autowired private MenuService menuService;
    @Autowired private SlideFlowSingleService slideFlowSingleService;
    @Autowired private TelegramRawSender rawSender;
    @Autowired private TestService testService;
    @Autowired private WordPdfService wordPdfService;

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

                    if (data.startsWith("TEST_COUNT_") || data.startsWith("TEST_DIFF_") || data.equals("TEST_CONFIRM")) {
                        if (!isUserInChannel(bot, chatId)) {
                            sendAlert(bot, query.getId(), switch (lang(user)) {
                                case "RU"  -> "❌ Пожалуйста, подпишитесь на каналы для продолжения!";
                                case "ENG" -> "❌ Please subscribe to the channels to continue!";
                                default    -> "❌ Davom etish uchun kanallarga a'zo bo'ling!";
                            });
                            forceRejoinIfNeeded(bot, user, chatId);
                            answerCallback(bot, query.getId(), false);
                            return true;
                        }

                        if (data.startsWith("TEST_COUNT_")) {
                            testService.handleCountCallback(bot, user, query);
                        } else if (data.startsWith("TEST_DIFF_")) {
                            testService.handleDifficultyCallback(bot, user, query);
                        } else if (data.equals("TEST_CONFIRM")) {
                            testService.handleConfirmCallback(bot, user, query);
                        }

                        answerCallback(bot, query.getId(), false);
                        return true;
                    }

                    if (!data.equals("CHECK_JOIN") && !isUserInChannel(bot, chatId)) {
                        sendAlert(bot, query.getId(), switch (lang(user)) {
                            case "RU"  -> "❌ Сначала подпишитесь на наши каналы!";
                            case "ENG" -> "❌ Please join our channels first!";
                            default    -> "❌ Avval kanalimizga a'zo bo'ling!";
                        });
                        sendJoinRequest(bot, chatId, user.getLanguage());
                        return true;
                    }

                    switch (data) {
                        case "CHECK_JOIN" -> {
                            clearChannelCache(chatId);
                            if (isUserInChannel(bot, chatId)) {
                                deleteMessage(bot, callbackMsg);

                                if (user.getReferrerId() != null && !Boolean.TRUE.equals(user.getReferralBonusGiven())) {
                                    UserEntity referrer = userRepository.findByChatId(user.getReferrerId()).orElse(null);
                                    if (referrer != null) {
                                        int bonusCount = referrer.getReferralBonusCount() == null
                                                ? 0 : referrer.getReferralBonusCount();
                                        if (bonusCount < 4) {
                                            referrer.setBalance(referrer.getBalance() + 1000);
                                            referrer.setReferralBonusCount(bonusCount + 1);
                                            userRepository.save(referrer);

                                            String notifyText = switch (lang(referrer)) {
                                                case "RU"  -> "🎉 Ваш друг <b>" + getDisplayName(user) + "</b> присоединился!\n💰 <b>+1000 сум</b> зачислено на баланс.";
                                                case "ENG" -> "🎉 Your friend <b>" + getDisplayName(user) + "</b> joined!\n💰 <b>+1000 sum</b> added to your balance.";
                                                default    -> "🎉 Do'stingiz <b>" + getDisplayName(user) + "</b> qo'shildi!\n💰 Hisobingizga <b>+1000 so'm</b> qo'shildi.";
                                            };
                                            try {
                                                SendMessage notify = new SendMessage(referrer.getChatId().toString(), notifyText);
                                                notify.setParseMode("HTML");
                                                bot.execute(notify);
                                            } catch (Exception ignored) {}
                                        }
                                    }
                                    user.setReferralBonusGiven(true);
                                    userRepository.save(user);
                                }

                                String welcomePopup = switch (lang(user)) {
                                    case "RU"  -> "🎉 Поздравляем! Добро пожаловать в бот!";
                                    case "ENG" -> "🎉 Congratulations! Welcome to the bot!";
                                    default    -> "🎉 Tabriklaymiz! Botga xush kelibsiz!";
                                };
                                AnswerCallbackQuery welcomeAnswer = new AnswerCallbackQuery();
                                welcomeAnswer.setCallbackQueryId(query.getId());
                                welcomeAnswer.setText(welcomePopup);
                                welcomeAnswer.setShowAlert(true);
                                bot.execute(welcomeAnswer);

                                menuService.sendMainMenu(bot, chatId, user.getLanguage());

                            } else {
                                sendAlert(bot, query.getId(), switch (lang(user)) {
                                    case "RU"  -> "❌ Вы ещё не подписались на канал!";
                                    case "ENG" -> "❌ You haven't joined the channel yet!";
                                    default    -> "❌ Siz hali kanalga a'zo bo'lmagansiz!";
                                });
                            }
                        }
                        case "ZIP_SAVE" -> zipService.handleZipConfirmReply(update, bot, user);
                        case "ZIP_BACK" -> {
                            user.setInZipFlow(false);
                            userRepository.save(user);
                            deleteMessage(bot, callbackMsg);
                            menuService.sendMainMenu(bot, chatId, user.getLanguage());
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

                    answerCallback(bot, query.getId(), false);

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
                u.setBalance(0);
                u.setState(UserState.START);
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

            if (user.getLanguage() == null || user.getLanguage().isEmpty()) {
                if (text.equals("🇺🇿 O'zbekcha") || text.equals("🇷🇺 Русский") || text.equals("🇺🇸 English")) {
                } else if (!text.equals("/start") && !text.startsWith("/start ")) {
                    menuService.sendLanguageSelector(bot, chatId);
                    return true;
                }
            }

            boolean isStartCommand      = text.equals("/start") || text.startsWith("/start ");
            boolean isLanguageSelection = text.equals("🇺🇿 O'zbekcha") || text.equals("🇷🇺 Русский") || text.equals("🇺🇸 English");

            if (isStartCommand) {
                user.setInCheckFlow(false);
                user.setInZipFlow(false);
                user.setInTestFlow(false);
                user.setState(UserState.START);

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
                userRepository.save(user);

                if (user.getLanguage() == null || user.getLanguage().isEmpty()) {
                    menuService.sendLanguageSelector(bot, chatId);
                    return true;
                }
                if (!isUserInChannel(bot, chatId)) {
                    sendJoinRequest(bot, chatId, user.getLanguage());
                    return true;
                }
                menuService.sendMainMenu(bot, chatId, user.getLanguage());
                return true;
            }

            if (isLanguageSelection) {
                String selectedLang = switch (text) {
                    case "🇷🇺 Русский" -> "RU";
                    case "🇺🇸 English" -> "ENG";
                    default -> "UZ";
                };
                user.setLanguage(selectedLang);
                userRepository.save(user);
                try {
                    deleteUserMessage(bot, message);
                    if (user.getLastLanguageMessageId() != null) {
                        deleteMessage(bot, chatId, user.getLastLanguageMessageId());
                        user.setLastLanguageMessageId(null);
                    } else {
                        deleteMessage(bot, chatId, message.getMessageId() - 1);
                    }
                    userRepository.save(user);
                } catch (Exception ignored) {}
                if (!isUserInChannel(bot, chatId)) {
                    sendJoinRequest(bot, chatId, user.getLanguage());
                } else {
                    menuService.sendMainMenu(bot, chatId, user.getLanguage());
                }
                return true;
            }

            if (user.getLanguage() != null && !isUserInChannel(bot, chatId)) {
                sendJoinRequest(bot, chatId, user.getLanguage());
                return true;
            }

            if (text.equals("◀️ Orqaga") || text.equals("◀️ Back") || text.equals("◀️ Наzad") || text.equals("◀️ Назад")) {
                user.setInZipFlow(false);
                user.setInCheckFlow(false);
                testService.resetTestFlow(user);
                wordPdfService.resetFlow(user);
                user.setState(UserState.START);
                userRepository.save(user);
                menuService.sendSimpleMenu(bot, chatId, user.getLanguage());
                return true;
            }

            if (message.hasText()) {
                switch (text) {
                    case "🎞️ Taqdimot yaratish", "🎞️ Создать презентацию", "🎞️ Create Presentation" -> {
                        user.setInCheckFlow(false);
                        userRepository.save(user);
                        KeyboardButton webAppBtn = new KeyboardButton(switch (user.getLanguage()) {
                            case "RU" -> "⚙️ Открыть настройки презентации";
                            case "ENG" -> "⚙️ Open presentation settings";
                            default -> "⚙️ Taqdimot sozlamalarini kiritish";
                        });
                        webAppBtn.setWebApp(new WebAppInfo(WEB_APP_URL));
                        KeyboardRow webRow = new KeyboardRow();
                        webRow.add(webAppBtn);
                        ReplyKeyboardMarkup webMarkup = new ReplyKeyboardMarkup(List.of(webRow));
                        webMarkup.setResizeKeyboard(true);
                        SendMessage webMsg = new SendMessage(chatId.toString(), switch (user.getLanguage()) {
                            case "RU" -> "Выберите необходимые настройки!";
                            case "ENG" -> "Choose your presentation settings!";
                            default -> "O'zingizga kerakli sozlamlarni tanlang!";
                        });
                        webMsg.setReplyMarkup(webMarkup);
                        bot.execute(webMsg);
                        return true;
                    }
                    case "💰 Balans", "💰 Баланс", "💰 Balance", "/balance" -> {
                        user.setInCheckFlow(false);
                        userRepository.save(user);
                        balanceService.showBalance(bot, user, chatId, null);
                        return true;
                    }
                    case "/check", "/chek" -> {
                        balanceService.handleCheckFlow(bot, user, chatId);
                        return true;
                    }
                    case "/buy" -> {
                        user.setInCheckFlow(false);
                        userRepository.save(user);
                        balanceService.handleBuy(bot, user, chatId, null);
                        return true;
                    }
                    case "/referal", "Referral", "Реферал" -> {
                        user.setInCheckFlow(false);
                        userRepository.save(user);
                        referralService.handleReferralCommand(bot, user, chatId);
                        return true;
                    }
                    case "🌐 Til Sozlamalari", "🌐 Язык", "🌐 Language Settings" -> {
                        user.setInCheckFlow(false);
                        userRepository.save(user);
                        menuService.sendLanguageSelector(bot, chatId);
                        return true;
                    }
                    case "📦 Fayllarni Zip qilish", "📦 Zip Files", "📦 Архивировать файлы (Zip)" -> {
                        user.setInCheckFlow(false);
                        user.setInZipFlow(true);
                        userRepository.save(user);
                        String enterText = switch (user.getLanguage()) {
                            case "RU" -> "📂 Пожалуйста, отправьте файл для создания ZIP.";
                            case "ENG" -> "📂 Please send any file to create ZIP.";
                            default -> "📂 ZIP yaratish uchun fayl yuboring.";
                        };
                        rawSender.sendColoredSingleMenu(bot, chatId.toString(), enterText, zipService.getBackBtnText(user.getLanguage()), "danger");
                        return true;
                    }
                    case "📋 Test tayyorlash", "📋 Создать тест", "📋 Create Test" -> {
                        user.setInCheckFlow(false);
                        userRepository.save(user);
                        testService.handleTestMenu(bot, user, chatId);
                        return true;
                    }
                    case "📄 Word <--> PDF" -> {
                        user.setInCheckFlow(false);
                        userRepository.save(user);
                        wordPdfService.handleMenu(bot, user, chatId);
                        return true;
                    }
                    case "📝 Referat tayyorlash", "📝 Создать реферат", "📝 Create Essay" -> {
                        user.setInCheckFlow(false);
                        userRepository.save(user);
                        String msgText = switch (user.getLanguage()) {
                            case "RU" -> "🚧 Раздел «Реферат» находится в разработке. Скоро будет доступен!";
                            case "ENG" -> "🚧 The Essay section is under development. Coming soon!";
                            default -> "🚧 «Referat tayyorlash» bo'limi ishlab chiqilmoqda. Tez orada ishga tushadi!";
                        };
                        sendMessage(bot, chatId, msgText, null, false);
                        return true;
                    }
                }
            }

            if (Boolean.TRUE.equals(user.getInCheckFlow())) {
                if (message.hasPhoto() || message.hasDocument()) {
                    balanceService.handleCheckFile(bot, user, message);
                    adminCheckService.sendCheckToAdmin(message, user, bot);
                    user.setInCheckFlow(false);
                    userRepository.save(user);
                    return true;
                }
                if (message.hasText() && !text.equals("/check") && !text.equals("/chek")) {
                    user.setInCheckFlow(false);
                    userRepository.save(user);
                }
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
                if (message.hasAudio() || message.hasVideo() || message.hasVoice()) {
                    String errorMessage = switch (user.getLanguage()) {
                        case "RU"  -> "❌ Только фото и документы!";
                        case "ENG" -> "❌ Only photos and documents!";
                        default    -> "❌ Faqat rasm va fayllar qabul qilinadi!";
                    };
                    sendMessage(bot, chatId, errorMessage, zipService.zipBackKeyboard(user.getLanguage()), false);
                    return true;
                }
                return true;
            }

            if (Boolean.TRUE.equals(user.getInWordPdfFlow())) {
                String lang = lang(user);
                if (text.equals("✅ Tasdiqlash") || text.equals("✅ Подтвердить") || text.equals("✅ Confirm")) {
                    wordPdfService.handleConfirm(bot, user, chatId);
                    return true;
                }
                if (message.hasDocument()) {
                    wordPdfService.handleFileInput(bot, user, message);
                    return true;
                }
                if (message.hasText() || message.hasPhoto() || message.hasAudio()
                        || message.hasVideo() || message.hasVoice() || message.hasSticker()) {
                    String rejectText = switch (lang) {
                        case "RU"  -> "❌ Я принимаю только .docx и .pdf файлы!";
                        case "ENG" -> "❌ I only accept .docx and .pdf files!";
                        default    -> "❌ Faqat .docx va .pdf fayllarni qabul qilaman!";
                    };
                    rawSender.sendColoredSingleMenu(bot, chatId.toString(), rejectText,
                            switch (lang) { case "RU" -> "◀️ Назад"; case "ENG" -> "◀️ Back"; default -> "◀️ Orqaga"; }, "danger");
                    return true;
                }
                return true;
            }

            if (pdfService.handlePdfFlow(update, bot, user)) return true;

            if (Boolean.TRUE.equals(user.getInTestFlow())) {
                if (user.getState() == UserState.WAITING_TEST_TOPIC && message.hasText()) {
                    testService.handleTopicInput(bot, user, chatId, text);
                    return true;
                }
                if (user.getState() == UserState.WAITING_TEST_QUESTION_COUNT && message.hasText()) {
                    testService.handleCustomCountInput(bot, user, chatId, text);
                    return true;
                }
                return true;
            }

            if (user.getState() == UserState.WAITING_TOPIC || user.getState() == UserState.WAITING_INFO) {
                slideFlowSingleService.handleMessage(user, message, bot);
                return true;
            }

            if (user.getState() == UserState.START || user.getState() == null) {
                if (!message.hasText()) {
                    menuService.sendMainMenu(bot, chatId, user.getLanguage());
                    return true;
                }
                menuService.sendMainMenu(bot, chatId, user.getLanguage());
                return true;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    public boolean isUserInChannel(SlideBot bot, Long userId) {
        long[] cached = channelMemberCache.get(userId);
        if (cached != null && System.currentTimeMillis() - cached[0] < CACHE_TTL_MS) {
            return cached[1] == 1L;
        }
        return refreshCacheAndCheck(bot, userId);
    }

    public boolean refreshCacheAndCheck(SlideBot bot, Long userId) {
        boolean isMember = checkSingleChannel(bot, userId, CHANNEL_1)
                && checkSingleChannel(bot, userId, CHANNEL_2);
        channelMemberCache.put(userId, new long[]{System.currentTimeMillis(), isMember ? 1L : 0L});
        return isMember;
    }

    public void clearChannelCache(Long userId) {
        channelMemberCache.remove(userId);
    }

    private void answerCallback(SlideBot bot, String callbackId, boolean showAlert) {
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(callbackId);
            answer.setShowAlert(showAlert);
            bot.execute(answer);
        } catch (Exception ignored) {}
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

    private void sendJoinRequest(SlideBot bot, Long chatId, String lang) {
        String currentLang = (lang != null) ? lang : "UZ";

        String text = switch (currentLang) {
            case "RU" -> "📢 Для использования бота необходимо быть участником обоих каналов!";
            case "ENG" -> "📢 To use the bot, you must subscribe to both channels!";
            default    -> "📢 Botdan foydalanish uchun ikkala kanalga a'zo bo'lishingiz kerak!";
        };

        String btn1Text = switch (currentLang) {
            case "RU"  -> "📢 Презентация и PDF";
            case "ENG" -> "📢 Presentation & PDF";
            default    -> "📢 Taqdimot & PDF";
        };

        String btn2Text = switch (currentLang) {
            case "RU"  -> "📢 Образовательные сообщения";
            case "ENG" -> "📢 Education News";
            default    -> "📢 Ta'lim xabarlari";
        };

        String checkText = switch (currentLang) {
            case "RU"  -> "✅ Подтверждение членства";
            case "ENG" -> "✅ Check subscription";
            default    -> "✅ A'zolikni tekshirish";
        };

        rawSender.sendJoinRequestWithStyledButtons(
                bot, chatId.toString(), text,
                btn1Text, "https://t.me/pptx_pdf_mustaqil_ish",
                btn2Text, "https://t.me/talimxabarlari",
                checkText, "CHECK_JOIN"
        );
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

    private void sendAlert(SlideBot bot, String callbackId, String text) {
        try {
            AnswerCallbackQuery alert = new AnswerCallbackQuery();
            alert.setCallbackQueryId(callbackId);
            alert.setText(text);
            alert.setShowAlert(true);
            bot.execute(alert);
        } catch (Exception ignored) {}
    }

    private String getDisplayName(UserEntity user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank())
            return user.getFirstName();
        if (user.getUsername() != null && !user.getUsername().isBlank())
            return "@" + user.getUsername();
        return "Foydalanuvchi";
    }

    private String lang(UserEntity user) {
        return (user.getLanguage() != null) ? user.getLanguage() : "UZ";
    }

    public void forceRejoinIfNeeded(SlideBot bot, UserEntity user, Long chatId) {
        if (user.getLanguage() == null) return;

        boolean in1 = checkSingleChannel(bot, chatId, CHANNEL_1);
        boolean in2 = checkSingleChannel(bot, chatId, CHANNEL_2);

        if (in1 && in2) {
            if (user.getLastWarningTime() != null) {
                user.setLastWarningTime(null);
                userRepository.save(user);
            }
            return;
        }

        if (user.getLastWarningTime() != null) {
            return;
        }

        user.setLastWarningTime(java.time.LocalDateTime.now());
        userRepository.save(user);

        String lang = lang(user);
        StringBuilder sb = new StringBuilder();

        if (!in1 && !in2) {
            sb.append(switch (lang) {
                case "RU" -> "❌ Вы вышли из обоих каналов!\n\n";
                case "ENG" -> "❌ You left both channels!\n\n";
                default -> "❌ Siz ikkala kanaldan ham chiqib ketgansiz!\n\n";
            });
        } else if (!in1) {
            sb.append(switch (lang) {
                case "RU" -> "❌ Вы покинули канал @pptx_pdf_mustaqil_ish!\n\n";
                case "ENG" -> "❌ You left the @pptx_pdf_mustaqil_ish channel!\n\n";
                default -> "❌ Siz @pptx_pdf_mustaqil_ish kanalini tark etdingiz!\n\n";
            });
        } else {
            sb.append(switch (lang) {
                case "RU" -> "❌ Вы покинули канал @talimxabarlari!\n\n";
                case "ENG" -> "❌ You left the @talimxabarlari channel!\n\n";
                default -> "❌ Siz @talimxabarlari kanalini tark etdingiz!\n\n";
            });
        }

        sb.append(switch (lang) {
            case "RU" -> "Если вы не вернетесь в течение 30 минут, доступ к боту будет закрыт!";
            case "ENG" -> "If you do not rejoin within 30 minutes, your access will be revoked!";
            default -> "30 daqiqa ichida qayta qo'shilmasangiz, botdan foydalanish huquqingiz to'xtatiladi!";
        });

        rawSender.sendJoinRequestWithStyledButtons(
                bot, chatId.toString(), sb.toString(),
                !in1 ? "📢 Taqdimot & PDF" : "", "https://t.me/pptx_pdf_mustaqil_ish",
                !in2 ? "📢 Ta'lim xabarlari" : "", "https://t.me/talimxabarlari",
                switch (lang) {
                    case "RU" -> "✅ Проверить подписку";
                    case "ENG" -> "✅ Check subscription";
                    default -> "✅ A'zolikni tekshirish";
                },
                "CHECK_JOIN"
        );
    }

    private boolean checkSingleChannel(SlideBot bot, Long userId, String channelUsername) {
        try {
            GetChatMember getChatMember = new GetChatMember(channelUsername, userId);
            ChatMember member = bot.execute(getChatMember);
            if (member == null || member.getStatus() == null) return false;
            String status = member.getStatus();
            return status.equals("member") || status.equals("administrator") || status.equals("creator");
        } catch (Exception e) {
            return false;
        }
    }
}