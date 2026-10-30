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

    @org.springframework.beans.factory.annotation.Value("${slides.web-app-url:https://ozodbekfakhriddinovich.github.io/AI_Slide_Bot/index.html?v=2.0}")
    private String WEB_APP_URL;
    private static final long   CACHE_TTL_MS = 60_000L;

    private final Map<Long, long[]> channelMemberCache = new ConcurrentHashMap<>();

    @Autowired private ReferralService       referralService;
    @Autowired private ZipService            zipService;
    @Autowired private UserRepository        userRepository;
    @Autowired private uz.ai.slideshowbot.repository.ChannelRepository channelRepository;
    @Autowired private PdfService            pdfService;
    @Autowired private BalanceService        balanceService;
    @Autowired private AdminCheckService     adminCheckService;
    @Autowired private BroadcastService      broadcastService;
    @Autowired private MenuService           menuService;
    @Autowired private SlideFlowSingleService slideFlowSingleService;
    @Autowired private TelegramRawSender     rawSender;
    @Autowired private TestService           testService;
    @Autowired private WordPdfService        wordPdfService;
    @Autowired private ReferatService        referatService;
    @Autowired private VideoTutorialService  videoTutorialService;

    // Temporary storage for admin channel flow
    private final Map<Long, uz.ai.slideshowbot.entity.ChannelEntity> pendingChannels = new ConcurrentHashMap<>();

    public boolean handleUpdate(Update update, SlideBot bot) {
        try {
            if (adminCheckService.handleAdminCallback(update, bot)) return true;

            if (update.hasCallbackQuery()) {
                CallbackQuery query = update.getCallbackQuery();
                String data = query.getData();
                Long chatId = query.getMessage().getChatId();
                Message callbackMsg = (Message) query.getMessage();

                // Reset PDF flow if they click any inline button
                pdfService.resetFlow(chatId);

                UserEntity user = userRepository.findByChatId(chatId).orElse(null);
                if (user == null) return false;

                try {
                    if (videoTutorialService.handleAdminCallback(bot, chatId, callbackMsg.getMessageId(), data)) {
                        answerCallback(bot, query.getId(), false);
                        return true;
                    }

                    if (data.startsWith("SLIDE_LANG_") || data.startsWith("SLIDE_NUM_")
                            || data.startsWith("SLIDE_TPL_") || data.startsWith("SLIDE_TARIFF_")) {
                        slideFlowSingleService.handleCallback(user, query, bot);
                        return true;
                    }

                    if (data.startsWith("TEST_COUNT_") || data.startsWith("TEST_DIFF_") || data.equals("TEST_CONFIRM")) {
                        if (!isUserInChannel(bot, chatId)) {
                            sendAlert(bot, query.getId(), switch (lang(user)) {
                                case "RU"  -> "❌ Пожалуйста, подпишитесь на канал для продолжения!";
                                case "ENG" -> "❌ Please subscribe to the channel to continue!";
                                default    -> "❌ Davom etish uchun kanalga a'zo bo'ling!";
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
                                case "RU"  -> "❌ Пожалуйста, подпишитесь на канал для продолжения!";
                                case "ENG" -> "❌ Please subscribe to the channel to continue!";
                                default    -> "❌ Davom etish uchun kanalga a'zo bo'ling!";
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
                            case "RU"  -> "❌ Сначала подпишитесь на наш канал!";
                            case "ENG" -> "❌ Please join our channel first!";
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
                        case "BALANCE_PAY" -> balanceService.showPaymentAmounts(bot, user, chatId, callbackMsg.getMessageId());
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
                        case "CHECK_AUTO_PAYMENT" -> {
                            String chkLang = lang(user);
                            String chkAlert = switch (chkLang) {
                                case "RU" -> "⏳ Оплата ещё не обнаружена. Переведите точную сумму и подождите 1-2 секунды.";
                                case "ENG" -> "⏳ Payment not detected yet. Transfer the exact amount and wait 1-2 seconds.";
                                default -> "⏳ To'lov hali aniqlanmadi. Aynan ko'rsatilgan summani o'tkazing va 1-2 soniya kuting.";
                            };
                            try {
                                org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery ans =
                                        new org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery();
                                ans.setCallbackQueryId(query.getId());
                                ans.setText(chkAlert);
                                ans.setShowAlert(true);
                                bot.execute(ans);
                            } catch (Exception ignored) {}
                            return true;
                        }
                        case "REFERAT_MENU_BACK", "TEST_MENU_BACK" -> {
                            menuService.sendSimpleMenu(bot, chatId, lang(user));
                        }
                        case "ADMIN_TOTAL_USERS" -> {
                            if (chatId.equals(6290351864L)) {
                                long count = userRepository.count();
                                try { bot.execute(new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "👥 Jami foydalanuvchilar: " + count + " ta")); } catch (Exception ignored) {}
                            }
                            answerCallback(bot, query.getId(), false); return true;
                        }
                        case "ADMIN_TOTAL_BALANCE" -> {
                            if (chatId.equals(6290351864L)) {
                                Long sum = userRepository.getTotalBalance();
                                try { bot.execute(new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "💰 Jami summa: " + sum + " so'm")); } catch (Exception ignored) {}
                            }
                            answerCallback(bot, query.getId(), false); return true;
                        }
                        case "ADMIN_USER_MENU" -> {
                            if (chatId.equals(6290351864L)) menuService.sendSimpleMenu(bot, chatId, lang(user));
                            answerCallback(bot, query.getId(), false); return true;
                        }
                        case "ADMIN_ADD_CHANNEL" -> {
                            if (chatId.equals(6290351864L)) {
                                user.setState(UserState.ADMIN_WAITING_CHANNEL_URL);
                                userRepository.save(user);
                                try { bot.execute(new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "➕ Kanal qo'shish\n\nKanal havolasini yuboring (public kanal):\nMasalan: @kanal_nomi yoki https://t.me/kanal_nomi")); } catch (Exception ignored) {}
                            }
                            answerCallback(bot, query.getId(), false); return true;
                        }
                        case "ADMIN_CHAN_REQ_YES", "ADMIN_CHAN_REQ_NO" -> {
                            if (chatId.equals(6290351864L) && user.getState() == UserState.ADMIN_WAITING_CHANNEL_MANDATORY) {
                                uz.ai.slideshowbot.entity.ChannelEntity ce = pendingChannels.remove(chatId);
                                if (ce != null) {
                                    ce.setIsRequired(data.equals("ADMIN_CHAN_REQ_YES"));
                                    channelRepository.save(ce);
                                    user.setState(UserState.START);
                                    userRepository.save(user);
                                    try { bot.execute(new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "✅ Kanal qo'shildi!\n\n🔗 " + ce.getUsername() + "\n🏷 Nom: " + ce.getName() + "\n📌 Majburiy: " + (ce.getIsRequired() ? "Ha" : "Yo'q") + "\n\n⚠️ Eslatma: a'zolikni tekshirish uchun bot shu kanalda admin bo'lishi shart.")); } catch (Exception ignored) {}
                                    deleteMessage(bot, callbackMsg);
                                }
                            }
                            answerCallback(bot, query.getId(), false); return true;
                        }
                        case "ADMIN_CHANNELS" -> {
                            if (chatId.equals(6290351864L)) {
                                java.util.List<uz.ai.slideshowbot.entity.ChannelEntity> list = channelRepository.findAll();
                                org.telegram.telegrambots.meta.api.methods.send.SendMessage msg = new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "📋 Ulangan kanallar (" + list.size() + " ta)\n\nKo'rish uchun tugmani bosing.");
                                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                                java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();
                                for (uz.ai.slideshowbot.entity.ChannelEntity ch : list) {
                                    java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> r = new java.util.ArrayList<>();
                                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btn1 = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
                                    btn1.setText("📢 " + ch.getName());
                                    String url = ch.getUsername().startsWith("@") ? "https://t.me/" + ch.getUsername().substring(1) : ch.getUsername();
                                    btn1.setUrl(url);
                                    org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btn2 = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton();
                                    btn2.setText("👁 Ko'rish"); btn2.setCallbackData("ADMIN_CHANNELS_VIEW_" + ch.getId());
                                    r.add(btn1); r.add(btn2);
                                    rows.add(r);
                                }
                                markup.setKeyboard(rows);
                                msg.setReplyMarkup(markup);
                                try { bot.execute(msg); } catch (Exception ignored) {}
                            }
                            answerCallback(bot, query.getId(), false); return true;
                        }
                        default -> {
                            if (data.startsWith("ADMIN_CHANNELS_VIEW_")) {
                                if (chatId.equals(6290351864L)) {
                                    try {
                                        Long id = Long.parseLong(data.replace("ADMIN_CHANNELS_VIEW_", ""));
                                        uz.ai.slideshowbot.entity.ChannelEntity ch = channelRepository.findById(id).orElse(null);
                                        if (ch != null) {
                                            String info = "<code>Kanal    : " + ch.getName() + "\nManzil   : " + ch.getUsername() + "\nMajburiy : " + (ch.getIsRequired() ? "Ha" : "Yo'q") + "\nHolati   : 🟢 Active\nQo'shildi: " + ch.getAddedUsersCount() + " ta user</code>";
                                            org.telegram.telegrambots.meta.api.methods.send.SendMessage msg2 = new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), info);
                                            msg2.setParseMode("HTML");
                                            org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup2 = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                                            java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> r = new java.util.ArrayList<>();
                                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton b1 = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(); b1.setText("✅ Qoldirish"); b1.setCallbackData("ADMIN_CHANNEL_KEEP");
                                            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton b2 = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(); b2.setText("🔌 Botdan uzish"); b2.setCallbackData("ADMIN_CHANNEL_REMOVE_" + id);
                                            r.add(b1); r.add(b2);
                                            markup2.setKeyboard(java.util.List.of(r));
                                            msg2.setReplyMarkup(markup2);
                                            bot.execute(msg2);
                                        }
                                    } catch (Exception ignored) {}
                                }
                                answerCallback(bot, query.getId(), false); return true;
                            }
                            if (data.startsWith("ADMIN_CHANNEL_REMOVE_")) {
                                if (chatId.equals(6290351864L)) {
                                    try {
                                        Long id = Long.parseLong(data.replace("ADMIN_CHANNEL_REMOVE_", ""));
                                        channelRepository.deleteById(id);
                                        deleteMessage(bot, callbackMsg);
                                        bot.execute(new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "✅ Kanal botdan uzildi va o'chirildi."));
                                    } catch (Exception ignored) {}
                                }
                                answerCallback(bot, query.getId(), false); return true;
                            }
                            if (data.equals("ADMIN_CHANNEL_KEEP")) {
                                deleteMessage(bot, callbackMsg);
                                answerCallback(bot, query.getId(), false); return true;
                            }
                            // PAY_AUTO_6000 yoki PAY_BANK_6000 formatidagi callbacklar
                            if (data.equals("SLIDE_TOPUP_5000") || data.equals("SLIDE_TOPUP_7000")) {
                                int amount = Integer.parseInt(data.substring("SLIDE_TOPUP_".length()));
                                balanceService.handleAutoPayment(bot, user, chatId, amount, callbackMsg.getMessageId());
                                answerCallback(bot, query.getId(), false);
                                return true;
                            } else if (data.startsWith("PAY_AUTO_")) {
                                try {
                                    int baseAmount = Integer.parseInt(data.replace("PAY_AUTO_", ""));
                                    balanceService.handleAutoPayment(bot, user, chatId, baseAmount, callbackMsg.getMessageId());
                                } catch (NumberFormatException ignored) {}
                                return true;
                            } else if (data.startsWith("PAY_BANK_")) {
                                try {
                                    int baseAmount = Integer.parseInt(data.replace("PAY_BANK_", ""));
                                    balanceService.handleBankPayment(bot, user, chatId, baseAmount, callbackMsg.getMessageId());
                                } catch (NumberFormatException ignored) {}
                                return true;
                            }
                        }
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

            if (message.getWebAppData() == null && (chatId.equals(8429918073L) || (message.getFrom() != null && "slides_admin1".equals(message.getFrom().getUserName())))) {
                broadcastService.broadcastToAll(bot, message);
                return true;
            }

            if (message.getWebAppData() != null) {
                if (user.getLanguage() == null || user.getLanguage().isBlank()) {
                    menuService.sendLanguageSelector(bot, chatId);
                    return true;
                }
                if (!isUserInChannel(bot, chatId)) {
                    sendJoinRequest(bot, chatId, user.getLanguage());
                    return true;
                }
                slideFlowSingleService.handleWebAppData(user, message, bot);
                return true;
            }

            String text = message.hasText() ? message.getText() : "";

            if ("📹 Video qo'shish".equals(text) && videoTutorialService.isVideoAdmin(chatId)) {
                videoTutorialService.showButtonSelector(bot, chatId);
                return true;
            }

            if (videoTutorialService.isVideoAdmin(chatId) && videoTutorialService.isAdminInFlow(chatId)) {
                videoTutorialService.handleAdminMessage(bot, chatId, message);
                return true;
            }

            if (user.getLanguage() == null || user.getLanguage().isEmpty()) {
                if (!text.equals("🇺🇿 O'zbekcha") && !text.equals("🇷🇺 Русский") && !text.equals("🇺🇸 English")
                        && !text.equals("/start") && !text.startsWith("/start ")) {
                    menuService.sendLanguageSelector(bot, chatId);
                    return true;
                }
            }

            boolean isStart   = text.equals("/start") || text.startsWith("/start ");
            boolean isLangSel = text.equals("🇺🇿 O'zbekcha") || text.equals("🇷🇺 Русский") || text.equals("🇺🇸 English");

            if (text.equals("/admin") && chatId.equals(6290351864L)) {
                user.setState(UserState.START);
                userRepository.save(user);
                org.telegram.telegrambots.meta.api.methods.send.SendMessage msg = new org.telegram.telegrambots.meta.api.methods.send.SendMessage();
                msg.setChatId(chatId.toString());
                msg.setText("🛠 Admin panel\n\nKerakli bo'limni tanlang.\n\n/user — oddiy menyuga qaytish");

                org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup markup = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                java.util.List<java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton>> rows = new java.util.ArrayList<>();

                java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> r1 = new java.util.ArrayList<>();
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton b1 = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(); b1.setText("💰 Jami summa"); b1.setCallbackData("ADMIN_TOTAL_BALANCE");
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton b2 = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(); b2.setText("👥 Jami userlar"); b2.setCallbackData("ADMIN_TOTAL_USERS");
                r1.add(b1); r1.add(b2);

                java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> r2 = new java.util.ArrayList<>();
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton b3 = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(); b3.setText("📋 Kanallar"); b3.setCallbackData("ADMIN_CHANNELS");
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton b4 = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(); b4.setText("👤 User menyu"); b4.setCallbackData("ADMIN_USER_MENU");
                r2.add(b3); r2.add(b4);

                java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> r3 = new java.util.ArrayList<>();
                org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton b5 = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(); b5.setText("➕ Kanal qo'shish"); b5.setCallbackData("ADMIN_ADD_CHANNEL");
                r3.add(b5);

                rows.add(r1); rows.add(r2); rows.add(r3);
                markup.setKeyboard(rows);
                msg.setReplyMarkup(markup);

                try { bot.execute(msg); } catch (Exception ignored) {}
                return true;
            }
            if (text.equals("/user") && chatId.equals(6290351864L)) {
                menuService.sendSimpleMenu(bot, chatId, lang(user));
                return true;
            }

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

            if (message.hasText() && isCommandOrMenu(text)) {
                user.setInZipFlow(false); user.setInCheckFlow(false);
                testService.resetTestFlow(user);
                wordPdfService.resetFlow(user);
                referatService.resetFlow(user);
                pdfService.resetFlow(chatId);
                user.setState(UserState.START);
                userRepository.save(user);
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
                rawSender.sendWithColoredBtn(bot, chatId.toString(),
                        switch (lang) {
                            case "RU"  -> "✏️ Пожалуйста, введите текстовое сообщение.";
                            case "ENG" -> "✏️ Please type a text message.";
                            default    -> "✏️ Iltimos, matn yozing.";
                        },
                        switch (lang) { case "RU" -> "◀️ Назад"; case "ENG" -> "◀️ Back"; default -> "◀️ Orqaga"; },
                        "danger");
                return true;
            }

            if (message.hasText()) {

                if (chatId == 6290351864L && (text.matches("^\\d+\\+\\d+$") || text.matches("^\\d+\\-\\d+$"))) {
                    try {
                        boolean isAdd = text.contains("+");
                        String[] parts = text.split(isAdd ? "\\+" : "\\-");
                        Long targetChatId = Long.parseLong(parts[0]);
                        int amount = Integer.parseInt(parts[1]);

                        UserEntity targetUser = userRepository.findByChatId(targetChatId).orElse(null);
                        if (targetUser != null) {
                            if (isAdd) {
                                targetUser.setBalance(targetUser.getBalance() + amount);
                                userRepository.save(targetUser);
                                org.telegram.telegrambots.meta.api.methods.send.SendMessage userMsg = new org.telegram.telegrambots.meta.api.methods.send.SendMessage(targetChatId.toString(), "Sizga " + amount + " so'm tushdi, balansingiz " + targetUser.getBalance() + " so'm bo'ldi.");
                                bot.execute(userMsg);
                                org.telegram.telegrambots.meta.api.methods.send.SendMessage adminMsg = new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "Muvaffaqiyatli qo'shildi! Yangi balans: " + targetUser.getBalance());
                                bot.execute(adminMsg);
                            } else {
                                targetUser.setBalance(targetUser.getBalance() - amount);
                                userRepository.save(targetUser);
                                org.telegram.telegrambots.meta.api.methods.send.SendMessage userMsg = new org.telegram.telegrambots.meta.api.methods.send.SendMessage(targetChatId.toString(), "Sizning balansingizdan " + amount + " so'm ayrildi, balansingizda " + targetUser.getBalance() + " so'm qoldi.");
                                bot.execute(userMsg);
                                org.telegram.telegrambots.meta.api.methods.send.SendMessage adminMsg = new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "Muvaffaqiyatli ayirildi! Yangi balans: " + targetUser.getBalance());
                                bot.execute(adminMsg);
                            }
                        } else {
                            org.telegram.telegrambots.meta.api.methods.send.SendMessage adminMsg = new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "Bunday foydalanuvchi topilmadi!");
                            bot.execute(adminMsg);
                        }
                    } catch (Exception e) {
                        org.telegram.telegrambots.meta.api.methods.send.SendMessage adminMsg = new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "Xatolik: " + e.getMessage());
                        try { bot.execute(adminMsg); } catch(Exception ex){}
                    }
                    return true;
                }

                String buttonKey = videoTutorialService.resolveButtonKey(text);
                if (buttonKey != null) {
                    videoTutorialService.sendTutorialIfExists(bot, chatId, buttonKey, user.getLanguage());
                }

                switch (text) {
                    case "🎞️ Taqdimot yaratish", "🎞️ Создать презентацию", "🎞️ Create Presentation" -> {
                        user.setInCheckFlow(false); userRepository.save(user);
                        KeyboardButton wb = new KeyboardButton(switch (user.getLanguage()) {
                            case "RU"  -> "⚙️ Открыть настройки презентации";
                            case "ENG" -> "⚙️ Open presentation settings";
                            default    -> "⚙️ Taqdimot sozlamalarini kiritish";
                        });
                        String uiLanguage = switch (user.getLanguage() == null ? "UZ" : user.getLanguage()) {
                            case "RU" -> "RU";
                            case "ENG" -> "ENG";
                            default -> "UZ";
                        };
                        String separator = WEB_APP_URL.contains("?") ? "&" : "?";
                        wb.setWebApp(new WebAppInfo(WEB_APP_URL + separator + "ui=" + uiLanguage + "&v=2"));
                        KeyboardRow wr = new KeyboardRow(); wr.add(wb);
                        ReplyKeyboardMarkup wm = new ReplyKeyboardMarkup(List.of(wr)); wm.setResizeKeyboard(true);
                        SendMessage wMsg = new SendMessage(chatId.toString(), switch (user.getLanguage()) {
                            case "RU"  -> "Выберите необходимые настройки!";
                            case "ENG" -> "Choose your settings!";
                            default    -> "Sozlamlarni tanlang!";
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
                        balanceService.showPaymentAmounts(bot, user, chatId, null); return true;
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
                                    case "RU"  -> "📂 Пожалуйста, отправьте файл для создания ZIP.";
                                    case "ENG" -> "📂 Please send any file to create ZIP.";
                                    default    -> "📂 ZIP yaratish uchun fayl yuboring.";
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
                    case "🖥️ Boshqa Xizmatlar", "🖥️ Другие услуги", "🖥️ Other Services" -> {
                        user.setInCheckFlow(false); userRepository.save(user);
                        String otherLang = lang(user);
                        String otherText = switch (otherLang) {
                            case "RU"  -> "🖥️ Для онлайн-компьютерных услуг перейдите к нашему партнёрскому боту:";
                            case "ENG" -> "🖥️ For online computer services, visit our partner bot:";
                            default    -> "🖥️ Online kompyuter xizmatlari uchun hamkor botimizga o'ting:";
                        };
                        String otherBtn = switch (otherLang) {
                            case "RU"  -> "🖥️ Перейти к боту";
                            case "ENG" -> "🖥️ Go to Bot";
                            default    -> "🖥️ Botga o'tish";
                        };
                        rawSender.sendMessageWithStyledBtn(bot, chatId.toString(), otherText,
                                otherBtn, "https://t.me/Slaydchi_Ro_bot", "success");
                        return true;
                    }
                    case "📄 PDF yaratish", "📄 Создать PDF", "📄 Create PDF" -> {
                        user.setInCheckFlow(false); userRepository.save(user);
                        try { pdfService.handlePdfFlow(update, bot, user); } catch (Exception e) { e.printStackTrace(); }
                        return true;
                    }
                    default -> {
                        // Reply keyboard summa tugmalarini tekshirish: "6 000 so'm", "6 000 сум", "6 000 UZS" va h.k.
                        Integer selectedAmount = uz.ai.slideshowbot.service.BalanceService.parseAmountFromButton(text);
                        if (selectedAmount != null) {
                            user.setInCheckFlow(false);
                            userRepository.save(user);
                            balanceService.handleAmountSelection(bot, user, chatId, selectedAmount, message.getMessageId());
                            return true;
                        }
                        // Raqam yozilgan bo'lsa lekin min 6000 dan kam bo'lsa
                        if (message.hasText()) {
                            String trimmed = text.replaceAll("[^0-9]", "");
                            if (!trimmed.isEmpty()) {
                                try {
                                    int typedAmount = Integer.parseInt(trimmed);
                                    if (typedAmount > 0 && typedAmount < 6000) {
                                        String minMsg = switch (lang(user)) {
                                            case "RU" -> "❌ Минимальная сумма пополнения — 6 000 сум.\nВыберите сумму из кнопок ниже.";
                                            case "ENG" -> "❌ Minimum top-up amount is 6,000 UZS.\nPlease select from the buttons below.";
                                            default -> "❌ Minimal to'lov summasi — 6 000 so'm.\nQuyidagi tugmalardan summani tanlang.";
                                        };
                                        try { bot.execute(new SendMessage(chatId.toString(), minMsg)); } catch (Exception ignored) {}
                                        return true;
                                    }
                                } catch (NumberFormatException ignored) {}
                            }
                        }
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
                        case "RU"  -> "❌ Только фото и документы!";
                        case "ENG" -> "❌ Only photos and documents!";
                        default    -> "❌ Faqat rasm va fayllar!";
                    }, zipService.zipBackKeyboard(user.getLanguage()), false);
                    return true;
                }
                return true;
            }

            if (Boolean.TRUE.equals(user.getInWordPdfFlow())) {
                String lang = lang(user);
                if (text.equals("✅ Tasdiqlash") || text.equals("✅ Подтвердить") || text.equals("✅ Confirm")) {
                    wordPdfService.handleConfirm(bot, user, chatId); return true;
                }
                if (message.hasDocument()) { wordPdfService.handleFileInput(bot, user, message); return true; }
                rawSender.sendColoredSingleMenu(bot, chatId.toString(), switch (lang) {
                    case "RU"  -> "❌ Я принимаю только .docx и .pdf файлы!";
                    case "ENG" -> "❌ I only accept .docx and .pdf files!";
                    default    -> "❌ Faqat .docx va .pdf fayllarni qabul qilaman!";
                }, switch (lang) { case "RU" -> "◀️ Назад"; case "ENG" -> "◀️ Back"; default -> "◀️ Orqaga"; }, "danger");
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

            if (user.getState() == UserState.ADMIN_WAITING_CHANNEL_URL) {
                if (message.hasText() && !text.startsWith("/")) {
                    uz.ai.slideshowbot.entity.ChannelEntity ce = new uz.ai.slideshowbot.entity.ChannelEntity();
                    ce.setUsername(text.trim());
                    pendingChannels.put(chatId, ce);
                    user.setState(UserState.ADMIN_WAITING_CHANNEL_NAME);
                    userRepository.save(user);
                    try { bot.execute(new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "✍️ Endi tugma uchun nom yozing (foydalanuvchilar obuna menyusida ko'radi):")); } catch (Exception ignored) {}
                }
                return true;
            }
            if (user.getState() == UserState.ADMIN_WAITING_CHANNEL_NAME) {
                if (message.hasText() && !text.startsWith("/")) {
                    uz.ai.slideshowbot.entity.ChannelEntity ce = pendingChannels.get(chatId);
                    if (ce != null) {
                        ce.setName(text.trim());
                        user.setState(UserState.ADMIN_WAITING_CHANNEL_MANDATORY);
                        userRepository.save(user);
                        org.telegram.telegrambots.meta.api.methods.send.SendMessage m = new org.telegram.telegrambots.meta.api.methods.send.SendMessage(chatId.toString(), "📌 Bu kanalga ulanish majburiy bo'lsinmi?\n\nHa — foydalanuvchilar shu kanalga a'zo bo'lmaguncha botdan foydalana olmaydi.");
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup mk = new org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup();
                        java.util.List<org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton> r = new java.util.ArrayList<>();
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btnYes = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(); btnYes.setText("✅ Ha"); btnYes.setCallbackData("ADMIN_CHAN_REQ_YES");
                        org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton btnNo = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton(); btnNo.setText("❌ Yo'q"); btnNo.setCallbackData("ADMIN_CHAN_REQ_NO");
                        r.add(btnYes); r.add(btnNo);
                        mk.setKeyboard(java.util.List.of(r));
                        m.setReplyMarkup(mk);
                        try { bot.execute(m); } catch (Exception ignored) {}
                    }
                }
                return true;
            }

            if (user.getState() == UserState.WAITING_TOPIC || user.getState() == UserState.WAITING_INFO) {
                slideFlowSingleService.handleMessage(user, message, bot); return true;
            }

            try { if (pdfService.handlePdfFlow(update, bot, user)) return true; } catch (Exception ignored) {}

            if (message.hasPhoto() || message.hasDocument()) {
                String lang = lang(user);
                sendMessage(bot, chatId, switch (lang) {
                    case "RU"  -> "⚠️ Уважаемый пользователь, если это чек об оплате, пожалуйста, сначала нажмите кнопку «📝 Отправить чек» или отправьте команду /chek!";
                    case "ENG" -> "⚠️ Dear user, if this is a payment receipt, please first click the «📝 Send receipt» button or send the /chek command!";
                    default    -> "⚠️ Hurmatli foydalanuvchi, agar bu to'lov cheki bo'lsa, iltimos avval «📝 Chekni yuborish» tugmasini bosing yoki /chek buyrug'ini yuboring!";
                }, null, false);
                return true;
            }

            if (user.getState() == UserState.START || user.getState() == null) {
                String lang = lang(user);
                sendMessage(bot, chatId, switch (lang) {
                    case "RU"  -> "❌ Неизвестная команда.\n\n/start — открыть меню";
                    case "ENG" -> "❌ Unknown command.\n\n/start — open menu";
                    default    -> "❌ Noto'g'ri buyruq.\n\n/start — menyuni ochish";
                }, null, false);
                return true;
            }

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
        java.util.List<uz.ai.slideshowbot.entity.ChannelEntity> mandatoryChannels = channelRepository.findAllByIsRequiredTrue();
        if (mandatoryChannels.isEmpty()) return true;

        boolean m = true;
        for (uz.ai.slideshowbot.entity.ChannelEntity ch : mandatoryChannels) {
            if (!checkSingleChannel(bot, userId, ch.getUsername())) {
                m = false;
                break;
            }
        }
        channelMemberCache.put(userId, new long[]{System.currentTimeMillis(), m ? 1L : 0L});
        return m;
    }

    public void clearChannelCache(Long userId) { channelMemberCache.remove(userId); }

    public void forceRejoinIfNeeded(SlideBot bot, UserEntity user, Long chatId) {
        if (user.getLanguage() == null) return;
        boolean inAll = refreshCacheAndCheck(bot, chatId);
        if (inAll) {
            if (user.getLastWarningTime() != null) { user.setLastWarningTime(null); userRepository.save(user); }
        }
    }

    private boolean checkSingleChannel(SlideBot bot, Long userId, String channelUsername) {
        try {
            String target = channelUsername;
            if (target.startsWith("https://t.me/")) {
                target = "@" + target.substring("https://t.me/".length());
            } else if (!target.startsWith("@")) {
                target = "@" + target;
            }
            ChatMember member = bot.execute(new GetChatMember(target, userId));
            if (member == null || member.getStatus() == null) return false;
            String s = member.getStatus();
            return s.equals("member") || s.equals("administrator") || s.equals("creator");
        } catch (Exception e) { return false; }
    }

    private void answerCallback(SlideBot bot, String id, boolean alert) {
        try { AnswerCallbackQuery a = new AnswerCallbackQuery(); a.setCallbackQueryId(id); a.setShowAlert(alert); bot.execute(a); }
        catch (Exception ignored) {}
    }

    private void deleteMessage(SlideBot bot, Long chatId, Integer msgId) {
        try { if (msgId != null) bot.execute(new DeleteMessage(chatId.toString(), msgId)); } catch (Exception ignored) {}
    }

    private void deleteMessage(SlideBot bot, Message msg) {
        try { if (msg != null) bot.execute(new DeleteMessage(msg.getChatId().toString(), msg.getMessageId())); }
        catch (Exception ignored) {}
    }

    private void deleteUserMessage(SlideBot bot, Message msg) {
        try { if (msg != null) bot.execute(new DeleteMessage(msg.getChatId().toString(), msg.getMessageId())); }
        catch (Exception ignored) {}
    }

    private void sendJoinRequest(SlideBot bot, Long chatId, String lang) {
        String l = lang != null ? lang : "UZ";
        String msgText = switch (l) { case "RU" -> "📢 Для использования бота подпишитесь на наши каналы!"; case "ENG" -> "📢 Subscribe to our channels to use the bot!"; default -> "📢 Botdan foydalanish uchun kanallarga a'zo bo'ling!"; };

        java.util.List<uz.ai.slideshowbot.entity.ChannelEntity> channels = channelRepository.findAllByIsRequiredTrue();
        if (channels.isEmpty()) return;

        SendMessage msg = new SendMessage(chatId.toString(), msgText);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        java.util.List<java.util.List<InlineKeyboardButton>> rows = new java.util.ArrayList<>();

        for (uz.ai.slideshowbot.entity.ChannelEntity ch : channels) {
            InlineKeyboardButton btn = new InlineKeyboardButton();
            btn.setText("📢 " + ch.getName());
            String url = ch.getUsername();
            if (url.startsWith("@")) url = "https://t.me/" + url.substring(1);
            btn.setUrl(url);
            rows.add(java.util.List.of(btn));
        }

        InlineKeyboardButton checkBtn = new InlineKeyboardButton();
        checkBtn.setText(switch (l) { case "RU" -> "✅ Подтверждение"; case "ENG" -> "✅ Check subscription"; default -> "✅ A'zolikni tekshirish"; });
        checkBtn.setCallbackData("CHECK_JOIN");
        rows.add(java.util.List.of(checkBtn));

        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        try { bot.execute(msg); } catch (Exception ignored) {}
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
        try { AnswerCallbackQuery a = new AnswerCallbackQuery(); a.setCallbackQueryId(callbackId); a.setText(text); a.setShowAlert(true); bot.execute(a); }
        catch (Exception ignored) {}
    }

    private String getDisplayName(UserEntity user) {
        if (user.getFirstName() != null && !user.getFirstName().isBlank()) return user.getFirstName();
        if (user.getUsername() != null && !user.getUsername().isBlank()) return "@" + user.getUsername();
        return "Foydalanuvchi";
    }

    private String lang(UserEntity user) { return user.getLanguage() != null ? user.getLanguage() : "UZ"; }

    private boolean isCommandOrMenu(String text) {
        if (text == null) return false;
        if (text.startsWith("/start") || text.equals("/balance") || text.equals("/check") || text.equals("/chek") || text.equals("/buy") || text.equals("/referal")) return true;

        return text.equals("🎞️ Taqdimot yaratish") || text.equals("🎞️ Создать презентацию") || text.equals("🎞️ Create Presentation")
            || text.equals("💰 Balans") || text.equals("💰 Баланс") || text.equals("💰 Balance")
            || text.equals("Referral") || text.equals("Реферал")
            || text.equals("🌐 Til Sozlamalari") || text.equals("🌐 Язык") || text.equals("🌐 Language Settings")
            || text.equals("📦 Fayllarni Zip qilish") || text.equals("📦 Zip Files") || text.equals("📦 Архивировать файлы (Zip)")
            || text.equals("📋 Test tayyorlash") || text.equals("📋 Создать тест") || text.equals("📋 Create Test")
            || text.equals("📄 Word <--> PDF")
            || text.equals("📝 Referat tayyorlash") || text.equals("📝 Создать реферат") || text.equals("📝 Create Essay")
            || text.equals("🖥️ Boshqa Xizmatlar") || text.equals("🖥️ Другие услуги") || text.equals("🖥️ Other Services")
            || text.equals("📄 PDF yaratish") || text.equals("📄 Создать PDF") || text.equals("📄 Create PDF")
            || text.equals("🇺🇿 O'zbekcha") || text.equals("🇷🇺 Русский") || text.equals("🇺🇸 English")
            || uz.ai.slideshowbot.service.BalanceService.parseAmountFromButton(text) != null;
    }
}
