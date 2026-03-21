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
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.UserRepository;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@Service
public class BalanceService {
    @Autowired
    private  UserRepository userRepository;


    public void showBalance(SlideBot bot, UserEntity user, Long chatId, Integer lastMessageId) {
        if (user.getBalance() == null) user.setBalance(4000);
        userRepository.save(user);

        String text = switch (user.getLanguage()) {
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

        InlineKeyboardButton payBtn = new InlineKeyboardButton();
        payBtn.setText(switch (user.getLanguage()) {
            case "RU" -> "💳 Оплатить";
            case "ENG" -> "💳 Pay";
            default -> "💳 To'lov qilish";
        });
        payBtn.setCallbackData("BALANCE_PAY");

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(List.of(List.of(payBtn)));

        if (lastMessageId != null) {
            try {
                bot.execute(new DeleteMessage(chatId.toString(), lastMessageId));
            } catch (Exception ignored) {}
        }

        Integer newMessageId = sendMessage(bot, chatId, text, markup);

        user.setLastBalanceMessageId(newMessageId);
        userRepository.save(user);
    }


    public void handleBuy(SlideBot bot, UserEntity user, Long chatId, Integer lastBalanceMessageId) {
        if (lastBalanceMessageId != null) {
            try {
                bot.execute(new DeleteMessage(chatId.toString(), lastBalanceMessageId));
            } catch (Exception ignored) {}
        }

        String text1 = switch (user.getLanguage()) {
            case "RU" -> """
                    📕Презентации/Слайды цены:
                    6-10 страниц - 4000 сум
                    11-15 страниц - 5000 сум
                    16-20 страниц - 6000 сум

                    📘Самостоятельная работа цены:
                    до 6-10 страниц - 4000 сум
                    до 11-15 страниц - 5000 сум
                    до 16-20 страниц - 6000 сум
                    """;
            case "ENG" -> """
                    📕Presentation/Slide prices:
                    6-10 pages - 4000 UZS
                    11-15 pages - 5000 UZS
                    16-20 pages - 6000 UZS

                    📘Independent Work prices:
                    up to 6-10 pages - 4000 UZS
                    up to 6-15 pages - 5000 UZS
                    up to 16-20 pages - 6000 UZS
                    """;
            default -> """
                    📕Taqdimot/Slayd narxlari:
                     6 sahifadan, 10 sahifagacha - 4000 so'm
                    11 sahifadan, 15 sahifagacha - 5000 so'm
                    16 sahifadan, 20 sahifagacha - 6000 so'm

                    📘Mustaqil ish narxlari:
                    6-10 sahifagacha - 4000 so'm
                    11-15 sahifagacha - 5000 so'm
                    16-20 sahifagacha - 6000 so'm
                    """;
        };
        sendMessage(bot, chatId, text1, null);

        String text2 = switch (user.getLanguage()) {
            case "RU" -> """
                    Пожалуйста, оплачивайте только следующие суммы:
                    - 2 000 сум
                    - 3 000 сум
                    - 5 000 сум
                    * 10 000 сум - +2000 сум бонус🎁
                    * 20 000 сум - +5000 сум бонус🎁
                    * 50 000 сум - +15000 сум бонус🎁

                    Оплатите на следующий номер карты и сделайте скриншот чека:
                    (Нажмите на номер карты, чтобы КОПИРОВАТЬ).
                    💳 9860040102530439 (HUMO)
                    👤 ДЖАЛИЛОВ ОЗОДБЕК
                    
                    ❕Если у вас возникли проблемы с оплатой на эту карту, оплатите на одну из следующих карт:
                    💳 9860350146548903 (HUMO)
                    👤 ДЖАЛИЛОВ ОЗОДБЕК
                    
                    💳 4916990311256090 (VISA)
                    👤 ДЖАЛИЛОВ ОЗОДБЕК
                    🧾 После оплаты отправьте /chek или нажмите кнопку👇
                    """;
            case "ENG" -> """
                    Please pay only the following amounts:
                    - 2 000 UZS
                    - 3 000 UZS
                    - 5 000 UZS
                    * 10 000 UZS - +2000 UZS bonus🎁
                    * 20 000 UZS - +5000 UZS bonus🎁
                    * 50 000 UZS - +15000 UZS bonus🎁

                    Pay to the following card number and take a screenshot of the receipt:
                    (Click on the card number to COPY).
                    💳 9860040102530439 (HUMO)
                    👤 JALILOV OZODBEK
                    
                    ❕If you have problems paying to this card, pay to one of the following cards:
                    💳 9860350146548903 (HUMO)
                    👤 JALILOV OZODBEK
                    
                    💳 4916990311256090 (VISA)
                    👤 JALILOV OZODBEK

                    🧾 After payment, send /chek or press the button👇
                    """;
            default -> """
                    Iltimos faqat ushbu summalar miqdorida to'lov qiling:
                    - 2 000 so'm
                    - 3 000 so'm
                    - 5 000 so'm
                    * 10 000 so'm to'lov qilsangiz, +2000 so'm bonus🎁
                    * 20 000 so'm to'lov qilsangiz, +5000 so'm bonus🎁
                    * 50 000 so'm to'lov qilsangiz, +15000 so'm bonus🎁

                    Quyidagi karta raqamiga to'lov qiling va chekni skrenshot qilib oling:
                    (COPY qilish uchun karta raqam ustiga bosing).
                    💳 9860040102530439  (HUMO)
                    👤 JALILOV OZODBEK   
                    
                    ❕ushbu kartaga to'lov qilishda muammo bo'lsa, quyidagi kartalardan biriga to'lov qiling:
                    💳 9860350146548903  (HUMO)
                    👤 JALILOV OZODBEK   
                    
                    💳 4916990311256090  (VISA)
                    👤 JALILOV OZODBEK
                    

                    🧾 To'lov qilganingizdan so'ng /chek buyrug'ini yuboring yoki quyidagi tugmani bosing👇
                    """;
        };

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        InlineKeyboardButton checkBtn = new InlineKeyboardButton();
        checkBtn.setText(switch (user.getLanguage()) {
            case "RU" -> "📝 Отправить чек";
            case "ENG" -> "📝 Send check";
            default -> "📝 Chekni yuborish";
        });
        checkBtn.setCallbackData("SEND_CHECK");
        markup.setKeyboard(List.of(List.of(checkBtn)));

        sendMessage(bot, chatId, text2, markup);
    }

    public void handleCheckFlow(SlideBot bot, UserEntity user, Long chatId) {
        String text = switch (user.getLanguage()) {
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
            if (message.hasPhoto()) {
                PhotoSize photo = message.getPhoto().get(message.getPhoto().size() - 1);
                GetFile getFileMethod = new GetFile(photo.getFileId());
                org.telegram.telegrambots.meta.api.objects.File tgFile = bot.execute(getFileMethod);

                File tempDir = new File("C:\\Users\\User\\Desktop\\saqlash\\saqlash");
                if (!tempDir.exists()) tempDir.mkdirs();

                File outFile = new File(tempDir, "check_" + user.getChatId() + "_" + System.currentTimeMillis() + ".jpg");
                bot.downloadFile(tgFile, outFile);

                user.setLastCheckFile(outFile.getAbsolutePath());
                userRepository.save(user);
            } else if (message.hasDocument()) {
                GetFile getFileMethod = new GetFile(message.getDocument().getFileId());
                org.telegram.telegrambots.meta.api.objects.File tgFile = bot.execute(getFileMethod);

                File tempDir = new File("C:\\Users\\User\\Desktop\\saqlash\\saqlash");
                if (!tempDir.exists()) tempDir.mkdirs();

                File outFile = new File(tempDir, message.getDocument().getFileName());
                bot.downloadFile(tgFile, outFile);

                user.setLastCheckFile(outFile.getAbsolutePath());
                userRepository.save(user);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteCheckFile(UserEntity user) {
        String filePath = user.getLastCheckFile();
        if (filePath != null) {
            try {
                File file = new File(filePath);
                if (file.exists()) Files.delete(file.toPath());
            } catch (Exception e) {
                e.printStackTrace();
            }
            user.setLastCheckFile(null);
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



