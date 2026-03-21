package uz.ai.slideshowbot.keyboard;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.*;

import java.util.ArrayList;
import java.util.List;

public class KeyboardBuilder {

    public static ReplyKeyboardMarkup requestContactKeyboard() {
        KeyboardButton contactButton = new KeyboardButton("📞 Raqamni yuborish");
        contactButton.setRequestContact(true);

        KeyboardRow row = new KeyboardRow();
        row.add(contactButton);

        List<KeyboardRow> keyboard = new ArrayList<>();
        keyboard.add(row);

        ReplyKeyboardMarkup markup = new ReplyKeyboardMarkup();
        markup.setKeyboard(keyboard);
        markup.setResizeKeyboard(true);
        markup.setOneTimeKeyboard(true);

        return markup;
    }

    public static ReplyKeyboardRemove removeKeyboard() {
        return new ReplyKeyboardRemove(true);
    }

    public static InlineKeyboardMarkup languageKeyboard() {
        InlineKeyboardButton uz = new InlineKeyboardButton("🇺🇿 O‘zbekcha");
        uz.setCallbackData("lang_uz");

        InlineKeyboardButton ru = new InlineKeyboardButton("🇷🇺 Русский");
        ru.setCallbackData("lang_ru");

        InlineKeyboardButton en = new InlineKeyboardButton("🇬🇧 English");
        en.setCallbackData("lang_en");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(uz, ru, en));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup mainMenuUz() {
        InlineKeyboardButton info = new InlineKeyboardButton("ℹ️ Ma'lumot");
        info.setCallbackData("info");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(info));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup mainMenuRu() {
        InlineKeyboardButton info = new InlineKeyboardButton("ℹ️ Информация");
        info.setCallbackData("info");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(info));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }

    public static InlineKeyboardMarkup mainMenuEn() {
        InlineKeyboardButton info = new InlineKeyboardButton("ℹ️ Info");
        info.setCallbackData("info");

        List<List<InlineKeyboardButton>> rows = new ArrayList<>();
        rows.add(List.of(info));

        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        markup.setKeyboard(rows);
        return markup;
    }
}
