package uz.ai.slideshowbot.service;

import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.*;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.UserRepository;
import java.util.List;

@Service
public class BroadcastService {

    private final UserRepository userRepository;

    public BroadcastService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void broadcastToAll(SlideBot bot, Message adminMessage) {
        List<UserEntity> allUsers = userRepository.findAll();

        for (UserEntity user : allUsers) {
            try {
                Long chatId = user.getChatId();

                if (adminMessage.hasText()) {
                    SendMessage msg = new SendMessage(chatId.toString(), adminMessage.getText());
                    bot.execute(msg);
                } else if (adminMessage.hasPhoto()) {
                    var photos = adminMessage.getPhoto();
                    String fileId = photos.get(photos.size() - 1).getFileId();
                    SendPhoto photo = new SendPhoto(chatId.toString(), new InputFile(fileId));
                    if (adminMessage.getCaption() != null)
                        photo.setCaption(adminMessage.getCaption());
                    bot.execute(photo);
                } else if (adminMessage.hasVideo()) {
                    String fileId = adminMessage.getVideo().getFileId();
                    SendVideo video = new SendVideo(chatId.toString(), new InputFile(fileId));
                    if (adminMessage.getCaption() != null)
                        video.setCaption(adminMessage.getCaption());
                    bot.execute(video);
                } else if (adminMessage.hasDocument()) {
                    String fileId = adminMessage.getDocument().getFileId();
                    SendDocument doc = new SendDocument(chatId.toString(), new InputFile(fileId));
                    if (adminMessage.getCaption() != null)
                        doc.setCaption(adminMessage.getCaption());
                    bot.execute(doc);
                }

                Thread.sleep(500);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
