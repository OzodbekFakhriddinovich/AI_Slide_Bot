package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import uz.ai.slideshowbot.bot.SlideBot;
import uz.ai.slideshowbot.entity.SlideOrderEntity;
import uz.ai.slideshowbot.entity.UserEntity;
import uz.ai.slideshowbot.slides.SlideDeck;
import uz.ai.slideshowbot.slides.SlideRequest;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/** The WebApp order pipeline. Work is bounded; balances never travel inside worker entities. */
@Service
public class SlideFlowSingleService {
    private final SlideOrderService orders;
    private final SlidePlanningService planner;
    private final PptxCreatorService pptx;
    private final ObjectMapper json = new ObjectMapper();
    private final ScheduledExecutorService progressClock = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "slide-progress-clock"); thread.setDaemon(true); return thread;
    });
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(8), task -> {
                Thread thread = new Thread(task, "slide-order-worker");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    public SlideFlowSingleService(SlideOrderService orders, SlidePlanningService planner,
                                  PptxCreatorService pptx, SlideExportService exporter) {
        this.orders = orders;
        this.planner = planner;
        this.pptx = pptx;
    }

    public void handleWebAppData(UserEntity user, Message message, SlideBot bot) {
        Long chatId = message.getChatId();
        String language = user.getLanguage() == null ? "UZ" : user.getLanguage();
        try {
            String raw = message.getWebAppData().getData();
            if (raw == null || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 4096)
                throw new IllegalArgumentException("Forma ma'lumoti noto'g'ri. Web appni qayta oching.");
            SlideRequest request = SlideRequest.from(json.readTree(raw), user.getFirstName(),
                    "legacy_" + chatId + "_" + message.getMessageId());
            if (workers.getQueue().remainingCapacity() == 0) {
                send(bot, chatId, localized(language, "Hozir buyurtmalar ko'p. Birozdan so'ng qayta urinib ko'ring.",
                        "Сейчас много заказов. Попробуйте немного позже.", "The queue is full. Please try again shortly."));
                return;
            }
            SlideOrderEntity order = orders.reserve(chatId, request);
            send(bot, chatId, localized(language,
                    "💰 Balansingizdan " + order.getCost() + " so'm yechildi.",
                    "💰 С баланса списано " + order.getCost() + " сум.",
                    "💰 " + order.getCost() + " UZS deducted from your balance."));
            Message waiting = send(bot, chatId, localized(language,
                    "⌛ Kuting! Taqdimot tayyorlanmoqda...",
                    "⌛ Подождите! Презентация создаётся...", "⌛ Please wait! Preparing your presentation..."));
            OrderProgress progress;
            try {
                progress = new OrderProgress(bot, chatId, language, waiting == null ? null : waiting.getMessageId());
            } catch (RejectedExecutionException e) {
                orders.refund(order.getId());
                if (waiting != null) removeProgress(bot, chatId, waiting.getMessageId());
                send(bot, chatId, "Bot yangilanmoqda. Mablag' to'liq qaytarildi. Birozdan so'ng qayta urinib ko'ring.");
                return;
            }
            try {
                workers.execute(() -> createAndSend(order, request, language, progress, bot));
            } catch (RejectedExecutionException e) {
                orders.refund(order.getId());
                progress.close();
                send(bot, chatId, "Navbat band. Mablag' qaytarildi. Birozdan so'ng qayta urinib ko'ring.");
            }
        } catch (SlideOrderService.InsufficientBalanceException e) {
            try { bot.execute(insufficientBalanceMessage(chatId, language, e.balance(), e.required())); }
            catch (Exception ignored) {}
        } catch (IllegalArgumentException e) {
            send(bot, chatId, e.getMessage());
        } catch (Exception e) {
            System.err.println("Slide order rejected: " + e.getClass().getSimpleName());
            send(bot, chatId, localized(language, "Buyurtmani qabul qilib bo'lmadi. Qayta urinib ko'ring.",
                    "Не удалось принять заказ. Попробуйте снова.", "Could not accept the order. Please try again."));
        }
    }

    private void createAndSend(SlideOrderEntity order, SlideRequest request, String language,
                               OrderProgress progress, SlideBot bot) {
        Long chatId = order.getChatId();
        List<Path> temporaryFiles = new ArrayList<>();
        boolean delivered = false;
        try {
            if (!orders.touch(order.getId())) return;
            SlideDeck deck = planner.createDeck(request.topic(), request.slideCount(), request.language(), request.tariff());
            validateDeck(deck, request.slideCount() + 3);
            if (deck.slides().get(0).layout() != SlideDeck.Layout.COVER
                    || deck.slides().get(1).layout() != SlideDeck.Layout.AGENDA)
                throw new IOException("Cover and agenda must precede the content");
            if (!orders.touch(order.getId())) return;
            progress.stage(localized(language,
                    "Manbalar topildi. Slaydlar va mavzuga mos rasmlar tayyorlanmoqda...",
                    "Материалы найдены. Готовим слайды и тематические изображения...",
                    "Sources found. Composing slides and finding relevant images..."));
            Path pptxFile = Path.of(pptx.createPptx(deck, request.name(), request.template()));
            temporaryFiles.add(pptxFile);
            requireFile(pptxFile);
            Path result = pptxFile;
            if (!orders.touch(order.getId())) return;
            progress.ready();
            SendDocument document = new SendDocument();
            document.setChatId(chatId.toString());
            document.setDocument(new InputFile(result.toFile()));
            document.setCaption(localized(language,
                    "📕 Sizning taqdimotingiz tayyor!\n\n📌 Mavzu: " + request.topic() + "\n" + request.slideCount() + " asosiy + 3 bonus = " + deck.slides().size() + " slayd\n\nTaqdimot tayyorlovchi bot: @AI_Slide_Assistant_Bot",
                    "Презентация готова!\n\n" + request.topic() + "\nСлайдов: " + deck.slides().size() + "\nИсточники текста и изображений указаны в файле.",
                    "Your presentation is ready!\n\n" + request.topic() + "\n" + deck.slides().size() + " slides\nText and image sources are included in the file."));
            bot.execute(document);
            delivered = true;
            orders.complete(order.getId());
        } catch (Exception e) {
            System.err.println("Slide creation failed (" + order.getId() + "): " + e.getClass().getSimpleName());
            if (!delivered) {
                boolean refunded = orders.refund(order.getId());
                if (refunded) send(bot, chatId, localized(language,
                        "Taqdimotni tayyorlashda manba yoki ulanish xatosi yuz berdi. " + order.getCost() + " so'm to'liq qaytarildi.\nBirozdan so'ng qayta urinib ko'ring.",
                        "Не удалось завершить презентацию из-за ошибки источника или соединения. Возвращено " + order.getCost() + " сум. Попробуйте ещё раз позже.",
                        "A source or connection error interrupted the presentation. " + order.getCost() + " UZS fully refunded. Please try again shortly."));
            } else {
                try { orders.complete(order.getId()); }
                catch (Exception persistenceError) {
                    System.err.println("Delivered slide receipt requires recovery: " + order.getId());
                }
            }
        } finally {
            progress.close();
            for (Path file : temporaryFiles) {
                try { Files.deleteIfExists(file); }
                catch (IOException ignored) { System.err.println("Could not remove temporary slide file."); }
            }
        }
    }

    static void validateDeck(SlideDeck deck, int expected) throws IOException {
        if (deck == null || deck.slides().size() != expected) throw new IOException("Slide count mismatch");
        Set<String> titles = new HashSet<>();
        Set<String> points = new HashSet<>();
        for (SlideDeck.Slide slide : deck.slides()) {
            if (slide.title().isBlank() || !titles.add(normalize(slide.title())))
                throw new IOException("Duplicate or missing slide title");
            if (slide.layout() != SlideDeck.Layout.COVER && slide.bullets().isEmpty())
                throw new IOException("Empty content slide");
            if (slide.layout() != SlideDeck.Layout.COVER && slide.layout() != SlideDeck.Layout.AGENDA && slide.sources().isEmpty())
                throw new IOException("Content slide without sources");
            if (slide.layout() == SlideDeck.Layout.AGENDA) continue;
            for (String point : slide.bullets()) {
                if (point == null || point.isBlank() || !points.add(normalize(point)))
                    throw new IOException("Duplicate or missing slide content");
            }
        }
    }

    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
    }

    private static void requireFile(Path file) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) == 0 || Files.size(file) > 49L * 1024 * 1024)
            throw new IOException("Missing, empty or oversized presentation");
    }

    static SendMessage insufficientBalanceMessage(Long chatId, String language, int balance, int required) {
        int missing = Math.max(0, required - balance);
        SendMessage message = new SendMessage(chatId.toString(), localized(language,
                "❌ Balansingizda mablag' yetarli emas.\n\n💰 Sizda: " + balance + " so'm\n📕 Taqdimot narxi: " + required + " so'm\n➕ Yetishmayapti: " + missing + " so'm\n\nBalansingizni to'ldiring va qayta buyurtma bering.",
                "❌ Недостаточно средств.\nБаланс: " + balance + " сум\nЦена: " + required + " сум\nНе хватает: " + missing + " сум",
                "❌ Insufficient balance.\nAvailable: " + balance + " UZS\nPrice: " + required + " UZS\nMissing: " + missing + " UZS"));
        InlineKeyboardButton button = InlineKeyboardButton.builder()
                .text(localized(language, "💳 Balansni to'ldirish", "💳 Пополнить баланс", "💳 Top up balance"))
                .callbackData("SLIDE_TOPUP_" + required).build();
        message.setReplyMarkup(new InlineKeyboardMarkup(List.of(List.of(button))));
        return message;
    }

    static String progressText(String language, long elapsedSeconds, boolean ready, String stage) {
        int percent = ready ? 100 : (int)Math.min(95, Math.max(0, elapsedSeconds) * 95 / 60);
        int filled = percent / 10;
        String bar = "▰".repeat(filled) + "▱".repeat(10 - filled);
        String timing = ready ? localized(language, "Tayyor! Yuborilmoqda...", "Готово! Отправляем...", "Ready! Sending...")
                : elapsedSeconds < 60 ? localized(language, "⏱ 1 daqiqalik tayyorlash: " + (60 - elapsedSeconds) + " soniya",
                    "⏱ Подготовка: " + (60 - elapsedSeconds) + " с", "⏱ Preparation: " + (60 - elapsedSeconds) + " seconds")
                : localized(language, "⏳ Yakuniy tayyorlash davom etmoqda...", "⏳ Завершаем подготовку...", "⏳ Finishing your presentation...");
        return "📊 " + bar + " " + percent + "%\n\n" + timing + "\n" + stage;
    }

    private final class OrderProgress implements AutoCloseable {
        private final SlideBot bot;
        private final Long chatId;
        private final String language;
        private final Integer messageId;
        private final Integer waitingId;
        private final long startedAt = System.nanoTime();
        private volatile String stage;
        private boolean closed;
        private final ScheduledFuture<?> timer;

        private OrderProgress(SlideBot bot, Long chatId, String language, Integer waitingId) {
            this.bot = bot; this.chatId = chatId; this.language = language; this.waitingId = waitingId;
            stage = localized(language, "Internetdan mavzuga mos ma'lumotlar qidirilmoqda...",
                    "Ищем материалы по теме...", "Finding relevant Internet sources...");
            Message message = send(bot, chatId, progressText(language, 0, false, stage));
            messageId = message == null ? null : message.getMessageId();
            timer = progressClock.scheduleAtFixedRate(this::tick, 5, 5, TimeUnit.SECONDS);
        }

        void stage(String text) { stage = text; }

        private synchronized void tick() {
            if (!closed) progress(bot, chatId, messageId,
                    progressText(language, TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - startedAt), false, stage));
        }

        void ready() throws InterruptedException {
            long remaining = TimeUnit.SECONDS.toNanos(60) - (System.nanoTime() - startedAt);
            if (remaining > 0) TimeUnit.NANOSECONDS.sleep(remaining);
            synchronized (this) {
                timer.cancel(false);
                if (!closed) progress(bot, chatId, messageId, progressText(language, 60, true, ""));
            }
        }

        @Override public synchronized void close() {
            closed = true; timer.cancel(false);
            removeProgress(bot, chatId, messageId);
            removeProgress(bot, chatId, waitingId);
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void recoverInterruptedOrders() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        for (SlideOrderEntity order : orders.abandoned(cutoff)) {
            try { orders.expire(order.getId(), cutoff); }
            catch (Exception e) { System.err.println("Slide refund recovery failed: " + order.getId()); }
        }
    }

    private void progress(SlideBot bot, Long chatId, Integer id, String text) {
        if (id == null) return;
        try {
            EditMessageText edit = new EditMessageText();
            edit.setChatId(chatId.toString()); edit.setMessageId(id); edit.setText(text);
            bot.execute(edit);
        } catch (Exception ignored) {}
    }

    private void removeProgress(SlideBot bot, Long chatId, Integer id) {
        if (id != null) try { bot.execute(new DeleteMessage(chatId.toString(), id)); } catch (Exception ignored) {}
    }

    private Message send(SlideBot bot, Long chatId, String text) {
        try { return bot.execute(new SendMessage(chatId.toString(), text)); }
        catch (Exception ignored) { return null; }
    }

    private static String localized(String language, String uz, String ru, String en) {
        return "RU".equals(language) ? ru : "ENG".equals(language) ? en : uz;
    }

    public void handleCallback(UserEntity user, CallbackQuery query, SlideBot bot) {
        try {
            AnswerCallbackQuery answer = new AnswerCallbackQuery();
            answer.setCallbackQueryId(query.getId());
            answer.setText("Taqdimot yaratish uchun menyudagi web appni oching.");
            bot.execute(answer);
        } catch (Exception ignored) {}
    }

    public void handleMessage(UserEntity user, Message message, SlideBot bot) {
        send(bot, message.getChatId(), "Taqdimot sozlamalarini menyudagi web app orqali kiriting.");
    }

    @PreDestroy public void shutdown() { progressClock.shutdownNow(); workers.shutdown(); }
}
