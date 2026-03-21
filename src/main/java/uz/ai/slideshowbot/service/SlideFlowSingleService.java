package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.enums.UserState;
import uz.ai.slideshowbot.repository.UserRepository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class SlideFlowSingleService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MenuService menuService;

    @Autowired
    private GptTextGeneratorService gptService;

    @Autowired
    private PptxCreatorService pptxService;

    public SendDocument createSlidesForUser(UserEntity user, SlideBot bot,
                                            GptTextGeneratorService gptService,
                                            PptxCreatorService pptxService) {
        try {
            int slideCount;
            try {
                slideCount = Integer.parseInt(user.getSlideCount());
            } catch (NumberFormatException e) {
                slideCount = 1;
            }

            List<GptTextGeneratorService.SlideData> slideData =
                    gptService.generateSlides(user.getTopic(), slideCount, user.getLanguage());

            String pptxPath = pptxService.createPptx(
                    user.getTopic(),
                    user.getUserInfo(),
                    user.getSubjectName(),
                    slideData,
                    user.getTemplate()
            );

            SendDocument doc = new SendDocument();
            doc.setChatId(user.getChatId().toString());
            doc.setDocument(new InputFile(new java.io.File(pptxPath)));
            doc.setCaption("✅ Slayd tayyor! Mavzu: " + user.getTopic());

            return doc;

        } catch (Exception e) {
            e.printStackTrace();

            File errorFile = new File("error.txt");
            if (!errorFile.exists()) {
                try {
                    errorFile.createNewFile();
                } catch (IOException ignored) {}
            }
            return new SendDocument(user.getChatId().toString(),
                    new InputFile(errorFile));
        }
    }

    public void sendGeneratingMessage(SlideBot bot, Long chatId, String lang) {
        String msgText = switch (lang) {
            case "RU" -> "🧠 Генерация презентации... Пожалуйста, подождите ⏳";
            case "ENG" -> "🧠 Generating your slides... Please wait ⏳";
            default -> "🧠 Slaydlar yaratilmoqda... Iltimos, kuting ⏳";
        };
        try {
            bot.execute(new SendMessage(chatId.toString(), msgText));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleMessage(UserEntity user, Message message, SlideBot bot) {
        Long chatId = message.getChatId();
        String text = message.getText();
        UserState state = user.getState();
        String lang = user.getLanguage();

        SendMessage msg;

        if (state == null || state == UserState.START) {
            if (text.contains("Taqdimot yaratish") || text.contains("Slayd")) {
                user.setState(UserState.WAITING_TOPIC);
                userRepository.save(user);

                msg = new SendMessage(chatId.toString(), getText("topic", lang));
                send(bot, msg);
            } else {
                msg = new SendMessage(chatId.toString(), getText("invalid_command", lang));
                send(bot, msg);
            }

        } else if (state == UserState.WAITING_TOPIC) {
            user.setTopic(text);
            user.setState(UserState.WAITING_INFO);
            userRepository.save(user);

            msg = new SendMessage(chatId.toString(), getText("user_info", lang));
            send(bot, msg);

        } else if (state == UserState.WAITING_INFO) {
            user.setUserInfo(text);
            user.setState(UserState.WAITING_SLIDE_COUNT);
            userRepository.save(user);

            SendMessage slideCountMsg = askSlideCount(chatId, lang);
            try {
                Message sentMessage = bot.execute(slideCountMsg);
                if (sentMessage != null) {
                    user.setLastSlideCountMessageId(sentMessage.getMessageId());
                    userRepository.save(user);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if (state == UserState.WAITING_SLIDE_COUNT) {
            msg = askSlideCount(chatId, lang);
            send(bot, msg);

        } else if (state == UserState.WAITING_TEMPLATE) {
            try {
                int templateNumber = user.getLastSelectedTemplateNumber();
                int slideCount = Integer.parseInt(user.getSlideCount());
                int slidePrice = 500;
                int totalCost = slideCount * slidePrice;

                if (user.getBalance() < totalCost) {
                    sendOverlayImage(bot, chatId,
                            "C:\\Users\\User\\Desktop\\saqlash\\shablon.jpg",
                            "❌ Mablag‘ yetarli emas!");
                    return;
                }

                user.setBalance(user.getBalance() - totalCost);
                userRepository.save(user);

                sendGeneratingMessage(bot, chatId, lang);

                SendDocument doc = createSlidesForUser(user, bot, gptService, pptxService);
                bot.execute(doc);


                SendMessage balanceMsg = new SendMessage(chatId.toString(),
                        "💰 Qolgan balans: " + user.getBalance() + " so‘m");
                bot.execute(balanceMsg);

                user.setState(UserState.START);
                userRepository.save(user);

            } catch (Exception e) {
                e.printStackTrace();
                SendMessage errorMsg = new SendMessage(chatId.toString(),
                        "❌ Xatolik yuz berdi. Iltimos, qayta urinib ko‘ring.");
                send(bot, errorMsg);
            }

        } else {
            msg = new SendMessage(chatId.toString(), getText("invalid_command", lang));
            send(bot, msg);
        }
    }

    public void handleSlideTemplateSelection(UserEntity user, Message message, SlideBot bot) {
        Long chatId = message.getChatId();
        String text = message.getText();
        String lang = user.getLanguage();

        int selectedSlides = 0;
        try {
            selectedSlides = Integer.parseInt(text.replace("SLIDE_COUNT_", ""));
        } catch (NumberFormatException e) {
            selectedSlides = 0;
        }

        int slidePrice = 500;
        int requiredBalance = selectedSlides * slidePrice;

        if (user.getBalance() < requiredBalance) {
            sendOverlayImage(bot, chatId,
                    "C:\\Users\\User\\Desktop\\saqlash\\shablon.jpg",
                    "❌ Mablag‘ yetarli emas!");
            return;
        }

        user.setSlideCount(String.valueOf(selectedSlides));
        user.setState(UserState.WAITING_TEMPLATE);
        userRepository.save(user);

        if (user.getLastSlideCountMessageId() != null) {
            try {
                DeleteMessage deleteMsg = new DeleteMessage();
                deleteMsg.setChatId(chatId.toString());
                deleteMsg.setMessageId(user.getLastSlideCountMessageId());
                bot.execute(deleteMsg);
            } catch (Exception e) {
                e.printStackTrace();
            }
            user.setLastSlideCountMessageId(null);
            userRepository.save(user);
        }

        sendTemplateOptions(bot, user, chatId, lang);
    }


    private void deletePreviousTemplateMessage(SlideBot bot, UserEntity user, Long chatId) {
        if (user.getLastTemplateMessageId() != null) {
            try {
                bot.execute(new DeleteMessage(chatId.toString(), user.getLastTemplateMessageId()));
            } catch (Exception e) {
                e.printStackTrace();
            }
            user.setLastTemplateMessageId(null);
            userRepository.save(user);
        }
    }
    private void deletePreviousSelectedTemplate(SlideBot bot, UserEntity user, Long chatId) {
        if (user.getLastSelectedTemplateMessageId() != null) {
            try {
                bot.execute(new DeleteMessage(chatId.toString(), user.getLastSelectedTemplateMessageId()));
            } catch (Exception e) {
                e.printStackTrace();
            }
            user.setLastSelectedTemplateMessageId(null);
            userRepository.save(user);
        }
    }

    public void handleCallback(UserEntity user, CallbackQuery query, SlideBot bot) {
        Long chatId = query.getMessage().getChatId();
        String data = query.getData();

        try {
            if (data.startsWith("TEMPLATE_")) {
                int templateNumber = Integer.parseInt(data.replace("TEMPLATE_", ""));
                deletePreviousTemplateMessage(bot, user, chatId);
                sendSelectedTemplate(bot, user, chatId, templateNumber, user.getLanguage());

            } else if (data.equals("BACK_TO_TEMPLATES")) {
                deletePreviousSelectedTemplate(bot, user, chatId);
                sendTemplateOptions(bot, user, chatId, user.getLanguage());

            } else if (data.startsWith("CREATE_SLIDE_")) {
                int templateNumber = Integer.parseInt(data.replace("CREATE_SLIDE_", ""));

                user.setLastSelectedTemplateNumber(templateNumber);
                userRepository.save(user);

                int slideCount = 1;
                try {
                    slideCount = Integer.parseInt(user.getSlideCount());
                } catch (Exception ignored) {}

                int slidePrice = 500;
                int totalCost = slidePrice * slideCount;

                if (user.getBalance() < totalCost) {
                    sendOverlayImage(bot, chatId,
                            "C:\\Users\\User\\Desktop\\saqlash\\shablon.jpg",
                            "❌ Mablag‘ yetarli emas!");
                    return;
                }

                user.setBalance(user.getBalance() - totalCost);
                userRepository.save(user);

                sendGeneratingMessage(bot, chatId, user.getLanguage());

                try {
                    SendDocument doc = createSlidesForUser(user, bot, gptService, pptxService);
                    bot.execute(doc);


                    SendMessage balanceMsg = new SendMessage();
                    balanceMsg.setChatId(chatId.toString());
                    balanceMsg.setText("💰 Qolgan balans: " + user.getBalance() + " so‘m");
                    bot.execute(balanceMsg);

                    user.setState(UserState.START);
                    userRepository.save(user);

                } catch (Exception e) {
                    e.printStackTrace();
                    SendMessage error = new SendMessage(chatId.toString(),
                            "❌ Xatolik yuz berdi. Iltimos, qayta urinib ko‘ring.");
                    bot.execute(error);
                }
            }

            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(query.getId());
            answer.setShowAlert(false);
            bot.execute(answer);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void sendSelectedTemplate(SlideBot bot, UserEntity user, Long chatId, int templateNumber, String lang) {
        try {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(new File("C:\\Users\\User\\Desktop\\saqlash\\" + templateNumber + "_rasm.jpg")));

            String caption = switch (lang) {
                case "RU" -> "Вы выбрали шаблон №" + templateNumber;
                case "ENG" -> "You selected template #" + templateNumber;
                default -> "Siz " + templateNumber + "-shablonni tanladingiz";
            };
            sendPhoto.setCaption(caption);

            String createText = switch (lang) {
                case "RU" -> "Создать";
                case "ENG" -> "Create";
                default -> "Yaratish";
            };
            String backText = switch (lang) {
                case "RU" -> "Назад";
                case "ENG" -> "Back";
                default -> "Orqaga";
            };

            InlineKeyboardButton createButton = new InlineKeyboardButton(createText);
            createButton.setCallbackData("CREATE_SLIDE_" + templateNumber);

            InlineKeyboardButton backButton = new InlineKeyboardButton(backText);
            backButton.setCallbackData("BACK_TO_TEMPLATES");

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(List.of(List.of(createButton, backButton)));

            sendPhoto.setReplyMarkup(markup);
            Message sentMessage = bot.execute(sendPhoto);
            if (sentMessage != null) {
                user.setLastSelectedTemplateMessageId(sentMessage.getMessageId());
                userRepository.save(user);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendTemplateOptions(SlideBot bot, UserEntity user, Long chatId, String lang) {
        try {
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(chatId.toString());
            sendPhoto.setPhoto(new InputFile(new File("C:\\Users\\User\\Desktop\\saqlash\\shablon.jpg")));

            String caption = switch (lang) {
                case "RU" -> "📚 Выберите один из 10 шаблонов ниже:";
                case "ENG" -> "📚 Choose one of the 10 templates below:";
                default -> "📚 Quyidagi 10 ta shablondan birini tanlang:";
            };
            sendPhoto.setCaption(caption);

            List<List<InlineKeyboardButton>> rows = new ArrayList<>();
            for (int i = 1; i <= 10; i += 2) {
                InlineKeyboardButton b1 = new InlineKeyboardButton(" " + i);
                b1.setCallbackData("TEMPLATE_" + i);
                InlineKeyboardButton b2 = new InlineKeyboardButton(" " + (i + 1));
                b2.setCallbackData("TEMPLATE_" + (i + 1));
                rows.add(List.of(b1, b2));
            }

            InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
            markup.setKeyboard(rows);
            sendPhoto.setReplyMarkup(markup);

            Message sentMessage = bot.execute(sendPhoto);
            if (sentMessage != null) {
                user.setLastTemplateMessageId(sentMessage.getMessageId());
                userRepository.save(user);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendOverlayImage(SlideBot bot, Long chatId, String imagePath, String caption) {
        try {
            SendPhoto photo = new SendPhoto();
            photo.setChatId(chatId.toString());
            photo.setPhoto(new InputFile(new File(imagePath)));
            photo.setCaption(caption);
            bot.execute(photo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private SendMessage askSlideCount(Long chatId, String lang) {
        String text = switch (lang) {
            case "RU" -> "📊 Выберите количество слайдов:";
            case "ENG" -> "📊 Choose the number of slides:";
            default -> "📊 Slaydlar sonini tanlang:";
        };
        return menuService.getSlideCountKeyboardMessage(chatId, lang, text);
    }

    public String getText(String key, String lang) {
        return switch (key) {
            case "topic" -> switch (lang) {
                case "RU" -> "📝 Предоставьте тему в полной, безошибочной и понятной форме :";
                case "ENG" -> "📝 Submit the topic in a complete, error-free and understandable manner :";
                default -> "📝 Mavzuni to‘liq, bexato va tushunarli shaklda yuboring :";
            };
            case "user_info" -> switch (lang) {
                case "RU" -> "🧑‍🏫 При отправке статьи, пожалуйста, укажите полное имя автора и название темы :";
                case "ENG" -> "🧑‍🏫 For submissions, please enter the author's full name and subject name :";
                default -> "🧑‍🏫 Taqdimot uchun muallif ism-familiyasini va fan nomini to‘liq kiriting :";
            };
            case "invalid_command" -> switch (lang) {
                case "RU" -> "❗ Неверная команда. Пожалуйста, нажмите /start.";
                case "ENG" -> "❗ Invalid command. Please press /start.";
                default -> "❗ Noto‘g‘ri buyruq. Iltimos, /start ni bosing.";
            };
            default -> "";
        };
    }

    private void send(SlideBot bot, SendMessage msg) {
        try {
            bot.execute(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
