package uz.ai.slideshowbot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Persistent receipt for charging, duplicate suppression and interrupted-order refunds. */
@Entity
@Table(name = "slide_orders", uniqueConstraints = @UniqueConstraint(columnNames = {"chat_id", "request_key"}),
        indexes = @Index(name = "idx_slide_order_state_time", columnList = "status,updated_at"))
public class SlideOrderEntity {
    @Id private String id;
    @Column(name = "chat_id", nullable = false) private Long chatId;
    @Column(name = "request_key", nullable = false, length = 80) private String requestKey;
    @Column(nullable = false) private int cost;
    @Column(nullable = false, length = 20) private String status;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }
    public String getRequestKey() { return requestKey; }
    public void setRequestKey(String requestKey) { this.requestKey = requestKey; }
    public int getCost() { return cost; }
    public void setCost(int cost) { this.cost = cost; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
