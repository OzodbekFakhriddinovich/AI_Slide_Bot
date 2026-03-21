package uz.ai.slideshowbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.ai.slideshowbot.entity.UserEntity;

import java.util.Optional;  // Bu importni qo'sh

@Repository
public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByChatId(Long chatId);
}