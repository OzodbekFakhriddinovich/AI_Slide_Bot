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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AdminCheckService {

    private final Map<String, Long> waitingForAmount = new HashMap<>();

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BalanceService balanceService;

    private final Long ADMIN_CHAT_ID = 6290351864L;


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

                // Faqat inline buttonlarni o'chir, chek rasmi/dokumenti qolsin
                try {
                    org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup removeBtn =
                            new org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup();
                    removeBtn.setChatId(ADMIN_CHAT_ID.toString());
                    removeBtn.setMessageId(query.getMessage().getMessageId());
                    removeBtn.setReplyMarkup(new InlineKeyboardMarkup(List.of()));
                    bot.execute(removeBtn);
                } catch (Exception ignored) {}

                if (data.startsWith("APPROVE_CHECK_")) {
                    waitingForAmount.put(checkId, userChatId);

                    SendMessage askAmount = new SendMessage();
                    askAmount.setChatId(ADMIN_CHAT_ID.toString());
                    String askText = switch (lang) {
                        case "RU" -> "💳 Введите сумму для пополнения баланса (только числа):";
                        case "ENG" -> "💳 Enter the amount to top up user's balance (numbers only):";
                        default -> "💳 Foydalanuvchi balansini oshirish uchun summani kiriting (faqat raqam):";
                    };
                    askAmount.setText(askText);
                    bot.execute(askAmount);

                    AnswerCallbackQuery answer = new AnswerCallbackQuery();
                    answer.setCallbackQueryId(query.getId());
                    answer.setText("❗️ Summani kiriting.");
                    bot.execute(answer);

                } else {
                    balanceService.deleteCheckFile(user);

                    String msgText = switch (lang) {
                        case "RU" -> "❌ Ваш чек был отклонён администратором.";
                        case "ENG" -> "❌ Your check was rejected by admin.";
                        default -> "❌ Chekingiz admin tomonidan rad etildi.";
                    };

                    bot.execute(SendMessage.builder()
                            .chatId(userChatId.toString())
                            .text(msgText)
                            .build());

                    AnswerCallbackQuery answer = new AnswerCallbackQuery();
                    answer.setCallbackQueryId(query.getId());
                    answer.setText("❌ Chek rad etildi.");
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

                            bot.execute(SendMessage.builder()
                                    .chatId(userChatId.toString())
                                    .text("💳 Sizning chekingiz tasdiqlandi!\nBalansingiz " + amount + " so'mga to'ldirildi.")
                                    .build());

                            bot.execute(SendMessage.builder()
                                    .chatId(ADMIN_CHAT_ID.toString())
                                    .text("✅ User balansiga " + amount + " so'm qo'shildi!")
                                    .build());

                            balanceService.deleteCheckFile(user);
                            waitingForAmount.remove(checkId);
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

            InlineKeyboardButton approve = InlineKeyboardButton.builder()
                    .text("✅ Tasdiqlash / Approve")
                    .callbackData("APPROVE_CHECK_" + user.getChatId() + "_" + checkId)
                    .build();

            InlineKeyboardButton reject = InlineKeyboardButton.builder()
                    .text("❌ Rad etish / Reject")
                    .callbackData("REJECT_CHECK_" + user.getChatId() + "_" + checkId)
                    .build();

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup(List.of(List.of(approve, reject)));

            String userLink;
            if (user.getUsername() != null && !user.getUsername().isBlank()) {
                userLink = "<a href=\"https://t.me/" + user.getUsername() + "\">@" + user.getUsername() + "</a>";
            } else {
                userLink = "<a href=\"tg://user?id=" + user.getChatId() + "\">" + user.getChatId() + "</a>";
            }

            String adminCaption = "💳 User: " + userLink + "\n" +
                    "⏳ Please verify the payment.\n" +
                    "❗ Admin: Summani kiritish uchun ✅ bosilsin.";

            if (isDocument) {
                SendDocument doc = SendDocument.builder()
                        .chatId(ADMIN_CHAT_ID.toString())
                        .document(new InputFile(fileId))
                        .caption(adminCaption)
                        .parseMode("HTML")
                        .replyMarkup(markup)
                        .build();
                bot.execute(doc);
            } else {
                SendPhoto photo = SendPhoto.builder()
                        .chatId(ADMIN_CHAT_ID.toString())
                        .photo(new InputFile(fileId))
                        .caption(adminCaption)
                        .parseMode("HTML")
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
            e.printStackTrace();
        }
    }
}