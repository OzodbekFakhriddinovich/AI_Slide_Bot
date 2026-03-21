package uz.ai.slideshowbot.config;

import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;
import uz.ai.slideshowbot.bot.SlideBot;

@Component
public class BotInitializer {

    public BotInitializer(SlideBot slideBot) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(slideBot);
            System.out.println("✅ Bot muvaffaqiyatli ishga tushdi!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
