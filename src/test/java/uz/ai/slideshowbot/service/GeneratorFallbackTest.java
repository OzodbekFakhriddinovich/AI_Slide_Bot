package uz.ai.slideshowbot.service;

import org.junit.jupiter.api.Test;
import uz.ai.slideshowbot.slides.SlideTheme;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GeneratorFallbackTest {
    @Test void quizFallbackUsesTopicSourcesInUzbekRussianAndEnglish() throws Exception {
        for(String language:List.of("UZ","RU","ENG")) {
            GptTextGeneratorService model=mock(GptTextGeneratorService.class);
            when(model.callGemini(anyString(),eq(false))).thenThrow(new IllegalStateException("model offline"));
            WebResearchService research=mock(WebResearchService.class);
            when(research.research(eq("Source-backed topic"),eq(language),anyInt(),anyInt())).thenReturn(bundle(language,12));
            TestService service=new TestService(); set(service,"gptTextGeneratorService",model); set(service,"webResearchService",research);
            Method generate=TestService.class.getDeclaredMethod("generateTestContent",String.class,int.class,String.class,String.class);
            generate.setAccessible(true);
            String content=(String)generate.invoke(service,"Source-backed topic",5,"MEDIUM",language);
            assertTrue(content.contains(language.equals("RU")?"Какое утверждение":language.equals("ENG")?"Which statement":"bo‘limida"));
            assertEquals(5,content.substring(0,content.indexOf("--- ")).lines().filter(line->line.matches("^\\d+\\. .+$")).count());
            assertTrue(content.contains("CORRECT ANSWERS")||content.contains("ПРАВИЛЬНЫЕ ОТВЕТЫ")||content.contains("TO'G'RI JAVOBLAR"));
        }
    }

    @Test void referatFallbackBuildsAllChaptersFromLanguageMatchedSources() throws Exception {
        GptTextGeneratorService model=mock(GptTextGeneratorService.class);
        when(model.callGemini(anyString(),eq(false))).thenThrow(new IllegalStateException("model offline"));
        WebResearchService research=mock(WebResearchService.class);
        when(research.research(eq("Source-backed topic"),eq("ENG"),anyInt(),anyInt())).thenReturn(bundle("ENG",15));
        ReferatDocxService service=new ReferatDocxService(); set(service,"gptService",model); set(service,"webResearchService",research);
        Method names=ReferatDocxService.class.getDeclaredMethod("buildBobNames",int.class,String.class); names.setAccessible(true);
        String[][] chapters=(String[][])names.invoke(service,3,"ENG");
        Method fetch=ReferatDocxService.class.getDeclaredMethod("fetchContent",String.class,int.class,int.class,String[][].class,String.class); fetch.setAccessible(true);
        @SuppressWarnings("unchecked") var result=(java.util.Map<String,Object>)fetch.invoke(service,"Source-backed topic",15,3,chapters,"ENG");
        assertEquals(3,((List<?>)result.get("chapters")).size());
        assertTrue(result.get("introduction").toString().toLowerCase().contains("evidence"),result.get("introduction").toString());
        assertTrue(((List<?>)result.get("references")).get(0).toString().contains("wikipedia.org"));
    }

    private static WebResearchService.ResearchBundle bundle(String language,int count) {
        String wiki=WebResearchService.wikiLanguage(language);
        List<WebResearchService.ResearchSection> sections=new ArrayList<>();
        String sentence=switch(language) {
            case "RU" -> "В этом разделе источник приводит проверяемое описание темы, объясняет причины и подтверждает выводы конкретными сведениями.";
            case "ENG" -> "This source section explains the topic, identifies its causes, and supports its conclusions with verifiable evidence.";
            default -> "Ushbu manba bo‘limi mavzuni tushuntiradi, sabablarni ko‘rsatadi va xulosalarni tekshiriladigan dalillar bilan asoslaydi.";
        };
        for(int i=0;i<count;i++) {
            StringBuilder text=new StringBuilder();
            for(int n=0;n<28;n++) text.append(sentence).append(" Example ").append(i).append('-').append(n).append(" is included as distinct supporting evidence. ");
            sections.add(new WebResearchService.ResearchSection("Source section "+i,text.toString()));
        }
        var doc=new WebResearchService.ResearchDocument("Source-backed topic","https://"+wiki+".wikipedia.org/wiki/Source_backed_topic",wiki,"Source-backed topic",sections);
        return new WebResearchService.ResearchBundle("Source-backed topic",language,List.of(doc));
    }

    private static void set(Object target,String name,Object value) throws Exception {
        Field field=target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
}
