package uz.ai.slideshowbot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class FreeTranslationServiceTest {
    @Test void batchBoundariesMustRemainCompleteAndOrdered() {
        assertEquals(List.of("Birinchi gap.","Ikkinchi gap."),FreeTranslationService.parseBatch(
                "[[SB0000]] Birinchi gap.\n[[SB0001]] Ikkinchi gap.",2));
        assertEquals(List.of(),FreeTranslationService.parseBatch("[[SB0001]] Reordered.",1));
        assertEquals(List.of(),FreeTranslationService.parseBatch("[[SB0000]] Missing one excerpt.",2));
        assertEquals(List.of(),FreeTranslationService.parseBatch("Unexpected prose [[SB0000]] Text.",1));
    }

    @Test void translationMustPreserveEveryNumericFact() {
        assertTrue(FreeTranslationService.preservesNumbers("In 2025 the rate was 3.5 percent.","2025 yilda ko‘rsatkich 3,5 foiz edi."));
        assertFalse(FreeTranslationService.preservesNumbers("In 2025 the rate was 3.5 percent.","2026 yilda ko‘rsatkich 3,5 foiz edi."));
        assertFalse(FreeTranslationService.preservesNumbers("There are 2 stages.","Uch bosqich bor."));
    }

    @Test void validAttributableExcerptsAreCachedWithoutAnotherNetworkCall() throws Exception {
        AtomicInteger calls=new AtomicInteger();
        ObjectMapper json=new ObjectMapper();
        OkHttpClient client=new OkHttpClient.Builder().addInterceptor(chain->{
            calls.incrementAndGet();
            assertEquals("translate.googleapis.com",chain.request().url().host());
            assertEquals("en",chain.request().url().queryParameter("sl"));
            assertEquals("uz",chain.request().url().queryParameter("tl"));
            assertNull(chain.request().header("Authorization"));
            String original=chain.request().url().queryParameter("q");
            String translated=original.replace("Plants have 2 reaction stages.","O‘simliklarda 2 reaksiya bosqichi bor.");
            String response=json.writeValueAsString(List.of(List.of(List.of(translated,original)),"en"));
            return new Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body(ResponseBody.create(response,MediaType.get("application/json"))).build();
        }).build();
        FreeTranslationService service=new FreeTranslationService(client);
        List<String> expected=List.of("O‘simliklarda 2 reaksiya bosqichi bor.");
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        assertEquals(expected,service.translate(List.of("Plants have 2 reaction stages."),"en","uz",deadline));
        assertEquals(expected,service.translate(List.of("Plants have 2 reaction stages."),"en","uz",deadline));
        assertEquals(1,calls.get());
    }

    @Test void quotaFailuresAndChangedFactsAreNeverAcceptedAsTranslations() {
        OkHttpClient limited=new OkHttpClient.Builder().addInterceptor(chain->new Response.Builder().request(chain.request())
                .protocol(Protocol.HTTP_1_1).code(429).message("Too Many Requests")
                .body(ResponseBody.create("Unavailable",MediaType.get("text/plain"))).build()).build();
        var service=new FreeTranslationService(limited);
        assertThrows(IOException.class,()->service.translate(List.of("There are 2 stages."),"en","uz",
                System.nanoTime()+TimeUnit.SECONDS.toNanos(5)));
        assertThrows(IOException.class,()->service.translate(List.of("There are 2 stages."),"en","uz",System.nanoTime()-1));
    }
}
