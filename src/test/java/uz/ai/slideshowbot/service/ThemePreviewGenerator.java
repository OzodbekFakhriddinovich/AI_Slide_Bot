package uz.ai.slideshowbot.service;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import uz.ai.slideshowbot.slides.SlideDeck;
import uz.ai.slideshowbot.slides.SlideTheme;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;
import java.util.Map;

/** Render the actual first PPTX slide for the WebApp theme selector. No network calls. */
public final class ThemePreviewGenerator {
    public static void main(String[] args) throws Exception {
        Path destination=Path.of(args.length==0?"docs/theme-previews":args[0]);
        PptxCreatorService renderer=new PptxCreatorService(null);
        Map<String,String[]> titles=Map.of(
            "uz",new String[]{"Tarmoq xavfsizligi","Okean oqimlari","Ekologiya va atrof-muhit","Raqamli madaniyat","Shaxsiy moliya","Ruhiy salomatlik","Zamonaviy arxitektura","Iqlim o‘zgarishi","Buyuk Ipak yo‘li","O‘rmon ekotizimlari","Quyosh tizimi","Dengiz biologiyasi","Jamoat salomatligi","Quyosh energiyasi","O‘zbekiston tarixi","Qayta tiklanuvchi energiya","Raqamli ta’lim","Kichik biznes moliyasi","Suvni tejash","O‘zbek adabiyoti","Sun’iy intellekt","Sanoat robotlari","Stressni boshqarish","Shahar transporti"},
            "ru",new String[]{"Безопасность сетей","Океанические течения","Экология и окружающая среда","Цифровая культура","Личные финансы","Психическое здоровье","Современная архитектура","Изменение климата","Великий шёлковый путь","Лесные экосистемы","Солнечная система","Морская биология","Общественное здоровье","Солнечная энергетика","История Узбекистана","Возобновляемая энергия","Цифровое обучение","Финансы малого бизнеса","Экономия воды","Литература Узбекистана","Искусственный интеллект","Промышленные роботы","Управление стрессом","Городской транспорт"},
            "eng",new String[]{"Network security","Ocean currents","Ecology and the environment","Digital culture","Personal finance","Mental health","Modern architecture","Climate change","The Silk Road","Forest ecosystems","The solar system","Marine biology","Public health","Solar energy","History of Uzbekistan","Renewable energy","Digital learning","Small business finance","Water conservation","Uzbek literature","Artificial intelligence","Industrial robotics","Stress management","Urban transport"});
        Map<String,String> authors=Map.of("uz","Azizbek Karimov","ru","Александр Иванов","eng","Alex Johnson");
        try {
            for(String language:titles.keySet()) {
                Path localized=destination.resolve(language);
                Files.createDirectories(localized);
                for(SlideTheme theme:SlideTheme.ALL) {
                    String title=titles.get(language)[Integer.parseInt(theme.id())-1];
                    title += switch(language) {
                        case "ru" -> " — влияние на общество, ключевые причины и практические решения";
                        case "eng" -> " and its impact, key challenges, and practical solutions";
                        default -> " va uning jamiyatga ta'siri, asosiy muammolar hamda amaliy yechimlar";
                    };
                    String code=language.equals("uz")?"UZ":language.equals("ru")?"RU":"ENG";
                    var cover=new SlideDeck.Slide(title,"",List.of(),SlideDeck.Layout.COVER,"",List.of());
                    Path file=Path.of(renderer.createPptx(new SlideDeck(title,code,List.of(cover)),authors.get(language),theme.id()));
                    try(XMLSlideShow ppt=new XMLSlideShow(Files.newInputStream(file))) {
                        BufferedImage image=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);
                        Graphics2D graphics=image.createGraphics();
                        try {
                            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                            graphics.scale(640d/960,360d/540);
                            ppt.getSlides().get(0).draw(graphics);
                        } finally { graphics.dispose(); }
                        ImageIO.write(image,"jpg",localized.resolve(theme.id()+".jpg").toFile());
                    } finally { Files.deleteIfExists(file); }
                }
            }
        } finally { renderer.shutdown(); }
        System.out.println("Rendered 72 localized cover previews to "+destination.toAbsolutePath());
    }
}
