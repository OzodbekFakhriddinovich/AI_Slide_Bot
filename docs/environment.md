# Runtime configuration

Set credentials as environment variables on the host before starting the bot. Do not put live credentials in this repository.

Required for the Telegram bot and its database:

- `TELEGRAM_BOT_TOKEN` — bot token issued by BotFather.
- `DB_USERNAME` and `DB_PASSWORD` — PostgreSQL login for the configured `Slide_Bot` database.

Required for paid generation and payments:

- `GEMINI_API_KEYS` — one or more Google AI Studio API keys, separated by commas. The bot tries the configured keys in order. A Gemini web or Google AI subscription does not itself configure this server API credential.
- `PAYMENT_API_SECRET` and `PAYMENT_HUMO_CARD` — payment integration settings.
- `FAL_API_KEY` — image generation integration credential, if that feature is enabled.

The Gemini model can be overridden with `GEMINI_API_MODEL`; the default is `gemini-3.6-flash`.
