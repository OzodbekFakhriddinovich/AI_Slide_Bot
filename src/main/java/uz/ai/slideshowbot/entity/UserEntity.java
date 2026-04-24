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

    public java.time.LocalDateTime getLastWarningTime() { return lastWarningTime; }
    public void setLastWarningTime(java.time.LocalDateTime lastWarningTime)
    { this.lastWarningTime = lastWarningTime; }

    public Boolean getInWordPdfFlow() { return inWordPdfFlow; }
    public void setInWordPdfFlow(Boolean inWordPdfFlow) { this.inWordPdfFlow = inWordPdfFlow; }

    public Long getWordPdfPendingFileId() { return wordPdfPendingFileId; }
    public void setWordPdfPendingFileId(Long wordPdfPendingFileId) { this.wordPdfPendingFileId = wordPdfPendingFileId; }

    public Boolean getInTestFlow() { return inTestFlow; }
    public void setInTestFlow(Boolean inTestFlow) { this.inTestFlow = inTestFlow; }

    public String getTestTopic() { return testTopic; }
    public void setTestTopic(String testTopic) { this.testTopic = testTopic; }

    public Integer getTestQuestionCount() { return testQuestionCount; }
    public void setTestQuestionCount(Integer testQuestionCount) { this.testQuestionCount = testQuestionCount; }

    public String getTestDifficulty() { return testDifficulty; }
    public void setTestDifficulty(String testDifficulty) { this.testDifficulty = testDifficulty; }

    public Integer getLastTestCountMsgId() { return lastTestCountMsgId; }
    public void setLastTestCountMsgId(Integer lastTestCountMsgId) { this.lastTestCountMsgId = lastTestCountMsgId; }

    public Integer getLastTestInputMsgId() { return lastTestInputMsgId; }
    public void setLastTestInputMsgId(Integer lastTestInputMsgId) { this.lastTestInputMsgId = lastTestInputMsgId; }

    public Integer getLastTestDiffMsgId() { return lastTestDiffMsgId; }
    public void setLastTestDiffMsgId(Integer lastTestDiffMsgId) { this.lastTestDiffMsgId = lastTestDiffMsgId; }

    public Integer getReferralBonusCount() { return referralBonusCount; }
    public void setReferralBonusCount(Integer referralBonusCount) { this.referralBonusCount = referralBonusCount; }

    public Boolean getReferralBonusGiven() { return referralBonusGiven; }
    public void setReferralBonusGiven(Boolean referralBonusGiven) { this.referralBonusGiven = referralBonusGiven; }

    public Boolean getInPdfFlow() { return inPdfFlow; }
    public void setInPdfFlow(Boolean inPdfFlow) { this.inPdfFlow = inPdfFlow; }

    public String getReferralCode() { return referralCode; }
    public void setReferralCode(String referralCode) { this.referralCode = referralCode; }

    public Long getReferrerId() { return referrerId; }
    public void setReferrerId(Long referrerId) { this.referrerId = referrerId; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }

    public Integer getLastSelectedTemplateNumber() { return lastSelectedTemplateNumber; }
    public void setLastSelectedTemplateNumber(Integer n) { this.lastSelectedTemplateNumber = n; }

    public Integer getLastTemplateMessageId() { return lastTemplateMessageId; }
    public void setLastTemplateMessageId(Integer id) { this.lastTemplateMessageId = id; }

    public Integer getLastSelectedTemplateMessageId() { return lastSelectedTemplateMessageId; }
    public void setLastSelectedTemplateMessageId(Integer id) { this.lastSelectedTemplateMessageId = id; }

    public Integer getLastSlideCountMessageId() { return lastSlideCountMessageId; }
    public void setLastSlideCountMessageId(Integer id) { this.lastSlideCountMessageId = id; }

    public String getSlideCount() { return slideCount; }
    public void setSlideCount(String slideCount) { this.slideCount = slideCount; }

    public String getTopic() { return topic; }
    public void setTopic(String topic) { this.topic = topic; }

    public String getUserInfo() { return userInfo; }
    public void setUserInfo(String userInfo) { this.userInfo = userInfo; }

    public UserState getState() { return state; }
    public void setState(UserState state) { this.state = state; }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getChatId() { return chatId; }
    public void setChatId(Long chatId) { this.chatId = chatId; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    public Integer getBalance() { return balance; }
    public void setBalance(Integer balance) { this.balance = balance; }

    public String getPhotoFileId() { return photoFileId; }
    public void setPhotoFileId(String photoFileId) { this.photoFileId = photoFileId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public Integer getLastBotMessageId() { return lastBotMessageId; }
    public void setLastBotMessageId(Integer id) { this.lastBotMessageId = id; }

    public Integer getLastLanguageMessageId() { return lastLanguageMessageId; }
    public void setLastLanguageMessageId(Integer id) { this.lastLanguageMessageId = id; }

    public List<String> getFileList() { return fileList; }
    public void setFileList(List<String> fileList) { this.fileList = fileList; }

    public Boolean getInZipFlow() { return inZipFlow; }
    public void setInZipFlow(Boolean inZipFlow) { this.inZipFlow = inZipFlow; }

    public Boolean getInCheckFlow() { return inCheckFlow; }
    public void setInCheckFlow(Boolean inCheckFlow) { this.inCheckFlow = inCheckFlow; }

    public Long getCheckFileId() { return checkFileId; }
    public void setCheckFileId(Long checkFileId) { this.checkFileId = checkFileId; }

    public Integer getLastBalanceMessageId() { return lastBalanceMessageId; }
    public void setLastBalanceMessageId(Integer id) { this.lastBalanceMessageId = id; }
}