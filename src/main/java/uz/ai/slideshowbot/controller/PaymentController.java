package uz.ai.slideshowbot.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.repository.UserRepository;
import uz.ai.slideshowbot.service.BalanceService;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Python userbot (HUMOcardbot kuzatuvchi) shu endpointga POST yuboradi.
 * POST /api/payment/notify
 * Body: { "secret": "<configured>", "amount": 6010 }
 */
@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    @Autowired private UserRepository userRepository;
    @Autowired private BalanceService balanceService;
    @Autowired private SlideBot slideBot;

    @Value("${payment.api-secret}")
    private String apiSecret;

    @PostMapping("/notify")
    public ResponseEntity<String> handlePayment(@RequestBody Map<String, Object> payload) {
        // 1. Secret tekshirish
        String secret = (String) payload.get("secret");
        if (secret == null || !secret.equals(apiSecret)) {
            return ResponseEntity.status(403).body("Unauthorized");
        }

        // 2. Summani olish
        Object amountObj = payload.get("amount");
        if (amountObj == null) {
            return ResponseEntity.badRequest().body("Amount is missing");
        }

        int amount;
        try {
            amount = Integer.parseInt(amountObj.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body("Invalid amount");
        }

        // 3. Shu summani kutayotgan foydalanuvchini topish (4 daqiqa ichida)
        LocalDateTime fourMinAgo = LocalDateTime.now().minusMinutes(4);
        Optional<UserEntity> optUser = userRepository.findByPendingAmountAndPendingTimeAfter(amount, fourMinAgo);

        if (optUser.isEmpty()) {
            return ResponseEntity.status(404).body("No pending payment found for amount: " + amount);
        }

        UserEntity user = optUser.get();

        // 4. Balansni to'ldirish va foydalanuvchiga xabar yuborish
        balanceService.confirmAutoPayment(slideBot, user, amount);

        return ResponseEntity.ok("Success: balance updated for user " + user.getChatId());
    }
}
