package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uz.ai.slideshowbot.bot.SlideBot;

@Service
public class MenuService {

    @Autowired
    private TelegramRawSender rawSender;

    private static final Long VIDEO_ADMIN_ID = 8769058760L;

    public void sendMainMenu(SlideBot bot, Long chatId, String lang) {
        String currentLang = (lang == null) ? "UZ" : lang;

        String text = switch (currentLang) {
            case "RU" -> """
                    Привет, AI Presentation | Slide | PPT | PDF Bot!
                    
                    Я предлагаю вам следующие услуги:
                    
                    1. Создание слайдов – подготовка профессиональных презентаций в PowerPoint или других форматах.
                    2. PDF-документы – легкое преобразование изображений в формат PDF.
                    3. Zip-архивирование файлов – возможность легко конвертировать файлы в формат ZIP.
                    4. Подготовка тестов – создание автоматических тестов по любой теме (5000 сумов).
                    5. Подготовка рефератов – подготовка рефератов в формате Word (5000 сумов).
                    
                    /balance – ваш баланс в кошельке бота.
                    /buy – раздел покупок
                    """;

            case "ENG" -> """
                    Hello AI Presentation | Slide | PPT | PDF Bot Welcome
                    
                    I offer you the following services:
                    
                    1. Creating slides – preparing professional presentations in PowerPoint or other formats.
                    2. PDF documents – easily converting images to PDF format.
                    3. Zipping files – the ability to easily convert files to zip format.
                    4. Preparing tests – creating automatic tests on any topic (5,000 soums).
                    5. Preparing essays – preparing essays in Word format (5,000 soums).
                    
                    /balance is your balance in the bot wallet.
                    /buy shopping section
                    """;

            default -> """
                    Salom AI Taqdimot | Slayd | PPT | PDF Bot ga xush kelibsiz
                    
                    Sizga quyidagi xizmatlarni taqdim etaman:
                    
                    1. Slaydlar yaratish – PowerPoint yoki boshqa formatlarda professional taqdimotlar tayyorlash.
                    2. PDF hujjatlar – Rasmlarni osongina PDF formatiga o'zgartirish.
                    3. Fayllarni zip qilish – Fayllarni osongina zip formatiga o'zgartirish imkoniyati.
                    4. Test tayyorlash – Istalgan mavzuda avtomatik test yaratish (5,000 so'm).
                    5. Referat tayyorlash – Istalgan mavzuda avtomatik Referat yaratish (5,000 so'm).
                    
                    /balance sizning bot hamyonidagi balansingiz.
                    /buy xarid bo'limi
                    """;
        };

        rawSender.sendColoredMainMenu(bot, chatId.toString(), text, currentLang);

        if (VIDEO_ADMIN_ID.equals(chatId)) {
            rawSender.sendColoredSingleMenu(bot, chatId.toString(),
                    "🎛️ <b>Admin panel:</b> Video qo'llanma qo'shish uchun tugmani bosing.",
                    "📹 Video qo'shish", "primary");
        }
    }

    public void sendLanguageSelector(SlideBot bot, Long chatId) {
        String text = """
                🇺🇿 Tilni tanlang
                🇷🇺 Выберите язык
                🇺🇸 Choose language:
                """;

        rawSender.sendColoredLanguageMenu(bot, chatId.toString(), text);
    }

    public void sendSimpleMenu(SlideBot bot, Long chatId, String lang) {
        String currentLang = (lang == null) ? "UZ" : lang;
        String text = switch (currentLang) {
            case "RU" -> "🏠 Главное меню";
            case "ENG" -> "🏠 Main menu";
            default -> "🏠 Asosiy menyu";
        };

        rawSender.sendColoredMainMenu(bot, chatId.toString(), text, currentLang);

        if (VIDEO_ADMIN_ID.equals(chatId)) {
            rawSender.sendColoredSingleMenu(bot, chatId.toString(),
                    "🎛️ <b>Admin panel:</b> Video qo'llanma qo'shish uchun tugmani bosing.",
                    "📹 Video qo'shish", "primary");
        }
    }
}