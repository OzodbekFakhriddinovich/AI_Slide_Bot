package uz.ai.slideshowbot.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import uz.ai.slideshowbot.entity.SlideOrderEntity;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SlideOrderRepository extends JpaRepository<SlideOrderEntity, String> {
    Optional<SlideOrderEntity> findByChatIdAndRequestKey(Long chatId, String requestKey);
    boolean existsByChatIdAndStatus(Long chatId, String status);
    List<SlideOrderEntity> findByStatusAndUpdatedAtBefore(String status, LocalDateTime before);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM SlideOrderEntity o WHERE o.id = :id")
    Optional<SlideOrderEntity> lockById(@Param("id") String id);
}
