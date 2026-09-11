package uz.ai.slideshowbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.ai.slideshowbot.entity.UserEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByChatId(Long chatId);

    Optional<UserEntity> findByPendingAmountAndPendingTimeAfter(Integer amount, LocalDateTime time);

    boolean existsByPendingAmountAndPendingTimeAfter(Integer amount, LocalDateTime time);

    List<UserEntity> findAllByPendingTimeBeforeAndPendingAmountIsNotNull(LocalDateTime time);
}