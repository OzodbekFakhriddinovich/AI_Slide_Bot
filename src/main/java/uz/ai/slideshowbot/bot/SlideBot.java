package uz.ai.slideshowbot.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.ai.slideshowbot.service.BotService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class SlideBot extends TelegramLongPollingBot {

    private final BotService botservice;
    private final ExecutorService executorService;

    @Value("${telegram.bot.username}")
    private String username;

    @Value("${telegram.bot.token}")
    private String token;

    public SlideBot(BotService botservice) {
        this.botservice = botservice;
        // 50 threadli hovuz, update-larni parallel qabul qilish uchun (qotmasligi uchun)
        this.executorService = Executors.newFixedThreadPool(50);
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public void onUpdateReceived(Update update) {
        // Updatelarni parallel ishlash uchun ThreadPool ga beramiz
        executorService.submit(() -> {
            try {
                botservice.handleUpdate(update, this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}
