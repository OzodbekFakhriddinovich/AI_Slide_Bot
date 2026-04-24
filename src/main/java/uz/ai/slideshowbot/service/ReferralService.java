package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.UserRepository;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

@Service
public class ReferralService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TelegramRawSender rawSender;

    private String generateReferralCode(UserEntity user) {
        if (user.getReferralCode() == null) {
            user.setReferralCode("ref_" + user.getChatId());
            userRepository.save(user);
        }
        return user.getReferralCode();
    }

    public void handleReferralCommand(SlideBot bot, UserEntity user, Long chatId) {
        try {
            String refCode = generateReferralCode(user);
            String botUsername = bot.getBotUsername();
            String refLink = "https://t.me/" + botUsername + "?start=" + refCode;

            String promoText = switch (user.getLanguage()) {
                case "RU" -> """
                        🚀Начните зарабатывать:
                        
                        Получайте 1000 сум за каждого приглашенного друга! Не прекращайте приглашать! 💸
                        
                        🎁 Также есть дополнительные бонусы! Не упустите эту возможность!\s""";

                case "ENG" -> """
                        🚀Start earning:
                     
                        Get 1000 sum for each friend you invite! Don't stop inviting! 💸
                        
                        🎁 There are also additional bonuses! Don't miss this opportunity! 🎉""";

                default -> """
                        🚀Pul ishlashni boshlang:
                        
                        
                        Har bir taklif qilgan do‘stingiz uchun 1000 sum oling! Taklif qilishdan to‘xtamang! 💸
                        
                        🎁 Qo‘shimcha bonuslar ham bor! Bu imkoniyatni qo‘ldan boy bermang! 🎉""";
            };

            String messageText = promoText + "\n\n🔗 Sizning referral havolangiz:\n" + refLink;

            String encodedLink = URLEncoder.encode(refLink, StandardCharsets.UTF_8);
            String shareUrl = "https://t.me/share/url?url=" + encodedLink;

            String btnText = switch (user.getLanguage()) {
                case "RU" -> "📤 Do'stlarga yuborish";
                case "ENG" -> "📤 Share with friends";
                default -> "📤 Do'stlarga ulashish";
            };

            rawSender.sendMessageWithStyledBtn(
                    bot,
                    chatId.toString(),
                    messageText,
                    btnText,
                    shareUrl,
                    "danger"
            );

        } catch (Exception e) {
            e.printStackTrace();
            try {
                SendMessage errorMsg = new SendMessage(chatId.toString(), "❌ Xatolik yuz berdi. Qayta urinib ko'ring.");
                bot.execute(errorMsg);
            } catch (Exception ignored) {
            }
        }
    }

    public void handleReferralJoin(SlideBot bot, UserEntity newUser, String refCode) {
        try {
            if (refCode == null || !refCode.startsWith("ref_")) return;

            Long referrerId;
            try {
                referrerId = Long.parseLong(refCode.substring(4));
            } catch (NumberFormatException e) {
                return;
            }

            if (referrerId.equals(newUser.getChatId())) return;

            if (newUser.getReferrerId() == null) {
                Optional<UserEntity> optionalReferrer = userRepository.findByChatId(referrerId);
                if (optionalReferrer.isPresent()) {
                    newUser.setReferrerId(referrerId);
                    userRepository.save(newUser);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}