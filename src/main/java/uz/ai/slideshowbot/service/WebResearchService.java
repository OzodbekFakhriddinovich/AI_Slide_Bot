package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.*;
import okhttp3.*;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;

/** Key-free, attributed research, including multilingual evidence for sparse-language topics. */
@Service
public class WebResearchService {
    private final OkHttpClient client;
    private final FreeTranslationService translation;
    private final ObjectMapper mapper = new ObjectMapper();
    private record Cached(JsonNode value,long expires) {}
    private final Map<String,Cached> cache=Collections.synchronizedMap(new LinkedHashMap<>(32,.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,Cached> eldest) { return size()>96; }
    });
    private static final Set<String> STOP = Set.of("va","ham","bu","bir","uning","uchun","haqida","mavzusida","boyicha","bo'yicha","menga","kerak","iltimos","slayd","slaydlar","taqdimot","prezentatsiya","tayyorlab","tayyorlash","bering","tuzib","qilib","batafsil","the","and","of","for","in","on","a","an","presentation","slides","about","и","в","на","о");
    private static final Set<String> FACETS = Set.of("tarixi","rivojlanishi","ahamiyati","xususiyatlari","turlari","asosiy","tushunchasi","bosqichlari","jarayoni","asoslari","roli","history","development");
    public record ResearchSection(String heading, String text) {}
    public record ResearchDocument(String title, String url, String language, String imageQuery, List<ResearchSection> sections) {
        public ResearchDocument { sections = List.copyOf(sections); }
    }
    public record ResearchBundle(String query, String language, List<ResearchDocument> documents) {
        public ResearchBundle { documents = List.copyOf(documents); }
    }
    public WebResearchService() {
        this(new FreeTranslationService());
    }
    @Autowired public WebResearchService(FreeTranslationService translation) {
        this(new OkHttpClient.Builder().connectTimeout(4,TimeUnit.SECONDS).readTimeout(7,TimeUnit.SECONDS)
                .callTimeout(9,TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),translation);
    }
    WebResearchService(OkHttpClient client) { this(client,new FreeTranslationService(client)); }
    WebResearchService(OkHttpClient client,FreeTranslationService translation) { this.client=client; this.translation=translation; }

    public ResearchBundle research(String topic,String language,int slideCount,int tariff) throws IOException {
        String target = wikiLanguage(language);
        List<String> variants = queryVariants(topic);
        if(variants.isEmpty()) throw new IOException(unavailable(language));
        int maxPages = slideCount>=20?10:8;
        int minPages = 1;
        int facts = slideCount*3+12;
        long deadline = System.nanoTime()+TimeUnit.SECONDS.toNanos(95);
        long nativeDeadline=Math.min(deadline,System.nanoTime()+TimeUnit.SECONDS.toNanos(22));
        LinkedHashMap<String,ResearchDocument> docs = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        // Aliases resolve article names only; every fact still comes from a fetched source.
        for(String title:subjectArticles(topic,target)) {
            if(expired(nativeDeadline) || docs.size()>=maxPages) break;
            try { add(target,title,"",docs,seen,nativeDeadline); }
            catch(IOException e) { if(Thread.currentThread().isInterrupted()) throw e; }
        }
        searchVariants(target,variants,docs,seen,maxPages,minPages,facts,nativeDeadline);
        if(enough(docs.values(),facts,minPages)) return new ResearchBundle(variants.get(0),language,List.copyOf(docs.values()));

        // Interlanguage links give an exact concept match even when the target wiki is short.
        List<ResearchDocument> anchors=new ArrayList<>(docs.values());
        for(ResearchDocument anchor:anchors) {
            if(expired(deadline) || docs.size()>=maxPages || enough(docs.values(),facts,minPages)) break;
            if(anchor.language().equals("en")) continue;
            try {
                String english=linkedTitle(anchor.language(),anchor.title(),"en",deadline);
                if(!english.isBlank()) add("en",english,"",docs,seen,deadline);
            } catch(IOException e) { if(Thread.currentThread().isInterrupted()) throw e; }
        }

        // Translate the search query, then translate only selected facts during planning.
        // This avoids requiring a full Uzbek/Tajik/German article for every ordinary subject.
        for(String discovery:List.of("en","ru")) {
            if(expired(deadline) || docs.size()>=maxPages || enough(docs.values(),facts,minPages)) break;
            LinkedHashSet<String> translated=new LinkedHashSet<>();
            for(String title:subjectArticles(topic,discovery)) {
                if(expired(deadline) || docs.size()>=maxPages) break;
                try { add(discovery,title,"",docs,seen,deadline); }
                catch(IOException e) { if(Thread.currentThread().isInterrupted()) throw e; }
            }
            if(enough(docs.values(),facts,minPages)) break;
            if(discovery.equals(target)) translated.addAll(variants);
            else {
                try {
                    String converted=translation.translateQuery(cleanQuery(topic),discovery,deadline);
                    translated.addAll(queryVariants(converted));
                } catch(IOException e) { if(Thread.currentThread().isInterrupted()) throw e; }
                // Input may already be English/Russian even when another output language is selected.
                translated.addAll(variants);
            }
            searchVariants(discovery,List.copyOf(translated),docs,seen,maxPages,minPages,facts,deadline);
        }

        // Extend using actual source section headings; never fill with unrelated random pages.
        if(!docs.isEmpty() && docs.size()<maxPages && !enough(docs.values(),facts,minPages)) {
            for(ResearchDocument anchor:new ArrayList<>(docs.values())) {
                int expanded=0;
                for(ResearchSection section:anchor.sections()) {
                    if(section.heading().equals(anchor.title()) || section.heading().length()>90) continue;
                    if(expired(deadline) || ++expanded>5) break;
                    try {
                        for(String title:search(anchor.language(),anchor.title()+" "+section.heading(),3,deadline)) {
                            add(anchor.language(),title,anchor.title(),docs,seen,deadline);
                            if(docs.size()>=maxPages || enough(docs.values(),facts,minPages)) break;
                        }
                    } catch(IOException e) { if(Thread.currentThread().isInterrupted()) throw e; }
                    if(docs.size()>=maxPages || enough(docs.values(),facts,minPages)) break;
                }
                if(expired(deadline) || docs.size()>=maxPages || enough(docs.values(),facts,minPages)) break;
            }
        }
        if(docs.isEmpty()) throw new IOException(unavailable(language));
        return new ResearchBundle(variants.get(0),language,List.copyOf(docs.values()));
    }

    private void searchVariants(String language,List<String> variants,Map<String,ResearchDocument> docs,Set<String> seen,
                                int maxPages,int minPages,int facts,long deadline) throws IOException {
        int attempted=0;
        for(String query:variants) {
            if(expired(deadline) || attempted++>=4 || docs.size()>=maxPages || enough(docs.values(),facts,minPages)) break;
            try {
                for(String title:search(language,query,6,deadline)) {
                    add(language,title,query,docs,seen,deadline);
                    if(docs.size()>=maxPages || enough(docs.values(),facts,minPages) || expired(deadline)) break;
                }
            } catch(IOException e) { if(Thread.currentThread().isInterrupted()) throw e; }
        }
    }

    private void add(String language,String title,String subject,Map<String,ResearchDocument> docs,Set<String> seen,long deadline) throws IOException {
        if(!seen.add(language+":"+title.toLowerCase(Locale.ROOT))) return;
        JsonNode data=request(language,Map.of("prop","extracts|info|pageprops|langlinks","titles",title,"redirects","1","inprop","url","explaintext","1","exsectionformat","wiki","lllang","en","lllimit","1"),deadline);
        for(JsonNode page:data.path("query").path("pages")) {
            if(page.has("missing") || page.path("pageprops").has("disambiguation")) continue;
            String text=page.path("extract").asText(), finalTitle=page.path("title").asText(title);
            if(text.length()>180_000) text=text.substring(0,180_000);
            if(text.length()<180 || !relevant(subject,finalTitle+" "+text.substring(0,Math.min(text.length(),3000)))) continue;
            String url=page.path("fullurl").asText(wikiUrl(language,finalTitle));
            if(!url.startsWith("https://"+language+".wikipedia.org/")) url=wikiUrl(language,finalTitle);
            String imageQuery=finalTitle;
            for(JsonNode link:page.path("langlinks")) imageQuery=link.path("title").asText(link.path("*").asText(finalTitle));
            List<ResearchSection> sections=sections(finalTitle,text);
            if(!sections.isEmpty()) docs.putIfAbsent(url,new ResearchDocument(finalTitle,url,language,imageQuery,sections));
        }
    }
    private List<String> search(String language,String query,int limit,long deadline) throws IOException {
        JsonNode data=request(language,Map.of("list","search","srsearch",query,"srnamespace","0","srlimit",String.valueOf(limit),"srprop","snippet"),deadline);
        List<JsonNode> ranked=new ArrayList<>();
        data.path("query").path("search").forEach(ranked::add);
        Set<String> core=keywords(query);
        ranked.sort(Comparator.comparingInt((JsonNode item)->score(core,item.path("title").asText(),item.path("snippet").asText())).reversed());
        List<String> titles=new ArrayList<>();
        for(JsonNode item:ranked) {
            // A mention inside a novel, company history or unrelated article is not a subject match.
            String candidate=item.path("title").asText().toLowerCase(Locale.ROOT);
            String snippet=ImageSearchService.plain(item.path("snippet").asText()).toLowerCase(Locale.ROOT);
            if(!core.isEmpty() && core.stream().noneMatch(candidate::contains)
                    && core.stream().filter(snippet::contains).count()<Math.min(2,core.size())) continue;
            titles.add(item.path("title").asText());
        }
        return titles;
    }
    private String linkedTitle(String language,String title,String target,long deadline) throws IOException {
        JsonNode data=request(language,Map.of("prop","langlinks","titles",title,"redirects","1","lllang",target,"lllimit","1"),deadline);
        for(JsonNode page:data.path("query").path("pages")) for(JsonNode link:page.path("langlinks")) return link.path("title").asText(link.path("*").asText());
        return "";
    }
    private JsonNode request(String language,Map<String,String> params,long deadline) throws IOException {
        if(expired(deadline)) throw new IOException("Research timed out");
        String cacheKey=language+new TreeMap<>(params);
        Cached cached=cache.get(cacheKey);
        if(cached!=null && cached.expires()>System.currentTimeMillis()) return cached.value();
        HttpUrl.Builder url=Objects.requireNonNull(HttpUrl.parse("https://"+language+".wikipedia.org/w/api.php")).newBuilder()
                .addQueryParameter("action","query").addQueryParameter("format","json").addQueryParameter("formatversion","2").addQueryParameter("maxlag","5");
        params.forEach(url::addQueryParameter);
        Call call=client.newCall(new Request.Builder().url(url.build()).header("User-Agent","SlideBot/1.0 (educational presentations; attributed Wikipedia research)").build());
        call.timeout().timeout(Math.min(10_000,Math.max(1,TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime()))),TimeUnit.MILLISECONDS);
        try(Response response=call.execute()) {
            if(!response.isSuccessful() || response.body()==null) throw new IOException("Public research source unavailable");
            JsonNode data=mapper.readTree(ImageSearchService.readBounded(response.body(),512*1024));
            if(data.has("error")) throw new IOException("Public research source unavailable");
            cache.put(cacheKey,new Cached(data,System.currentTimeMillis()+TimeUnit.MINUTES.toMillis(45)));
            return data;
        }
    }

    static List<ResearchSection> sections(String title,String extract) {
        List<ResearchSection> result=new ArrayList<>();
        Matcher headings=Pattern.compile("(?m)^={2,6}\\s*(.*?)\\s*={2,6}\\s*$").matcher(extract);
        int start=0; String heading=title;
        while(headings.find()) { addSection(result,heading,extract.substring(start,headings.start())); heading=headings.group(1).trim(); start=headings.end(); }
        addSection(result,heading,extract.substring(start)); return result;
    }
    private static void addSection(List<ResearchSection> result,String heading,String text) {
        if(heading.matches("(?iu).*(references|bibliography|external links|see also|further reading|notes|manbalar|adabiyotlar|havolalar|izohlar|qarang|примечания|литература|ссылки|источники|weblinks|einzelnachweise|literatur|referencias|enlaces|kaynakça|dış bağlantılar|сарчашма).*")) return;
        String clean=text.replaceAll("\\[[^\\]]{0,80}\\]","").replaceAll("[\\t ]+"," ").replaceAll("\\n{3,}","\n\n").trim();
        if(clean.length()>=65) result.add(new ResearchSection(heading,clean));
    }
    static String cleanQuery(String value) {
        if(value==null) return "";
        String text=value.replace('‘','\'').replace('’','\'').replace('ʻ','\'').replace('ʼ','\'').replaceAll("[^\\p{L}\\p{N}' -]"," ").replaceAll("\\s+"," ").trim();
        return String.join(" ",Arrays.stream(text.split(" ")).filter(word->!STOP.contains(word.toLowerCase(Locale.ROOT))).filter(word->!word.matches("\\d+")).limit(18).toList());
    }
    private static List<String> subjectArticles(String topic,String language) {
        String key=topic.toLowerCase(Locale.ROOT);
        boolean network=(key.contains("tarmoq") && key.contains("xavfsiz"))
                || key.contains("network security") || key.contains("cybersecurity")
                || key.contains("сетевая безопас") || key.contains("кибербезопас");
        if(!network) return List.of();
        return switch(language) {
            case "uz" -> List.of("Axborot xavfsizligi","Kiberhujum","Fishing","Zararli dastur");
            case "en" -> List.of("Network security","Cyberattack","Phishing","Malware");
            case "ru" -> List.of("Сетевая безопасность","Кибератака","Фишинг","Вредоносная программа");
            case "de" -> List.of("Netzwerksicherheit","Cyberangriff","Phishing","Schadprogramm");
            case "es" -> List.of("Seguridad de redes","Ataque informático","Phishing","Malware");
            case "tr" -> List.of("Ağ güvenliği","Siber saldırı","Oltalama","Zararlı yazılım");
            default -> List.of();
        };
    }
    static List<String> queryVariants(String topic) {
        String clean=cleanQuery(topic); if(clean.isBlank()) return List.of();
        LinkedHashSet<String> result=new LinkedHashSet<>();
        String key=clean.toLowerCase(Locale.ROOT);
        // Subject aliases identify a public article, never supply slide facts.
        if(key.contains("tarmoq") && key.contains("xavfsiz")) result.add("Axborot xavfsizligi");
        String primary=cleanQuery(topic.split("(?iu)\\s+(?:va|hamda|and|и|y|und|ve)\\s+",2)[0]);
        if(!primary.isBlank()) result.add(primary);
        result.add(clean);
        String core=String.join(" ",Arrays.stream(clean.split(" ")).filter(word->!FACETS.contains(word.toLowerCase(Locale.ROOT))).toList());
        if(!core.isBlank()) result.add(core);
        List<String> words=Arrays.asList((core.isBlank()?clean:core).split(" "));
        if(words.size()>3) result.add(String.join(" ",words.subList(0,3)));
        if(words.size()>2) result.add(String.join(" ",words.subList(0,2)));
        return List.copyOf(result);
    }
    static Set<String> keywords(String value) {
        LinkedHashSet<String> result=new LinkedHashSet<>();
        for(String word:cleanQuery(value).toLowerCase(Locale.ROOT).split(" ")) {
            if(word.length()<3 || FACETS.contains(word)) continue;
            String stem=word.replaceAll("(?iu)(larining|laridan|larning|lariga|larini|lari|ning|dagi|dan|lar|ni|ga)$","");
            if(stem.length()>=3) result.add(stem);
        } return result;
    }
    private static int score(Set<String> terms,String title,String snippet) {
        String a=title.toLowerCase(Locale.ROOT),b=ImageSearchService.plain(snippet).toLowerCase(Locale.ROOT);
        int score=terms.stream().mapToInt(term->(a.contains(term)?12:0)+(b.contains(term)?2:0)).sum();
        if(!terms.isEmpty() && terms.stream().allMatch(a::contains)) score+=80;
        if(terms.stream().allMatch(term -> (a+" "+b).contains(term))) score+=12;
        return score;
    }
    static boolean relevant(String subject,String text) {
        Set<String> words=keywords(subject);
        String lower=text.toLowerCase(Locale.ROOT);
        if(words.isEmpty()) return true;
        long matched=words.stream().filter(lower::contains).count();
        return matched>=Math.max(1,(int)Math.ceil(words.size()*.5));
    }
    private static boolean enough(Collection<ResearchDocument> docs,int count,int pages) {
        return docs.size()>=pages && docs.stream().flatMap(doc->doc.sections().stream())
                .flatMap(section->SlidePlanningService.sentences(section.text()).stream()).distinct().count()>=count;
    }
    static String wikiLanguage(String language) {
        return switch(language==null?"UZ":language.toUpperCase(Locale.ROOT)) {case "UZ"->"uz";case "ENG","EN"->"en";case "RU"->"ru";case "ES"->"es";case "DE"->"de";case "TOJ","TG"->"tg";case "TUR","TR"->"tr";default->throw new IllegalArgumentException("Unsupported presentation language");};
    }
    private static String wikiUrl(String language,String title) { return "https://"+language+".wikipedia.org/wiki/"+URLEncoder.encode(title.replace(' ','_'),StandardCharsets.UTF_8).replace("+","%20"); }
    static String unavailable(String language) {
        return switch(wikiLanguage(language)) {
            case "en"->"Verified sources or free source translation are temporarily unavailable for this topic. Try again later or use a more specific topic.";
            case "ru"->"Проверяемые источники или бесплатный перевод временно недоступны. Попробуйте позже или уточните тему.";
            case "es"->"Las fuentes verificables o la traducción gratuita no están disponibles temporalmente. Inténtelo de nuevo o precise el tema.";
            case "de"->"Überprüfbare Quellen oder die kostenlose Übersetzung sind vorübergehend nicht verfügbar. Versuchen Sie es später erneut.";
            case "tg"->"Манбаъҳои санҷидашаванда ё тарҷумаи ройгон муваққатан дастрас нестанд. Баъдтар такрор кунед ё мавзӯъро дақиқтар нависед.";
            case "tr"->"Doğrulanabilir kaynaklar veya ücretsiz çeviri geçici olarak kullanılamıyor. Daha sonra deneyin veya konuyu netleştirin.";
            default->"Bu mavzu uchun tekshiriladigan manbalar yoki bepul tarjima hozircha mavjud emas. Birozdan so‘ng qayta urinib ko‘ring yoki mavzuni aniqroq yozing.";};
    }
    private static boolean expired(long deadline) { return Thread.currentThread().isInterrupted() || System.nanoTime()>=deadline; }
}
