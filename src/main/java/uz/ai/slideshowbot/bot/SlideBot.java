package uz.ai.slideshowbot.bot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.objects.Update;
import uz.ai.slideshowbot.service.BotService;

@Component
public class SlideBot extends TelegramLongPollingBot {

    private final BotService botHandlerService;

    @Value("${telegram.bot.username}")
    private String username;

    @Value("${telegram.bot.token}")
    private String token;

    public SlideBot(BotService botHandlerService) {
        this.botHandlerService = botHandlerService;
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
        botHandlerService.handleUpdate(update, this);
    }


}
