package uz.ai.slideshowbot.repository;

import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uz.ai.slideshowbot.entity.FileEntity;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface FileRepository extends JpaRepository<FileEntity, Long> {

    List<FileEntity> findAllByChatId(Long chatId);

    @Transactional
    void deleteByChatId(Long chatId);

    @Transactional
    @Modifying
    @Query("DELETE FROM FileEntity f WHERE f.fileType = :fileType AND f.createdAt < :before")
    int deleteByFileTypeAndCreatedAtBefore(@Param("fileType") String fileType,
                                           @Param("before") LocalDateTime before);
}