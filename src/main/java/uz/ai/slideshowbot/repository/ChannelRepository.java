package uz.ai.slideshowbot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import uz.ai.slideshowbot.entity.ChannelEntity;

import java.util.List;

@Repository
public interface ChannelRepository extends JpaRepository<ChannelEntity, Long> {
    List<ChannelEntity> findAllByIsRequiredTrue();
}
