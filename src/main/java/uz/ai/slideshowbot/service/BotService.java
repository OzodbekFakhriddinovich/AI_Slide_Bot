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

    private static final String WEB_APP_URL  = "https://ozodbekfakhriddinovich.github.io/AI_Slide_Bot/index.html";
    private static final String CHANNEL_1    = "@pptx_pdf_mustaqil_ish";
    private static final String CHANNEL_2    = "@talimxabarlari";
    private static final long   CACHE_TTL_MS = 60_000L;

    private final Map<Long, long[]> channelMemberCache = new ConcurrentHashMap<>();

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
    @Autowired private ReferatService referatService;

    public boolean handleUpdate(Update update, SlideBot bot) {
        try {
            if (adminCheckService.handleAdminCallback(update, bot)) return true;

            if (update.hasCallbackQuery()) {
                CallbackQuery query      = update.getCallbackQuery();
                String        data       = query.getData();
                Message       callbackMsg = (Message) query.getMessage();
                Long          chatId     = callbackMsg.getChatId();

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
                        if (data.startsWith("TEST_COUNT_"))     testService.handleCountCallback(bot, user, query);
                        else if (data.startsWith("TEST_DIFF_")) testService.handleDifficultyCallback(bot, user, query);
                        else                                    testService.handleConfirmCallback(bot, user, query);
                        answerCallback(bot, query.getId(), false);
                        return true;
                    }

                    if (data.startsWith("REF_SAHIFA_") || data.equals("REF_CONFIRM")) {
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
                        if (data.startsWith("REF_SAHIFA_")) referatService.handleSahifaCallback(bot, user, query);
                        else                                referatService.handleConfirmCallback(bot, user, query);
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
                                        int cnt = referrer.getReferralBonusCount() == null ? 0 : referrer.getReferralBonusCount();
                                        if (cnt < 4) {
                                            referrer.setBalance(referrer.getBalance() + 1000);
                                            referrer.setReferralBonusCount(cnt + 1);
                                            userRepository.save(referrer);
                                            String notifyText = switch (lang(referrer)) {
                                                case "RU"  -> "🎉 Ваш друг <b>" + getDisplayName(user) + "</b> присоединился!\n💰 <b>+1000 сум</b> зачислено.";
                                                case "ENG" -> "🎉 Your friend <b>" + getDisplayName(user) + "</b> joined!\n💰 <b>+1000 sum</b> added.";
                                                default    -> "🎉 Do'stingiz <b>" + getDisplayName(user) + "</b> qo'shildi!\n💰 <b>+1000 so'm</b> qo'shildi.";
                                            };
                                            try {
                                                SendMessage n = new SendMessage(referrer.getChatId().toString(), notifyText);
                                                n.setParseMode("HTML");
                                                bot.execute(n);
                                            } catch (Exception ignored) {}
                                        }
                                    }
                                    user.setReferralBonusGiven(true);
                                    userRepository.save(user);
                                }
                                AnswerCallbackQuery ans = new AnswerCallbackQuery();
                                ans.setCallbackQueryId(query.getId());
                                ans.setText(switch (lang(user)) {
                                    case "RU" -> "🎉 Добро пожаловать!"; case "ENG" -> "🎉 Welcome!"; default -> "🎉 Xush kelibsiz!";
                                });
                                ans.setShowAlert(true);
                                bot.execute(ans);
                                menuService.sendMainMenu(bot, chatId, user.getLanguage());
                            } else {
                                sendAlert(bot, query.getId(), switch (lang(user)) {
                                    case "RU"  -> "❌ Вы ещё не подписались!";
                                    case "ENG" -> "❌ You haven't joined yet!";
                                    default    -> "❌ Siz hali a'zo bo'lmagansiz!";
                                });
                            }
                        }
                        case "ZIP_SAVE" -> zipService.handleZipConfirmReply(update, bot, user);
                        case "ZIP_BACK" -> {
                            user.setInZipFlow(false); userRepository.save(user);
                            deleteMessage(bot, callbackMsg);
                            menuService.sendMainMenu(bot, chatId, user.getLanguage());
                        }
                        case "BALANCE_PAY" -> balanceService.handleBuy(bot, user, chatId, null);
                        case "REFERAT_MENU_BACK", "TEST_MENU_BACK" -> {
                            menuService.sendSimpleMenu(bot, chatId, lang(user));
                        }
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
            Long    chatId  = message.getChatId();

            UserEntity user = userRepository.findByChatId(chatId).orElseGet(() -> {
                UserEntity u = new UserEntity();
                u.setChatId(chatId); u.setBalance(0); u.setState(UserState.START);
                userRepository.save(u);
                return u;
            });

            if (message.getFrom() != null) {
                boolean changed = false;
                String nf = message.getFrom().getFirstName(), nu = message.getFrom().getUserName();
                if (!Objects.equals(user.getFirstName(), nf)) { user.setFirstName(nf); changed = true; }
                if (!Objects.equals(user.getUsername(), nu))  { user.setUsername(nu);  changed = true; }
                if (changed) userRepository.save(user);
            }

            if (message.getFrom() != null && "slides_admin1".equals(message.getFrom().getUserName())) {
                broadcastService.broadcastToAll(bot, message);
                bot.execute(new SendMessage(chatId.toString(), "✅ Xabar barcha foydalanuvchilarga yuborildi."));
                return true;
            }

            if (message.getWebAppData() != null) {
                slideFlowSingleService.handleWebAppData(user, message, bot);
                return true;
            }

            String text = message.hasText() ? message.getText() : "";

            if (user.getLanguage() == null || user.getLanguage().isEmpty()) {
                if (!text.equals("🇺🇿 O'zbekcha") && !text.equals("🇷🇺 Русский") && !text.equals("🇺🇸 English")
                    && !text.equals("/start") && !text.startsWith("/start ")) {
                    menuService.sendLanguageSelector(bot, chatId);
                    return true;
                }
            }

            boolean isStart   = text.equals("/start") || text.startsWith("/start ");
            boolean isLangSel = text.equals("🇺🇿 O'zbekcha") || text.equals("🇷🇺 Русский") || text.equals("🇺🇸 English");

            if (isStart) {
                user.setInCheckFlow(false); user.setInZipFlow(false); user.setInTestFlow(false);
                referatService.resetFlow(user);
                user.setState(UserState.START);
                String refCode = null;
                if (message.hasText()) {
                    String[] parts = message.getText().trim().split("\\s+");
                    if (parts.length > 1 && parts[1].startsWith("ref_")) refCode = parts[1];
                }
                if (refCode != null) referralService.handleReferralJoin(bot, user, refCode);
                userRepository.save(user);
                if (user.getLanguage() == null || user.getLanguage().isEmpty()) {
                    menuService.sendLanguageSelector(bot, chatId); return true;
                }
                if (!isUserInChannel(bot, chatId)) { sendJoinRequest(bot, chatId, user.getLanguage()); return true; }
                menuService.sendMainMenu(bot, chatId, user.getLanguage());
                return true;
            }

            if (isLangSel) {
                String selectedLang = switch (text) {
                    case "🇷🇺 Русский" -> "RU"; case "🇺🇸 English" -> "ENG"; default -> "UZ";
                };
                user.setLanguage(selectedLang);
                userRepository.save(user);
                try {
                    deleteUserMessage(bot, message);
                    if (user.getLastLanguageMessageId() != null) {
                        deleteMessage(bot, chatId, user.getLastLanguageMessageId());
                        user.setLastLanguageMessageId(null);
                    } else deleteMessage(bot, chatId, message.getMessageId() - 1);
                    userRepository.save(user);
                } catch (Exception ignored) {}
                if (!isUserInChannel(bot, chatId)) sendJoinRequest(bot, chatId, user.getLanguage());
                else menuService.sendMainMenu(bot, chatId, user.getLanguage());
                return true;
            }

            if (user.getLanguage() != null && !isUserInChannel(bot, chatId)) {
                sendJoinRequest(bot, chatId, user.getLanguage()); return true;
            }

            boolean isBack = text.equals("◀️ Orqaga") || text.equals("◀️ Back")
                             || text.equals("◀️ Наzad") || text.equals("◀️ Назад");
            if (isBack) {
                user.setInZipFlow(false); user.setInCheckFlow(false);
                testService.resetTestFlow(user);
                wordPdfService.resetFlow(user);
                referatService.resetFlow(user);
                user.setState(UserState.START);
                userRepository.save(user);
                menuService.sendSimpleMenu(bot, chatId, user.getLanguage());
                return true;
            }

            if (Boolean.TRUE.equals(user.getInReferatFlow())) {
                if (message.hasText()) {
                    if (user.getState() == UserState.WAITING_REFERAT_TOPIC) {
                        referatService.handleTopicInput(bot, user, chatId, text);
                        return true;
                    }
                    if (user.getState() == UserState.WAITING_REFERAT_ISM) {
                        referatService.handleIsmInput(bot, user, chatId, text);
                        return true;
                    }
                }
                String lang = lang(user);
                String warn = switch (lang) {
                    case "RU"  -> "✏️ Пожалуйста, введите текстовое сообщение.";
                    case "ENG" -> "✏️ Please type a text message.";
                    default    -> "✏️ Iltimos, matn yozing.";
                };
                rawSender.sendWithColoredBtn(bot, chatId.toString(), warn,
                        switch (lang) { case "RU" -> "◀️ Назад"; case "ENG" -> "◀️ Back"; default -> "◀️ Orqaga"; }, "danger");
                return true;
            }

            if (message.hasText()) {
                switch (text) {
                    case "🎞️ Taqdimot yaratish", "🎞️ Создать презентацию", "🎞️ Create Presentation" -> {
                        user.setInCheckFlow(false); userRepository.save(user);
                        KeyboardButton wb = new KeyboardButton(switch (user.getLanguage()) {
                            case "RU" -> "⚙️ Открыть настройки презентации";
                            case "ENG" -> "⚙️ Open presentation settings";
                            default -> "⚙️ Taqdimot sozlamalarini kiritish";
                        });
                        wb.setWebApp(new WebAppInfo(WEB_APP_URL));
                        KeyboardRow wr = new KeyboardRow(); wr.add(wb);
                        ReplyKeyboardMarkup wm = new ReplyKeyboardMarkup(List.of(wr)); wm.setResizeKeyboard(true);
                        SendMessage wMsg = new SendMessage(chatId.toString(), switch (user.getLanguage()) {
                            case "RU" -> "Выберите необходимые настройки!";
                            case "ENG" -> "Choose your settings!";
                            default -> "Sozlamlarni tanlang!";
                        });
                        wMsg.setReplyMarkup(wm); bot.execute(wMsg);
                        return true;
                    }
                    case "💰 Balans", "💰 Баланс", "💰 Balance", "/balance" -> {
                        user.setInCheckFlow(false); userRepository.save(user);
                        balanceService.showBalance(bot, user, chatId, null); return true;
                    }
                    case "/check", "/chek" -> { balanceService.handleCheckFlow(bot, user, chatId); return true; }
                    case "/buy" -> {
                        user.setInCheckFlow(false); userRepository.save(user);
                        balanceService.handleBuy(bot, user, chatId, null); return true;
                    }
                    case "/referal", "Referral", "Реферал" -> {
                        user.setInCheckFlow(false); userRepository.save(user);
                        referralService.handleReferralCommand(bot, user, chatId); return true;
                    }
                    case "🌐 Til Sozlamalari", "🌐 Язык", "🌐 Language Settings" -> {
                        user.setInCheckFlow(false); userRepository.save(user);
                        menuService.sendLanguageSelector(bot, chatId); return true;
                    }
                    case "📦 Fayllarni Zip qilish", "📦 Zip Files", "📦 Архивировать файлы (Zip)" -> {
                        user.setInCheckFlow(false); user.setInZipFlow(true); userRepository.save(user);
                        rawSender.sendColoredSingleMenu(bot, chatId.toString(),
                                switch (user.getLanguage()) {
                                    case "RU" -> "📂 Пожалуйста, отправьте файл для создания ZIP.";
                                    case "ENG" -> "📂 Please send any file to create ZIP.";
                                    default -> "📂 ZIP yaratish uchun fayl yuboring.";
                                }, zipService.getBackBtnText(user.getLanguage()), "danger");
                        return true;
                    }
                    case "📋 Test tayyorlash", "📋 Создать тест", "📋 Create Test" -> {
                        user.setInCheckFlow(false); userRepository.save(user);
                        testService.handleTestMenu(bot, user, chatId); return true;
                    }
                    case "📄 Word <--> PDF" -> {
                        user.setInCheckFlow(false); userRepository.save(user);
                        wordPdfService.handleMenu(bot, user, chatId); return true;
                    }
                    case "📝 Referat tayyorlash", "📝 Создать реферат", "📝 Create Essay" -> {
                        user.setInCheckFlow(false); userRepository.save(user);
                        referatService.startFlow(bot, user, chatId); return true;
                    }
                    case "📄 PDF yaratish", "📄 Создать PDF", "📄 Create PDF" -> {
                        user.setInCheckFlow(false); userRepository.save(user);
                        try { pdfService.handlePdfFlow(update, bot, user); } catch (Exception e) { e.printStackTrace(); }
                        return true;
                    }
                }
            }

            if (Boolean.TRUE.equals(user.getInCheckFlow())) {
                if (message.hasPhoto() || message.hasDocument()) {
                    balanceService.handleCheckFile(bot, user, message);
                    adminCheckService.sendCheckToAdmin(message, user, bot);
                    user.setInCheckFlow(false); userRepository.save(user);
                    return true;
                }
                if (message.hasText() && !text.equals("/check") && !text.equals("/chek")) {
                    user.setInCheckFlow(false); userRepository.save(user);
                }
            }

            if (Boolean.TRUE.equals(user.getInZipFlow())) {
                if (message.hasPhoto() || message.hasDocument()) { zipService.handleFile(update, bot, user); return true; }
                if (text.equals("📦 ZIP yaratish") || text.equals("📦 Create ZIP") || text.equals("📦 Создать ZIP")) {
                    zipService.handleZipConfirmReply(update, bot, user); return true;
                }
                if (message.hasAudio() || message.hasVideo() || message.hasVoice()) {
                    sendMessage(bot, chatId, switch (user.getLanguage()) {
                        case "RU" -> "❌ Только фото и документы!";
                        case "ENG" -> "❌ Only photos and documents!";
                        default -> "❌ Faqat rasm va fayllar!";
                    }, zipService.zipBackKeyboard(user.getLanguage()), false);
                    return true;
                }
                return true;
            }

            if (Boolean.TRUE.equals(user.getInWordPdfFlow())) {
                String lang = lang(user);
                String bk = switch (lang) { case "RU" -> "◀️ Назад"; case "ENG" -> "◀️ Back"; default -> "◀️ Orqaga"; };
                if (text.equals("✅ Tasdiqlash") || text.equals("✅ Подтвердить") || text.equals("✅ Confirm")) {
                    wordPdfService.handleConfirm(bot, user, chatId); return true;
                }
                if (message.hasDocument()) { wordPdfService.handleFileInput(bot, user, message); return true; }
                rawSender.sendColoredSingleMenu(bot, chatId.toString(), switch (lang) {
                    case "RU" -> "❌ Я принимаю только .docx и .pdf файлы!";
                    case "ENG" -> "❌ I only accept .docx and .pdf files!";
                    default -> "❌ Faqat .docx va .pdf fayllarni qabul qilaman!";
                }, bk, "danger");
                return true;
            }

            if (Boolean.TRUE.equals(user.getInTestFlow())) {
                if (user.getState() == UserState.WAITING_TEST_TOPIC && message.hasText()) {
                    testService.handleTopicInput(bot, user, chatId, text); return true;
                }
                if (user.getState() == UserState.WAITING_TEST_QUESTION_COUNT && message.hasText()) {
                    testService.handleCustomCountInput(bot, user, chatId, text); return true;
                }
                return true;
            }

            if (user.getState() == UserState.WAITING_TOPIC || user.getState() == UserState.WAITING_INFO) {
                slideFlowSingleService.handleMessage(user, message, bot); return true;
            }

            try { if (pdfService.handlePdfFlow(update, bot, user)) return true; } catch (Exception ignored) {}

            if (user.getState() == UserState.START || user.getState() == null) {
                String lang = lang(user);
                String unknownMsg = switch (lang) {
                    case "RU"  -> "❌ Неизвестная команда.\n\n/start — открыть меню";
                    case "ENG" -> "❌ Unknown command.\n\n/start — open menu";
                    default    -> "❌ Noto'g'ri buyruq.\n\n/start — menyuni ochish";
                };
                sendMessage(bot, chatId, unknownMsg, null, false);
                return true;
            }

            // Hech qaysi flow aktiv bo'lmasa, main menuga qaytaramiz
            menuService.sendSimpleMenu(bot, chatId, lang(user));

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return false;
    }

    public boolean isUserInChannel(SlideBot bot, Long userId) {
        long[] cached = channelMemberCache.get(userId);
        if (cached != null && System.currentTimeMillis() - cached[0] < CACHE_TTL_MS) return cached[1] == 1L;
        return refreshCacheAndCheck(bot, userId);
    }

    public boolean refreshCacheAndCheck(SlideBot bot, Long userId) {
        boolean m = checkSingleChannel(bot, userId, CHANNEL_1) && checkSingleChannel(bot, userId, CHANNEL_2);
        channelMemberCache.put(userId, new long[]{System.currentTimeMillis(), m ? 1L : 0L});
        return m;
    }

    public void updateChannelCache(Long userId, boolean isMember) {
        channelMemberCache.put(userId, new long[]{System.currentTimeMillis(), isMember ? 1L : 0L});
    }

    public void clearChannelCache(Long userId) { channelMemberCache.remove(userId); }

    public void forceRejoinIfNeeded(SlideBot bot, UserEntity user, Long chatId) {
        if (user.getLanguage() == null) return;
        boolean in1 = checkSingleChannel(bot, chatId, CHANNEL_1);
        boolean in2 = checkSingleChannel(bot, chatId, CHANNEL_2);
        if (in1 && in2) {
            if (user.getLastWarningTime() != null) { user.setLastWarningTime(null); userRepository.save(user); }
            return;
        }
        if (user.getLastWarningTime() != null) return;
        user.setLastWarningTime(java.time.LocalDateTime.now()); userRepository.save(user);
        sendChannelWarningMessage(bot, user, chatId, in1, in2);
    }

    public void sendChannelWarningMessage(SlideBot bot, UserEntity user, Long chatId, boolean in1, boolean in2) {
        String lang = lang(user);
        StringBuilder sb = new StringBuilder();
        if (!in1 && !in2) sb.append(switch (lang) {
            case "RU"  -> "❌ Вы вышли из обоих каналов!\n\n";
            case "ENG" -> "❌ You left both channels!\n\n";
            default    -> "❌ Siz ikkala kanaldan chiqib ketgansiz!\n\n";
        });
        else if (!in1) sb.append(switch (lang) {
            case "RU"  -> "❌ Вы покинули канал @pptx_pdf_mustaqil_ish!\n\n";
            case "ENG" -> "❌ You left @pptx_pdf_mustaqil_ish!\n\n";
            default    -> "❌ Siz @pptx_pdf_mustaqil_ish kanalini tark etdingiz!\n\n";
        });
        else sb.append(switch (lang) {
                case "RU"  -> "❌ Вы покинули канал @talimxabarlari!\n\n";
                case "ENG" -> "❌ You left @talimxabarlari!\n\n";
                default    -> "❌ Siz @talimxabarlari kanalini tark etdingiz!\n\n";
            });
        sb.append(switch (lang) {
            case "RU"  -> "Вернитесь в течение 30 минут!";
            case "ENG" -> "Rejoin within 30 minutes!";
            default    -> "30 daqiqa ichida qayta qo'shiling!";
        });
        rawSender.sendJoinRequestWithStyledButtons(bot, chatId.toString(), sb.toString(),
                !in1 ? "📢 Taqdimot & PDF" : "", "https://t.me/pptx_pdf_mustaqil_ish",
                !in2 ? "📢 Ta'lim xabarlari" : "", "https://t.me/talimxabarlari",
                switch (lang) { case "RU" -> "✅ Проверить подписку"; case "ENG" -> "✅ Check subscription"; default -> "✅ A'zolikni tekshirish"; },
                "CHECK_JOIN");
    }

    private boolean checkSingleChannel(SlideBot bot, Long userId, String channelUsername) {
        try {
            ChatMember member = bot.execute(new GetChatMember(channelUsername, userId));
            if (member == null || member.getStatus() == null) return false;
            String s = member.getStatus();
            return s.equals("member") || s.equals("administrator") || s.equals("creator");
        } catch (Exception e) { return false; }
    }

    private void answerCallback(SlideBot bot, String id, boolean alert) {
        try { AnswerCallbackQuery a = new AnswerCallbackQuery(); a.setCallbackQueryId(id); a.setShowAlert(alert); bot.execute(a); } catch (Exception ignored) {}
    }

    private void deleteMessage(SlideBot bot, Long chatId, Integer msgId) {
        try { if (msgId != null) bot.execute(new DeleteMessage(chatId.toString(), msgId)); } catch (Exception ignored) {}
    }

    private void deleteMessage(SlideBot bot, Message msg) {
        try { if (msg != null) bot.execute(new DeleteMessage(msg.getChatId().toString(), msg.getMessageId())); } catch (Exception ignored) {}
    }

    private void deleteUserMessage(SlideBot bot, Message msg) {
        try { if (msg != null) bot.execute(new DeleteMessage(msg.getChatId().toString(), msg.getMessageId())); } catch (Exception ignored) {}
    }

    private void sendJoinRequest(SlideBot bot, Long chatId, String lang) {
        String l = lang != null ? lang : "UZ";
        rawSender.sendJoinRequestWithStyledButtons(bot, chatId.toString(),
                switch (l) { case "RU" -> "📢 Для использования бота подпишитесь на оба канала!"; case "ENG" -> "📢 Subscribe to both channels to use the bot!"; default -> "📢 Botdan foydalanish uchun ikkala kanalga a'zo bo'ling!"; },
                switch (l) { case "RU" -> "📢 Презентация и PDF"; case "ENG" -> "📢 Presentation & PDF"; default -> "📢 Taqdimot & PDF"; }, "https://t.me/pptx_pdf_mustaqil_ish",
                switch (l) { case "RU" -> "📢 Образовательные новости"; case "ENG" -> "📢 Education News"; default -> "📢 Ta'lim xabarlari"; }, "https://t.me/talimxabarlari",
                switch (l) { case "RU" -> "✅ Подтверждение"; case "ENG" -> "✅ Check subscription"; default -> "✅ A'zolikni tekshirish"; },
                "CHECK_JOIN");
    }

    private Message sendMessage(SlideBot bot, Long chatId, String text, ReplyKeyboard keyboard, boolean isLangMsg) {
        try {
            SendMessage msg = new SendMessage(chatId.toString(), text);
            msg.setParseMode("HTML");
            if (keyboard != null) msg.setReplyMarkup(keyboard);
            Message sent = bot.execute(msg);
            userRepository.findByChatId(chatId).ifPresent(u -> {
                if (isLangMsg) u.setLastLanguageMessageId(sent.getMessageId());
                else u.setLastBotMessageId(sent.getMessageId());
                userRepository.save(u);
            });
            return sent;
        } catch (Exception e) { e.printStackTrace(); return null; }
    }

    private void sendAlert(SlideBot bot, String callbackId, String text) {
        try { AnswerCallbackQuery a = new AnswerCallbackQuery(); a.setCallbackQueryId(callbackId); a.setText(text); a.setShowAlert(true); bot.execute(a); } catch (Exception ignored) {}
    }

    private String getDisplayName(UserEntity user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) return user.getFirstName();
        if (user.getUsername() != null && !user.getUsername().isBlank()) return "@" + user.getUsername();
        return "Foydalanuvchi";
    }

    private String lang(UserEntity user) { return user.getLanguage() != null ? user.getLanguage() : "UZ"; }
}