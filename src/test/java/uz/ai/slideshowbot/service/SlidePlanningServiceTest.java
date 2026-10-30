package uz.ai.slideshowbot.service;

import org.junit.jupiter.api.Test;
import uz.ai.slideshowbot.slides.SlideDeck;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SlidePlanningServiceTest {
    private WebResearchService.ResearchDocument document() {
        String[][] facts = {
                {"Information security", "Confidentiality restricts access to information to authorized people and systems.", "Integrity protects information from unauthorized modification during storage and transfer.", "Availability allows authorized users to access information when it is needed."},
                {"Authentication", "Authentication checks an identity before a system permits access to protected resources.", "A second authentication factor reduces reliance on a single password for account access.", "Access permissions should reflect the work each user actually needs to perform."},
                {"Encryption", "Encryption converts readable information into a form that requires a key to decipher.", "Transport encryption protects the contents of communication while data crosses a network.", "Secure key management is necessary to maintain the protection that encryption provides."},
                {"Phishing", "Phishing messages impersonate trusted senders to obtain credentials or other sensitive data.", "A suspicious message can direct the recipient to a counterfeit sign-in website.", "Independent verification of a request can help reveal an attempted impersonation."},
                {"Malware", "Malware includes software designed to disrupt systems or access information without permission.", "Ransomware can encrypt files and demand payment to restore access to the affected data.", "Software updates repair known security weaknesses that malicious programs can exploit."},
                {"Network monitoring", "Network monitoring helps operators identify unusual traffic and investigate suspicious events.", "Log records preserve details that analysts can review after a security incident occurs.", "Alerts require assessment because unusual activity does not always indicate a successful attack."},
                {"Backups", "Backup copies can help an organization restore important information after files are lost.", "Restoration exercises test whether a backup can support the recovery of a working system.", "A disconnected backup can reduce exposure to attacks that spread through an active network."},
                {"Segmentation", "Network segmentation separates systems into areas with different access requirements.", "Traffic filtering can limit which connections are permitted between network segments.", "An inventory helps administrators understand which devices belong on their networks."},
                {"Incident response", "An incident response plan defines responsibilities before a security emergency happens.", "Containment limits the spread of an incident while a response team investigates its effects.", "Recovery restores affected services and verifies that the required protections are in place."},
                {"Risk management", "Risk assessment considers potential threats and the consequences for an organization.", "Security controls should address the systems and information that the organization depends on.", "Periodic reviews help teams adjust their protections when their operating environment changes."},
                {"Secure development", "Threat modeling examines how an attacker could misuse an application before deployment.", "Input validation rejects data that does not meet the requirements of an application interface.", "Code review allows another developer to examine a proposed change before it is released."},
                {"Software supply chains", "Software dependencies introduce third-party components into the products that teams maintain.", "A software bill of materials records the components included in a particular application release.", "Package signatures can help verify the origin and integrity of downloaded software artifacts."},
                {"Physical security", "Locked equipment rooms restrict physical access to servers and communication infrastructure.", "Visitor records help organizations track access to facilities that contain protected equipment.", "Environmental monitoring can reveal excessive heat or water leaks in a computing facility."},
                {"Wireless networks", "Wireless communication broadcasts radio signals that can extend beyond the walls of a building.", "WPA3 provides a security protocol for protecting access to compatible wireless networks.", "A guest wireless network separates visitor devices from internal organizational resources."},
                {"Public key infrastructure", "A digital certificate associates a public key with the identity described by its issuer.", "Certificate authorities sign certificates that clients can check against their trusted issuers.", "Certificate revocation mechanisms indicate that a previously issued credential should no longer be trusted."},
                {"Security testing", "Penetration testing examines an authorized system for weaknesses that could be exploited.", "Vulnerability scanners identify known patterns that can indicate a security weakness.", "A testing scope defines the systems and methods that an assessment is allowed to include."},
                {"Data retention", "A retention schedule establishes how long categories of organizational records should be kept.", "Secure disposal prevents discarded storage devices from exposing recoverable confidential records.", "Data classification distinguishes information according to its sensitivity and handling requirements."},
                {"Remote access", "A virtual private network can protect communications between endpoints across an untrusted network.", "Remote desktop services provide interactive access to a computer through a network connection.", "Session expiration limits how long an authenticated connection can remain active without renewal."},
                {"Cloud responsibilities", "Cloud service agreements divide operational responsibilities between providers and customers.", "Configuration mistakes can expose a cloud storage resource beyond its intended audience.", "Separate service identities allow applications to receive permissions without sharing a personal account."},
                {"Endpoint protection", "Disk encryption protects stored information when a powered-off device is lost or stolen.", "Application allowlisting limits execution to software that administrators have explicitly permitted.", "Device management policies can enforce configuration requirements on an organization’s computers."},
                {"Audit and assurance", "Independent audits assess whether documented controls are implemented and operating as intended.", "Evidence retention supports the examination of security activities during an organizational review.", "A control owner is responsible for maintaining a specific protection within an organization."},
                {"Resilience", "Redundant network links can preserve connectivity when a single connection becomes unavailable.", "Business continuity planning identifies the activities required to maintain essential operations.", "Recovery objectives describe the acceptable interruption and data loss for a particular service."}
        };
        List<WebResearchService.ResearchSection> sections = new ArrayList<>();
        for (String[] fact : facts) sections.add(new WebResearchService.ResearchSection(fact[0], String.join(" ", Arrays.copyOfRange(fact, 1, fact.length))));
        return new WebResearchService.ResearchDocument("Information security", "https://en.wikipedia.org/wiki/Information_security", "en", "Information security", sections);
    }

    private WebResearchService research(List<WebResearchService.ResearchDocument> documents) throws Exception {
        WebResearchService research = mock(WebResearchService.class);
        when(research.research(anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(new WebResearchService.ResearchBundle("Information security", "ENG", documents));
        return research;
    }

    @Test void createsExactNonRepeatingCitedPlanWithNoAiCall() throws Exception {
        GptTextGeneratorService ai = mock(GptTextGeneratorService.class);
        SlideDeck deck = new SlidePlanningService(research(List.of(document())), ai)
                .createDeck("Information security", 10, "ENG", 5000);
        SlideFlowSingleService.validateDeck(deck, 13);
        assertEquals(13, deck.slides().size());
        assertEquals(SlideDeck.Layout.COVER, deck.slides().get(0).layout());
        assertEquals(SlideDeck.Layout.AGENDA, deck.slides().get(1).layout());
        assertEquals(4, deck.slides().get(1).bullets().size());
        assertEquals(SlideDeck.Layout.SUMMARY, deck.slides().get(12).layout());
        assertTrue(deck.slides().stream().skip(2).allMatch(s -> !s.sources().isEmpty()));
        assertEquals(deck.slides().stream().skip(2).flatMap(s -> s.bullets().stream()).count(),
                deck.slides().stream().skip(2).flatMap(s -> s.bullets().stream()).distinct().count());
        verifyNoInteractions(ai);
    }

    @Test void insufficientEvidenceNeverBecomesFiller() throws Exception {
        assertThrows(IOException.class, () -> new SlidePlanningService(research(List.of()), null).createDeck("Information security", 10, "ENG", 5000));
    }

    @Test void everySelectedCountHasSequentialSectionsAndSubstantialUniqueBody() throws Exception {
        GptTextGeneratorService ai = mock(GptTextGeneratorService.class);
        SlidePlanningService planner = new SlidePlanningService(research(List.of(document())), ai);
        for(int selected:List.of(10,15,20)) {
            SlideDeck deck=planner.createDeck("Information security", selected,"ENG",5000);
            SlideFlowSingleService.validateDeck(deck,selected+3);
            List<String> agenda=deck.slides().get(1).bullets();
            int sections=selected==10?4:selected==15?5:6;
            assertEquals(sections,agenda.size());
            assertEquals(agenda.get(0),deck.slides().get(2).title());
            int offset=2;
            for(int section=0;section<sections;section++) {
                int allocation=selected/sections+(section<selected%sections?1:0);
                for(int i=0;i<allocation;i++) {
                    var slide=deck.slides().get(offset++);
                    assertEquals(agenda.get(section),slide.subtitle());
                    assertTrue(slide.bullets().size()>=2,"A body slide must contain more than a single fact");
                    assertTrue(slide.bullets().stream().mapToInt(String::length).sum()>=120);
                }
            }
            assertEquals(selected+2,offset);
            assertEquals(SlideDeck.Layout.SUMMARY,deck.slides().get(offset).layout());
        }
        verifyNoInteractions(ai);
    }

    @Test void foreignEvidenceIsTranslatedAndItsOriginalSourceIsKept() throws Exception {
        FreeTranslationService translator=mock(FreeTranslationService.class);
        when(translator.translate(anyList(),eq("en"),eq("ru"),anyLong())).thenAnswer(invocation -> {
            List<String> values=invocation.getArgument(0);
            return values.stream().map(this::cyrillicTestTranslation).toList();
        });
        var deck=new SlidePlanningService(research(List.of(document())),null,translator)
                .createDeck("Информационная безопасность",10,"RU",5000);
        assertEquals(13,deck.slides().size());
        assertTrue(deck.slides().get(2).bullets().stream().allMatch(s->s.matches("(?s).*[А-Яа-я].*")));
        assertTrue(deck.slides().get(2).sources().get(0).url().startsWith("https://en.wikipedia.org/"));
        verify(translator).translate(anyList(),eq("en"),eq("ru"),anyLong());
    }

    @Test void failedTranslationNeverDeliversForeignTextAsSelectedLanguage() throws Exception {
        FreeTranslationService translator=mock(FreeTranslationService.class);
        when(translator.translate(anyList(),anyString(),anyString(),anyLong())).thenThrow(new IOException("Unavailable"));
        assertThrows(IOException.class,()->new SlidePlanningService(research(List.of(document())),null,translator)
                .createDeck("Информационная безопасность",10,"RU",5000));
    }

    /** A deterministic translator stub; the provider's response validation is tested separately. */
    private String cyrillicTestTranslation(String text) {
        String latin="abcdefghijklmnopqrstuvwxyz",cyrillic="абцдефгхийклмнопкрстуввхыз";
        StringBuilder out=new StringBuilder();
        for(char c:text.toLowerCase(Locale.ROOT).toCharArray()) {
            int at=latin.indexOf(c); out.append(at<0?c:cyrillic.charAt(at));
        }
        return out.toString();
    }

    @Test void extractionKeepsCompleteStandaloneStatements() {
        List<String> result = SlidePlanningService.sentences("This depends on the previous sentence and provides no standalone explanation. "
                + "Encryption protects information while it travels across untrusted network connections. "
                + "An incomplete phrase without final punctuation");
        assertEquals(List.of("Encryption protects information while it travels across untrusted network connections."), result);
    }
}
