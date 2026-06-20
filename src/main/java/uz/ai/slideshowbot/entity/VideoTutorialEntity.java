package uz.ai.slideshowbot.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "video_tutorials")
public class VideoTutorialEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // "PDF", "TAQDIMOT", "ZIP", "WORD_PDF", "TEST", "REFERAT"
    @Column(name = "button_key", unique = true, length = 100)
    private String buttonKey;

    @Column(name = "video_file_id", length = 500)
    private String videoFileId;

    @Column(name = "video_name", length = 1000)
    private String videoName;

    @Column(name = "active")
    private Boolean active = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getButtonKey() { return buttonKey; }
    public void setButtonKey(String buttonKey) { this.buttonKey = buttonKey; }

    public String getVideoFileId() { return videoFileId; }
    public void setVideoFileId(String videoFileId) { this.videoFileId = videoFileId; }

    public String getVideoName() { return videoName; }
    public void setVideoName(String videoName) { this.videoName = videoName; }

    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}