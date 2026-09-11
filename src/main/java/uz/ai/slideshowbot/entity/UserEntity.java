package uz.ai.slideshowbot.entity;

import jakarta.persistence.*;
import uz.ai.slideshowbot.enums.UserState;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private Long chatId;

    private UserState state;
    private String language;
    private Integer balance = 0;
    private String photoFileId;

    @Column
    private String username;

    @Column
    private String firstName;

    private Integer lastBotMessageId;
    private Integer lastLanguageMessageId;

    @ElementCollection
    private List<String> fileList = new ArrayList<>();

    private Boolean inZipFlow = false;
    private Boolean inCheckFlow;

    @Column(name = "check_file_id")
    private Long checkFileId;

    @Column(name = "last_balance_message_id")
    private Integer lastBalanceMessageId;

    // === AVTO TO'LOV FIELDLARI ===
    @Column(name = "pending_amount")
    private Integer pendingAmount;

    @Column(name = "pending_time")
    private java.time.LocalDateTime pendingTime;

    @Column(name = "pending_message_id")
    private Integer pendingMessageId;

    /** Asl tanlangan summa (6000), pendingAmount = 6010 (unique tiyinlar bilan) */
    @Column(name = "pending_base_amount")
    private Integer pendingBaseAmount;

    private String topic;
    private String userInfo;
    private String slideCount;

    private Integer lastSlideCountMessageId;
    private Integer lastTemplateMessageId;
    private Integer lastSelectedTemplateMessageId;
    private Integer lastSelectedTemplateNumber;
    private String template;

    private String referralCode;
    private Long referrerId;

    @Column(name = "in_pdf_flow")
    private Boolean inPdfFlow = false;

    private String subjectName;
    private Boolean referralBonusGiven = false;

    @Column(name = "referral_bonus_count", nullable = false)
    private Integer referralBonusCount = 0;

    @Column(name = "in_test_flow")
    private Boolean inTestFlow = false;

    @Column(name = "test_topic")
    private String testTopic;

    @Column(name = "test_question_count")
    private Integer testQuestionCount;

    @Column(name = "test_difficulty")
    private String testDifficulty;

    @Column(name = "last_test_count_msg_id")
    private Integer lastTestCountMsgId;

    @Column(name = "last_test_input_msg_id")
    private Integer lastTestInputMsgId;

    @Column(name = "last_test_diff_msg_id")
    private Integer lastTestDiffMsgId;

    @Column(name = "in_word_pdf_flow")
    private Boolean inWordPdfFlow = false;

    @Column(name = "word_pdf_pending_file_id")
    private Long wordPdfPendingFileId;

    @Column(name = "last_warning_time")
    private java.time.LocalDateTime lastWarningTime;

    @Column(name = "in_referat_flow")
    private Boolean inReferatFlow = false;

    @Column(name = "referat_topic", length = 500)
    private String referatTopic;

    @Column(name = "referat_ism", length = 200)
    private String referatIsm;

    @Column(name = "referat_sahifa")
    private Integer referatSahifa;

    public java.time.LocalDateTime getLastWarningTime() { return lastWarningTime; }
    public void setLastWarningTime(java.time.LocalDateTime v) { this.lastWarningTime = v; }

    public Boolean getInWordPdfFlow() { return inWordPdfFlow; }
    public void setInWordPdfFlow(Boolean v) { this.inWordPdfFlow = v; }

    public Long getWordPdfPendingFileId() { return wordPdfPendingFileId; }
    public void setWordPdfPendingFileId(Long v) { this.wordPdfPendingFileId = v; }

    public Boolean getInTestFlow() { return inTestFlow; }
    public void setInTestFlow(Boolean v) { this.inTestFlow = v; }

    public String getTestTopic() { return testTopic; }
    public void setTestTopic(String v) { this.testTopic = v; }

    public Integer getTestQuestionCount() { return testQuestionCount; }
    public void setTestQuestionCount(Integer v) { this.testQuestionCount = v; }

    public String getTestDifficulty() { return testDifficulty; }
    public void setTestDifficulty(String v) { this.testDifficulty = v; }

    public Integer getLastTestCountMsgId() { return lastTestCountMsgId; }
    public void setLastTestCountMsgId(Integer v) { this.lastTestCountMsgId = v; }

    public Integer getLastTestInputMsgId() { return lastTestInputMsgId; }
    public void setLastTestInputMsgId(Integer v) { this.lastTestInputMsgId = v; }

    public Integer getLastTestDiffMsgId() { return lastTestDiffMsgId; }
    public void setLastTestDiffMsgId(Integer v) { this.lastTestDiffMsgId = v; }

    public Integer getReferralBonusCount() { return referralBonusCount; }
    public void setReferralBonusCount(Integer v) { this.referralBonusCount = v; }

    public Boolean getReferralBonusGiven() { return referralBonusGiven; }
    public void setReferralBonusGiven(Boolean v) { this.referralBonusGiven = v; }

    public Boolean getInPdfFlow() { return inPdfFlow; }
    public void setInPdfFlow(Boolean v) { this.inPdfFlow = v; }

    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String v) { this.referralCode = v; }

    public Long getReferrerId() { return referrerId; }
    public void setReferrerId(Long v) { this.referrerId = v; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String v) { this.subjectName = v; }

    public String getTemplate() { return template; }
    public void setTemplate(String v) { this.template = v; }

    public Integer getLastSelectedTemplateNumber() { return lastSelectedTemplateNumber; }
    public void setLastSelectedTemplateNumber(Integer n) { this.lastSelectedTemplateNumber = n; }

    public Integer getLastTemplateMessageId() { return lastTemplateMessageId; }
    public void setLastTemplateMessageId(Integer id) { this.lastTemplateMessageId = id; }

    public Integer getLastSelectedTemplateMessageId() { return lastSelectedTemplateMessageId; }
    public void setLastSelectedTemplateMessageId(Integer id) { this.lastSelectedTemplateMessageId = id; }

    public Integer getLastSlideCountMessageId() { return lastSlideCountMessageId; }
    public void setLastSlideCountMessageId(Integer id) { this.lastSlideCountMessageId = id; }

    public String getSlideCount() { return slideCount; }
    public void setSlideCount(String v) { this.slideCount = v; }

    public String getTopic() { return topic; }
    public void setTopic(String v) { this.topic = v; }

    public String getUserInfo() { return userInfo; }
    public void setUserInfo(String v) { this.userInfo = v; }

    public UserState getState() { return state; }
    public void setState(UserState v) { this.state = v; }

    public Long getId() { return id; }
    public void setId(Long v) { this.id = v; }

    public Long getChatId() { return chatId; }
    public void setChatId(Long v) { this.chatId = v; }

    public String getLanguage() { return language; }
    public void setLanguage(String v) { this.language = v; }

    public Integer getBalance() { return balance; }
    public void setBalance(Integer v) { this.balance = v; }

    public String getPhotoFileId() { return photoFileId; }
    public void setPhotoFileId(String v) { this.photoFileId = v; }

    public String getUsername() { return username; }
    public void setUsername(String v) { this.username = v; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String v) { this.firstName = v; }

    public Integer getLastBotMessageId() { return lastBotMessageId; }
    public void setLastBotMessageId(Integer id) { this.lastBotMessageId = id; }

    public Integer getLastLanguageMessageId() { return lastLanguageMessageId; }
    public void setLastLanguageMessageId(Integer id) { this.lastLanguageMessageId = id; }

    public List<String> getFileList() { return fileList; }
    public void setFileList(List<String> v) { this.fileList = v; }

    public Boolean getInZipFlow() { return inZipFlow; }
    public void setInZipFlow(Boolean v) { this.inZipFlow = v; }

    public Boolean getInCheckFlow() { return inCheckFlow; }
    public void setInCheckFlow(Boolean v) { this.inCheckFlow = v; }

    public Long getCheckFileId() { return checkFileId; }
    public void setCheckFileId(Long v) { this.checkFileId = v; }

    public Integer getLastBalanceMessageId() { return lastBalanceMessageId; }
    public void setLastBalanceMessageId(Integer id) { this.lastBalanceMessageId = id; }

    public Integer getPendingAmount() { return pendingAmount; }
    public void setPendingAmount(Integer v) { this.pendingAmount = v; }

    public java.time.LocalDateTime getPendingTime() { return pendingTime; }
    public void setPendingTime(java.time.LocalDateTime v) { this.pendingTime = v; }

    public Integer getPendingMessageId() { return pendingMessageId; }
    public void setPendingMessageId(Integer v) { this.pendingMessageId = v; }

    public Integer getPendingBaseAmount() { return pendingBaseAmount; }
    public void setPendingBaseAmount(Integer v) { this.pendingBaseAmount = v; }

    public Boolean getInReferatFlow() { return inReferatFlow; }
    public void setInReferatFlow(Boolean v) { this.inReferatFlow = v; }

    public String getReferatTopic() { return referatTopic; }
    public void setReferatTopic(String v) { this.referatTopic = v; }

    public String getReferatIsm() { return referatIsm; }
    public void setReferatIsm(String v) { this.referatIsm = v; }

    public Integer getReferatSahifa() { return referatSahifa; }
    public void setReferatSahifa(Integer v) { this.referatSahifa = v; }
}