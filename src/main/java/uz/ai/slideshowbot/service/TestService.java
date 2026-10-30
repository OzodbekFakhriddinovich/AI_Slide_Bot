package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.FileEntity;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.enums.UserState;
import uz.ai.slideshowbot.repository.FileRepository;
import uz.ai.slideshowbot.repository.UserRepository;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * ✅ TestService — Test tayyorlash bo'limining to'liq logikasi.
 *
 * Gemini 2.5 Flash orqali ishlaydi (arzonroq va tezroq)
 */
@Service
public class TestService {

    private final ThreadPoolExecutor generationWorkers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(8), task -> {
                Thread thread = new Thread(task, "test-generation-worker");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    @PreDestroy public void shutdownGenerationWorkers() { generationWorkers.shutdown(); }

    @Autowired
    private GptTextGeneratorService gptTextGeneratorService;   // Gemini orqali test generatsiya

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FileRepository fileRepository;

    @Autowired
    private TelegramRawSender rawSender;

    @Autowired
    private MenuService menuService;

    private static final int TEST_PRICE = 5000;

    public void handleTestMenu(SlideBot bot, UserEntity user, Long chatId) {
        resetTestFlow(user);
        user.setInTestFlow(true);
        user.setState(UserState.WAITING_TEST_TOPIC);
        userRepository.save(user);

        String lang = lang(user);

        String text = switch (lang) {
            case "RU" -> """
                    🧪 <b>Напишите тему теста</b>
                    
                    Пример: <i>"Современные информационные технологии"</i>
                    
                    📊 Количество: 1-30 вопросов
                    💰 Цена: 5000 сум
                    📝 Тема: 10-100 символов""";
            case "ENG" -> """
                    🧪 <b>Write the test topic</b>
                    
                    Example: <i>"Modern information technologies"</i>
                    
                    📊 Count: 1-30 questions
                    💰 Price: 5000 UZS
                    📝 Topic: 10-100 characters""";
            default -> """
                    🧪 <b>Test mavzusini yozing</b>
                    
                    Namuna: <i>"Zamonaviy axborot texnologiyalari"</i>
                    
                    📊 Soni: 1-30 ta savol
                    💰 Narxi: 5000 so'm
                    📝 Mavzu: 10-100 belgi""";
        };

        String backBtn = backBtnText(lang);
        rawSender.sendWithColoredBtn(bot, chatId.toString(), text, backBtn, "danger");
    }

    public void handleTopicInput(SlideBot bot, UserEntity user, Long chatId, String topic) {
        String lang = lang(user);

        if (topic.length() < 3) {
            String errText = switch (lang) {
                case "RU" -> "❌ Тема слишком короткая! Минимум 3 символа.";
                case "ENG" -> "❌ Topic is too short! Minimum 3 characters.";
                default -> "❌ Mavzu juda qisqa! Kamida 3 ta belgi kiriting.";
            };
            rawSender.sendWithColoredBtn(bot, chatId.toString(), errText, backBtnText(lang), "danger");
            return;
        }

        user.setTestTopic(topic);
        userRepository.save(user);

        Integer countMsgId = sendCountInlineButtons(bot, user, chatId);
        user.setLastTestCountMsgId(countMsgId);
        userRepository.save(user);
    }

    private Integer sendCountInlineButtons(SlideBot bot, UserEntity user, Long chatId) {
        String lang = lang(user);

        String title = switch (lang) {
            case "RU" -> "❓ Сколько вопросов нужно?";
            case "ENG" -> "❓ How many questions do you need?";
            default -> "❓ Nechta savol kerak?";
        };

        String markup = """
            {
              "inline_keyboard": [
                [
                  {"text": "✅ 15", "callback_data": "TEST_COUNT_15"},
                  {"text": "✅ 20", "callback_data": "TEST_COUNT_20"}
                ]
              ]
            }""";

        return rawSender.sendInlineMessage(bot, chatId.toString(), title, markup);
    }

    public void handleCountCallback(SlideBot bot, UserEntity user, CallbackQuery query) {
        String data = query.getData();
        Long chatId = ((Message) query.getMessage()).getChatId();
        Integer callbackMsgId = query.getMessage().getMessageId();

        int count = switch (data) {
            case "TEST_COUNT_15" -> 15;
            case "TEST_COUNT_20" -> 20;
            default -> 20;
        };

        rawSender.deleteMessageById(bot, chatId.toString(), callbackMsgId);
        user.setLastTestCountMsgId(null);
        user.setTestQuestionCount(count);
        userRepository.save(user);

        Integer diffMsgId = sendDifficultyInlineButtons(bot, user, chatId);
        user.setLastTestDiffMsgId(diffMsgId);
        userRepository.save(user);
    }

    public void handleCustomCountInput(SlideBot bot, UserEntity user, Long chatId, String text) {
        String lang = lang(user);

        int count;
        try {
            count = Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            String err = switch (lang) {
                case "RU" -> "❌ Введите только число (например: 15)";
                case "ENG" -> "❌ Enter a number only (e.g.: 15)";
                default -> "❌ Faqat son kiriting (masalan: 15)";
            };
            rawSender.sendSimpleMessage(bot, chatId.toString(), err);
            return;
        }

        if (count < 1 || count > 20) {
            String err = switch (lang) {
                case "RU" -> "❌ Число должно быть от 1 до 20! Попробуйте снова.";
                case "ENG" -> "❌ Number must be between 1 and 20! Try again.";
                default -> "❌ Son 1 dan 20 gacha bo'lishi kerak! Qayta kiriting.";
            };
            rawSender.sendSimpleMessage(bot, chatId.toString(), err);
            return;
        }

        if (user.getLastTestInputMsgId() != null) {
            rawSender.deleteMessageById(bot, chatId.toString(), user.getLastTestInputMsgId());
            user.setLastTestInputMsgId(null);
        }

        user.setTestQuestionCount(count);
        user.setState(UserState.WAITING_TEST_TOPIC);
        userRepository.save(user);

        Integer diffMsgId = sendDifficultyInlineButtons(bot, user, chatId);
        user.setLastTestDiffMsgId(diffMsgId);
        userRepository.save(user);
    }

    private Integer sendDifficultyInlineButtons(SlideBot bot, UserEntity user, Long chatId) {
        String lang = lang(user);

        String title = switch (lang) {
            case "RU" -> "🎯 Выберите уровень сложности:";
            case "ENG" -> "🎯 Choose difficulty level:";
            default -> "🎯 Qiyinlik darajasini tanlang:";
        };

        String easy   = switch (lang) { case "RU" -> "😊 Лёгкий";    case "ENG" -> "😊 Easy";   default -> "😊 Oson"; };
        String medium = switch (lang) { case "RU" -> "🤔 Средний";   case "ENG" -> "🤔 Medium"; default -> "🤔 O'rta"; };
        String hard   = switch (lang) { case "RU" -> "🔥 Сложный";   case "ENG" -> "🔥 Hard";   default -> "🔥 Qiyin"; };
        String mixed  = switch (lang) { case "RU" -> "🎲 Смешанный"; case "ENG" -> "🎲 Mixed";  default -> "🎲 Aralash"; };
        String back   = switch (lang) { case "RU" -> "↩️ Назад";     case "ENG" -> "↩️ Back";   default -> "↩️ Orqaga"; };

        String markup = """
                {
                  "inline_keyboard": [
                    [
                      {"text": "%s", "callback_data": "TEST_DIFF_EASY"},
                      {"text": "%s", "callback_data": "TEST_DIFF_MEDIUM"}
                    ],
                    [
                      {"text": "%s", "callback_data": "TEST_DIFF_HARD"},
                      {"text": "%s", "callback_data": "TEST_DIFF_MIXED"}
                    ],
                    [
                      {"text": "%s", "callback_data": "TEST_DIFF_BACK"}
                    ]
                  ]
                }""".formatted(
                rawSender.esc(easy), rawSender.esc(medium),
                rawSender.esc(hard), rawSender.esc(mixed),
                rawSender.esc(back)
        );

        return rawSender.sendInlineMessage(bot, chatId.toString(), title, markup);
    }

    public void handleDifficultyCallback(SlideBot bot, UserEntity user, CallbackQuery query) {
        String data = query.getData();
        Long chatId = ((Message) query.getMessage()).getChatId();
        Integer callbackMsgId = query.getMessage().getMessageId();
        String lang = lang(user);

        if ("TEST_DIFF_BACK".equals(data)) {
            rawSender.deleteMessageById(bot, chatId.toString(), callbackMsgId);
            user.setLastTestDiffMsgId(null);

            Integer countMsgId = sendCountInlineButtons(bot, user, chatId);
            user.setLastTestCountMsgId(countMsgId);
            userRepository.save(user);
            return;
        }

        String difficulty = switch (data) {
            case "TEST_DIFF_EASY"   -> "EASY";
            case "TEST_DIFF_MEDIUM" -> "MEDIUM";
            case "TEST_DIFF_HARD"   -> "HARD";
            default                 -> "MIXED";
        };

        user.setTestDifficulty(difficulty);
        userRepository.save(user);

        rawSender.deleteMessageById(bot, chatId.toString(), callbackMsgId);
        user.setLastTestDiffMsgId(null);
        userRepository.save(user);

        String testLangLabel = switch (lang) {
            case "RU"  -> "🇷🇺 русском";
            case "ENG" -> "🇺🇸 English";
            default    -> "🇺🇿 o'zbek";
        };

        String confirmText = switch (lang) {
            case "RU"  -> "ℹ️ Данный тест будет подготовлен на <b>" + testLangLabel + "</b> языке.\n\nНажмите кнопку ниже для подтверждения:";
            case "ENG" -> "ℹ️ This test will be prepared in <b>" + testLangLabel + "</b>.\n\nPress the button below to confirm:";
            default    -> "ℹ️ Bu test <b>" + testLangLabel + "</b> tilida tayyorlanadi.\n\nTasdiqlash uchun quyidagi tugmani bosing:";
        };

        String confirmBtnLabel = switch (lang) {
            case "RU"  -> "✅ Подтвердить";
            case "ENG" -> "✅ Confirm";
            default    -> "✅ Tasdiqlash";
        };

        String markup = """
                {"inline_keyboard":[[{"text":"%s","callback_data":"TEST_CONFIRM","style":"success"}]]}
                """.formatted(rawSender.esc(confirmBtnLabel));

        Integer confirmMsgId = rawSender.sendInlineMessage(bot, chatId.toString(), confirmText, markup);
        user.setLastTestDiffMsgId(confirmMsgId);
        userRepository.save(user);
    }

    public void handleConfirmCallback(SlideBot bot, UserEntity user, CallbackQuery query) {
        Long chatId = ((Message) query.getMessage()).getChatId();
        Integer callbackMsgId = query.getMessage().getMessageId();
        String lang = lang(user);

        rawSender.deleteMessageById(bot, chatId.toString(), callbackMsgId);
        user.setLastTestDiffMsgId(null);
        userRepository.save(user);

        int balance = (user.getBalance() != null) ? user.getBalance() : 0;
        if (balance < TEST_PRICE) {
            // Flowni tozalaymiz
            resetTestFlow(user);

            String errText = switch (lang) {
                case "RU"  -> "❌ Недостаточно средств!\n\n💰 Ваш баланс: <b>" + balance + " сум</b>\n💳 Нужно: <b>5 000 сум</b>";
                case "ENG" -> "❌ Insufficient balance!\n\n💰 Your balance: <b>" + balance + " UZS</b>\n💳 Required: <b>5,000 UZS</b>";
                default    -> "❌ Balansingiz yetarli emas!\n\n💰 Balansingiz: <b>" + balance + " so'm</b>\n💳 Kerak: <b>5 000 so'm</b>";
            };
            String buyBtn = switch (lang) {
                case "RU" -> "💳 Пополнить баланс"; case "ENG" -> "💳 Top up balance"; default -> "💳 Balansni to'ldirish";
            };
            String backBtn = switch (lang) {
                case "RU" -> "◀️ Назад"; case "ENG" -> "◀️ Back"; default -> "◀️ Orqaga";
            };
            String markup = "{\"inline_keyboard\":[[{\"text\":\"%s\",\"callback_data\":\"BALANCE_PAY\",\"style\":\"success\"}]]}"
                    .formatted(rawSender.esc(buyBtn));
            rawSender.sendInlineMessage(bot, chatId.toString(), errText, markup);
            return;
        }

        user.setBalance(balance - TEST_PRICE);
        user.setInTestFlow(false);
        user.setState(UserState.START);
        userRepository.save(user);

        // Alohida "yechildi" xabari
        String deductText = switch (lang) {
            case "RU"  -> "💸 С вашего счёта списано <b>5 000 сум</b>.";
            case "ENG" -> "💸 <b>5,000 UZS</b> has been deducted from your balance.";
            default    -> "💸 Hisobingizdan <b>5 000 so'm</b> yechildi.";
        };
        rawSender.sendSimpleMessage(bot, chatId.toString(), deductText);

        // Progress bar — alohida xabar
        String progressLabel = switch (lang) {
            case "RU"  -> "⏳ Тест готовится...";
            case "ENG" -> "⏳ Creating test...";
            default    -> "⏳ Test tayyorlanmoqda...";
        };
        Integer progressMsgId = rawSender.sendSimpleMessage(bot, chatId.toString(),
                progressLabel + "\n" + buildProgressBar(lang, 0));

        final Integer finalProgressMsgId = progressMsgId;
        final UserEntity finalUser = user;
        try {
            generationWorkers.execute(() -> createAndSendTest(bot, finalUser, chatId, finalProgressMsgId));
        } catch (RejectedExecutionException e) {
            userRepository.findByChatId(chatId).ifPresent(saved -> {
                saved.setBalance((saved.getBalance() == null ? 0 : saved.getBalance()) + TEST_PRICE);
                userRepository.save(saved);
            });
            if (progressMsgId != null) rawSender.deleteMessageById(bot, chatId.toString(), progressMsgId);
            rawSender.sendSimpleMessage(bot, chatId.toString(), switch (lang) {
                case "RU" -> "Сейчас много запросов на создание тестов. Деньги возвращены на баланс, попробуйте позже.";
                case "ENG" -> "Test generation is busy right now. Your payment was refunded; please try again shortly.";
                default -> "Hozir test yaratish navbati band. Mablag' balansingizga qaytarildi, keyinroq urinib ko'ring.";
            });
        }
    }

    private void createAndSendTest(SlideBot bot, UserEntity user, Long chatId, Integer progressMsgId) {
        String lang = lang(user);
        String topic = user.getTestTopic();
        int count = (user.getTestQuestionCount() != null) ? user.getTestQuestionCount() : 10;
        String difficulty = (user.getTestDifficulty() != null) ? user.getTestDifficulty() : "MIXED";
        Thread progressThread = null;

        try {
            progressThread = startProgressUpdater(bot, chatId, progressMsgId, lang);

            String testContent = generateTestContent(topic, count, difficulty, lang);

            progressThread.interrupt();

            byte[] wordBytes = createWordDocument(topic, testContent, lang);

            FileEntity fileEntity = new FileEntity();
            fileEntity.setChatId(chatId);
            String safeFileName = "test_" + topic.replaceAll("[^a-zA-Z0-9\\u0400-\\u04FF]", "_")
                    .substring(0, Math.min(topic.length(), 25)) + ".docx";
            fileEntity.setFileName(safeFileName);
            fileEntity.setFileType("TEST");
            fileEntity.setData(wordBytes);
            fileRepository.save(fileEntity);

            if (progressMsgId != null) {
                rawSender.deleteMessageById(bot, chatId.toString(), progressMsgId);
            }

            String caption = switch (lang) {
                case "RU"  -> "✅ <b>Тест готов!</b>\n\n🚀 Тема: " + topic + "\n\n🔢 Количество: " + count + " вопросов\n\nТест подготовлен ботом: @AI_Slide_Assistant_Bot";
                case "ENG" -> "✅ <b>Test is ready!</b>\n\n🚀 Topic: " + topic + "\n\n🔢 Count: " + count + " questions\n\nTest prepared by bot: @AI_Slide_Assistant_Bot";
                default    -> "✅ <b>Test tayyor!</b>\n\n🚀 Mavzu: " + topic + "\n\n🔢 Soni: " + count + " ta\n\nTest tayyorlovchi bot: @AI_Slide_Assistant_Bot";
            };

            String shareUrl = "https://t.me/share/url?url=" +
                    java.net.URLEncoder.encode("https://t.me/AI_Slide_Assistant_Bot", java.nio.charset.StandardCharsets.UTF_8);
            String shareBtnText = switch (lang) {
                case "RU"  -> "📤 Поделиться";
                case "ENG" -> "📤 Share";
                default    -> "📤 Ulashish";
            };

            rawSender.sendDocxFileWithShareBtn(bot, chatId.toString(), wordBytes, safeFileName,
                    caption, shareBtnText, shareUrl);

            menuService.sendSimpleMenu(bot, chatId, lang);

        } catch (Exception e) {
            e.printStackTrace();
            if (progressThread != null) progressThread.interrupt();

            userRepository.findByChatId(chatId).ifPresent(u -> {
                u.setBalance((u.getBalance() != null ? u.getBalance() : 0) + TEST_PRICE);
                userRepository.save(u);
            });

            if (progressMsgId != null) {
                rawSender.deleteMessageById(bot, chatId.toString(), progressMsgId);
            }

            String errText = switch (lang) {
                case "RU"  -> "❌ Ошибка при создании теста. <b>5 000 сум</b> возвращены на баланс.";
                case "ENG" -> "❌ Error creating test. <b>5,000 UZS</b> returned to balance.";
                default    -> "❌ Test yaratishda xatolik. <b>5 000 so'm</b> qaytarildi.";
            };
            rawSender.sendSimpleMessage(bot, chatId.toString(), errText);
        }
    }

    private String generateTestContent(String topic, int count, String difficulty, String lang) throws Exception {
        String difficultyDesc = switch (difficulty) {
            case "EASY"   -> switch (lang) {
                case "RU" -> "лёгкий уровень (простые, базовые вопросы)";
                case "ENG" -> "easy level (simple, basic questions)";
                default -> "oson daraja (oddiy, asosiy savollar)";
            };
            case "MEDIUM" -> switch (lang) {
                case "RU" -> "средний уровень (умеренно сложные вопросы)";
                case "ENG" -> "medium level (moderately complex questions)";
                default -> "o'rta daraja (o'rtacha murakkab savollar)";
            };
            case "HARD"   -> switch (lang) {
                case "RU" -> "сложный уровень (трудные, углублённые вопросы)";
                case "ENG" -> "hard level (difficult, in-depth questions)";
                default -> "qiyin daraja (murakkab, chuqur savollar)";
            };
            default -> switch (lang) {
                case "RU" -> "смешанный уровень (разная сложность)";
                case "ENG" -> "mixed level (varying difficulty)";
                default -> "aralash daraja (turli qiyinlikda)";
            };
        };

        String langInstruction = switch (lang) {
            case "RU"  -> "на русском языке";
            case "ENG" -> "in English";
            default    -> "o'zbek tilida";
        };

        String topicLabel = switch (lang) {
            case "RU"  -> "Тема";
            case "ENG" -> "Topic";
            default    -> "Mavzu";
        };

        String difficultyLabel = switch (lang) {
            case "RU" -> "Сложность";
            case "ENG" -> "Difficulty";
            default -> "Qiyinlik";
        };
        String languageLabel = switch (lang) {
            case "RU" -> "Язык";
            case "ENG" -> "Language";
            default -> "Til";
        };

        String correctLabel = switch (lang) {
            case "RU"  -> "ПРАВИЛЬНЫЕ ОТВЕТЫ";
            case "ENG" -> "CORRECT ANSWERS";
            default    -> "TO'G'RI JAVOBLAR";
        };

        String prompt = switch (lang) {
            case "RU" -> """
                Вы — генератор тестов. Создайте ровно %d вопросов по указанной теме.

                %s: %s
                %s: %s
                %s: %s

                ПРАВИЛА ФОРМАТА:
                - Для каждого вопроса укажите четыре варианта (A, B, C, D).
                - Правильный ответ должен быть только один.
                - Разделяйте вопросы пустой строкой.
                - Не добавляйте вступление, пояснения или заключение.

                ФОРМАТ:
                1. Текст вопроса?
                A) вариант
                B) вариант
                C) вариант
                D) вариант

                ...все %d вопросов...

                --- %s ---
                1. A
                2. B
                ...до %d

                Верните только тест в указанном формате.
                """.formatted(count, topicLabel, topic, difficultyLabel, difficultyDesc,
                    languageLabel, langInstruction, count, correctLabel, count);
            case "ENG" -> """
                You are a test generator. Create exactly %d questions on the requested topic.

                %s: %s
                %s: %s
                %s: %s

                FORMAT RULES:
                - Give four options (A, B, C, D) for every question.
                - There must be exactly one correct answer.
                - Separate questions with a blank line.
                - Do not add an introduction, explanations, or a conclusion.

                FORMAT:
                1. Question text?
                A) option
                B) option
                C) option
                D) option

                ...all %d questions...

                --- %s ---
                1. A
                2. B
                ...up to %d

                Return only the test in the requested format.
                """.formatted(count, topicLabel, topic, difficultyLabel, difficultyDesc,
                    languageLabel, langInstruction, count, correctLabel, count);
            default -> """
                Siz test generatori siz. Quyidagi talabga ko'ra aniq %d ta test savoli yarating.

                %s: %s
                %s: %s
                %s: %s

                MUHIM FORMAT QOIDALARI (hech qachon o'zgartirmang):
                - Har bir savol uchun 4 ta variant (A, B, C, D)
                - To'g'ri javob faqat bitta bo'lishi shart
                - Savollar bo'sh qator bilan ajratilsin
                - Hech qanday izoh, kirish yoki xulosa yozmang — faqat savollar va javoblar

                ANIQ FORMAT:
                1. Savol matni?
                A) variant
                B) variant
                C) variant
                D) variant

                2. Savol matni?
                A) variant
                B) variant
                C) variant
                D) variant

                ...barcha %d savol...

                --- %s ---
                1. A
                2. B
                ...%d tagacha

                Faqat yuqoridagi formatda javob bering, boshqa hech narsa yozmang.
                """.formatted(count, topicLabel, topic, difficultyLabel, difficultyDesc,
                    languageLabel, langInstruction, count, correctLabel, count);
        };

        String responseJson = gptTextGeneratorService.callGemini(prompt, false);

        String rawText = gptTextGeneratorService.extractGeneratedText(responseJson);
        validateTestFormat(rawText, count);
        return rawText.trim();
    }

    private void validateTestFormat(String text, int expectedCount) {
        java.util.regex.Matcher marker = java.util.regex.Pattern.compile(
                "(?im)^\\s*-{2,}\\s*(?:TO['’`]?G['’`]?RI\\s+JAVOBLAR|CORRECT\\s+ANSWERS|ПРАВИЛЬНЫЕ\\s+ОТВЕТЫ)\\s*-{2,}\\s*$")
                .matcher(text);
        if (!marker.find()) throw new IllegalStateException("Generated test is missing its answer key.");
        String questions = text.substring(0, marker.start());
        String answers = text.substring(marker.end());
        long questionCount = java.util.regex.Pattern.compile("(?m)^\\s*\\d+[.)]\\s+\\S.*$")
                .matcher(questions).results().count();
        long optionCount = java.util.regex.Pattern.compile("(?im)^\\s*[A-DА-Г][).]\\s*\\S.*$")
                .matcher(questions).results().count();
        long answerCount = java.util.regex.Pattern.compile("(?im)^\\s*\\d+[.)]\\s*[A-DА-Г]\\s*$")
                .matcher(answers).results().count();
        if (questionCount != expectedCount || optionCount != expectedCount * 4L || answerCount != expectedCount)
            throw new IllegalStateException("Generated test is incomplete or has an invalid answer key.");
    }

    private byte[] createWordDocument(String topic, String testContent, String lang) throws Exception {
        XWPFDocument doc = new XWPFDocument();

        CTSectPr sectPr = doc.getDocument().getBody().addNewSectPr();

        CTPageSz pageSize = sectPr.addNewPgSz();
        pageSize.setW(BigInteger.valueOf(12240));
        pageSize.setH(BigInteger.valueOf(15840));

        CTPageMar margins = sectPr.addNewPgMar();
        margins.setTop(BigInteger.valueOf(1440));
        margins.setBottom(BigInteger.valueOf(1440));
        margins.setLeft(BigInteger.valueOf(1800));
        margins.setRight(BigInteger.valueOf(1440));

        CTPageBorders pageBorders = sectPr.addNewPgBorders();
        pageBorders.setOffsetFrom(STPageBorderOffset.PAGE);

        CTBorder topBorder    = pageBorders.addNewTop();
        CTBorder bottomBorder = pageBorders.addNewBottom();
        CTBorder leftBorder   = pageBorders.addNewLeft();
        CTBorder rightBorder  = pageBorders.addNewRight();

        for (CTBorder border : new CTBorder[]{topBorder, bottomBorder, leftBorder, rightBorder}) {
            border.setVal(STBorder.SINGLE);
            border.setSz(BigInteger.valueOf(12));
            border.setSpace(BigInteger.valueOf(24));
            border.setColor("000000");
        }

        String titleLabel = switch (lang) {
            case "RU"  -> "Тема: ";
            case "ENG" -> "Topic: ";
            default    -> "Mavzu: ";
        };

        XWPFParagraph titlePar = doc.createParagraph();
        titlePar.setAlignment(ParagraphAlignment.CENTER);
        setSpacing(titlePar, 0, 160);

        XWPFRun titleRun = titlePar.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(14);
        titleRun.setColor("000000");
        titleRun.setFontFamily("Times New Roman");
        titleRun.setText(titleLabel + topic);

        addEmptyParagraph(doc);

        String[] lines = testContent.split("\n");
        for (String line : lines) {
            XWPFParagraph para = doc.createParagraph();
            para.setAlignment(ParagraphAlignment.LEFT);
            setSpacing(para, 0, 120);

            XWPFRun run = para.createRun();
            run.setFontSize(11);
            run.setFontFamily("Times New Roman");
            run.setColor("000000");

            if (line.trim().startsWith("---") &&
                    (line.contains("JAVOB") || line.contains("ANSWER") || line.contains("ОТВЕТ"))) {

                XWPFParagraph breakPara = doc.createParagraph();
                breakPara.createRun().addBreak(BreakType.PAGE);

                XWPFParagraph answerPar = doc.createParagraph();
                answerPar.setAlignment(ParagraphAlignment.LEFT);
                setSpacing(answerPar, 0, 120);
                XWPFRun answerRun = answerPar.createRun();
                answerRun.setFontSize(11);
                answerRun.setFontFamily("Times New Roman");
                answerRun.setColor("000000");
                answerRun.setText(line);
                continue;
            }

            run.setText(line.isEmpty() ? "" : line);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        doc.write(baos);
        doc.close();
        return baos.toByteArray();
    }

    private void setSpacing(XWPFParagraph par, int before, int after) {
        if (par.getCTP().getPPr() == null) par.getCTP().addNewPPr();
        if (par.getCTP().getPPr().getSpacing() == null) par.getCTP().getPPr().addNewSpacing();
        par.getCTP().getPPr().getSpacing().setBefore(BigInteger.valueOf(before));
        par.getCTP().getPPr().getSpacing().setAfter(BigInteger.valueOf(after));
    }

    private void addEmptyParagraph(XWPFDocument doc) {
        XWPFParagraph empty = doc.createParagraph();
        empty.createRun().setText("");
    }

    private Thread startProgressUpdater(SlideBot bot, Long chatId, Integer msgId, String lang) {
        if (msgId == null) return new Thread(() -> {});

        Thread t = new Thread(() -> {
            final int TOTAL_SECONDS = 60;
            final int STEPS = 30;
            final int DELAY_MS = TOTAL_SECONDS * 1000 / STEPS;

            String label = switch (lang) {
                case "RU"  -> "⏳ Тест готовится...";
                case "ENG" -> "⏳ Creating test...";
                default    -> "⏳ Test tayyorlanmoqda...";
            };

            int step = 0;
            while (!Thread.currentThread().isInterrupted()) {
                step++;
                int filled = Math.min(19, step * 20 / STEPS);
                int percent = Math.min(95, step * 100 / STEPS);
                String bar = "█".repeat(filled) + "░".repeat(20 - filled);

                try {
                    rawSender.editMessageText(bot, chatId.toString(), msgId,
                            label + "\n" + bar + " " + percent + "%");
                    Thread.sleep(DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception ignored) {}
            }

        });

        t.setDaemon(true);
        t.start();
        return t;
    }

    private String buildProgressBar(String lang, int pct) {
        int filled = pct / 10;
        String bar = "█".repeat(filled) + "░".repeat(10 - filled);
        return bar + " " + pct + "%";
    }

    private String lang(UserEntity user) {
        return (user.getLanguage() != null) ? user.getLanguage() : "UZ";
    }

    private String backBtnText(String lang) {
        return switch (lang) {
            case "RU"  -> "◀️ Назад";
            case "ENG" -> "◀️ Back";
            default    -> "◀️ Orqaga";
        };
    }

    public void resetTestFlow(UserEntity user) {
        if (user == null) return;
        user.setInTestFlow(false);
        user.setState(UserState.START);
        user.setTestTopic(null);
        user.setTestQuestionCount(null);
        user.setTestDifficulty(null);
        user.setLastTestCountMsgId(null);
        user.setLastTestInputMsgId(null);
        user.setLastTestDiffMsgId(null);

        userRepository.save(user);
    }
}
