package uz.ai.slideshowbot.service;

import jakarta.annotation.PreDestroy;
import org.apache.poi.sl.usermodel.PictureData;
import org.apache.poi.sl.usermodel.ShapeType;
import org.apache.poi.sl.usermodel.TextParagraph.TextAlign;
import org.apache.poi.sl.usermodel.TextShape.TextAutofit;
import org.apache.poi.sl.usermodel.VerticalAlignment;
import org.apache.poi.xslf.usermodel.*;
import org.springframework.stereotype.Service;
import uz.ai.slideshowbot.slides.SlideDeck;
import uz.ai.slideshowbot.slides.SlideDeck.Layout;
import uz.ai.slideshowbot.slides.SlideDeck.Slide;
import uz.ai.slideshowbot.slides.SlideDeck.Source;
import uz.ai.slideshowbot.slides.SlideTheme;
import uz.ai.slideshowbot.slides.SlideLanguage;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.text.AttributedString;
import java.util.List;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Native, editable, source-aware slides. A plan item always produces one slide. */
@Service
public class PptxCreatorService {
    private static final int W = 960, H = 540, M = 48, FOOTER_Y = 500;
    private static final String FONT = "Arial";
    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);
    private final ImageSearchService imageSearch;
    private final ExecutorService imageWorkers;

    public PptxCreatorService(ImageSearchService imageSearch) {
        this.imageSearch = imageSearch;
        AtomicInteger sequence = new AtomicInteger();
        imageWorkers = new ThreadPoolExecutor(5, 5, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(48), task -> {
                    Thread worker = new Thread(task, "slide-images-" + sequence.incrementAndGet());
                    worker.setDaemon(true);
                    return worker;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public String createPptx(SlideDeck deck, String userName, String themeId) throws IOException {
        Objects.requireNonNull(deck, "Presentation plan is required");
        if (deck.slides().isEmpty()) throw new IOException("Presentation plan has no slides");
        if (userName == null || userName.isBlank()) throw new IOException("Presentation author is required");
        SlideTheme theme = SlideTheme.resolve(themeId, deck.title());
        Map<Integer, ImageSearchService.ImageAsset> images = fetchImages(deck,theme);
        Path directory = Path.of("slides");
        Files.createDirectories(directory);
        String safe = deck.title().replaceAll("[^\\p{L}\\p{N} -]", "_").strip();
        if (safe.length() > 60) safe = safe.substring(0, 60);
        if (safe.isBlank()) safe = "Presentation";
        Path output = directory.resolve(safe + "_" + UUID.randomUUID() + ".pptx");
        try (XMLSlideShow ppt = new XMLSlideShow()) {
            ppt.setPageSize(new Dimension(W, H));
            ppt.getProperties().getCoreProperties().setTitle(deck.title());
            ppt.getProperties().getCoreProperties().setCreator("SlideBot");
            ppt.getProperties().getCoreProperties().setSubjectProperty(theme.name());
            for (int index = 0; index < deck.slides().size(); index++) {
                Slide content = deck.slides().get(index);
                XSLFSlide slide = ppt.createSlide();
                slide.getBackground().setFillColor(theme.background());
                boolean referenceDesign = referenceBackground(ppt, slide, theme, deck.title(), content.layout() == Layout.COVER);
                if (!referenceDesign) background(slide, theme, index, content.layout() == Layout.COVER);
                ImageSearchService.ImageAsset image = images.get(index);
                if(referenceDesign && content.layout()==Layout.COVER) image=null;
                if (content.layout() == Layout.COVER) {
                    cover(ppt, slide, content, userName, theme, deck.language(), referenceDesign, image);
                } else {
                    int bodyY = header(slide, content, index, theme);
                    boolean denseFacts=content.layout()!=Layout.AGENDA && content.layout()!=Layout.SOURCES
                            && content.bullets().size()>=3 && (content.bullets().stream().mapToInt(String::length).sum()>760
                            || content.bullets().stream().anyMatch(fact->fact.length()>260));
                    if(image!=null && content.layout()!=Layout.AGENDA && content.layout()!=Layout.SOURCES)
                        split(ppt,slide,content.bullets(),bodyY,theme,image,index);
                    else if(denseFacts) factRows(slide,content.bullets(),bodyY,theme);
                    else
                    switch (content.layout()) {
                        case AGENDA -> agenda(slide, content.bullets(), bodyY, theme);
                        case CARDS -> cards(slide, content.bullets(), bodyY, theme);
                        case SPLIT -> {
                            if (image != null) split(ppt, slide, content.bullets(), bodyY, theme, image, index);
                            else splitText(slide, content.bullets(), bodyY, theme);
                        }
                        case PROCESS -> process(slide, content.bullets(), bodyY, theme, false);
                        case TIMELINE -> process(slide, content.bullets(), bodyY, theme, true);
                        case COMPARISON -> comparison(slide, content.bullets(), bodyY, theme);
                        case SUMMARY -> editorial(slide, content.bullets(), bodyY, theme, true);
                        case SOURCES -> sources(slide, content, bodyY, theme);
                        default -> {
                            if (image != null) split(ppt, slide, content.bullets(), bodyY, theme, image, index);
                            else editorial(slide, content.bullets(), bodyY, theme, false);
                        }
                    }
                }
                footer(slide, content, index, deck.slides().size(), theme, image);
                addNotes(ppt, slide, content, image, referenceDesign, deck.language());
            }
            try (OutputStream stream = Files.newOutputStream(output)) { ppt.write(stream); }
        } catch (Exception failure) {
            Files.deleteIfExists(output);
            if (failure instanceof IOException io) throw io;
            throw new IOException("Presentation rendering failed", failure);
        }
        return output.toString();
    }

    /** One sequential image pass per deck prevents duplicate URL races across slides. */
    private Map<Integer, ImageSearchService.ImageAsset> fetchImages(SlideDeck deck,SlideTheme theme) {
        Map<Integer, ImageSearchService.ImageAsset> found = new ConcurrentHashMap<>();
        if (imageSearch == null) return found;
        if (deck.slides().stream().noneMatch(s -> wantsImage(s) && !s.imageQuery().isBlank())) return found;
        Future<?> job;
        try {
            job = imageWorkers.submit(() -> {
                Set<String> excludedUrls = ConcurrentHashMap.newKeySet(), hashes = ConcurrentHashMap.newKeySet();
                int maximum = (int) deck.slides().stream().filter(s -> s.layout()!=Layout.AGENDA && s.layout()!=Layout.SOURCES).count();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(75);
                List<Future<?>> requests=new ArrayList<>();
                for (int i = 0; i < deck.slides().size() && found.size() < maximum; i++) {
                    if (Thread.currentThread().isInterrupted() || System.nanoTime() >= deadline) break;
                    Slide slide = deck.slides().get(i);
                    if(slide.layout()==Layout.COVER && "1".equals(theme.id())
                            && deck.title().toLowerCase(Locale.ROOT).matches(".*(xavfsiz|security|cyber|безопас|кибер).*")
                            && getClass().getResource("/slides/cyber-cover.jpg")!=null) continue;
                    if (!wantsImage(slide) || slide.imageQuery().isBlank()) continue;
                    final int index=i; final Slide candidate=slide;
                    requests.add(imageWorkers.submit(() -> {
                        try {
                            ImageSearchService.ImageAsset asset=imageSearch.findImage(candidate.imageQuery(),deck.title(),excludedUrls);
                            if(asset==null || asset.data()==null || asset.data().length==0 || asset.url()==null || asset.url().isBlank()) return;
                            excludedUrls.add(asset.url());
                            String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(asset.data()));
                            if(!hashes.add(hash)) return;
                            try(ByteArrayInputStream input=new ByteArrayInputStream(asset.data())) {
                                BufferedImage decoded=ImageIO.read(input);
                                if(decoded==null || decoded.getWidth()<160 || decoded.getHeight()<100) return;
                            }
                            found.put(index,asset);
                        } catch(Exception ignored) {}
                    }));
                }
                for(Future<?> request:requests) {
                    long remaining=deadline-System.nanoTime();
                    if(remaining<=0) { request.cancel(true); continue; }
                    try { request.get(remaining,TimeUnit.NANOSECONDS); }
                    catch(Exception unavailable) { request.cancel(true); }
                }
            });
        } catch (RejectedExecutionException busy) { return found; }
        try { job.get(78, TimeUnit.SECONDS); }
        catch (InterruptedException interrupted) { job.cancel(true); Thread.currentThread().interrupt(); }
        catch (Exception unavailable) { job.cancel(true); }
        return new HashMap<>(found);
    }

    private static boolean wantsImage(Slide slide) {
        return slide.layout() != Layout.AGENDA && slide.layout() != Layout.SOURCES;
    }

    private void cover(XMLSlideShow ppt, XSLFSlide slide, Slide content, String userName, SlideTheme theme,
                       String language, boolean referenceDesign, ImageSearchService.ImageAsset image) throws IOException {
        String label = switch (language == null ? "UZ" : language) {
            case "RU" -> "ПРЕЗЕНТАЦИЯ";
            case "ENG" -> "PRESENTATION";
            case "ES" -> "PRESENTACIÓN";
            case "DE" -> "PRÄSENTATION";
            case "TOJ" -> "МУАРРИФӢ";
            case "TUR" -> "SUNUM";
            default -> "TAQDIMOT";
        };
        label(slide, label, M, 48, 700, theme.accent());
        rect(slide, M, 84, 72, 4, theme.accent());
        int textWidth=referenceDesign || image!=null?510:535;
        if(!referenceDesign) {
            frame(slide,26,24,W-52,455,tint(theme,.20),.8);
            rect(slide,W-M-7,52,7,59,theme.accent());
            if (image == null) scene(slide,theme,625,137,280,250);
        }
        int lastSpace=content.title().lastIndexOf(' ');
        if(content.title().length()<=46 && lastSpace>2) {
            text(slide,content.title().substring(0,lastSpace),M,123,textWidth,85,58,32,true,theme.text());
            text(slide,content.title().substring(lastSpace+1),M,211,textWidth,87,58,32,true,theme.accent());
        } else text(slide,content.title(),M,119,textWidth,226,54,25,true,theme.text());
        String authorLabel=switch(language==null?"UZ":language) {
            case "RU"->"ПОДГОТОВИЛ(А)";case "ENG"->"PREPARED BY";case "ES"->"AUTORÍA";
            case "DE"->"ERSTELLT VON";case "TOJ"->"ОМОДАКУНАНДА";case "TUR"->"HAZIRLAYAN";
            default->"TAYYORLADI";
        };
        int authorWidth=referenceDesign || image!=null?510:W-2*M;
        rect(slide,M,371,authorWidth,1,tint(theme,.3));
        label(slide,authorLabel,M,388,authorWidth,theme.muted());
        text(slide,userName,M,411,authorWidth,53,23,16,false,theme.text());
        if (!referenceDesign && image!=null) {
            frame(slide,605,107,315,354,tint(theme,.35),1);
            photo(ppt,slide,image,614,116,297,336);
        }
    }

    private int header(XSLFSlide slide, Slide content, int index, SlideTheme theme) {
        label(slide,String.format(Locale.ROOT,"%02d",index+1),M,31,46,theme.accent());
        if(!content.subtitle().isBlank() && content.layout()!=Layout.AGENDA)
            text(slide,content.subtitle(),M+62,31,W-2*M-62,25,13,10,true,theme.muted());
        rect(slide,M,59,34,3,theme.accent());
        text(slide,content.title(),M,77,W-2*M,76,36,24,true,theme.text());
        rect(slide,M,155,W-2*M,1,tint(theme,.18));
        return 174;
    }

    /** An agenda contains section labels only, with a full-page rhythm for four, five or six sections. */
    private void agenda(XSLFSlide slide,List<String> sections,int y,SlideTheme theme) {
        if(sections.isEmpty()) return;
        int count=sections.size(),gap=16,h=475-y;
        if(count==5) {
            int rowH=h/count;
            for(int i=0;i<count;i++) {
                int top=y+i*rowH;
                rect(slide,M,top,W-2*M,rowH-8,i%2==0?theme.surface():tint(theme,.035));
                rect(slide,M,top,3,rowH-8,theme.accent());
                text(slide,String.format(Locale.ROOT,"%02d",i+1),M+18,top+10,50,rowH-21,26,21,true,theme.accent());
                text(slide,sections.get(i),M+87,top+10,W-2*M-112,rowH-21,25,18,true,theme.text());
            }
            return;
        }
        int columns=2,rows=(count+1)/2,width=(W-2*M-gap)/columns,height=(h-gap*(rows-1))/rows;
        for(int i=0;i<count;i++) {
            int x=M+(i%2)*(width+gap),top=y+(i/2)*(height+gap);
            rect(slide,x,top,width,height,theme.surface());
            frame(slide,x,top,width,height,tint(theme,.24),.8);
            rect(slide,x,top,3,height,theme.accent());
            text(slide,String.format(Locale.ROOT,"%02d",i+1),x+20,top+20,56,height-38,34,26,true,theme.accent());
            text(slide,sections.get(i),x+92,top+21,width-112,height-39,26,18,true,theme.text());
        }
    }

    private void editorial(XSLFSlide slide, List<String> bullets, int y, SlideTheme theme, boolean summary) {
        if (bullets.isEmpty()) return;
        int h = 475 - y;
        if (bullets.size() == 1) {
            rect(slide,M,y,W-2*M,h,theme.surface());
            frame(slide,M,y,W-2*M,h,tint(theme,.28),1);
            rect(slide,M,y,6,h,theme.accent());
            text(slide,bullets.get(0),M+35,y+31,W-2*M-70,h-61,33,20,true,theme.text());
            return;
        }
        if(summary) {
            splitText(slide,bullets,y,theme);
            return;
        }
        if(bullets.size()==2) {
            int gap=22,width=(W-2*M-gap)/2;
            for(int i=0;i<2;i++) {
                int x=M+i*(width+gap);
                rect(slide,x,y,width,h,theme.surface());
                frame(slide,x,y,width,h,tint(theme,.22),1);
                rect(slide,x,y,width,4,i==0?theme.accent():tint(theme,.48));
                text(slide,String.format(Locale.ROOT,"%02d",i+1),x+23,y+20,width-46,36,28,23,true,theme.accent());
                text(slide,bullets.get(i),x+23,y+74,width-46,h-99,25,17,false,theme.text());
            }
            return;
        }
        if(bullets.size()==3) {
            if(bullets.subList(1,3).stream().anyMatch(fact->fact.length()>210)) {
                factRows(slide,bullets,y,theme); return;
            }
            int leadH=112,gap=17,width=(W-2*M-gap)/2,lowerY=y+leadH+gap,lowerH=h-leadH-gap;
            rect(slide,M,y,W-2*M,leadH,tint(theme,.11));
            frame(slide,M,y,W-2*M,leadH,tint(theme,.26),1);
            rect(slide,M,y,5,leadH,theme.accent());
            text(slide,"01",M+23,y+23,48,40,30,24,true,theme.accent());
            text(slide,bullets.get(0),M+94,y+20,W-2*M-119,leadH-39,24,17,true,theme.text());
            for(int i=1;i<3;i++) {
                int x=M+(i-1)*(width+gap);
                rect(slide,x,lowerY,width,lowerH,theme.surface());
                frame(slide,x,lowerY,width,lowerH,tint(theme,.18),.8);
                text(slide,String.format(Locale.ROOT,"%02d",i+1),x+21,lowerY+16,34,23,18,16,true,theme.accent());
                text(slide,bullets.get(i),x+21,lowerY+52,width-42,lowerH-71,22,16,false,theme.text());
            }
            return;
        }
        cards(slide,bullets,y,theme);
    }

    /** Text-only split is intentional too: one leading point and a framed stack of supporting facts. */
    private void splitText(XSLFSlide slide,List<String> bullets,int y,SlideTheme theme) {
        if(bullets.size()<2) { editorial(slide,bullets,y,theme,false); return; }
        if(bullets.subList(1,bullets.size()).stream().anyMatch(fact->fact.length()>240)) {
            factRows(slide,bullets,y,theme); return;
        }
        int h=475-y,leftW=345,gap=25,rightX=M+leftW+gap,rightW=W-M-rightX;
        rect(slide,M,y,leftW,h,tint(theme,.14));
        frame(slide,M,y,leftW,h,tint(theme,.38),1.2);
        rect(slide,M,y,5,h,theme.accent());
        text(slide,"01",M+24,y+20,leftW-48,39,31,25,true,theme.accent());
        text(slide,bullets.get(0),M+24,y+78,leftW-48,h-102,25,17,true,theme.text());
        List<String> rest=bullets.subList(1,bullets.size()); int itemH=h/rest.size();
        for(int i=0;i<rest.size();i++) {
            int top=y+i*itemH;
            rect(slide,rightX,top,rightW,itemH-10,theme.surface());
            frame(slide,rightX,top,rightW,itemH-10,tint(theme,.18),.8);
            text(slide,String.format(Locale.ROOT,"%02d",i+2),rightX+15,top+19,34,25,19,16,true,theme.accent());
            text(slide,rest.get(i),rightX+66,top+17,rightW-84,itemH-43,23,15,false,theme.text());
        }
    }

    /** Dense sourced facts use a wider reading measure, with row height allocated to actual text length. */
    private void factRows(XSLFSlide slide,List<String> facts,int y,SlideTheme theme) {
        int available=475-y,total=facts.stream().mapToInt(fact->Math.max(120,fact.length())).sum(),top=y;
        for(int i=0;i<facts.size();i++) {
            int rowH=i==facts.size()-1?475-top:Math.max(1,available*Math.max(120,facts.get(i).length())/total);
            rect(slide,M,top,W-2*M,rowH-8,theme.surface());
            frame(slide,M,top,W-2*M,rowH-8,tint(theme,.23),.8);
            rect(slide,M,top,3,rowH-8,theme.accent());
            text(slide,String.format(Locale.ROOT,"%02d",i+1),M+18,top+13,37,28,22,17,true,theme.accent());
            text(slide,facts.get(i),M+76,top+11,W-2*M-97,rowH-28,24,14,false,theme.text());
            top+=rowH;
        }
    }

    private void cards(XSLFSlide slide, List<String> bullets, int y, SlideTheme theme) {
        if (bullets.isEmpty()) return;
        if (bullets.size()>4) { comparison(slide,bullets,y,theme); return; }
        int count = bullets.size(), cols = count == 4 ? 2 : Math.min(3, count), rows = (count + cols - 1) / cols;
        int gap = 17, width = (W - 2*M - gap * (cols-1)) / cols, height = (475 - y - gap * (rows-1)) / rows;
        for (int i=0; i<count; i++) {
            int x = M + (i % cols) * (width + gap), top = y + (i / cols) * (height + gap);
            rounded(slide, x, top, width, height, theme.surface());
            frame(slide,x,top,width,height,tint(theme,.24),.8);
            if(count==4) {
                rect(slide,x,top,3,height,theme.accent());
                text(slide,String.format(Locale.ROOT,"%02d",i+1),x+16,top+17,28,24,17,15,true,theme.accent());
                text(slide,bullets.get(i),x+57,top+16,width-75,height-32,21,14,false,theme.text());
                continue;
            }
            rect(slide, x+18, top+20, 28, 3, theme.accent());
            label(slide, String.format(Locale.ROOT, "%02d", i+1), x+width-49, top+15, 30, theme.muted());
            String[] pieces = divideHeading(bullets.get(i));
            if (!pieces[0].isBlank()) {
                text(slide, pieces[0], x+18, top+41, width-36, Math.min(57, height/3), 22, 16, true, theme.text());
                int bodyTop = top + Math.min(107, height/2);
                text(slide, pieces[1], x+18, bodyTop, width-36, top+height-bodyTop-17, 19, 14, false, theme.muted());
            } else text(slide, pieces[1], x+18, top+45, width-36, height-64, 23, 16, false, theme.text());
        }
    }

    private void split(XMLSlideShow ppt, XSLFSlide slide, List<String> bullets, int y,
                       SlideTheme theme, ImageSearchService.ImageAsset image, int index) throws IOException {
        int photoW = 363, gap = 32, textW = W - 2*M - photoW - gap, h = 475-y;
        boolean imageRight = index % 2 == 0;
        int imageX = imageRight ? W-M-photoW : M, textX = imageRight ? M : M+photoW+gap;
        rect(slide,imageX,y,photoW,h,theme.surface());
        frame(slide,imageX,y,photoW,h,tint(theme,.3),1);
        photo(ppt, slide, image, imageX+8, y+8, photoW-16, h-16);
        rect(slide,textX,y,4,h,theme.accent());
        bulletList(slide, bullets, textX+18, y+3, textW-18, h-6, theme, 22, true);
    }

    private void comparison(XSLFSlide slide, List<String> bullets, int y, SlideTheme theme) {
        if (bullets.size() < 2) { editorial(slide, bullets, y, theme, false); return; }
        int gap = 24, width = (W - 2*M - gap)/2, h=475-y, midpoint=(bullets.size()+1)/2;
        for (int side=0; side<2; side++) {
            int x=M+side*(width+gap);
            rounded(slide, x, y, width, h, theme.surface());
            frame(slide,x,y,width,h,tint(theme,.25),1);
            rect(slide, x, y+3, width, 4, side==0 ? theme.accent() : tint(theme,.4));
            List<String> group = side==0 ? bullets.subList(0,midpoint) : bullets.subList(midpoint,bullets.size());
            bulletList(slide, group, x+24, y+22, width-48, h-44, theme, 23, true);
        }
    }

    private void process(XSLFSlide slide, List<String> bullets, int y, SlideTheme theme, boolean timeline) {
        if (bullets.isEmpty()) return;
        if(timeline) {
            int rowH=(475-y)/bullets.size();
            rect(slide,M+24,y+16,2,Math.max(1,475-y-32),tint(theme,.35));
            for(int i=0;i<bullets.size();i++) {
                int top=y+i*rowH;
                circle(slide,M+5,top+14,40,theme.surface(),theme.accent(),1.6);
                text(slide,String.format(Locale.ROOT,"%02d",i+1),M+5,top+23,40,23,16,14,true,theme.accent(),TextAlign.CENTER);
                rect(slide,M+75,top,W-2*M-75,rowH-12,theme.surface());
                frame(slide,M+75,top,W-2*M-75,rowH-12,tint(theme,.19),.8);
                text(slide,bullets.get(i),M+96,top+18,W-2*M-118,rowH-46,25,16,false,theme.text());
            }
            return;
        }
        int count=bullets.size(), gap=16, width=(W-2*M-gap*(count-1))/count, h=475-y;
        if (width<150) { cards(slide, bullets, y, theme); return; }
        if (timeline) rect(slide, M+18, y+25, W-2*M-36, 2, tint(theme,.28));
        for (int i=0; i<count; i++) {
            int x=M+i*(width+gap);
            rect(slide,x,y+66,width,h-66,theme.surface());
            frame(slide,x,y+66,width,h-66,tint(theme,.23),.8);
            if (!timeline && i<count-1) line(slide, x+51,y+26,x+width+gap-7,y+26,theme.accent(),1.4);
            circle(slide, x, y+2, 48, timeline ? theme.background() : theme.surface(), theme.accent(), 1.8);
            text(slide, String.format(Locale.ROOT,"%02d",i+1),x,y+11,48,24,17,15,true,theme.accent(),TextAlign.CENTER);
            String[] pieces=divideHeading(bullets.get(i));
            if (!pieces[0].isBlank()) {
                text(slide,pieces[0],x+17,y+83,width-34,60,22,16,true,theme.text());
                text(slide,pieces[1],x+17,y+149,width-34,h-167,19,14,false,theme.muted());
            } else text(slide,pieces[1],x+17,y+85,width-34,h-104,22,15,false,theme.text());
        }
    }

    private void sources(XSLFSlide slide, Slide content, int y, SlideTheme theme) {
        List<String> entries=content.bullets();
        if (entries.isEmpty()) entries=content.sources().stream().map(s -> s.title() + "\n" + host(s.url())).toList();
        if (entries.isEmpty()) return;
        int columns=entries.size()>4?2:1, rows=(entries.size()+columns-1)/columns;
        int gap=28,width=(W-2*M-gap*(columns-1))/columns,itemH=(475-y)/rows;
        for (int i=0;i<entries.size();i++) {
            int x=M+(i/rows)*(width+gap), top=y+(i%rows)*itemH;
            rect(slide,x,top+itemH-8,width,1,tint(theme,.12));
            label(slide,String.format(Locale.ROOT,"%02d",i+1),x,top+8,35,theme.accent());
            text(slide,entries.get(i),x+47,top+5,width-47,itemH-19,21,14,false,theme.text());
        }
    }

    private void bulletList(XSLFSlide slide, List<String> bullets, int x,int y,int width,int height,
                            SlideTheme theme,double preferred,boolean separated) {
        if (bullets.isEmpty() || height<=0) return;
        int itemH=height/bullets.size();
        for (int i=0;i<bullets.size();i++) {
            int top=y+i*itemH;
            if (separated && i>0) rect(slide,x,top-2,width,1,tint(theme,.12));
            circle(slide,x,top+12,5,theme.accent(),null,0);
            text(slide,bullets.get(i),x+19,top+5,width-19,itemH-11,preferred,14,false,theme.text());
        }
    }

    private void footer(XSLFSlide slide, Slide content, int index,int count,SlideTheme theme,
                        ImageSearchService.ImageAsset image) {
        rect(slide,M,FOOTER_Y-9,W-2*M,1,tint(theme,.13));
        String refs=content.sources().stream().map(s -> host(s.url())).filter(s -> !s.isBlank())
                .distinct().limit(3).reduce((a,b)->a+"  ·  "+b).orElse("");
        if (image!=null) refs+=(refs.isEmpty()?"":"  ·  ")+host(image.url());
        if (!refs.isBlank() && content.layout()!=Layout.AGENDA) text(slide,refs,M,FOOTER_Y,715,17,9,8,false,theme.muted());
        text(slide,String.format(Locale.ROOT,"%02d / %02d",index+1,count),W-M-86,FOOTER_Y,86,18,11,10,true,theme.muted(),TextAlign.RIGHT);
    }

    private void addNotes(XMLSlideShow ppt, XSLFSlide slide, Slide content, ImageSearchService.ImageAsset image, boolean referenceDesign, String language) {
        boolean ru="RU".equals(SlideLanguage.normalize(language)), en="ENG".equals(SlideLanguage.normalize(language));
        String textSources=ru?"ТЕКСТОВЫЕ ИСТОЧНИКИ":en?"TEXT SOURCES":"MATN MANBALARI";
        String noSource=ru?"К этому слайду не прикреплён внешний текстовый источник.\n":en?"No external text source attached to this slide.\n":"Bu slaydga tashqi matn manbasi biriktirilmagan.\n";
        String imageSource=ru?"ИСТОЧНИК ИЗОБРАЖЕНИЯ":en?"IMAGE SOURCE":"RASM MANBASI";
        String placement=ru?"Размещение: изображение вписано в область с сохранением исходных пропорций.\n":en?"Placement: fitted to the image area, original aspect ratio retained.\n":"Joylashuv: rasm nisbatlari saqlangan holda ajratilgan sohaga joylashtirildi.\n";
        String background=ru?"\nДЕКОРАТИВНЫЙ ФОН\n":en?"\nDECORATIVE BACKGROUND\n":"\nBEZAK FONI\n";
        String designReference=ru?"Образец дизайна предоставлен пользователем: Tarmoq_xavfsizligi_10_slayd.pptx\n":en?"User-provided design reference: Tarmoq_xavfsizligi_10_slayd.pptx\n":"Foydalanuvchi bergan dizayn namunasi: Tarmoq_xavfsizligi_10_slayd.pptx\n";
        StringBuilder note=new StringBuilder(content.title()).append("\n\n").append(textSources).append('\n');
        if (content.sources().isEmpty()) note.append(noSource);
        for (Source source:content.sources()) note.append(source.title()).append('\n').append(source.url()).append("\n\n");
        if (image!=null) note.append(imageSource).append('\n').append(image.title()).append('\n').append(image.url()).append('\n').append(image.attribution()).append('\n').append(placement);
        if (referenceDesign) note.append(background).append(designReference);
        XSLFNotes notes=ppt.getNotesSlide(slide);
        XSLFTextBox box=notes.createTextBox(); box.setAnchor(new Rectangle(30,80,650,650));
        box.setText(note.toString()).setFontSize(11d);
    }

    private boolean referenceBackground(XMLSlideShow ppt, XSLFSlide slide, SlideTheme theme, String topic, boolean cover) {
        String key = topic.toLowerCase(Locale.ROOT);
        if (!"1".equals(theme.id()) || !(key.contains("xavfsiz") || key.contains("security") || key.contains("cyber")
                || key.contains("безопас") || key.contains("кибер"))) return false;
        try (InputStream input = getClass().getResourceAsStream("/slides/" + (cover ? "cyber-cover.jpg" : "cyber-body.jpg"))) {
            if (input == null) return false;
            XSLFPictureShape picture = slide.createPicture(ppt.addPicture(input.readAllBytes(), PictureData.PictureType.JPEG));
            picture.setName("User-provided cybersecurity design background");
            picture.setAnchor(new Rectangle(0, 0, W, H));
            return true;
        } catch (IOException unavailable) { return false; }
    }

    private void background(XSLFSlide slide,SlideTheme theme,int index,boolean cover) {
        Color faint=tint(theme,.055);
        switch(theme.decoration()) {
            case GRID -> {
                for(int x=0;x<W;x+=40) rect(slide,x,0,.5,H,faint);
                for(int y=0;y<H;y+=40) rect(slide,0,y,W,.5,faint);
            }
            case DOTS -> {
                for(int x=720;x<940;x+=22) for(int y=32;y<154;y+=22) circle(slide,x,y,2.5,tint(theme,.13),null,0);
            }
            case LINES -> { for(int i=0;i<5;i++) line(slide,720+i*40,0,960,110+i*35,faint,1); }
            case FRAME -> { rect(slide,0,0,W,7,theme.accent()); rect(slide,W-14,20,2,135,tint(theme,.15)); }
            case WAVES -> { for(int i=0;i<4;i++) circle(slide,780+i*24,-120,270+i*20,null,faint,2); }
            case BOTANICAL -> {
                for(int i=0;i<4;i++) {
                    XSLFAutoShape leaf=shape(slide,ShapeType.ELLIPSE,800+i*25,-40+i*17,110,220,faint,null,0);
                    leaf.setRotation(30+i*13);
                }
            }
            case ORBITS -> { circle(slide,790,-91,250,null,faint,1.8); circle(slide,842,-40,145,null,faint,1.2); }
            case NETWORK -> {
                int[][] nodes={{805,29},{866,71},{921,29},{915,132},{775,105}};
                for(int i=0;i<nodes.length;i++) {
                    int[] a=nodes[i],b=nodes[(i+1)%nodes.length];
                    line(slide,a[0],a[1],b[0],b[1],faint,1);
                    circle(slide,a[0]-3,a[1]-3,6,tint(theme,.11),null,0);
                }
            }
        }
    }

    /** Native thematic geometry, never a fabricated replacement photo. */
    private void scene(XSLFSlide slide,SlideTheme theme,int x,int y,int width,int height) {
        Color subtle=tint(theme,.13);
        switch(theme.decoration()) {
            case NETWORK -> {
                int cx=x+width/2,cy=y+height/2;
                int[][] points={{x+23,y+34},{x+width-28,y+38},{x+14,y+height-40},{x+width-20,y+height-30},{x+width/2,y+4},{x+width/2,y+height-2}};
                for(int[] p:points) {
                    line(slide,cx,cy,p[0],p[1],subtle,2);
                    circle(slide,p[0]-13,p[1]-13,26,theme.surface(),theme.accent(),1.4);
                    circle(slide,p[0]-3,p[1]-3,6,theme.accent(),null,0);
                }
                shape(slide,ShapeType.HEXAGON,cx-49,cy-45,98,90,theme.surface(),theme.accent(),2);
                circle(slide,cx-15,cy-18,30,null,theme.accent(),2); rect(slide,cx-19,cy+2,38,28,theme.accent());
            }
            case ORBITS -> {
                for(int i=0;i<3;i++) { XSLFAutoShape orbit=shape(slide,ShapeType.ELLIPSE,x+4,y+65,width-8,90,null,theme.accent(),1.6); orbit.setRotation(i*60); }
                circle(slide,x+width/2-18,y+height/2-18,36,theme.accent(),null,0);
            }
            case BOTANICAL -> {
                line(slide,x+width/2,y+height,x+width/2,y+15,theme.accent(),2);
                for(int i=0;i<3;i++) {
                    XSLFAutoShape a=shape(slide,ShapeType.ELLIPSE,x+width/2-77,y+28+i*54,77,35,subtle,theme.accent(),1); a.setRotation(28);
                    XSLFAutoShape b=shape(slide,ShapeType.ELLIPSE,x+width/2,y+48+i*54,77,35,subtle,theme.accent(),1); b.setRotation(-28);
                }
            }
            case WAVES -> {
                for(int i=0;i<4;i++) {
                    line(slide,x,y+62+i*36,x+width/2,y+35+i*36,theme.accent(),2);
                    line(slide,x+width/2,y+35+i*36,x+width,y+62+i*36,theme.accent(),2);
                }
            }
            case LINES -> {
                for(int i=0;i<5;i++) rounded(slide,x+i*43,y+height-(i+1)*34,25,(i+1)*34,i==4?theme.accent():subtle);
                line(slide,x,y+height+8,x+width,y+height+8,theme.accent(),1);
            }
            case FINANCE -> {
                int base=y+height-24;
                for(int i=0;i<6;i++) rounded(slide,x+12+i*39,base-(i+1)*(18+(i%2)*9),25,(i+1)*(18+(i%2)*9),i==5?theme.accent():subtle);
                line(slide,x+5,base+3,x+width-5,base+3,theme.accent(),1.5);
                line(slide,x+15,y+112,x+85,y+80,theme.accent(),2); line(slide,x+85,y+80,x+145,y+91,theme.accent(),2); line(slide,x+145,y+91,x+width-10,y+20,theme.accent(),2);
            }
            case MEDICAL -> {
                rounded(slide,x+width/2-19,y+31,38,height-50,subtle); circle(slide,x+width/2-27,y+height-57,54,theme.surface(),theme.accent(),2);
                rounded(slide,x+width/2-8,y+55,16,height-90,theme.accent());
                rounded(slide,x+width/2-54,y+height/2-8,108,16,theme.accent());
                for(int i=0;i<3;i++) circle(slide,x+14+i*30,y+15,8,theme.accent(),null,0);
            }
            case ARCHITECTURE -> {
                int base=y+height-20;
                line(slide,x+4,base,x+width-4,base,theme.accent(),2);
                for(int i=0;i<4;i++) { int cx=x+24+i*62; line(slide,cx,base,cx,y+78,theme.accent(),3); line(slide,cx+35,base,cx+35,y+78,theme.accent(),3); shape(slide,ShapeType.ARC,cx,y+43,35,70,null,subtle,2); }
                line(slide,x+16,y+77,x+width-12,y+77,theme.accent(),3);
                line(slide,x+width/2,y+20,x+width/2,y+57,subtle,1);
            }
            case CLIMATE -> {
                rounded(slide,x+width/2-12,y+15,24,height-50,theme.surface()); circle(slide,x+width/2-23,y+height-65,46,theme.accent(),null,0);
                rounded(slide,x+width/2-4,y+50,8,height-100,theme.accent());
                for(int i=0;i<5;i++) { int yy=y+height-26-i*40; line(slide,x+width/2+17,yy,x+width/2+39,yy,theme.muted(),1); }
                for(int i=0;i<3;i++) { line(slide,x+25+i*28,y+52,x+25+i*28,y+28,theme.accent(),1); circle(slide,x+19+i*28,y+18,12,subtle,theme.accent(),1); }
            }
            case HISTORY -> {
                int base=y+height-18;
                line(slide,x+10,base,x+width-10,base,theme.accent(),2);
                for(int i=0;i<3;i++) { int xx=x+35+i*78; line(slide,xx,base,xx,y+91,theme.accent(),3); line(slide,xx+31,base,xx+31,y+91,theme.accent(),3); shape(slide,ShapeType.ARC,xx,y+60,31,62,null,subtle,2); }
                line(slide,x+19,y+88,x+width-19,y+88,theme.accent(),3); line(slide,x+26,y+79,x+width-26,y+79,subtle,1.5);
            }
            case EDUCATION, BOOKS -> {
                shape(slide,ShapeType.RECT,x+20,y+42,width/2-24,height-75,theme.surface(),theme.accent(),2);
                shape(slide,ShapeType.RECT,x+width/2+4,y+42,width/2-24,height-75,theme.surface(),theme.accent(),2);
                line(slide,x+width/2,y+46,x+width/2,y+height-34,theme.accent(),2);
                for(int i=0;i<4;i++) { line(slide,x+35,y+66+i*28,x+width/2-18,y+66+i*28,subtle,1.4); line(slide,x+width/2+18,y+66+i*28,x+width-35,y+66+i*28,subtle,1.4); }
                if(theme.decoration()==SlideTheme.Decoration.EDUCATION) { line(slide,x+width/2,y+35,x+width/2,y+height-24,theme.accent(),3); circle(slide,x+width/2-7,y+15,14,theme.accent(),null,0); }
            }
            case SOLAR -> {
                circle(slide,x+width/2-27,y+height/2-27,54,theme.accent(),null,0);
                for(int i=0;i<4;i++) { XSLFAutoShape orbit=shape(slide,ShapeType.ELLIPSE,x+5,y+height/2-40,width-10,80,null,subtle,1.5); orbit.setRotation(i*45); }
                for(int i=0;i<5;i++) circle(slide,x+18+i*48,y+20+(i%2)*27,8+i%3*3,theme.text(),null,0);
            }
            case FOREST -> {
                for(int tree=0;tree<3;tree++) { int tx=x+45+tree*83,base=y+height-20-(tree%2)*16;
                    line(slide,tx,base,tx,y+height/2,theme.accent(),3);
                    for(int level=0;level<4;level++) { int yy=y+height/2-level*30;
                        line(slide,tx,yy,tx-30,yy-22,subtle,2); line(slide,tx,yy,tx+30,yy-22,theme.accent(),2);
                        circle(slide,tx-40,yy-31,18,subtle,theme.accent(),1); circle(slide,tx+24,yy-31,18,subtle,theme.accent(),1);
                    }
                }
                line(slide,x+12,y+height-16,x+width-12,y+height-16,theme.accent(),2);
            }
            case LEAF -> {
                line(slide,x+width/2,y+height-10,x+width/2,y+20,theme.accent(),3);
                for(int i=0;i<5;i++) { int yy=y+height-34-i*38; XSLFAutoShape a=shape(slide,ShapeType.ELLIPSE,x+width/2-60,yy,62,28,subtle,theme.accent(),1.2); a.setRotation(25); XSLFAutoShape b=shape(slide,ShapeType.ELLIPSE,x+width/2,yy-15,62,28,subtle,theme.accent(),1.2); b.setRotation(-25); }
            }
            case CORAL -> {
                int base=y+height-12; line(slide,x+12,base,x+width-12,base,theme.accent(),2);
                for(int i=0;i<5;i++) { int cx=x+28+i*50,top=y+35+(i%2)*28; line(slide,cx,base,cx,top+58,theme.accent(),3); line(slide,cx,top+74,cx-17,top+42,theme.accent(),2); line(slide,cx,top+70,cx+18,top+34,theme.accent(),2); circle(slide,cx-23,top+28,14,subtle,theme.accent(),1); circle(slide,cx+12,top+19,14,subtle,theme.accent(),1); }
                for(int i=0;i<8;i++) circle(slide,x+16+i*38,y+12+(i%2)*10,5,theme.accent(),null,0);
            }
            case BLUEPRINT -> {
                for(int i=0;i<6;i++) { line(slide,x+i*42,y,x+i*42,y+height,subtle,0.8); line(slide,x,y+i*36,x+width,y+i*36,subtle,0.8); }
                shape(slide,ShapeType.RECT,x+28,y+30,width-56,height-65,null,theme.accent(),2);
                line(slide,x+28,y+height/2,x+width-28,y+height/2,theme.accent(),1.8);
                line(slide,x+width/2,y+30,x+width/2,y+height-35,theme.accent(),1.8);
                circle(slide,x+width/2-24,y+height/2-24,48,theme.surface(),theme.accent(),2);
            }
            case MOLECULE -> {
                int[][] n={{x+40,y+45},{x+width-40,y+55},{x+width/2,y+height/2},{x+50,y+height-45},{x+width-42,y+height-55},{x+width/2,y+25}};
                int[][] edges={{0,2},{1,2},{2,3},{2,4},{2,5},{0,5},{1,5}};
                for(int[] e:edges) line(slide,n[e[0]][0],n[e[0]][1],n[e[1]][0],n[e[1]][1],subtle,3);
                for(int i=0;i<n.length;i++) circle(slide,n[i][0]-12,n[i][1]-12,24,i==2?theme.accent():theme.surface(),theme.accent(),2);
            }
            case TECH -> {
                shape(slide,ShapeType.RECT,x+width/2-48,y+height/2-43,96,86,theme.surface(),theme.accent(),2);
                text(slide,"AI",x+width/2-38,y+height/2-17,76,38,28,20,true,theme.accent(),TextAlign.CENTER);
                for(int i=0;i<5;i++) { int d=y+height/2-32+i*16; line(slide,x+width/2-65,d,x+width/2-49,d,theme.accent(),2); line(slide,x+width/2+48,d,x+width/2+64,d,theme.accent(),2); }
                for(int i=0;i<4;i++) { int xx=x+width/2-30+i*20; line(slide,xx,y+height/2-59,xx,y+height/2-43,theme.accent(),2); line(slide,xx,y+height/2+43,xx,y+height/2+59,theme.accent(),2); }
            }
            case COMPASS -> {
                circle(slide,x+width/2-70,y+height/2-70,140,null,theme.accent(),2);
                circle(slide,x+width/2-48,y+height/2-48,96,null,subtle,1.5);
                shape(slide,ShapeType.TRIANGLE,x+width/2-18,y+height/2-58,36,68,theme.accent(),null,0);
                shape(slide,ShapeType.TRIANGLE,x+width/2-18,y+height/2-10,36,68,theme.surface(),theme.accent(),1);
                circle(slide,x+width/2-6,y+height/2-6,12,theme.text(),null,0);
            }
            case QUOTE -> {
                text(slide,"“ ”",x+width/2-106,y+height/2-116,212,140,112,84,true,theme.accent(),TextAlign.CENTER);
                for(int i=0;i<4;i++) { rounded(slide,x+24,y+height/2+30+i*20,width-48-i*25,5,i==0?theme.accent():subtle); }
            }
            case MAP -> {
                shape(slide,ShapeType.HEXAGON,x+30,y+30,width-60,height-60,theme.surface(),theme.accent(),2);
                line(slide,x+width/2,y+45,x+width/2,y+height-45,subtle,1.5); line(slide,x+45,y+height/2,x+width-45,y+height/2,subtle,1.5);
                circle(slide,x+width/2-12,y+height/2-12,24,theme.accent(),null,0);
                line(slide,x+width/2,y+height/2,x+width-53,y+58,theme.accent(),2);
                circle(slide,x+width-69,y+43,30,theme.surface(),theme.accent(),1.5);
            }
            case STARS -> {
                for(int i=0;i<18;i++) { int sx=x+12+(i*47)%(width-24),sy=y+12+(i*71)%(height-24),r=5+(i%3)*3; circle(slide,sx,sy,r,theme.accent(),null,0); }
                shape(slide,ShapeType.STAR_5,x+width/2-41,y+height/2-41,82,82,theme.surface(),theme.accent(),2);
                for(int i=0;i<3;i++) { XSLFAutoShape orbit=shape(slide,ShapeType.ELLIPSE,x+6,y+height/2-35,width-12,70,null,subtle,1); orbit.setRotation(i*60); }
            }
            case HEART -> {
                shape(slide,ShapeType.HEART,x+width/2-57,y+height/2-52,114,104,subtle,theme.accent(),2);
                line(slide,x+15,y+height/2,x+width/2-45,y+height/2,theme.accent(),2);
                int[] xx={x+width/2-45,x+width/2-25,x+width/2-13,x+width/2+1,x+width/2+14,x+width/2+30,x+width/2+47};
                int[] yy={y+height/2,y+height/2,y+height/2-28,y+height/2+30,y+height/2-14,y+height/2,y+height/2};
                for(int i=0;i<xx.length-1;i++) line(slide,xx[i],yy[i],xx[i+1],yy[i+1],theme.accent(),2);
                line(slide,x+width/2+47,y+height/2,x+width-12,y+height/2,theme.accent(),2);
            }
            default -> {
                for(int i=0;i<3;i++) {
                    rounded(slide,x+i*18,y+i*40,width-i*36,70,theme.surface());
                    rect(slide,x+i*18+18,y+i*40+20,36,4,theme.accent());
                    rect(slide,x+i*18+18,y+i*40+35,width-i*36-36,3,subtle);
                }
            }
        }
    }

    private void photo(XMLSlideShow ppt,XSLFSlide slide,ImageSearchService.ImageAsset asset,int x,int y,int width,int height) throws IOException {
        byte[] cropped=fitToFrame(asset.data(),width*2,height*2);
        XSLFPictureData data=ppt.addPicture(cropped,PictureData.PictureType.PNG);
        XSLFPictureShape picture=slide.createPicture(data);
        picture.setName(asset.title()==null?"Source photograph":asset.title());
        picture.setAnchor(new Rectangle(x,y,width,height));
    }

    /** Keep the complete source image, including any diagram labels at its edges. */
    static byte[] fitToFrame(byte[] bytes,int width,int height) throws IOException {
        BufferedImage original=ImageIO.read(new ByteArrayInputStream(bytes));
        if(original==null || width<=0 || height<=0) throw new IOException("Unsupported image");
        double scale=Math.min((double)width/original.getWidth(),(double)height/original.getHeight());
        int scaledW=Math.max(1,(int)Math.round(original.getWidth()*scale)),scaledH=Math.max(1,(int)Math.round(original.getHeight()*scale));
        BufferedImage target=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics=target.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(original,(width-scaledW)/2,(height-scaledH)/2,scaledW,scaledH,null);
        } finally { graphics.dispose(); }
        ByteArrayOutputStream output=new ByteArrayOutputStream(); ImageIO.write(target,"png",output); return output.toByteArray();
    }

    private XSLFTextBox text(XSLFSlide slide,String value,double x,double y,double width,double height,double preferred,double minimum,boolean bold,Color color) {
        return text(slide,value,x,y,width,height,preferred,minimum,bold,color,TextAlign.LEFT);
    }
    private XSLFTextBox text(XSLFSlide slide,String value,double x,double y,double width,double height,double preferred,double minimum,boolean bold,Color color,TextAlign align) {
        XSLFTextBox box=slide.createTextBox(); box.setAnchor(new Rectangle2D.Double(x,y,width,Math.max(1,height)));
        box.setLeftInset(0); box.setRightInset(0); box.setTopInset(0); box.setBottomInset(0);
        box.setWordWrap(true); box.setVerticalAlignment(VerticalAlignment.TOP); box.setTextAutofit(TextAutofit.NORMAL);
        String clean=clean(value); double fontSize=fit(clean,width,height,preferred,minimum,bold);
        String[] paragraphs=clean.split("\\R",-1);
        for(int i=0;i<paragraphs.length;i++) {
            XSLFTextParagraph paragraph=i==0?box.getTextParagraphs().get(0):box.addNewTextParagraph();
            paragraph.setTextAlign(align); paragraph.setSpaceBefore(0d); paragraph.setSpaceAfter(0d); paragraph.setLineSpacing(112d);
            XSLFTextRun run=paragraph.addNewTextRun(); run.setText(paragraphs[i]); run.setFontFamily(FONT); run.setFontSize(fontSize); run.setBold(bold); run.setFontColor(color);
        }
        for(int pass=0;pass<3;pass++) {
            double measured=box.getTextHeight();
            if(measured<=height+0.5 || fontSize<=minimum) break;
            fontSize=Math.max(minimum,fontSize*Math.min(.94,(height-2)/measured));
            for(XSLFTextParagraph paragraph:box.getTextParagraphs()) for(XSLFTextRun run:paragraph.getTextRuns()) run.setFontSize(fontSize);
        }
        return box;
    }
    private static double fit(String text,double width,double height,double preferred,double minimum,boolean bold) {
        double size=preferred; while(size>minimum && estimatedHeight(text,width,size,bold)>height-2) size-=.5; return Math.max(size,minimum);
    }
    private static double estimatedHeight(String text,double width,double size,boolean bold) {
        if(text.isEmpty()) return size*1.15;
        Font font=new Font(FONT,bold?Font.BOLD:Font.PLAIN,1).deriveFont((float)size); int lines=0;
        for(String paragraph:text.split("\\R",-1)) {
            if(paragraph.isEmpty()) { lines++; continue; }
            AttributedString attributed=new AttributedString(paragraph); attributed.addAttribute(TextAttribute.FONT,font);
            LineBreakMeasurer measure=new LineBreakMeasurer(attributed.getIterator(),FRC);
            while(measure.getPosition()<paragraph.length()) { measure.nextLayout((float)Math.max(1,width-3)); lines++; }
        }
        return lines*size*1.24;
    }
    private void label(XSLFSlide slide,String value,int x,int y,int width,Color color) { text(slide,value,x,y,width,18,11,9,true,color); }
    private static Color tint(SlideTheme theme,double ratio) { return SlideTheme.mix(theme.background(),theme.accent(),ratio); }
    private static Color contrast(Color color) {
        double l=.2126*color.getRed()+.7152*color.getGreen()+.0722*color.getBlue(); return l>150?new Color(16,27,38):Color.WHITE;
    }
    private static String clean(String text) {
        return text==null?"":text.replaceAll("\\*\\*([^*]+)\\*\\*","$1").replaceAll("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]","").strip();
    }
    private static String[] divideHeading(String text) {
        String cleaned=clean(text); int colon=cleaned.indexOf(':');
        if(colon>2 && colon<58 && colon<cleaned.length()-3) return new String[]{cleaned.substring(0,colon),cleaned.substring(colon+1).strip()};
        return new String[]{"",cleaned};
    }
    private static String host(String url) {
        try { String host=URI.create(url).getHost(); return host==null?"":host.replaceFirst("^www\\.",""); } catch(Exception ignored) { return ""; }
    }
    private XSLFAutoShape shape(XSLFSlide slide,ShapeType type,double x,double y,double w,double h,Color fill,Color stroke,double lineWidth) {
        XSLFAutoShape shape=slide.createAutoShape(); shape.setShapeType(type); shape.setAnchor(new Rectangle2D.Double(x,y,w,h)); shape.setFillColor(fill); shape.setLineColor(stroke);
        if(stroke!=null) shape.setLineWidth(lineWidth); return shape;
    }
    private void rect(XSLFSlide slide,double x,double y,double width,double height,Color fill) { shape(slide,ShapeType.RECT,x,y,width,height,fill,null,0); }
    private void frame(XSLFSlide slide,double x,double y,double width,double height,Color color,double thickness) {
        shape(slide,ShapeType.RECT,x,y,width,height,null,color,thickness);
    }
    private void rounded(XSLFSlide slide,int x,int y,int width,int height,Color fill) {
        XSLFAutoShape card=shape(slide,ShapeType.ROUND_RECT,x,y,width,height,fill,null,0);
        var preset=((org.openxmlformats.schemas.presentationml.x2006.main.CTShape)card.getXmlObject()).getSpPr().getPrstGeom();
        var guides=preset.isSetAvLst()?preset.getAvLst():preset.addNewAvLst();
        var radius=guides.addNewGd(); radius.setName("adj"); radius.setFmla("val 5500");
    }
    private void circle(XSLFSlide slide,double x,double y,double diameter,Color fill,Color stroke,double thickness) { shape(slide,ShapeType.ELLIPSE,x,y,diameter,diameter,fill,stroke,thickness); }
    private void line(XSLFSlide slide,double x1,double y1,double x2,double y2,Color color,double width) {
        XSLFConnectorShape connector=slide.createConnector(); connector.setAnchor(new Rectangle2D.Double(Math.min(x1,x2),Math.min(y1,y2),Math.abs(x2-x1),Math.abs(y2-y1)));
        connector.setFlipVertical(y2<y1); connector.setLineColor(color); connector.setLineWidth(width);
    }
    @PreDestroy public void shutdown() { imageWorkers.shutdownNow(); }
}
