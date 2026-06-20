package uz.ai.slideshowbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.ai.slideshowbot.entity.VideoTutorialEntity;

import java.util.Optional;

@Repository
public interface VideoTutorialRepository extends JpaRepository<VideoTutorialEntity, Long> {

    Optional<VideoTutorialEntity> findByButtonKeyAndActive(String buttonKey, Boolean active);

    Optional<VideoTutorialEntity> findByButtonKey(String buttonKey);
}