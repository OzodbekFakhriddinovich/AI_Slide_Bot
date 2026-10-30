package uz.ai.slideshowbot.service;

import org.apache.poi.xslf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import uz.ai.slideshowbot.slides.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PptxCreatorServiceTest {
    @TempDir Path directory;

    @Test void imageEdgesAreRetainedWithoutStretching() throws Exception {
        var source=new java.awt.image.BufferedImage(800,400,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics=source.createGraphics();
        graphics.setColor(java.awt.Color.RED);graphics.fillRect(0,0,400,200);
        graphics.setColor(java.awt.Color.GREEN);graphics.fillRect(400,0,400,200);
        graphics.setColor(java.awt.Color.BLUE);graphics.fillRect(0,200,400,200);
        graphics.setColor(java.awt.Color.YELLOW);graphics.fillRect(400,200,400,200);graphics.dispose();
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(source,"png",bytes);
        var fitted=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(PptxCreatorService.fitToFrame(bytes.toByteArray(),400,400)));
        assertEquals(400,fitted.getWidth());assertEquals(400,fitted.getHeight());
        assertEquals(0,(fitted.getRGB(200,5)>>>24));
        assertEquals(java.awt.Color.RED.getRGB(),fitted.getRGB(5,105));
        assertEquals(java.awt.Color.GREEN.getRGB(),fitted.getRGB(395,105));
        assertEquals(java.awt.Color.BLUE.getRGB(),fitted.getRGB(5,295));
        assertEquals(java.awt.Color.YELLOW.getRGB(),fitted.getRGB(395,295));
    }

    @Test void preservesAllContentAndSourcesAcrossEveryLayoutWithoutOverflow() throws Exception {
        var source=new SlideDeck.Source("Network security", "https://en.wikipedia.org/wiki/Network_security");
        List<SlideDeck.Slide> slides=new ArrayList<>();
        int n=0;
        for(SlideDeck.Layout layout:SlideDeck.Layout.values()) {
            slides.add(new SlideDeck.Slide("A distinct heading for "+layout,"",
                    layout==SlideDeck.Layout.COVER?List.of():List.of(
                            "Access control: Different permissions limit the resources that each person can reach through the network.",
                            "Monitoring: Logs help a response team investigate unusual events and understand which systems need attention.",
                            "Recovery: Backup restoration exercises check whether important services can return to a working state."),
                    layout,"",List.of(source)));
            n++;
        }
        PptxCreatorService renderer=new PptxCreatorService(null);
        try {
            for(String theme:List.of("1","15")) {
                Path generated=Path.of(renderer.createPptx(new SlideDeck("Network security","ENG",slides),"Test author",theme));
                Path file=Files.move(generated,directory.resolve("layout-"+theme+".pptx"));
                try(XMLSlideShow ppt=new XMLSlideShow(Files.newInputStream(file))) {
                    assertEquals(SlideDeck.Layout.values().length,ppt.getSlides().size());
                    for(int i=0;i<ppt.getSlides().size();i++) {
                        XSLFSlide slide=ppt.getSlides().get(i);
                        String text=slide.getShapes().stream().filter(XSLFTextShape.class::isInstance)
                                .map(XSLFTextShape.class::cast).map(XSLFTextShape::getText).reduce("",(a,b)->a+"\n"+b);
                        assertTrue(text.replaceAll("\\s+"," ").contains(slides.get(i).title()));
                        for(String point:slides.get(i).bullets()) {
                            // Card headings and bodies can occupy separate editable text boxes.
                            assertTrue(text.replaceAll("\\s+","").contains(point.replace(":","").replaceAll("\\s+",""))
                                    || (text.contains(point.split(":",2)[0]) && text.contains(point.split(":",2)[1].strip())));
                        }
                        for(XSLFShape shape:slide.getShapes()) if(shape instanceof XSLFTextShape box && !box.getText().isBlank()) {
                            var bounds=box.getAnchor();
                            assertTrue(bounds.getX()>=0 && bounds.getY()>=0 && bounds.getMaxX()<=961 && bounds.getMaxY()<=541);
                            assertTrue(box.getTextHeight()<=bounds.getHeight()+2,
                                    "Overflow on "+theme+" slide "+i+": "+box.getText());
                        }
                        assertNotNull(slide.getNotes());
                        assertTrue(slide.getNotes().getShapes().stream().filter(XSLFTextShape.class::isInstance)
                                .map(XSLFTextShape.class::cast).map(XSLFTextShape::getText).anyMatch(t->t.contains(source.url())));
                    }
                }
            }
        } finally { renderer.shutdown(); }
    }

    @Test void everyThemeHasStableIdAndTopicSelectionIsDeterministic() {
        assertEquals(24,SlideTheme.ALL.size());
        assertEquals(24,SlideTheme.ALL.stream().map(SlideTheme::id).distinct().count());
        assertEquals("1",SlideTheme.resolve("auto","Tarmoq xavfsizligi").id());
        assertEquals("15",SlideTheme.resolve("auto","O‘zbekiston tarixi").id());
        assertEquals("16",SlideTheme.resolve("auto","Biologiya").id());
        assertEquals("19",SlideTheme.resolve("auto","Oziq-ovqat xavfsizligi").id());
        assertEquals("1",SlideTheme.resolve("auto","Kompyuter virusi").id());
        assertEquals(SlideTheme.resolve("auto","Fizika"),SlideTheme.resolve("auto","Fizika"));
    }

    @Test void bodyCountHasThreeWrapperSlidesAndAgendaRemainsLabelsOnly() throws Exception {
        String author="Azizbek Abdullayev Karimovich va Dilnoza Rustamova Abdurahmon qizi";
        var source=new SlideDeck.Source("Network security","https://en.wikipedia.org/wiki/Network_security");
        PptxCreatorService renderer=new PptxCreatorService(null);
        try {
            for(int bodyCount:List.of(10,15,20)) {
                int agendaCount=bodyCount==10?4:bodyCount==15?5:6;
                List<String> agenda=new ArrayList<>();
                for(int i=0;i<agendaCount;i++) agenda.add("Tarmoq xavfsizligining "+(i+1)+"-bo‘limi");
                List<SlideDeck.Slide> plan=new ArrayList<>();
                plan.add(new SlideDeck.Slide("Tarmoq xavfsizligi va tarmoq hujumlarining asosiy turlari","",List.of(),SlideDeck.Layout.COVER,"",List.of()));
                plan.add(new SlideDeck.Slide("Reja","Agenda descriptions must not be rendered",agenda,SlideDeck.Layout.AGENDA,"",List.of(source)));
                List<SlideDeck.Layout> bodyLayouts=List.of(SlideDeck.Layout.EDITORIAL,SlideDeck.Layout.CARDS,SlideDeck.Layout.SPLIT,SlideDeck.Layout.PROCESS,SlideDeck.Layout.COMPARISON,SlideDeck.Layout.TIMELINE);
                for(int i=0;i<bodyCount;i++) plan.add(new SlideDeck.Slide(i==0?agenda.get(0):"Tarmoqdagi hodisalarni aniqlash "+i,
                        agenda.get(Math.min(agendaCount-1,i*agendaCount/bodyCount)),List.of(
                        "Ruxsatlarni boshqarish foydalanuvchi vazifasiga qarab tarmoq resurslariga kirishni cheklaydi va keraksiz vakolatlarning to‘planishini kamaytiradi.",
                        "Tarmoq jurnallarini muntazam tahlil qilish noodatiy ulanishlarni aniqlashga, hodisa vaqtini tekshirishga va zararlangan tizimlarni ajratishga yordam beradi.",
                        "Zaxira nusxalarini tiklash sinovlari muhim xizmatlar ishlashini qayta yo‘lga qo‘yish imkoniyatini tekshiradi va amaldagi tiklash tartibidagi kamchiliklarni ko‘rsatadi."),
                        bodyLayouts.get(i%bodyLayouts.size()),"",List.of(source)));
                plan.add(new SlideDeck.Slide("Xulosa","",List.of("Tarmoqdagi nazorat vositalari hodisa qaydlarini saqlash va muhim resurslarga kirishni tekshirish orqali tekshiruv jarayonini qo‘llab-quvvatlaydi.",
                        "Tiklash sinovi zaxira nusxasi mavjudligini tasdiqlash bilan birga zarur xizmatlarni qayta ishga tushirish vaqtini baholash imkonini beradi."),SlideDeck.Layout.SUMMARY,"",List.of(source)));
                Path generated=Path.of(renderer.createPptx(new SlideDeck("Tarmoq xavfsizligi","UZ",plan),author,"1"));
                Path saved=Files.move(generated,directory.resolve("body-"+bodyCount+".pptx"));
                try(XMLSlideShow ppt=new XMLSlideShow(Files.newInputStream(saved))) {
                    assertEquals(bodyCount+3,ppt.getSlides().size());
                    String cover=slideText(ppt.getSlides().get(0)); assertTrue(cover.contains(author));
                    String agendaText=slideText(ppt.getSlides().get(1));
                    for(String label:agenda) assertTrue(agendaText.contains(label));
                    assertFalse(agendaText.contains("Agenda descriptions"));
                    assertFalse(agendaText.contains("wikipedia.org"));
                    assertTrue(slideText(ppt.getSlides().get(2)).contains(agenda.get(0)));
                    for(XSLFSlide slide:ppt.getSlides()) for(XSLFShape shape:slide.getShapes())
                        if(shape instanceof XSLFTextShape text && !text.getText().isBlank())
                            assertTrue(text.getTextHeight()<=text.getAnchor().getHeight()+2,"Overflow: "+text.getText());
                }
            }
        } finally { renderer.shutdown(); }
    }

    private static String slideText(XSLFSlide slide) {
        return slide.getShapes().stream().filter(XSLFTextShape.class::isInstance).map(XSLFTextShape.class::cast)
                .map(XSLFTextShape::getText).reduce("",(left,right)->left+"\n"+right);
    }
}
