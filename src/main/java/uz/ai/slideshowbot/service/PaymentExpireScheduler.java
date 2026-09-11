package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Har 60 soniyada ishga tushib, 4 daqiqadan oshgan pending to'lovlarni bekor qiladi.
 */
@Component
public class PaymentExpireScheduler {

    @Autowired private UserRepository userRepository;
    @Autowired private BalanceService balanceService;
    @Autowired private SlideBot slideBot;

    @Scheduled(fixedDelay = 60_000)  // Har 60 soniyada
    public void expireOldPendingPayments() {
        LocalDateTime fourMinutesAgo = LocalDateTime.now().minusMinutes(4);
        List<UserEntity> expiredUsers = userRepository.findAllByPendingTimeBeforeAndPendingAmountIsNotNull(fourMinutesAgo);

        for (UserEntity user : expiredUsers) {
            try {
                balanceService.expirePendingPayment(slideBot, user);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
