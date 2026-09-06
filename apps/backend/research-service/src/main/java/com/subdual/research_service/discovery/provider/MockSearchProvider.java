package com.subdual.research_service.discovery.provider;

import com.subdual.research_service.common.exception.ExternalServiceException;
import com.subdual.research_service.research.model.DiscoveredSource;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

@Slf4j
public class MockSearchProvider implements SearchProvider {

    private static final int DEFAULT_MAX_RESULTS = 5;

    @Override
    public List<DiscoveredSource> search(String query, int maxResults) {
        log.info("MockSearchProvider executing deterministic mock discovery for query: '{}' (maxResults: {})", query, maxResults);

        if (isQueryBlank(query)) return List.of();
        
        String lowerQuery = query.toLowerCase(Locale.ROOT);

        checkSimulationHooks(lowerQuery);

        if (isZeroResultQuery(lowerQuery)) return List.of();

        List<DiscoveredSource> candidates = generateCandidates(query, lowerQuery);
        return applyLimit(candidates, maxResults);
    }

    @Override
    public String providerName() {
        return "mock";
    }

    private boolean isQueryBlank(String query) {
        return query == null || query.isBlank();
    }

    private void checkSimulationHooks(String lowerQuery) {
        if (lowerQuery.contains("simulate-failure") || lowerQuery.contains("provider-error") || lowerQuery.contains("simulate-error")) {
            throw new ExternalServiceException("Upstream search provider returned 502 Bad Gateway");
        }
        if (lowerQuery.contains("simulate-timeout")) {
            throw new ExternalServiceException("Search discovery timed out after 4000ms", new TimeoutException("Connection timed out"));
        }
    }

    private boolean isZeroResultQuery(String lowerQuery) {
        return lowerQuery.contains("empty")
                || lowerQuery.contains("obscure")
                || lowerQuery.contains("non-existent")
                || lowerQuery.contains("zero-results");
    }

    private List<DiscoveredSource> generateCandidates(String rawQuery, String lowerQuery) {
        Instant now = Instant.now();

        if (lowerQuery.contains("spring-boot") || lowerQuery.contains("spring-projects")) {
            return mockSpringBootSources(now);
        }
        if (lowerQuery.contains("linux") || lowerQuery.contains("torvalds")) {
            return mockLinuxSources(now);
        }
        if (lowerQuery.contains("acme")) {
            return mockAcmeSources(now);
        }
        if (lowerQuery.contains("jane-doe") || lowerQuery.contains("dr-jane-doe")) {
            return mockJaneDoeSources(now);
        }
        if (lowerQuery.contains("parv-agrawal") || lowerQuery.contains("parv agrawal")) {
            return mockParvAgrawalSources(lowerQuery, now);
        }
        if (lowerQuery.contains("alex-rivera") || lowerQuery.contains("alex rivera")) {
            return mockAlexRiveraSources(now);
        }
        if (lowerQuery.contains("sarah-chen") || lowerQuery.contains("sarah chen")) {
            return mockSarahChenSources(now);
        }
        if (lowerQuery.contains("marcus-vance") || lowerQuery.contains("marcus vance")) {
            return mockMarcusVanceSources(now);
        }
        if (lowerQuery.contains("saloni-sharma") || lowerQuery.contains("saloni sharma") || lowerQuery.contains("saloni")) {
            return mockSaloniSharmaSources(lowerQuery, now);
        }
        if (lowerQuery.contains("monika") || lowerQuery.contains("monika-verma")) {
            return mockMonikaSources(lowerQuery, now);
        }
        if (lowerQuery.contains("shyam") || lowerQuery.contains("shyam-sundar")) {
            return mockShyamSources(lowerQuery, now);
        }
        if (lowerQuery.contains("linkedin") || lowerQuery.contains("example")) {
            return mockLinkedInSources(now);
        }

        return mockFallbackSources(rawQuery, lowerQuery, now);
    }

    private List<DiscoveredSource> mockParvAgrawalSources(String lowerQuery, Instant now) {
        if (lowerQuery.contains("education") || lowerQuery.contains("college") || lowerQuery.contains("university")) {
            return List.of(
                    new DiscoveredSource(
                            "https://mnit.ac.in/academics/parv-agrawal",
                            "[MOCK] Parv Agrawal - Academic Profile - MNIT Jaipur",
                            "OFFICIAL_WEBSITE",
                            now,
                            0.95,
                            "Parv Agrawal is a student at MNIT Jaipur. Education: B.Tech in Chemical Engineering. Graduated from MNIT Jaipur."
                    )
            );
        }
        return List.of(
                new DiscoveredSource(
                        "https://www.linkedin.com/in/parv-agrawal-170174308",
                        "[MOCK] Parv Agrawal - MNIT Jaipur | Chemical Engineering | LinkedIn",
                        "SOCIAL_PROFILE",
                        now,
                        1.00,
                        "Parv Agrawal is an undergraduate in Chemical Engineering at Malaviya National Institute of Technology Jaipur (MNIT Jaipur)."
                ),
                new DiscoveredSource(
                        "https://mnit.ac.in/students/parv-agrawal",
                        "[MOCK] Parv Agrawal - Department of Chemical Engineering - MNIT Jaipur",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.95,
                        "Parv Agrawal is a student at Malaviya National Institute of Technology Jaipur. Department: Chemical Engineering. Location: Jaipur, India."
                ),
                new DiscoveredSource(
                        "https://deshaw.com/people/parv-agrawal",
                        "[MOCK] Parv Agrawal - Quantitative Analysis - D. E. Shaw",
                        "SEARCH_RESULT",
                        now,
                        0.70,
                        "Parv Agrawal is a Quantitative Analyst at D. E. Shaw in Hyderabad. Alumnus of IIT Delhi in Computer Science."
                ),
                new DiscoveredSource(
                        "https://iitd.ac.in/alumni/parv-agrawal",
                        "[MOCK] Parv Agrawal - IIT Delhi Alumni Network",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.65,
                        "Parv Agrawal graduated from Indian Institute of Technology Delhi (IIT Delhi) in Computer Science."
                )
        );
    }

    private List<DiscoveredSource> mockJaneDoeSources(Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://www.linkedin.com/in/jane-doe",
                        "[MOCK] Jane Doe - Principal Infrastructure Engineer | LinkedIn",
                        "SOCIAL_PROFILE",
                        now,
                        1.00,
                        "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems in San Francisco, CA."
                ),
                new DiscoveredSource(
                        "https://cloudscale.example.com/team/jane-doe",
                        "[MOCK] Jane Doe - Engineering Leadership - CloudScale Systems",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.95,
                        "Jane Doe is a Principal Infrastructure Engineer at CloudScale Systems, based in San Francisco, CA. Graduated from MIT. Connect: linkedin.com/in/jane-doe"
                ),
                new DiscoveredSource(
                        "https://dentistry.example.com/dr-jane-doe",
                        "[MOCK] Dr. Jane Doe, DDS - Family Dentistry & Dental Clinic",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.70,
                        "Dr. Jane Doe, DDS is a family dentist at Downtown Dental Clinic in Chicago, IL specializing in pediatric dentistry."
                ),
                new DiscoveredSource(
                        "https://imdb.example.com/name/jane-doe",
                        "[MOCK] Jane Doe - Actress Filmography and Movie Credits",
                        "SEARCH_RESULT",
                        now,
                        0.65,
                        "Jane Doe is a theater and film actress known for dramatic roles in Los Angeles, CA."
                )
        );
    }

    private List<DiscoveredSource> mockSpringBootSources(Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://github.com/spring-projects/spring-boot",
                        "[MOCK] spring-projects/spring-boot: Spring Boot helps you create Spring-powered applications",
                        "GITHUB",
                        now,
                        1.00,
                        "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications."
                ),
                new DiscoveredSource(
                        "https://spring.io/projects/spring-boot",
                        "[MOCK] Spring Boot Overview - official website",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.95,
                        "Spring Boot provides a comprehensive set of features for modern web apps."
                ),
                new DiscoveredSource(
                        "https://docs.spring.io/spring-boot",
                        "[MOCK] Spring Boot Reference Documentation",
                        "DOCUMENTATION",
                        now,
                        0.90,
                        "Spring Boot reference guide and documentation."
                )
        );
    }

    private List<DiscoveredSource> mockLinuxSources(Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://github.com/torvalds",
                        "[MOCK] torvalds (Linus Torvalds) · GitHub",
                        "GITHUB",
                        now,
                        1.00,
                        "Linus Torvalds is the creator of Linux kernel and Git revision control system."
                ),
                new DiscoveredSource(
                        "https://en.wikipedia.org/wiki/Linus_Torvalds",
                        "[MOCK] Linus Torvalds - Wikipedia Biography",
                        "WIKIPEDIA",
                        now,
                        0.95,
                        "Linus Benedict Torvalds is a Finnish-American software engineer who is the principal developer of Linux."
                )
        );
    }

    private List<DiscoveredSource> mockAcmeSources(Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://acme.org",
                        "[MOCK] Acme Corporation - Global Innovation",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.95,
                        "Acme Corporation produces leading technology products and software solutions."
                ),
                new DiscoveredSource(
                        "https://github.com/acme",
                        "[MOCK] Acme Corp GitHub Organization",
                        "GITHUB",
                        now,
                        0.90,
                        "Open-source projects and developer tools by Acme Corporation."
                )
        );
    }

    private List<DiscoveredSource> mockLinkedInSources(Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://www.example.org",
                        "[MOCK] Example Organization - Official Site",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.95,
                        "Official website of Example Organization."
                ),
                new DiscoveredSource(
                        "https://www.linkedin.com/company/example",
                        "[MOCK] Example Organization | LinkedIn",
                        "SOCIAL_PROFILE",
                        now,
                        0.90,
                        "LinkedIn profile and corporate directory for Example Organization."
                ),
                new DiscoveredSource(
                        "https://www.google.com/search?q=example+organization",
                        "[MOCK] Google Search - Example Organization",
                        "SEARCH_RESULT",
                        now,
                        0.70,
                        "Search index results for Example Organization."
                )
        );
    }

    private List<DiscoveredSource> mockAlexRiveraSources(Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://cloudscale.io/team/alex-rivera",
                        "[MOCK] Alex Rivera - Senior Technical Recruiter - CloudScale Systems",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.98,
                        "Alex Rivera is Senior Technical Recruiter at CloudScale Systems in San Francisco, CA. Actively recruiting backend engineers and Java/Spring Boot interns."
                ),
                new DiscoveredSource(
                        "https://www.linkedin.com/in/alex-rivera",
                        "[MOCK] Alex Rivera - Senior Technical Recruiter | LinkedIn",
                        "SOCIAL_PROFILE",
                        now,
                        0.95,
                        "Senior Technical Recruiter at CloudScale Systems. Activity: [AUTHORED] We are actively hiring backend interns and junior engineers for our Spring Boot platform at CloudScale Systems! DM me with your resume."
                )
        );
    }

    private List<DiscoveredSource> mockSarahChenSources(Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://fintechglobal.com/engineering/sarah-chen",
                        "[MOCK] Dr. Sarah Chen - Staff Backend Architect - FinTech Global",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.98,
                        "Dr. Sarah Chen is Staff Backend Architect at FinTech Global. Education: Ph.D. in Computer Science from MIT. Core expertise: Java, Spring Boot, Microservices, Kubernetes."
                ),
                new DiscoveredSource(
                        "https://www.linkedin.com/in/sarah-chen",
                        "[MOCK] Dr. Sarah Chen - Staff Backend Architect | LinkedIn",
                        "SOCIAL_PROFILE",
                        now,
                        0.95,
                        "Staff Backend Architect at FinTech Global. Activity: [AUTHORED] Designing High-Throughput Spring Boot 3 Microservices in Production: key architectural patterns and resilience strategies."
                )
        );
    }

    private List<DiscoveredSource> mockMarcusVanceSources(Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://devscale.ai/leadership/marcus-vance",
                        "[MOCK] Marcus Vance - Founder & CEO - DevScale AI",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.98,
                        "Marcus Vance is Founder and CEO at DevScale AI in Austin, TX. Formerly Engineering Director. Education: B.S. in Computer Science from Stanford University."
                ),
                new DiscoveredSource(
                        "https://www.linkedin.com/in/marcus-vance",
                        "[MOCK] Marcus Vance - Founder & CEO | LinkedIn",
                        "SOCIAL_PROFILE",
                        now,
                        0.95,
                        "Founder & CEO at DevScale AI. Activity: [AUTHORED] Building scalable engineering teams and scaling our distributed data infrastructure. Always open to mentorship and hiring talented engineers."
                )
        );
    }

    private List<DiscoveredSource> mockSaloniSharmaSources(String lowerQuery, Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://www.linkedin.com/in/saloni-sharma-872379180",
                        "[MOCK] Saloni Sharma - Senior Java Backend Engineer | TechScale Systems | LinkedIn",
                        "SOCIAL_PROFILE",
                        now,
                        1.00,
                        "Saloni Sharma is a Senior Java Backend Engineer at TechScale Systems. Specializing in Java 17+, Spring Boot, Microservices, and Distributed Systems. Activity: [AUTHORED] We are actively hiring Java / Spring Boot interns and backend engineers at TechScale Systems! Looking for candidates with strong core Java, REST APIs, and database fundamentals. Reach out with your CV."
                ),
                new DiscoveredSource(
                        "https://github.com/salonisharma/spring-boot-microservices-template",
                        "[MOCK] salonisharma/spring-boot-microservices-template · GitHub",
                        "GITHUB",
                        now,
                        0.95,
                        "Production-ready template for Spring Boot 3 microservices by Saloni Sharma. Includes Spring Cloud, Docker, Kafka, PostgreSQL, and JUnit 5 tests."
                ),
                new DiscoveredSource(
                        "https://medium.com/@salonisharma/mastering-spring-boot-virtual-threads",
                        "[MOCK] Mastering Spring Boot 3 with Java 21 Virtual Threads - Saloni Sharma",
                        "ARTICLE",
                        now,
                        0.92,
                        "Article by Saloni Sharma discussing high-throughput backend architecture using Spring Boot 3, Project Loom virtual threads, and reactive streams."
                ),
                new DiscoveredSource(
                        "https://techscale.io/team/saloni-sharma",
                        "[MOCK] Saloni Sharma - Backend Engineering Team - TechScale Systems",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.90,
                        "Saloni Sharma is a Senior Java Backend Engineer at TechScale Systems in Bengaluru, India. Education: B.Tech in Computer Science."
                )
        );
    }

    private List<DiscoveredSource> mockMonikaSources(String lowerQuery, Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://www.linkedin.com/in/monika-verma-lead",
                        "[MOCK] Monika Verma - Engineering Lead & Hiring Manager | Cloud Platforms | LinkedIn",
                        "SOCIAL_PROFILE",
                        now,
                        1.00,
                        "Engineering Lead & Hiring Manager. Activity: [AUTHORED] Hiring Java/Spring Boot developers and summer software engineering interns. Open to mentoring students passionate about backend systems."
                ),
                new DiscoveredSource(
                        "https://github.com/monikaverma",
                        "[MOCK] monikaverma (Monika Verma) · GitHub",
                        "GITHUB",
                        now,
                        0.95,
                        "Monika Verma GitHub profile. Repositories: distributed-task-scheduler, spring-boot-security-jwt. Java, Spring Cloud, Docker, Kubernetes."
                ),
                new DiscoveredSource(
                        "https://cloudplatforms.io/team/monika-verma",
                        "[MOCK] Monika Verma - Engineering Leadership - Cloud Platforms",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.90,
                        "Monika Verma is an Engineering Lead at Cloud Platforms. Focus areas include distributed architectures, Spring Boot, and team mentorship."
                )
        );
    }

    private List<DiscoveredSource> mockShyamSources(String lowerQuery, Instant now) {
        return List.of(
                new DiscoveredSource(
                        "https://www.linkedin.com/in/shyam-sundar-recruiter",
                        "[MOCK] Shyam Sundar - Senior Technical Recruiter & Talent Partner | LinkedIn",
                        "SOCIAL_PROFILE",
                        now,
                        1.00,
                        "Senior Technical Recruiter. Activity: [AUTHORED] Actively recruiting for Java, Spring Boot, and Backend engineering roles (including internships & junior engineers). DM me your resume!"
                ),
                new DiscoveredSource(
                        "https://talentcraft.io/team/shyam-sundar",
                        "[MOCK] Shyam Sundar - Talent Partner - TalentCraft",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.95,
                        "Shyam Sundar is Senior Technical Talent Acquisition Specialist at TalentCraft. Hiring software engineers and backend interns for top tech companies."
                )
        );
    }

    private List<DiscoveredSource> mockFallbackSources(String rawQuery, String lowerQuery, Instant now) {
        String sanitized = lowerQuery.replaceAll("[^a-z0-9]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        String domainName = sanitized.isEmpty() ? "example" : sanitized;

        return List.of(
                new DiscoveredSource(
                        "https://" + domainName + ".org",
                        "[MOCK] " + rawQuery + " - Official Site",
                        "OFFICIAL_WEBSITE",
                        now,
                        0.90,
                        "General overview and information regarding " + rawQuery + "."
                ),
                new DiscoveredSource(
                        "https://en.wikipedia.org/wiki/" + domainName,
                        "[MOCK] " + rawQuery + " - Wikipedia Overview",
                        "WIKIPEDIA",
                        now,
                        0.85,
                        "Reference article for " + rawQuery + "."
                )
        );
    }

    private List<DiscoveredSource> applyLimit(List<DiscoveredSource> results, int maxResults) {
        int limit = Math.min(maxResults > 0 ? maxResults : DEFAULT_MAX_RESULTS, results.size());
        return results.subList(0, limit);
    }
}
