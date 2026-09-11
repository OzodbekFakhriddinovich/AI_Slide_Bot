from telethon import TelegramClient, events
import re
import requests
import asyncio

# TELEGRAM API BILAN ULASH
# my.telegram.org saytidan olingan
API_ID = 30755844
API_HASH = 'fece645a8f104024a4edc4533265b564'

# SLIDEBOT SPRING BOOT API
SPRING_BOOT_URL = 'http://79.76.127.224:8089/api/payment/notify'
API_SECRET = 'SUPER_SECRET_HUMO_TOKEN_123'

client = TelegramClient('humo_session', API_ID, API_HASH)

@client.on(events.NewMessage(chats='HUMOcardbot'))
async def humo_handler(event):
    text = event.raw_text

    # Faqat "To'ldirish" yoki "Kirim" yoki "➕" bo'lgan xabarlarni olish
    if "To'ldirish" in text or 'Kirim' in text or '➕' in text:

        # Summani regex bilan ajratib olish.
        # HUMOcardbot xabari formati: ➕ 6.010,00 UZS
        match = re.search(r'(?:➕\s*)?([\d\.]+,\d{2})\s*UZS', text)

        if match:
            amount_str = match.group(1)  # Masalan: "6.010,00"
            # Formatlashni tozalaymiz: nuqtalarni olib, verguldan keyinini kesib tashlaymiz
            clean_amount = amount_str.split(',')[0].replace('.', '')

            try:
                amount = int(clean_amount)
                print(f"💰 Yangi to'lov aniqlandi: {amount} so'm")

                # SlideBot Spring Boot API ga xabar yuborish
                payload = {
                    "secret": API_SECRET,
                    "amount": amount
                }

                response = requests.post(SPRING_BOOT_URL, json=payload, timeout=10)
                if response.status_code == 200:
                    print(f"✅ SlideBot ga muvaffaqiyatli xabar berildi: {amount} so'm tasdiqlandi!")
                elif response.status_code == 404:
                    print(f"ℹ️ {amount} so'mlik kutilayotgan to'lov topilmadi (ehtimol muddati o'tgan)")
                else:
                    print(f"⚠️ Bot xatosi [{response.status_code}]: {response.text}")

            except ValueError:
                print("Summani songa aylantirishda xatolik:", clean_amount)
            except requests.exceptions.RequestException as e:
                print(f"❌ API ga ulanishda xatolik: {e}")

print("🚀 SlideBot Userbot ishga tushdi va @HUMOcardbot ni 24/7 poylamoqda...")
client.start()
client.run_until_disconnected()
