package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.UserRepository;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminCheckService {

    private final Map<String, Long> waitingForAmount = new HashMap<>();

    private final Map<String, String> checkFiles = new HashMap<>();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BalanceService balanceService;

    private final Long ADMIN_CHAT_ID = 6290351864L;

    private static final String SAVE_DIR = "C:\\Users\\User\\Desktop\\saqlash\\saqlash\\";

    public void sendCheckToAdmin(Message userMessage, UserEntity user, SlideBot bot) {
        try {
            String fileId;
            boolean isDocument;

            if (userMessage.hasDocument()) {
                fileId = userMessage.getDocument().getFileId();
                isDocument = true;
            } else if (userMessage.hasPhoto()) {
                int lastIndex = userMessage.getPhoto().size() - 1;
                fileId = userMessage.getPhoto().get(lastIndex).getFileId();
                isDocument = false;
            } else return;

            String checkId = UUID.randomUUID().toString();
            String filePath = SAVE_DIR + user.getChatId();
            checkFiles.put(checkId, filePath);


            InlineKeyboardButton approve = InlineKeyboardButton.builder()
                    .text("✅ Tasdiqlash / Approve")
                    .callbackData("APPROVE_CHECK_" + user.getChatId() + "_" + checkId)
                    .build();

            InlineKeyboardButton reject = InlineKeyboardButton.builder()
                    .text("❌ Rad etish / Reject")
                    .callbackData("REJECT_CHECK_" + user.getChatId() + "_" + checkId)
                    .build();

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(List.of(approve, reject)));


            String adminCaption = """
                💳 User: @%s (ID: %d)
                ⏳ Please verify the payment.
                ❗ Admin: Summani kiritish uchun ✅ bosilsin.
                """.formatted(user.getUsername(), user.getChatId());


            if (isDocument) {
                SendDocument doc = SendDocument.builder()
                        .chatId(ADMIN_CHAT_ID.toString())
                        .document(new InputFile(fileId))
                        .caption(adminCaption)
                        .replyMarkup(markup)
                        .build();
                bot.execute(doc);
            } else {
                SendPhoto photo = SendPhoto.builder()
                        .chatId(ADMIN_CHAT_ID.toString())
                        .photo(new InputFile(fileId))
                        .caption(adminCaption)
                        .replyMarkup(markup)
                        .build();
                bot.execute(photo);
            }


            String userMsg = switch (user.getLanguage()) {
                case "RU" -> """
                    💳 Ваш чек отправлен администраторам!
                    ⏳ Пожалуйста, подождите — процесс проверки продолжается.
                    👨‍💻 Админ: @slides_admin1
                    ⚠️ Пожалуйста, не отправляйте чек повторно.
                    """;
                case "ENG" -> """
                    💳 Your check has been sent to the admins!
                    ⏳ Please wait — verification is in progress.
                    👨‍💻 Admin: @slides_admin1
                    ⚠️ Please do not resend the check.
                    """;
                default -> """
                    💳 Sizning chekingiz adminlarga yuborildi!
                    ⏳ Iltimos, kuting — tekshiruv jarayoni davom etmoqda.
                    👨‍💻 Admin: @slides_admin1
                    ⚠️ Iltimos, chekni qayta yubormang.
                    """;
            };

            bot.execute(SendMessage.builder()
                    .chatId(user.getChatId().toString())
                    .text(userMsg)
                    .build());

        } catch (Exception e) {
            e.printStackTrace(); // TODO: logger bilan almashtirish tavsiya etiladi
        }
    }

    public boolean handleAdminCallback(Update update, SlideBot bot) {
        try {
            if (update.hasCallbackQuery()) {
                var query = update.getCallbackQuery();
                String data = query.getData();

                if (!(data.startsWith("APPROVE_CHECK_") || data.startsWith("REJECT_CHECK_")))
                    return false;

                String[] parts = data.split("_", 4);
                Long userChatId = Long.parseLong(parts[2]);
                String checkId = parts[3];
                UserEntity user = userRepository.findByChatId(userChatId).orElse(null);
                if (user == null) return false;

                String lang = user.getLanguage() != null ? user.getLanguage() : "UZ";

                if (data.startsWith("APPROVE_CHECK_")) {
                    waitingForAmount.put(checkId, userChatId);

                    SendMessage askAmount = new SendMessage();
                    askAmount.setChatId(ADMIN_CHAT_ID.toString());
                    String askText = switch (lang) {
                        case "RU" -> "💳 Админ: Пожалуйста, введите сумму для пополнения баланса пользователя (только числа):";
                        case "ENG" -> "💳 Admin: Please enter the amount to top up user's balance (numbers only):";
                        default -> "💳 Admin: Iltimos, foydalanuvchi balansini oshirish uchun summani kiriting (faqat raqam):";
                    };
                    askAmount.setText(askText);
                    bot.execute(askAmount);

                    AnswerCallbackQuery answer = new AnswerCallbackQuery();
                    answer.setCallbackQueryId(query.getId());
                    answer.setText("❗️ Summani kiriting.");
                    bot.execute(answer);

                } else {
                    String msgText = switch (lang) {
                        case "RU" -> "❌ На ваш баланс добавлено 0 сумов. Проверьте с помощью команды /balance.";
                        case "ENG" -> "❌ 0 soums have been added to your balance. Please check using the /balance command.";
                        default -> "❌ Balansingizga 0 so'm qabul qilindi. Iltmos, /balance buyrug'i orqali tekshiring";
                    };

                    SendMessage msgToUser = new SendMessage();
                    msgToUser.setChatId(userChatId.toString());
                    msgToUser.setText(msgText);
                    bot.execute(msgToUser);

                    balanceService.deleteCheckFile(user);

                    AnswerCallbackQuery answer = new AnswerCallbackQuery();
                    answer.setCallbackQueryId(query.getId());
                    answer.setText("❌ Chek rad etildi va fayl o‘chirildi.");
                    bot.execute(answer);
                }

                return true;
            }

            if (update.hasMessage() && update.getMessage().getChatId().equals(ADMIN_CHAT_ID)) {
                Message msg = update.getMessage();
                String text = msg.hasText() ? msg.getText() : "";

                if (text.matches("\\d+")) {
                    int amount = Integer.parseInt(text);

                    String checkId = waitingForAmount.keySet().stream().findFirst().orElse(null);
                    if (checkId != null) {
                        Long userChatId = waitingForAmount.get(checkId);
                        UserEntity user = userRepository.findByChatId(userChatId).orElse(null);
                        if (user != null) {
                            user.setBalance(user.getBalance() + amount);
                            userRepository.save(user);

                            String lang = user.getLanguage() != null ? user.getLanguage() : "UZ";

                            SendMessage msgToUser = new SendMessage();
                            msgToUser.setChatId(userChatId.toString());
                            msgToUser.setText("💳 Sizning chekingiz tasdiqlandi!\nBalansingiz " + amount + " so'mga to‘ldirildi.\n/balance orqali tekshirishingiz mumkin.");
                            bot.execute(msgToUser);

                            SendMessage msgToAdmin = new SendMessage();
                            msgToAdmin.setChatId(ADMIN_CHAT_ID.toString());
                            msgToAdmin.setText("✅ User balansiga " + amount + " so‘m qo‘shildi!");
                            bot.execute(msgToAdmin);

                            balanceService.deleteCheckFile(user);

                            waitingForAmount.remove(user);
                        }
                    }

                    return true;
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }



}
