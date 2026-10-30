package uz.ai.slideshowbot.service;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import uz.ai.slideshowbot.slides.SlideDeck;
import uz.ai.slideshowbot.slides.SlideTheme;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.List;

/** Render the actual first PPTX slide for the WebApp theme selector. No network calls. */
public final class ThemePreviewGenerator {
    public static void main(String[] args) throws Exception {
        Path destination=Path.of(args.length==0?"docs/theme-previews":args[0]);
        Files.createDirectories(destination);
        PptxCreatorService renderer=new PptxCreatorService(null);
        try {
            for(SlideTheme theme:SlideTheme.ALL) {
                String title=theme.id().equals("1")?"Tarmoq xavfsizligi":"Taqdimot mavzusi";
                var cover=new SlideDeck.Slide(title,"",List.of(),SlideDeck.Layout.COVER,"",List.of());
                Path file=Path.of(renderer.createPptx(new SlideDeck(title,"UZ",List.of(cover)),"Azizbek Karimov",theme.id()));
                try(XMLSlideShow ppt=new XMLSlideShow(Files.newInputStream(file))) {
                    BufferedImage image=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics=image.createGraphics();
                    try {
                        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
                        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        graphics.scale(640d/960,360d/540);
                        ppt.getSlides().get(0).draw(graphics);
                    } finally { graphics.dispose(); }
                    ImageIO.write(image,"jpg",destination.resolve(theme.id()+".jpg").toFile());
                } finally { Files.deleteIfExists(file); }
            }
        } finally { renderer.shutdown(); }
        System.out.println("Rendered 24 actual cover previews to "+destination.toAbsolutePath());
    }
}
