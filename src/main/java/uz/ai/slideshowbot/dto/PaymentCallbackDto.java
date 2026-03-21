package uz.ai.slideshowbot.dto;

public class PaymentCallbackDto {
    private Long userId;
    private int amount;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public int getAmount() { return amount; }
    public void setAmount(int amount) { this.amount = amount; }
}
