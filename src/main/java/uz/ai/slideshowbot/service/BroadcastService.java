package uz.ai.slideshowbot.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.CopyMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.UserRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class BroadcastService {

    private final UserRepository userRepository;

    private static final Long ADMIN_CHAT_ID = 6290351864L;

    public BroadcastService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void broadcastToAll(SlideBot bot, Message adminMessage) {
        List<UserEntity> allUsers = userRepository.findAll();

        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger fail    = new AtomicInteger(0);

        String fromChatId = adminMessage.getChatId().toString();
        Integer messageId = adminMessage.getMessageId();

        for (UserEntity user : allUsers) {
            try {
                Long chatId = user.getChatId();

                if (chatId.equals(ADMIN_CHAT_ID) || chatId.equals(8429918073L)) continue;

                CopyMessage copy = new CopyMessage();
                copy.setFromChatId(fromChatId);
                copy.setMessageId(messageId);
                copy.setChatId(chatId.toString());
                bot.execute(copy);

                success.incrementAndGet();
                Thread.sleep(50);

            } catch (Exception e) {
                fail.incrementAndGet();
            }
        }

        try {
            String report = "✅ Broadcast yakunlandi!\n\n" +
                            "👥 Jami foydalanuvchilar: " + allUsers.size() + "\n" +
                            "✅ Muvaffaqiyatli yuborildi: " + success.get() + "\n" +
                            "❌ Yetib bormadi (bloklagan): " + fail.get();
            SendMessage msg = new SendMessage(fromChatId, report);
            bot.execute(msg);
        } catch (Exception ignored) {}
    }
}