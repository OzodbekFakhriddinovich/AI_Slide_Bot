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

    private Integer balance = 4000;

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

    @Column(name = "last_check_file")
    private String lastCheckFile;

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

    public Boolean getInPdfFlow() { return inPdfFlow; }
    public void setInPdfFlow(Boolean inPdfFlow) { this.inPdfFlow = inPdfFlow; }

    public String getReferralCode() {
        return referralCode;
    }
    public void setReferralCode(String referralCode) {
        this.referralCode = referralCode;
    }
    public Long getReferrerId() {
        return referrerId;
    }
    public void setReferrerId(Long referrerId) {
        this.referrerId = referrerId;
    }


    private String subjectName;

    public String getSubjectName() {
        return subjectName;
    }

    public void setSubjectName(String subjectName) {
        this.subjectName = subjectName;
    }


    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public Integer getLastSelectedTemplateNumber() {
        return lastSelectedTemplateNumber;
    }

    public void setLastSelectedTemplateNumber(Integer lastSelectedTemplateNumber) {
        this.lastSelectedTemplateNumber = lastSelectedTemplateNumber;
    }


    public Integer getLastTemplateMessageId() {
        return lastTemplateMessageId;
    }

    public void setLastTemplateMessageId(Integer lastTemplateMessageId) {
        this.lastTemplateMessageId = lastTemplateMessageId;
    }

    public Integer getLastSelectedTemplateMessageId() {
        return lastSelectedTemplateMessageId;
    }

    public void setLastSelectedTemplateMessageId(Integer lastSelectedTemplateMessageId) {
        this.lastSelectedTemplateMessageId = lastSelectedTemplateMessageId;
    }

    public Integer getLastSlideCountMessageId() { return lastSlideCountMessageId; }
    public void setLastSlideCountMessageId(Integer id) { this.lastSlideCountMessageId = id; }


    public String getSlideCount() {
        return slideCount;
    }

    public void setSlideCount(String slideCount) {
        this.slideCount = slideCount;
    }


    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getUserInfo() {
        return userInfo;
    }

    public void setUserInfo(String userInfo) {
        this.userInfo = userInfo;
    }

    public UserState getState() {
        return state;
    }

    public void setState(UserState state) {
        this.state = state;
    }



    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }


    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public Integer getBalance() {
        return balance;
    }

    public void setBalance(Integer balance) {
        this.balance = balance;
    }

    public String getPhotoFileId() {
        return photoFileId;
    }

    public void setPhotoFileId(String photoFileId) {
        this.photoFileId = photoFileId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public Integer getLastBotMessageId() {
        return lastBotMessageId;
    }

    public void setLastBotMessageId(Integer lastBotMessageId) {
        this.lastBotMessageId = lastBotMessageId;
    }

    public Integer getLastLanguageMessageId() {
        return lastLanguageMessageId;
    }

    public void setLastLanguageMessageId(Integer lastLanguageMessageId) {
        this.lastLanguageMessageId = lastLanguageMessageId;
    }

    public List<String> getFileList() {
        return fileList;
    }

    public void setFileList(List<String> fileList) {
        this.fileList = fileList;
    }

    public Boolean getInZipFlow() {
        return inZipFlow;
    }

    public void setInZipFlow(Boolean inZipFlow) {
        this.inZipFlow = inZipFlow;
    }

    public Boolean getInCheckFlow() {
        return inCheckFlow;
    }

    public void setInCheckFlow(Boolean inCheckFlow) {
        this.inCheckFlow = inCheckFlow;
    }

    public String getLastCheckFile() {
        return lastCheckFile;
    }

    public void setLastCheckFile(String lastCheckFile) {
        this.lastCheckFile = lastCheckFile;
    }

    public Integer getLastBalanceMessageId() {
        return lastBalanceMessageId;
    }

    public void setLastBalanceMessageId(Integer lastBalanceMessageId) {
        this.lastBalanceMessageId = lastBalanceMessageId;
    }
}
