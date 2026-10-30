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

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM UserEntity u WHERE u.chatId = :chatId")
    Optional<UserEntity> lockSlideUser(@org.springframework.data.repository.query.Param("chatId") Long chatId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE UserEntity u SET u.balance = COALESCE(u.balance, 0) + :amount WHERE u.chatId = :chatId")
    int creditSlideRefund(@org.springframework.data.repository.query.Param("chatId") Long chatId,
                          @org.springframework.data.repository.query.Param("amount") int amount);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("UPDATE UserEntity u SET u.state = :next WHERE u.chatId = :chatId AND u.state = :expected")
    int finishSlideState(@org.springframework.data.repository.query.Param("chatId") Long chatId,
                         @org.springframework.data.repository.query.Param("expected") uz.ai.slideshowbot.enums.UserState expected,
                         @org.springframework.data.repository.query.Param("next") uz.ai.slideshowbot.enums.UserState next);

    Optional<UserEntity> findByPendingAmountAndPendingTimeAfter(Integer amount, LocalDateTime time);

    boolean existsByPendingAmountAndPendingTimeAfter(Integer amount, LocalDateTime time);

    List<UserEntity> findAllByPendingTimeBeforeAndPendingAmountIsNotNull(LocalDateTime time);

    @org.springframework.data.jpa.repository.Query("SELECT COALESCE(SUM(u.balance), 0) FROM UserEntity u")
    Long getTotalBalance();
}