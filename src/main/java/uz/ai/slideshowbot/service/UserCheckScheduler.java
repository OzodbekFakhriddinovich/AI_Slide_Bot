package uz.ai.slideshowbot.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.groupadministration.GetChatMember;
import org.telegram.telegrambots.meta.api.objects.chatmember.ChatMember;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.UserRepository;

import java.util.List;
import java.util.concurrent.*;

@Component
@RequiredArgsConstructor
public class UserCheckScheduler {

    private final UserRepository userRepository;
    private final BotService botService;
    private final SlideBot bot;

    private static final String CHANNEL_1 = "@pptx_pdf_mustaqil_ish";
    private static final String CHANNEL_2 = "@talimxabarlari";

    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    @Scheduled(fixedDelay = 10)
    public void checkUsers() {
        List<UserEntity> users = userRepository.findAll();

        List<CompletableFuture<Void>> futures = users.stream()
                .filter(u -> u.getLanguage() != null)
                .map(u -> CompletableFuture.runAsync(() -> process(u), executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    private void process(UserEntity user) {
        try {
            Long chatId = user.getChatId();
            boolean in1 = checkChannel(chatId, CHANNEL_1);
            boolean in2 = checkChannel(chatId, CHANNEL_2);
            boolean isMember = in1 && in2;

            botService.updateChannelCache(chatId, isMember);

            if (isMember) {
                if (user.getLastWarningTime() != null) {
                    user.setLastWarningTime(null);
                    userRepository.save(user);
                }
                return;
            }

            if (user.getLastWarningTime() != null) return;

            user.setLastWarningTime(java.time.LocalDateTime.now());
            userRepository.save(user);

            botService.sendChannelWarningMessage(bot, user, chatId, in1, in2);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean checkChannel(Long userId, String channelUsername) {
        try {
            GetChatMember req = new GetChatMember(channelUsername, userId);
            ChatMember member = bot.execute(req);
            if (member == null || member.getStatus() == null) return true;
            String status = member.getStatus();
            return status.equals("member") || status.equals("administrator") || status.equals("creator");
        } catch (Exception e) {
            return true;
        }
    }
}