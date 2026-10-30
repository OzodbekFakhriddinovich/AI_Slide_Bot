package uz.ai.slideshowbot.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uz.ai.slideshowbot.slides.SlideDeck;
import uz.ai.slideshowbot.slides.SlideDeck.*;

import java.io.IOException;
import java.text.BreakIterator;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** A complete, source-grounded narrative; selected counts refer to substantive body slides. */
@Service
public class SlidePlanningService {
    private final WebResearchService research;
    private final FreeTranslationService translation;

    @Autowired public SlidePlanningService(WebResearchService research, GptTextGeneratorService unused,
                                           FreeTranslationService translation) {
        this.research=research; this.translation=translation;
    }
    public SlidePlanningService(WebResearchService research,GptTextGeneratorService unused) {
        this(research,unused,new FreeTranslationService());
    }
    private record Fact(String text,String heading,WebResearchService.ResearchDocument doc,int rank) {}
    private record Group(String heading,int rank,List<Fact> facts) {}
    private record Draft(String heading,List<Fact> facts) {}

    public SlideDeck createDeck(String topic,int count,String language,int tariff) throws Exception {
        if(topic==null || topic.isBlank() || !Set.of(10,15,20).contains(count))
            throw new IllegalArgumentException("Invalid presentation settings");
        String target=WebResearchService.wikiLanguage(language);
        var bundle=research.research(topic,language,count,tariff);
        List<Fact> candidates=extract(bundle.documents(),topic);
        List<Fact> facts=localize(candidates,target,count*3+24,language);
        int sectionCount=count==10?4:count==15?5:6;
        if(facts.size()<count*2+3) throw new IOException(WebResearchService.unavailable(language));
        List<Draft> drafts=drafts(facts,3);
        if(drafts.size()<count+1) drafts=drafts(facts,2);
        if(drafts.size()<count+1) throw new IOException(WebResearchService.unavailable(language));
        Draft finalFacts=drafts.stream().max(Comparator.comparingInt(SlidePlanningService::closingScore)).orElseThrow();
        List<Draft> bodyCandidates=new ArrayList<>(drafts); bodyCandidates.remove(finalFacts);
        List<Draft> body=spread(bodyCandidates,count);
        List<String> agenda=new ArrayList<>(); List<Slide> middle=new ArrayList<>();
        Set<String> titles=new HashSet<>(); titles.add(normalize(topic));
        titles.add(normalize(agendaLabel(language))); titles.add(normalize(summaryLabel(language)));
        int offset=0;
        for(int section=0;section<sectionCount;section++) {
            int length=count/sectionCount+(section<count%sectionCount?1:0);
            List<Draft> chapter=body.subList(offset,offset+length);
            String heading=chapterTitle(chapter,topic,agenda,titles,language,section);
            agenda.add(heading);
            for(int i=0;i<chapter.size();i++) {
                Draft draft=chapter.get(i);
                String title=i==0?heading:slideTitle(draft,titles,language,offset+i);
                if(i==0) titles.add(normalize(title));
                middle.add(slide(title,heading,draft.facts(),layout(draft.heading(),draft.facts().size(),offset+i)));
            }
            offset+=length;
        }
        LinkedHashMap<String,Source> usedSources=new LinkedHashMap<>();
        middle.forEach(s->s.sources().forEach(source->usedSources.putIfAbsent(source.url(),source)));
        finalFacts.facts().forEach(f->usedSources.putIfAbsent(f.doc().url(),source(f)));
        List<Slide> slides=new ArrayList<>();
        slides.add(new Slide(topic.strip(),"",List.of(),Layout.COVER,"",List.of()));
        slides.add(new Slide(agendaLabel(language),"",agenda,Layout.AGENDA,"",List.of()));
        slides.addAll(middle);
        slides.add(new Slide(summaryLabel(language),sourceLabel(language),finalFacts.facts().stream().map(Fact::text).toList(),
                Layout.SUMMARY,"",List.copyOf(usedSources.values())));
        if(slides.size()!=count+3) throw new IOException("Presentation plan has an invalid slide count");
        return new SlideDeck(topic,language,slides);
    }

    private List<Fact> extract(List<WebResearchService.ResearchDocument> documents,String topic) {
        List<Group> groups=new ArrayList<>(); List<Fact> all=new ArrayList<>(); Set<String> exact=new HashSet<>();
        for(var doc:documents) {
            int taken=0;
            for(var section:doc.sections()) {
                List<Fact> sectionFacts=new ArrayList<>();
                for(String raw:sentences(section.text())) {
                    String text=terminology(raw,doc);
                    if(!exact.add(normalize(text)) || similar(all,text)) continue;
                    Fact fact=new Fact(text,section.heading(),doc,rank(section.heading(),doc.title(),topic));
                    sectionFacts.add(fact); all.add(fact);
                    if(++taken>=100) break;
                }
                if(!sectionFacts.isEmpty()) groups.add(new Group(section.heading(),rank(section.heading(),doc.title(),topic),sectionFacts));
                if(taken>=100 || all.size()>=300) break;
            }
            if(all.size()>=300) break;
        }
        groups.sort(Comparator.comparingInt(Group::rank));
        return groups.stream().flatMap(g->g.facts().stream()).toList();
    }

    /** Native evidence is preferred; source language is not an artificial topic restriction. */
    private List<Fact> localize(List<Fact> candidates,String target,int desired,String language) throws IOException {
        List<Fact> localized=new ArrayList<>();
        for(Fact fact:candidates) if(fact.doc().language().equals(target)) addUnique(localized,fact);
        if(localized.size()>=desired) return localized;
        Map<String,List<Fact>> foreign=new LinkedHashMap<>();
        for(Fact fact:candidates) if(!fact.doc().language().equals(target))
            foreign.computeIfAbsent(fact.doc().language(),unused->new ArrayList<>()).add(fact);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(100);
        IOException failure=null;
        for(var entry:foreign.entrySet()) {
            if(localized.size()>=desired) break;
            List<Fact> selected=entry.getValue().stream().limit(Math.min(entry.getValue().size(),desired-localized.size()+12)).toList();
            LinkedHashSet<String> inputs=new LinkedHashSet<>();
            selected.forEach(f->{inputs.add(f.heading());inputs.add(f.text());});
            try {
                List<String> originals=List.copyOf(inputs);
                List<String> translated=translation.translate(originals,entry.getKey(),target,deadline);
                if(translated.size()!=originals.size()) throw new IOException("Incomplete source translation");
                Map<String,String> converted=new HashMap<>();
                for(int i=0;i<originals.size();i++) converted.put(originals.get(i),translated.get(i));
                for(Fact fact:selected) {
                    String text=converted.get(fact.text());
                    if(text==null || text.isBlank()) throw new IOException("Empty source translation");
                    addUnique(localized,new Fact(text,converted.get(fact.heading()),fact.doc(),fact.rank()));
                }
            } catch(IOException unavailable) { failure=unavailable; }
        }
        if(localized.isEmpty() && failure!=null) throw new IOException(WebResearchService.unavailable(language),failure);
        localized.sort(Comparator.comparingInt(Fact::rank));
        return localized;
    }
    private static void addUnique(List<Fact> facts,Fact fact) {
        if(!similar(facts,fact.text())) facts.add(fact);
    }

    /** Keep section facts together and move short remainders into a same-source group. */
    private static List<Draft> drafts(List<Fact> facts,int perSlide) {
        LinkedHashMap<String,List<Fact>> grouped=new LinkedHashMap<>();
        for(Fact fact:facts) grouped.computeIfAbsent(fact.doc().url()+"\n"+fact.heading(),unused->new ArrayList<>()).add(fact);
        List<Draft> result=new ArrayList<>(); LinkedHashMap<String,List<Fact>> remainders=new LinkedHashMap<>();
        for(List<Fact> group:grouped.values()) {
            int at=0;
            while(group.size()-at>=2) {
                int take=Math.min(perSlide,group.size()-at);
                if(group.size()-at-take==1 && take>2) take--;
                result.add(new Draft(group.get(at).heading(),List.copyOf(group.subList(at,at+take))));
                at+=take;
            }
            if(at<group.size()) remainders.computeIfAbsent(group.get(at).doc().url(),unused->new ArrayList<>()).add(group.get(at));
        }
        for(List<Fact> loose:remainders.values()) {
            int at=0;
            while(loose.size()-at>=2) {
                int take=Math.min(perSlide,loose.size()-at);
                if(loose.size()-at-take==1 && take>2) take--;
                result.add(new Draft(loose.get(at).heading(),List.copyOf(loose.subList(at,at+take)))); at+=take;
            }
            if(at<loose.size()) {
                Fact remaining=loose.get(at);
                for(int i=result.size()-1;i>=0;i--) {
                    Draft draft=result.get(i);
                    if(draft.facts().size()<4 && draft.facts().get(0).doc().url().equals(remaining.doc().url())) {
                        List<Fact> expanded=new ArrayList<>(draft.facts()); expanded.add(remaining);
                        result.set(i,new Draft(draft.heading(),List.copyOf(expanded))); break;
                    }
                }
            }
        }
        return result;
    }
    private static List<Draft> spread(List<Draft> drafts,int count) {
        List<Draft> result=new ArrayList<>();
        for(int i=0;i<count;i++) result.add(drafts.get((int)((long)i*(drafts.size()-1)/(count-1))));
        return result;
    }
    private static int closingScore(Draft draft) {
        String heading=draft.heading().toLowerCase(Locale.ROOT);
        int score=heading.matches(".*(xulosa|conclusion|summary|takeaway|заключ|вывод|sonuç|fazit|conclusi|хулоса).*")?30:0;
        if(heading.matches(".*(foyda|ahamiyat|benefit|importance|application|qo.llan|uses|risk|himoya|protection|recovery|prevention|примен|bedeutung|kullanım).*") ) score+=12;
        if(draft.facts().size()>=3) score+=3;
        return score;
    }

    private static String chapterTitle(List<Draft> drafts,String topic,List<String> previous,Set<String> used,String language,int index) {
        LinkedHashSet<String> headings=new LinkedHashSet<>();
        for(Draft draft:drafts) {
            String heading=label(draft.heading(),66);
            headings.add(heading);
        }
        String title=headings.stream().limit(2).reduce((a,b)->a+" / "+b).orElse(overviewLabel(language)+": "+label(topic,54));
        if(normalize(title).equals(normalize(topic))) title=overviewLabel(language)+": "+label(topic,54);
        title=label(title,92);
        if(used.contains(normalize(title)) || previous.stream().map(SlidePlanningService::normalize).anyMatch(normalize(title)::equals))
            title=label(firstClause(drafts.get(0).facts().get(0).text()),88);
        if(previous.contains(title) || used.contains(normalize(title))) title=label(title,80)+" · "+(index+1);
        return title;
    }
    private static String slideTitle(Draft draft,Set<String> used,String language,int index) {
        String title=label(draft.heading(),90);
        if(used.add(normalize(title))) return title;
        title=label(firstClause(draft.facts().get(0).text()),90);
        if(used.add(normalize(title))) return title;
        title=label(overviewLabel(language)+": "+draft.heading(),80)+" · "+(index+1);
        used.add(normalize(title)); return title;
    }
    private static String firstClause(String text) {
        return text.split("[;:–—]",2)[0].replaceAll("[.!?]+$","").strip();
    }
    private Slide slide(String title,String subtitle,List<Fact> facts,Layout layout) {
        LinkedHashMap<String,Source> sources=new LinkedHashMap<>(); facts.forEach(f->sources.putIfAbsent(f.doc().url(),source(f)));
        String query=facts.get(0).doc().imageQuery();
        return new Slide(title,subtitle,facts.stream().map(Fact::text).toList(),layout,query,List.copyOf(sources.values()));
    }
    private static Source source(Fact fact) { return new Source(fact.doc().title(),fact.doc().url()); }

    /** Complete standalone source statements, with a useful range for real academic prose. */
    static List<String> sentences(String text) {
        if(text==null) return List.of();
        String clean=text.replaceAll("\\[[^\\]]{0,80}\\]","")
                .replaceAll("(?iu)\\([^()]{0,100}(?:inglizcha|ruscha|turkcha|lotincha|англ\\.|englisch)[^()]{0,100}\\)"," ")
                .replaceAll("\\s+"," ").trim();
        BreakIterator iterator=BreakIterator.getSentenceInstance(Locale.ROOT); iterator.setText(clean);
        List<String> result=new ArrayList<>(); int start=iterator.first();
        for(int end=iterator.next();end!=BreakIterator.DONE;start=end,end=iterator.next()) {
            String sentence=clean.substring(start,end).trim().replaceAll("\\s+([,.!?])","$1");
            if(sentence.length()<45 || sentence.length()>350 || sentence.split("\\s+").length<6) continue;
            if(!sentence.matches("(?s).*[.!?][\"”»']?$")) continue;
            if(sentence.matches("(?iu)^(bu|bunga|bunday|bular|ushbu|uning|ular|ularning|u|masalan|misol uchun|bundan tashqari|shunday qilib|biroq|esingizda|it|this|these|they|their|for example|это|он|она|они|например|dies|diese|sie|esto|estos|por ejemplo|bunlar)\\b.*")) continue;
            if(sentence.matches("(?iu).*\\b(ing|engl|rus|lat|etc|al)\\.$")) continue;
            if(sentence.matches("(?iu).*(citation needed|iqtibos kerak|manba kerak|править|нужен источник|ISBN|https?://|\\{\\{|\\}\\}).*")) continue;
            result.add(sentence);
        }
        return result;
    }
    private static String terminology(String text,WebResearchService.ResearchDocument doc) {
        if(!doc.language().equals("uz") || !doc.title().toLowerCase(Locale.ROOT).contains("xavfsiz")) return text;
        return text.replaceAll("(?iu)hokimiyatlarning bo[‘’ʻʼ']linishi","vazifalarni ajratish")
                .replaceAll("(?iu)to[‘’ʻʼ']lov dasturi","tovlamachi dastur");
    }
    private static boolean similar(List<Fact> facts,String text) {
        String normalized=normalize(text); Set<String> terms=new HashSet<>(Arrays.asList(normalized.split(" ")));
        for(Fact fact:facts) {
            String other=normalize(fact.text());
            if(normalized.equals(other) || normalized.contains(other) || other.contains(normalized)) return true;
            Set<String> overlap=new HashSet<>(Arrays.asList(other.split(" "))); int size=overlap.size(); overlap.retainAll(terms);
            if(overlap.size()>=Math.min(size,terms.size())*.9) return true;
        } return false;
    }
    private static Layout layout(String heading,int facts,int index) {
        String h=heading.toLowerCase(Locale.ROOT);
        if(h.matches(".*(tarix|history|histor|истори|tarih|таърих|geschichte).*")) return Layout.TIMELINE;
        if(h.matches(".*(bosqich|jarayon|process|этап|процесс|aşama|ablauf|proceso).*")) return Layout.PROCESS;
        if(h.matches(".*(taqqos|comparison|сравнен|vergleich|comparaci|karşılaştır).*")) return Layout.COMPARISON;
        return switch(index%4) {case 0->Layout.EDITORIAL;case 1->facts>=3?Layout.CARDS:Layout.COMPARISON;case 2->Layout.SPLIT;default->Layout.CARDS;};
    }
    private static int rank(String heading,String title,String topic) {
        if(heading.equals(title)) return 0;
        String h=heading.toLowerCase(Locale.ROOT);
        if(h.matches(".*(tarix|history|histor|истори|tarih|таърих|geschichte).*")) return 1;
        if(h.matches(".*(tuzil|tushun|definition|structure|определ|структур|aufbau|definici).*")) return 2;
        if(h.matches(".*(maxfiy|yaxlit|mavjud|confidential|integrity|availability|authenticat|principle|tamoyil).*")) return 2;
        if(h.matches(".*(bosqich|jarayon|process|mexanizm|mechanism|процесс|aşama).*")) return 3;
        if(h.matches(".*(encrypt|shifrl|cryptograph|protocol|protokol|key management).*")) return 3;
        if(h.matches(".*(turlar|types|forms|виды|arten|tipos|türler).*")) return 4;
        if(h.matches(".*(tahdid|threat|phish|malware|ransom|hujum|attack).*")) return 4;
        if(h.matches(".*(qo.llan|application|uses|применен|anwendung|aplicaci|kullanım).*")) return 6;
        if(h.matches(".*(monitor|backup|segmentation|response|remote access|endpoint|physical security).*")) return 6;
        if(h.matches(".*(risk|audit|resilien|istiqbol|future|outlook|assessment).*")) return 8;
        return 5;
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]"," ").replaceAll("\\s+"," ").trim(); }
    private static String label(String text,int max) {
        String clean=text.replaceAll("\\s+"," ").trim(); if(clean.length()<=max) return clean;
        int at=clean.lastIndexOf(' ',max-1); return clean.substring(0,at<max/2?max-1:at)+"…";
    }
    private static String agendaLabel(String lang) { return switch(WebResearchService.wikiLanguage(lang)) {case "en"->"Agenda";case "ru"->"План";case "es"->"Contenido";case "de"->"Gliederung";case "tg"->"Нақша";case "tr"->"İçerik";default->"Reja";}; }
    private static String summaryLabel(String lang) { return switch(WebResearchService.wikiLanguage(lang)) {case "en"->"Key takeaways";case "ru"->"Основные выводы";case "es"->"Conclusiones clave";case "de"->"Wichtige Erkenntnisse";case "tg"->"Хулосаҳои асосӣ";case "tr"->"Temel çıkarımlar";default->"Asosiy xulosalar";}; }
    private static String overviewLabel(String lang) { return switch(WebResearchService.wikiLanguage(lang)) {case "en"->"Overview";case "ru"->"Обзор";case "es"->"Conceptos";case "de"->"Überblick";case "tg"->"Мафҳумҳо";case "tr"->"Genel bakış";default->"Asosiy tushunchalar";}; }
    private static String sourceLabel(String lang) { return switch(WebResearchService.wikiLanguage(lang)) {case "en"->"Sources";case "ru"->"Источники";case "es"->"Fuentes";case "de"->"Quellen";case "tg"->"Сарчашмаҳо";case "tr"->"Kaynaklar";default->"Foydalanilgan manbalar";}; }
}
