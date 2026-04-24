package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uz.ai.slideshowbot.bot.SlideBot;

@Service
public class MenuService {

    @Autowired
    private TelegramRawSender rawSender;

    public void sendMainMenu(SlideBot bot, Long chatId, String lang) {
        String currentLang = (lang == null) ? "UZ" : lang;

        String text = switch (currentLang) {
            case "RU" -> """
                    Привет! Добро пожаловать в AI Презентация | Слайд | PPT | PDF Бот
                    
                    Я умный бот, который поможет вам подготовить презентации, визуально представить документы и управлять файлами. Я предлагаю вам следующие услуги:
                    
                    1. Создание слайдов – подготовка профессиональных презентаций в формате PowerPoint или других форматах.
                    2. PDF документы – легкое преобразование изображений в формат PDF.
                    3. Идеи для презентаций – советы по эффективной и понятной организации контента.
                    4. Дизайн презентаций – выбор подходящего дизайна для профессионального вида слайдов.
                    5. ZIP-архивация файлов – возможность легко конвертировать файлы в формат ZIP.
                    6. Создать тест – автоматическая генерация тестов по любой теме (5000 сум).
                    7. Создать реферат – подготовка рефератов в формате Word (скоро).
                    
                    /balance — ваш баланс в кошельке бота. Если вы новый участник, у вас есть бонус 4000 сум.
                    /buy — раздел покупок.
                    """;

            case "ENG" -> """
                    Hello! Welcome to AI Presentation | Slide | PPT | PDF Bot
                    
                    I am an intelligent bot that helps you prepare presentations, visually present documents, and manage files. I offer you the following services:
                    
                    1. Create slides – prepare professional presentations in PowerPoint or other formats.
                    2. PDF documents – easily convert images to PDF format.
                    3. Presentation ideas – tips on organizing content effectively and clearly.
                    4. Presentation design – choosing a suitable design for professional-looking slides.
                    5. Zip files – the ability to easily convert files to ZIP format.
                    6. Create Test – auto-generate tests on any topic (5,000 UZS).
                    7. Create Essay – prepare essays in Word format (coming soon).
                    
                    /balance is your balance in the bot wallet. If you are a new member, you have a 4000 sum bonus.
                    /buy purchase section.
                    """;

            default -> """
                    Salom AI Taqdimot | Slayd | PPT | PDF Bot ga xush kelibsiz
                    
                    Men taqdimot tayyorlash, hujjatlarni vizual tarzda taqdim etish va fayllarni boshqarish bo'yicha yordam beradigan aqlli botman. Sizga quyidagi xizmatlarni taqdim etaman:
                    
                    1. Slaydlar yaratish – PowerPoint yoki boshqa formatlarda professional taqdimotlar tayyorlash.
                    2. PDF hujjatlar – Rasmlarni osongina PDF formatiga o'zgartirish.
                    3. Taqdimot g'oyalari – Kontentni samarali va tushunarli tarzda tashkil qilish bo'yicha maslahatlar.
                    4. Taqdimot dizayni – Professional ko'rinishga ega slaydlar uchun mos dizayn tanlash.
                    5. Fayllarni zip qilish – Fayllarni osongina zip formatiga o'zgartirish imkoniyati.
                    6. Test tayyorlash – Istalgan mavzuda avtomatik test yaratish (5,000 so'm).
                    7. Referat tayyorlash – Word formatida referat tayyorlash (tez orada qo'shiladi).
                    
                    /balance sizning bot hamyonidagi balansingiz. Agar yangi a'zo bo'lgan bo'lsangiz sizda 4000 so'm bonus bor.
                    /buy xarid bo'limi
                    """;
        };

        rawSender.sendColoredMainMenu(bot, chatId.toString(), text, currentLang);
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
    }

}