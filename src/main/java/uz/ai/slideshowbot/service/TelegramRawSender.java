package uz.ai.slideshowbot.service;

import okhttp3.*;
import org.springframework.stereotype.Service;
import uz.ai.slideshowbot.bot.SlideBot;

import java.util.concurrent.TimeUnit;


@Service
public class TelegramRawSender {

    private static final String API = "https://api.telegram.org/bot";

    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build();

    public void sendColoredBinaryMenu(SlideBot bot, String chatId, String text, String btn1, String style1, String btn2, String style2) {
        try {
            String markup = """
        {
          "keyboard": [
            [{"text": "%s", "style": "%s"}],
            [{"text": "%s", "style": "%s"}]
          ],
          "resize_keyboard": true
        }
        """.formatted(esc(btn1), style1, esc(btn2), style2);

            String json = """
        {
          "chat_id": "%s",
          "text": "%s",
          "parse_mode": "HTML",
          "reply_markup": %s
        }
        """.formatted(esc(chatId), esc(text), markup);

            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            post(bot, "sendMessage", body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendDocumentWithStyledBtn(SlideBot bot, String chatId, byte[] fileBytes, String fileName, String caption, String btnText, String btnUrl, String btnStyle) {
        try {
            String markup = buildUrlMarkup(btnText, btnUrl, btnStyle);

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart("caption", caption)
                    .addFormDataPart("parse_mode", "HTML")
                    .addFormDataPart("reply_markup", markup)
                    .addFormDataPart("document", fileName,
                            RequestBody.create(fileBytes, MediaType.get("application/zip")))
                    .build();

            post(bot, "sendDocument", requestBody);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void sendDocxFileWithShareBtn(SlideBot bot, String chatId, byte[] fileBytes,
                                         String fileName, String caption,
                                         String btnText, String btnUrl) {
        try {
            String markup = buildUrlMarkup(btnText, btnUrl, "success");

            RequestBody requestBody = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", chatId)
                    .addFormDataPart("caption", caption)
                    .addFormDataPart("parse_mode", "HTML")
                    .addFormDataPart("reply_markup", markup)
                    .addFormDataPart("document", fileName,
                            RequestBody.create(fileBytes, MediaType.get(
                                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")))
                    .build();

            post(bot, "sendDocument", requestBody);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendPptxFileWithShareBtn(SlideBot bot, String chatId, byte[] fileBytes,
                                         String fileName, String caption,
                                         String btnText, String btnUrl) throws Exception {
        String markup = buildUrlMarkup(btnText, btnUrl, "success");
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("chat_id", chatId)
                .addFormDataPart("caption", caption)
                .addFormDataPart("parse_mode", "HTML")
                .addFormDataPart("reply_markup", markup)
                .addFormDataPart("document", fileName,
                        RequestBody.create(fileBytes, MediaType.get("application/vnd.openxmlformats-officedocument.presentationml.presentation")))
                .build();
        post(bot, "sendDocument", requestBody);
    }

    public void sendMessageWithStyledBtn(SlideBot bot, String chatId, String text, String btnText, String btnUrl, String btnStyle) {
        try {
            String markup = buildUrlMarkup(btnText, btnUrl, btnStyle);
            sendJsonRequest(bot, chatId, text, markup);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendMessageWithStyledCallbackBtn(SlideBot bot, String chatId, String text, String btnText, String callbackData, String btnStyle) {
        try {
            String markup = buildCallbackMarkup(btnText, callbackData, btnStyle);
            sendJsonRequest(bot, chatId, text, markup);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void editMessageTextWithStyledCallbackBtn(SlideBot bot, String chatId, Integer messageId, String text, String btnText, String callbackData, String btnStyle) {
        try {
            String markup = buildCallbackMarkup(btnText, callbackData, btnStyle);
            String json = """
                {"chat_id":"%s","message_id":%d,"text":"%s","parse_mode":"HTML","reply_markup":%s}
                """.formatted(esc(chatId), messageId, esc(text), markup);
            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            String url = API + bot.getBotToken() + "/editMessageText";
            Request request = new Request.Builder().url(url).post(body).build();
            try (Response response = client.newCall(request).execute()) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void editMessageTextWithTwoStyledCallbackBtns(SlideBot bot, String chatId, Integer messageId, String text,
                                                        String btn1Text, String cb1, String style1,
                                                        String btn2Text, String cb2, String style2) {
        try {
            String markup = """
            {
              "inline_keyboard": [
                [{"text": "%s", "callback_data": "%s", "style": "%s"}],
                [{"text": "%s", "callback_data": "%s", "style": "%s"}]
              ]
            }
            """.formatted(esc(btn1Text), esc(cb1), style1, esc(btn2Text), esc(cb2), style2);
            String json = """
                {"chat_id":"%s","message_id":%d,"text":"%s","parse_mode":"HTML","reply_markup":%s}
                """.formatted(esc(chatId), messageId, esc(text), markup);
            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            String url = API + bot.getBotToken() + "/editMessageText";
            Request request = new Request.Builder().url(url).post(body).build();
            try (Response response = client.newCall(request).execute()) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendJoinRequestWithOneButton(SlideBot bot, String chatId, String text,
                                             String btnText, String btnUrl,
                                             String checkText, String checkCallback) {
        try {
            String markup = """
            {
              "inline_keyboard": [
                [{"text": "%s", "url": "%s", "style": "primary"}],
                [{"text": "%s", "callback_data": "%s", "style": "success"}]
              ]
            }
            """.formatted(
                    esc(btnText), esc(btnUrl),
                    esc(checkText), esc(checkCallback)
            );

            String json = """
            {
              "chat_id": "%s",
              "text": "%s",
              "parse_mode": "HTML",
              "reply_markup": %s
            }
            """.formatted(esc(chatId), esc(text), markup);

            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            post(bot, "sendMessage", body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendColoredMainMenu(SlideBot bot, String chatId, String text, String lang) {
        try {
            String markup = buildColoredMainMenuMarkup(lang);

            String json = """
                {
                  "chat_id": "%s",
                  "text": "%s",
                  "parse_mode": "HTML",
                  "reply_markup": %s
                }
                """.formatted(esc(chatId), esc(text), markup);

            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            post(bot, "sendMessage", body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String buildColoredMainMenuMarkup(String lang) {
        String pdf, zip, presentation, language, wordPdf, balance, test, referat, otherServices;

        switch (lang != null ? lang : "UZ") {
            case "RU" -> {
                pdf           = "📄 Создать PDF";
                zip           = "📦 Архивировать файлы (Zip)";
                presentation  = "🎞️ Создать презентацию";
                language      = "🌐 Язык";
                wordPdf       = "📄 Word <--> PDF";
                balance       = "💰 Баланс";
                test          = "📋 Создать тест";
                referat       = "📝 Создать реферат";
                otherServices = "🖥️ Другие услуги";
            }
            case "ENG" -> {
                pdf           = "📄 Create PDF";
                zip           = "📦 Zip Files";
                presentation  = "🎞️ Create Presentation";
                language      = "🌐 Language Settings";
                wordPdf       = "📄 Word <--> PDF";
                balance       = "💰 Balance";
                test          = "📋 Create Test";
                referat       = "📝 Create Essay";
                otherServices = "🖥️ Other Services";
            }
            default -> {
                pdf           = "📄 PDF yaratish";
                zip           = "📦 Fayllarni Zip qilish";
                presentation  = "🎞️ Taqdimot yaratish";
                language      = "🌐 Til Sozlamalari";
                wordPdf       = "📄 Word <--> PDF";
                balance       = "💰 Balans";
                test          = "📋 Test tayyorlash";
                referat       = "📝 Referat tayyorlash";
                otherServices = "🖥️ Boshqa Xizmatlar";
            }
        }

        return """
        {
          "keyboard": [
            [{"text": "%s", "style": "primary"}, {"text": "%s", "style": "primary"}],
            [{"text": "%s", "style": "success"}, {"text": "%s", "style": "success"}],
            [{"text": "%s", "style": "danger"},  {"text": "%s", "style": "danger"}],
            [{"text": "%s", "style": "primary"}, {"text": "%s", "style": "primary"}],
            [{"text": "%s", "style": "success"}]
          ],
          "resize_keyboard": true,
          "one_time_keyboard": false
        }
        """.formatted(
                esc(pdf),  esc(zip),
                esc(presentation), esc(language),
                esc(wordPdf), esc(balance),
                esc(test), esc(referat),
                esc(otherServices)
        ).strip();
    }

    private void sendJsonRequest(SlideBot bot, String chatId, String text, String markup) throws Exception {
        String json = """
                {
                  "chat_id": "%s",
                  "text": "%s",
                  "parse_mode": "HTML",
                  "reply_markup": %s
                }
                """.formatted(esc(chatId), esc(text), markup);
        RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
        post(bot, "sendMessage", body);
    }

    private String buildUrlMarkup(String btnText, String btnUrl, String btnStyle) {
        return """
                {"inline_keyboard":[[{"text":"%s","url":"%s","style":"%s"}]]}
                """.formatted(esc(btnText), btnUrl, btnStyle).strip();
    }

    private String buildCallbackMarkup(String btnText, String callbackData, String btnStyle) {
        return """
                {"inline_keyboard":[[{"text":"%s","callback_data":"%s","style":"%s"}]]}
                """.formatted(esc(btnText), callbackData, btnStyle).strip();
    }

    public void sendReplyWithColoredBtn(SlideBot bot, String chatId,
                                        Integer replyToMessageId,
                                        String text,
                                        String btnText, String btnStyle) {
        try {
            String markup = """
                    {"keyboard":[[{"text":"%s","style":"%s"}]],"resize_keyboard":true}
                    """.formatted(esc(btnText), btnStyle).strip();

            String json = """
                    {"chat_id":"%s","text":"%s","parse_mode":"HTML","reply_to_message_id":%d,"reply_markup":%s}
                    """.formatted(esc(chatId), esc(text), replyToMessageId, markup).strip();

            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            post(bot, "sendMessage", body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendWithColoredBtn(SlideBot bot, String chatId,
                                   String text,
                                   String btnText, String btnStyle) {
        try {
            String markup = """
                    {"keyboard":[[{"text":"%s","style":"%s"}]],"resize_keyboard":true}
                    """.formatted(esc(btnText), btnStyle).strip();

            String json = """
                    {"chat_id":"%s","text":"%s","parse_mode":"HTML","reply_markup":%s}
                    """.formatted(esc(chatId), esc(text), markup).strip();

            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            post(bot, "sendMessage", body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void post(SlideBot bot, String method, RequestBody body) throws Exception {
        String url = API + bot.getBotToken() + "/" + method;
        Request request = new Request.Builder().url(url).post(body).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("❌ Telegram API xato: " + response.code() + " - " + response.message());
            }
        }
    }

    public void sendColoredSingleMenu(SlideBot bot, String chatId, String text, String btnText, String btnStyle) {
        try {
            String markup = """
                {"keyboard":[[{"text":"%s","style":"%s"}]],"resize_keyboard":true}
                """.formatted(esc(btnText), btnStyle).strip();

            String json = """
                {"chat_id":"%s","text":"%s","parse_mode":"HTML","reply_markup":%s}
                """.formatted(esc(chatId), esc(text), markup).strip();

            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            post(bot, "sendMessage", body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    public void sendColoredLanguageMenu(SlideBot bot, String chatId, String text) {
        try {
            String json = """
        {
          "chat_id": "%s",
          "text": "%s",
          "reply_markup": {
            "keyboard": [
              [
                {"text": "🇺🇿 O'zbekcha", "style": "success"},
                {"text": "🇷🇺 Русский", "style": "danger"},
                {"text": "🇺🇸 English", "style": "primary"}
              ]
            ],
            "resize_keyboard": true,
            "one_time_keyboard": true
          }
        }
        """.formatted(esc(chatId), esc(text));

            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            post(bot, "sendMessage", body);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public Integer sendInlineMessage(SlideBot bot, String chatId, String text, String inlineMarkupJson) {
        try {
            String json = """
                {
                  "chat_id": "%s",
                  "text": "%s",
                  "parse_mode": "HTML",
                  "reply_markup": %s
                }
                """.formatted(esc(chatId), esc(text), inlineMarkupJson);

            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            String url = API + bot.getBotToken() + "/sendMessage";
            Request request = new Request.Builder().url(url).post(body).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    int idx = responseBody.indexOf("\"message_id\":");
                    if (idx != -1) {
                        String sub = responseBody.substring(idx + 13).trim();
                        StringBuilder numStr = new StringBuilder();
                        for (char c : sub.toCharArray()) {
                            if (Character.isDigit(c)) numStr.append(c);
                            else break;
                        }
                        if (!numStr.isEmpty()) return Integer.parseInt(numStr.toString());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Integer sendSimpleMessage(SlideBot bot, String chatId, String text) {
        try {
            String json = """
                {"chat_id":"%s","text":"%s","parse_mode":"HTML"}
                """.formatted(esc(chatId), esc(text));

            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            String url = API + bot.getBotToken() + "/sendMessage";
            Request request = new Request.Builder().url(url).post(body).build();
            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    int idx = responseBody.indexOf("\"message_id\":");
                    if (idx != -1) {
                        String sub = responseBody.substring(idx + 13).trim();
                        StringBuilder numStr = new StringBuilder();
                        for (char c : sub.toCharArray()) {
                            if (Character.isDigit(c)) numStr.append(c);
                            else break;
                        }
                        if (!numStr.isEmpty()) return Integer.parseInt(numStr.toString());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public void editMessageText(SlideBot bot, String chatId, Integer messageId, String newText) {
        try {
            String json = """
                {"chat_id":"%s","message_id":%d,"text":"%s","parse_mode":"HTML"}
                """.formatted(esc(chatId), messageId, esc(newText));

            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            String url = API + bot.getBotToken() + "/editMessageText";
            Request request = new Request.Builder().url(url).post(body).build();
            try (Response response = client.newCall(request).execute()) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void deleteMessageById(SlideBot bot, String chatId, Integer messageId) {
        try {
            String json = """
                {"chat_id":"%s","message_id":%d}
                """.formatted(esc(chatId), messageId);

            RequestBody body = RequestBody.create(json, MediaType.get("application/json"));
            String url = API + bot.getBotToken() + "/deleteMessage";
            Request request = new Request.Builder().url(url).post(body).build();
            try (Response response = client.newCall(request).execute()) {
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
