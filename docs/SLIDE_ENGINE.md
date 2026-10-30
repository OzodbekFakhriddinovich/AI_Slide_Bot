# Slaydlar

Web app ochiq internet manbalaridan mavzuga mos faktlarni qidiradi. O‘zbekcha sahifalar yetarli bo‘lmasa, inglizcha yoki ruscha manbalar tekshiriladi va faqat topilgan faktlar tanlangan tilga tarjima qilinadi. Har bir sahifadagi faktlar uchun manbalar tahrirlanadigan PowerPoint faylining izohlarida saqlanadi. Mos dalil yoki to‘g‘ri tarjima topilmasa, boshqa mavzudagi matn bilan to‘ldirish o‘rniga buyurtma puli qaytariladi.

Tarjima Google tarjimaning API kalitisiz ishlaydigan ochiq veb xizmatidan foydalanadi. Bu xizmat norasmiy va vaqtincha ishlamasligi mumkin. Slaydlar boshqa tilda yoki manbada yo‘q faktlar bilan yuborilmaydi. Gemini Pro obunasi Gemini API kaliti yoki botdan foydalanish huquqini bermaydi. Slayd yaratish Gemini API’sini chaqirmaydi.

## Slayd soni va tariflar

| Asosiy mazmun sahifasi | Muqova, reja va xulosa | Jami sahifa | Narxi |
| ---: | ---: | ---: | ---: |
| 10 | 3 bonus | 13 | 5 000 so‘m |
| 15 | 3 bonus | 18 | 7 000 so‘m |
| 20 | 3 bonus | 23 | 7 000 so‘m |

Muqovada tanlangan shablon, mavzu va majburiy kiritilgan ism-familiya ko‘rsatiladi. Ikkinchi sahifada 10/15/20 tanloviga mos 4/5/6 ta reja bandi turadi. Keyingi asosiy sahifalar rejadagi birinchi banddan boshlanadi. Xulosa sahifasida qo‘shimcha faktlar beriladi va barcha manbalar izohlarda keltiriladi. Takroriy matn bilan sahifalar to‘ldirilmaydi.

Web app 24 ta shablonning haqiqiy muqova namunalarini ko‘rsatadi. Natija faqat tahrirlash mumkin bo‘lgan PPTX formatida tayyorlanadi. Rasmlar litsenziyasi va muallifi ko‘rsatilgan Wikimedia Commons’dan qidiriladi; mos rasm topilmasa matnga mo‘ljallangan chiroyli maket ishlatiladi. Cyber Navy fonlari siz bergan `Tarmoq_xavfsizligi_10_slayd.pptx` namunasiga moslangan.

## To‘lov va jarayon

Server faqat 5 000 yoki 7 000 so‘mni, 10/15/20 asosiy sahifani va PPTX formatini qabul qiladi. Pul buyurtma boshlanganda tranzaksiyada yechiladi. Har buyurtmaning o‘z raqami bor, shu sababli yuborish takrorlansa pul yana yechilmaydi. Xatoda mablag‘ bir marta qaytariladi. 30 daqiqa yangilanmagan tugallanmagan buyurtmalarning puli avtomatik qaytariladi.

Bot avval yechilgan summani, keyin kutish xabarini va uchinchi xabarda jarayon panelini yuboradi. Panel fayl tayyor bo‘lishidan oldin 100% ko‘rsatmaydi. Tayyor PPTX Telegramga yuborilgach jarayon xabarlari o‘chiriladi.

Balans yetmaganda xabar mavjud summa, narx va yetishmayotgan miqdorni ko‘rsatadi. Inline tugma mavjud avtomatik to‘lov jarayonini ochadi. Uchinchi tomon to‘lovi foydalanuvchi pul o‘tkazmaguncha amalga oshmaydi.

## Server sozlamalari

```properties
slides.web-app-url=https://example.org/slidebot/index.html
slides.ai.enabled=false
```

Botning maxfiy sozlamalari serverda qoladi. Yuqoridagi ikkita qiymat alohida `application` property faylida berilishi mumkin. Hibernate `ddl-auto=update` o‘chiq bo‘lsa, [jadval migratsiyasini](slide-engine-migration.sql) bajaring. Migratsiya yangi `slide_orders` jadvalini qo‘shadi; mavjud foydalanuvchi balanslarini o‘zgartirmaydi.

Manbalar: [MediaWiki API](https://www.mediawiki.org/wiki/API:REST_API), [MediaWiki Search](https://www.mediawiki.org/wiki/API:Search), [Wikimedia Commons](https://commons.wikimedia.org/wiki/Commons:API).
