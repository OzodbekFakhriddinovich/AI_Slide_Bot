package uz.ai.slideshowbot.service;

import org.junit.jupiter.api.Test;
import okhttp3.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class WebResearchServiceTest {
    @Test void longCompoundTopicStartsWithItsActualSubject() {
        var queries = WebResearchService.queryVariants("Tarmoq xavfsizligi va tarmoq hujumlarining asosiy turlari");
        assertEquals("Axborot xavfsizligi", queries.get(0));
        assertTrue(queries.contains("Tarmoq xavfsizligi"));
        assertFalse(queries.contains("Tarmoq"));
    }

    @Test void referencesAndNavigationDoNotBecomePresentationContent() {
        var sections = WebResearchService.sections("Security", "A substantive introduction containing enough original explanatory text to form a useful section.\n\n"
                + "== Principles ==\nConfidentiality and integrity protect access to information and its accuracy during legitimate use.\n\n"
                + "== References ==\nA bibliography and external links belong in citations rather than in explanatory slide text.");
        assertEquals(2, sections.size());
        assertEquals("Principles", sections.get(1).heading());
    }

    @Test void allSevenSelectedLanguagesResolveExplicitly() {
        assertEquals("uz", WebResearchService.wikiLanguage("UZ"));
        assertEquals("en", WebResearchService.wikiLanguage("ENG"));
        assertEquals("ru", WebResearchService.wikiLanguage("RU"));
        assertEquals("es", WebResearchService.wikiLanguage("ES"));
        assertEquals("de", WebResearchService.wikiLanguage("DE"));
        assertEquals("tg", WebResearchService.wikiLanguage("TOJ"));
        assertEquals("tr", WebResearchService.wikiLanguage("TUR"));
        assertThrows(IllegalArgumentException.class, () -> WebResearchService.wikiLanguage("XX"));
    }

    @Test void ordinaryTopicsAreNotRejectedForOneMissingQueryModifier() {
        assertTrue(WebResearchService.relevant("Global iqlim o'zgarishi oqibatlari",
                "Iqlim o'zgarishi — sayyoradagi haroratning uzoq muddatli o'zgarishi."));
        assertFalse(WebResearchService.relevant("Fotosintez jarayoni", "A musical instrument is used to perform a melody."));
        assertTrue(WebResearchService.queryVariants("Fotosintez jarayoni va o‘simliklarda ahamiyati").contains("Fotosintez jarayoni"));
    }

    @Test void repeatedResearchUsesBoundedPublicResponseCache() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain->{
            calls.incrementAndGet();
            String payload=chain.request().url().queryParameter("list")!=null
                    ?"{\"query\":{\"search\":[{\"title\":\"Photosynthesis\",\"snippet\":\"Photosynthesis converts sunlight into chemical energy.\"}]}}"
                    :new ObjectMapper().writeValueAsString(Map.of("query",Map.of("pages",List.of(Map.of(
                            "title","Photosynthesis","fullurl","https://en.wikipedia.org/wiki/Photosynthesis",
                            "extract",IntStream.range(0,48).mapToObj(i->"Photosynthesis example "+i+" describes how plants convert light energy into chemical energy for growth. ")
                                    .reduce("",String::concat))))));
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(payload,MediaType.get("application/json"))).build();
        }).build();
        WebResearchService service=new WebResearchService(client);
        var first=service.research("Photosynthesis","ENG",10,3000);
        int firstCalls=calls.get();
        var second=service.research("Photosynthesis","ENG",10,3000);
        assertFalse(first.documents().isEmpty());
        assertEquals(first.documents(),second.documents());
        assertEquals(firstCalls,calls.get());
    }
}
