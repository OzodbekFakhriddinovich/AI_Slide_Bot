package uz.ai.slideshowbot.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;

import java.util.ArrayList;
import java.util.List;

@Service
public class MenuService {

    public SendMessage mainMenu(Long chatId, String lang) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(switch (lang) {
            case "RU" -> "🏠 Главное меню";
            case "ENG" -> "🏠 Main menu";
            default -> "🏠 Asosiy menyu";
        });

        ReplyKeyboardMarkup keyboard = new ReplyKeyboardMarkup();
        keyboard.setResizeKeyboard(true);

        KeyboardRow r1 = new KeyboardRow();
        r1.add(new KeyboardButton(switch (lang) {
            case "RU" -> "📄 PDF создать";
            case "ENG" -> "📄 Create PDF";
            default -> "📄 PDF yaratish";
        }));
        r1.add(new KeyboardButton(switch (lang) {
            case "RU" -> "📦 Zip файлы";
            case "ENG" -> "📦 Zip files";
            default -> "📦 Fayllarni Zip qilish";
        }));

        KeyboardRow r2 = new KeyboardRow();
        r2.add(new KeyboardButton(switch (lang) {
            case "RU" -> "🎞️ Создать презентацию";
            case "ENG" -> "🎞️ Create Presentation";
            default -> "🎞️ Taqdimot yaratish";
        }));
        r2.add(new KeyboardButton(switch (lang) {
            case "RU" -> "🌐 Настройки языка";
            case "ENG" -> "🌐 Language settings";
            default -> "🌐 Til sozlamalari";
        }));

        KeyboardRow r3 = new KeyboardRow();
        r3.add(new KeyboardButton(switch (lang) {
            case "RU" -> "📄 Word <--> PDF";
            case "ENG" -> "📄 Word <--> PDF";
            default -> "📄 Word <--> PDF";
        }));
        r3.add(new KeyboardButton(switch (lang) {
            case "RU" -> "💰 Баланс";
            case "ENG" -> "💰 Balance";
            default -> "💰 Balans";
        }));

        List<KeyboardRow> rows = new ArrayList<>();
        rows.add(r1); rows.add(r2); rows.add(r3);

        keyboard.setKeyboard(rows);
        msg.setReplyMarkup(keyboard);
        return msg;
    }

    public SendMessage getSlideCountKeyboardMessage(Long chatId, String lang, String text) {
        SendMessage msg = new SendMessage();
        msg.setChatId(chatId.toString());
        msg.setText(text);

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        String button1 = switch (lang) {
            case "RU" -> "6–10 слайдов – 4000 сум";
            case "ENG" -> "6–10 slides – 4000 soum";
            default -> "6–10 sahifa – 4000 so'm";
        };
        String button2 = switch (lang) {
            case "RU" -> "11–15 слайдов – 5000 сум";
            case "ENG" -> "11–15 slides – 5000 soum";
            default -> "11–15 sahifa – 5000 so'm";
        };
        String button3 = switch (lang) {
            case "RU" -> "16–20 слайдов – 6000 сум";
            case "ENG" -> "16–20 slides – 6000 soum";
            default -> "16–20 sahifa – 6000 so'm";
        };

        rows.add(List.of(InlineKeyboardButton.builder().text(button1).callbackData("SLIDE_COUNT_10").build()));
        rows.add(List.of(InlineKeyboardButton.builder().text(button2).callbackData("SLIDE_COUNT_15").build()));
        rows.add(List.of(InlineKeyboardButton.builder().text(button3).callbackData("SLIDE_COUNT_20").build()));

        markup.setKeyboard(rows);
        msg.setReplyMarkup(markup);
        return msg;
    }



}
