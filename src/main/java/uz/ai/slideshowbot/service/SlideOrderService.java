package uz.ai.slideshowbot.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uz.ai.slideshowbot.entity.SlideOrderEntity;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.enums.UserState;
import uz.ai.slideshowbot.repository.SlideOrderRepository;
import uz.ai.slideshowbot.repository.UserRepository;
import uz.ai.slideshowbot.slides.SlideRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class SlideOrderService {
    private final UserRepository users;
    private final SlideOrderRepository orders;

    public SlideOrderService(UserRepository users, SlideOrderRepository orders) {
        this.users = users;
        this.orders = orders;
    }

    @Transactional
    public SlideOrderEntity reserve(Long chatId, SlideRequest request) {
        if (!java.util.Set.of(5000, 7000).contains(request.tariff()))
            throw new IllegalArgumentException("Tarif tanlovi noto'g'ri.");
        UserEntity user = users.lockSlideUser(chatId).orElseThrow(() -> new IllegalArgumentException("Avval /start bosing."));
        if (orders.findByChatIdAndRequestKey(chatId, request.requestId()).isPresent())
            throw new IllegalArgumentException("Bu buyurtma avval qabul qilingan. Yangi buyurtma uchun web appni yangidan oching.");
        if (orders.existsByChatIdAndStatus(chatId, "RUNNING"))
            throw new IllegalArgumentException("Avvalgi taqdimotingiz tayyorlanmoqda. Tugashini kuting.");
        int balance = user.getBalance() == null ? 0 : user.getBalance();
        if (balance < request.tariff()) throw new InsufficientBalanceException(balance, request.tariff());
        user.setBalance(balance - request.tariff());
        user.setState(UserState.CREATING_SLIDES);
        users.save(user);
        SlideOrderEntity order = new SlideOrderEntity();
        order.setId(UUID.randomUUID().toString());
        order.setChatId(chatId);
        order.setRequestKey(request.requestId());
        order.setCost(request.tariff());
        order.setStatus("RUNNING");
        order.setUpdatedAt(LocalDateTime.now());
        return orders.save(order);
    }

    @Transactional
    public boolean touch(String id) {
        SlideOrderEntity order = orders.lockById(id).orElse(null);
        if (order == null || !"RUNNING".equals(order.getStatus())) return false;
        order.setUpdatedAt(LocalDateTime.now());
        orders.save(order);
        return true;
    }

    @Transactional
    public boolean complete(String id) { return finish(id, false, null); }

    @Transactional
    public boolean refund(String id) { return finish(id, true, null); }

    @Transactional
    public boolean expire(String id, LocalDateTime cutoff) { return finish(id, true, cutoff); }

    private boolean finish(String id, boolean refund, LocalDateTime cutoff) {
        SlideOrderEntity order = orders.lockById(id).orElse(null);
        if (order == null || !"RUNNING".equals(order.getStatus())) return false;
        if (cutoff != null && !order.getUpdatedAt().isBefore(cutoff)) return false;
        if (refund) users.creditSlideRefund(order.getChatId(), order.getCost());
        users.finishSlideState(order.getChatId(), UserState.CREATING_SLIDES, UserState.START);
        order.setStatus(refund ? "REFUNDED" : "SENT");
        order.setUpdatedAt(LocalDateTime.now());
        orders.save(order);
        return true;
    }

    @Transactional(readOnly = true)
    public List<SlideOrderEntity> abandoned(LocalDateTime cutoff) {
        return orders.findByStatusAndUpdatedAtBefore("RUNNING", cutoff);
    }

    public static class InsufficientBalanceException extends IllegalArgumentException {
        private final int balance;
        private final int required;
        public InsufficientBalanceException(int balance, int required) {
            super("Balansingiz yetarli emas. Balans: " + balance + " so'm. Kerak: " + required + " so'm.");
            this.balance = balance;
            this.required = required;
        }
        public int balance() { return balance; }
        public int required() { return required; }
    }
}
