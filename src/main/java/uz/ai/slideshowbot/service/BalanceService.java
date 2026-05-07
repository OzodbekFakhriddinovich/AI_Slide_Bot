package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.PhotoSize;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.FileEntity;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.FileRepository;
import uz.ai.slideshowbot.repository.UserRepository;

import java.io.InputStream;
import java.net.URL;
import java.util.List;

@Service
public class BalanceService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private TelegramRawSender rawSender;

    private String lang(UserEntity user) {
        return (user.getLanguage() != null) ? user.getLanguage() : "UZ";
    }

    public void showBalance(SlideBot bot, UserEntity user, Long chatId, Integer lastMessageId) {
        if (user.getBalance() == null) user.setBalance(4000);
        userRepository.save(user);

        String language = lang(user);

        String text = switch (language) {
            case "RU" -> "💰 Ваш баланс: " + user.getBalance() + " сум\n" +
                    "/buy - Пополнить баланс💵\n" +
                    "/referal - Приглашайте друзей и пополняйте баланс🔗";
            case "ENG" -> "💰 Your balance: " + user.getBalance() + " UZS\n" +
                    "/buy - Top up your balance💵\n" +
                    "/referal - Invite friends to increase your balance🔗";
            default -> "💰 Balansingiz: " + user.getBalance() + " so'm\n" +
                    "/buy - to'lov orqali balansni to'ldirish💵\n" +
                    "/referal - do'stlarni taklif qilib balansni to'ldirish🔗";
        };

        String btnText = switch (language) {
            case "RU" -> "💳 Оплатить";
            case "ENG" -> "💳 Pay";
            default -> "💳 To'lov qilish";
        };

        if (lastMessageId != null) {
            try {
                bot.execute(new DeleteMessage(chatId.toString(), lastMessageId));
            } catch (Exception ignored) {
            }
        }

        rawSender.sendMessageWithStyledCallbackBtn(
                bot,
                chatId.toString(),
                text,
                btnText,
                "BALANCE_PAY",
                "success"
        );

    }

    public void handleBuy(SlideBot bot, UserEntity user, Long chatId, Integer lastBalanceMessageId) {
        if (lastBalanceMessageId != null) {
            try {
                bot.execute(new DeleteMessage(chatId.toString(), lastBalanceMessageId));
            } catch (Exception ignored) {
            }
        }

        String language = lang(user);

        String text1 = switch (language) {
            case "RU" -> """
                    📕Презентации/Слайды цены:
                    10 страниц - 5 000 сум
                    15 страниц - 7 000 сум
                    
                    📋 Тест цена:
                    5 000 сум
                    
                    📝 Реферат цена:
                    5 000 сум
                    """;
            case "ENG" -> """
                    📕Presentation/Slide prices:
                    10 pages - 5,000 UZS
                    15 pages - 7,000 UZS
                    
                    📋 Test price:
                    5,000 UZS
                    
                    📝 Essay price:
                    5,000 UZS
                    """;
            default -> """
                    📕Taqdimot/Slayd narxlari:
                    10 sahifa - 5 000 so'm
                    15 sahifa - 7 000 so'm
                    
                    📋 Test narxi:
                    5 000 so'm
                    
                    📝 Referat narxi:
                    5 000 so'm
                    """;
        };
        sendMessage(bot, chatId, text1, null);

        String text2 = switch (language) {
            case "RU" -> """
                    Пожалуйста, оплачивайте только следующие суммы:
                    - 5 000 сум
                    - 7 000 сум
                    * Оплатите 10 000 сум → получите +2 000 сум бонус🎁
                    * Оплатите 20 000 сум → получите +5 000 сум бонус🎁
                    * Оплатите 50 000 сум → получите +15 000 сум бонус🎁
                    
                    Переведите оплату на kartu ниже и сделайте скриншот чека:
                    (Нажмите на номер карты, чтобы скопировать)
                    
                    💳 HUMO Card:
                    <code>9860 3501 4654 8903</code>
                    👤 JALILOV OZODBEK
                    
                    ❕ Если возникли проблемы с оплатой на эту карту, воспользуйтесь одной из следующих:
                    
                    💳 VISA Card:
                    <code>4916 9903 1125 6090</code>
                    👤 JALILOV OZODBEK
                    
                    🧾 После оплаты отправьте команду /chek или нажмите кнопку ниже👇
                    """;
            case "ENG" -> """
                    Please pay only the following amounts:
                    - 5,000 UZS
                    - 7,000 UZS
                    * Pay 10,000 UZS → get +2,000 UZS bonus🎁
                    * Pay 20,000 UZS → get +5,000 UZS bonus🎁
                    * Pay 50,000 UZS → get +15,000 UZS bonus🎁
                    
                    Send payment to the card below and take a screenshot of the receipt:
                    (Tap on the card number to copy it)
                    
                    💳 HUMO Card:
                    <code>9860 3501 4654 8903</code>
                    👤 JALILOV OZODBEK
                    
                    ❕ If you have trouble paying to this card, use one of the following:
                    
                    💳 VISA Card:
                    <code>4916 9903 1125 6090</code>
                    👤 JALILOV OZODBEK
                    
                    🧾 After payment, send /chek command or press the button below👇
                    """;
            default -> """
                    Iltimos faqat ushbu summalar miqdorida to'lov qiling:
                    - 5 000 so'm
                    - 7 000 so'm
                    * 10 000 so'm to'lov qilsangiz, +2000 so'm bonus🎁
                    * 20 000 so'm to'lov qilsangiz, +5000 so'm bonus🎁
                    * 50 000 so'm to'lov qilsangiz, +15000 so'm bonus🎁
                    
                    Quyidagi karta raqamiga to'lov qiling va chekni skrenshot qilib oling:
                    (COPY qilish uchun karta raqam ustiga bosing).
                    
                    💳 HUMO Card:
                    <code>9860 3501 4654 8903</code>
                    👤 JALILOV OZODBEK
                    
                    ❕ushbu kartaga to'lov qilishda muammo bo'lsa, quyidagi kartalardan biriga to'lov qiling:
                    
                    💳 VISA Card:
                    <code>4916 9903 1125 6090</code>
                    👤 JALILOV OZODBEK
                    
                    🧾 To'lov qilganingizdan so'ng /chek buyrug'ini yuboring yoki quyidagi tugmani bosing👇
                    """;
        };

        String checkBtnText = switch (language) {
            case "RU" -> "📝 Отправить чек";
            case "ENG" -> "📝 Send check";
            default -> "📝 Chekni yuborish";
        };

        rawSender.sendMessageWithStyledCallbackBtn(
                bot,
                chatId.toString(),
                text2,
                checkBtnText,
                "SEND_CHECK",
                "primary"
        );
    }

    public void handleCheckFlow(SlideBot bot, UserEntity user, Long chatId) {
        String language = lang(user);
        String text = switch (language) {
            case "RU" -> "📨 Пожалуйста, отправьте скриншот или файл чека:";
            case "ENG" -> "📨 Please send a screenshot or file of your payment check:";
            default -> "📨 To'lov qilganingizni tasdiqlovchi chekni skrenshotini yoki faylini yuboring:";
        };
        sendMessage(bot, chatId, text, null);
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
            try {
                fileRepository.deleteById(fileId);
            } catch (Exception e) {
                e.printStackTrace();
            }
            user.setCheckFileId(null);
            userRepository.save(user);
        }
    }

    private Integer sendMessage(SlideBot bot, Long chatId, String text, InlineKeyboardMarkup markup) {
        try {
            SendMessage sm = new SendMessage(chatId.toString(), text);
            if (markup != null) sm.setReplyMarkup(markup);
            Message sentMsg = bot.execute(sm);
            return sentMsg.getMessageId();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}