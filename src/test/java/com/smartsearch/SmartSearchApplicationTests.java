package com.smartsearch;

import com.smartsearch.dto.SearchRequestDto;
import com.smartsearch.dto.SearchResponseDto;
import com.smartsearch.dto.SearchResultItemDto;
import com.smartsearch.model.Category;
import com.smartsearch.model.IndexedPage;
import com.smartsearch.model.Role;
import com.smartsearch.model.User;
import com.smartsearch.service.LuceneService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SmartSearchApplicationTests {

    private LuceneService luceneService;
    private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        passwordEncoder = new BCryptPasswordEncoder();
        luceneService = new LuceneService();
        ReflectionTestUtils.setField(luceneService, "indexDirPath", tempDir.resolve("test_lucene_index").toString());
        luceneService.init();
    }

    @AfterEach
    void tearDown() {
        if (luceneService != null) {
            luceneService.cleanUp();
        }
    }

    @Test
    void testLuceneIndexingAndSearchWorkflow() {
        IndexedPage page1 = new IndexedPage(
                "https://spring.io/projects/spring-boot",
                "Spring Boot Framework",
                "Rapid enterprise application development with Spring Boot and Java.",
                "spring boot java framework microservices",
                "ALL",
                "Spring Boot makes it easy to create stand-alone production-grade Spring based applications.",
                "https://spring.io/logo.png",
                "spring.io"
        );

        IndexedPage page2 = new IndexedPage(
                "https://lucene.apache.org/",
                "Apache Lucene Search Engine",
                "High-performance, full-featured search engine library in Java.",
                "apache lucene search indexing bm25",
                "ALL",
                "Lucene Core provides inverted indexing and full-text search capabilities.",
                "https://lucene.apache.org/logo.png",
                "apache.org"
        );

        luceneService.indexDocuments(List.of(page1, page2));

        // Test Query 1: Search "Lucene"
        SearchRequestDto req1 = new SearchRequestDto("Lucene", "ALL", 1);
        Map<String, Object> result1 = luceneService.search(req1);

        assertNotNull(result1);
        long hits1 = (long) result1.get("totalHits");
        assertTrue(hits1 >= 1);

        @SuppressWarnings("unchecked")
        List<SearchResultItemDto> items1 = (List<SearchResultItemDto>) result1.get("items");
        assertFalse(items1.isEmpty());
        assertEquals("Apache Lucene Search Engine", items1.get(0).getTitle());
        assertTrue(items1.get(0).getSnippet().contains("mark class=\"search-highlight\""));

        // Test Query 2: Search "Spring Boot"
        SearchRequestDto req2 = new SearchRequestDto("Spring", "ALL", 1);
        Map<String, Object> result2 = luceneService.search(req2);
        long hits2 = (long) result2.get("totalHits");
        assertTrue(hits2 >= 1);

        // Test Index Stats
        Map<String, Object> stats = luceneService.getIndexStats();
        assertEquals(2, stats.get("totalDocuments"));
    }

    @Test
    void testBCryptPasswordEncryption() {
        String rawPassword = "SecurePassword@2026!";
        String encodedPassword = passwordEncoder.encode(rawPassword);

        assertNotNull(encodedPassword);
        assertNotEquals(rawPassword, encodedPassword);
        assertTrue(passwordEncoder.matches(rawPassword, encodedPassword));
        assertFalse(passwordEncoder.matches("WrongPassword", encodedPassword));
    }

    @Test
    void testUserEntityAndRoles() {
        User user = new User("developer", "dev@smartsearch.local", "hash123", "Java Developer", Role.ROLE_USER);
        assertEquals("developer", user.getUsername());
        assertEquals(Role.ROLE_USER, user.getRole());
        assertTrue(user.isEnabled());
    }

    @Test
    void testSearchResponsePaginationMath() {
        SearchResponseDto response = new SearchResponseDto("java", "ALL", 45, 2, 10);
        assertEquals(5, response.getTotalPages());
        assertTrue(response.isHasNext());
        assertTrue(response.isHasPrevious());
    }

    @Test
    void testCategoryParsing() {
        assertEquals(Category.IMAGES, Category.fromString("images"));
        assertEquals(Category.VIDEOS, Category.fromString("Videos"));
        assertEquals(Category.NEWS, Category.fromString("NEWS"));
        assertEquals(Category.MAPS, Category.fromString("maps"));
        assertEquals(Category.SHOPPING, Category.fromString("shopping"));
        assertEquals(Category.ALL, Category.fromString("invalid_category"));
    }

    @Test
    void testAnalyticsServiceDeleteKeyword() {
        java.util.List<Long> deletedIds = new java.util.ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean clearedAll = new java.util.concurrent.atomic.AtomicBoolean(false);

        com.smartsearch.model.SearchAnalytics sample = new com.smartsearch.model.SearchAnalytics("spring boot", "ALL");
        sample.setId(99L);
        sample.setSearchCount(25L);

        com.smartsearch.repository.SearchAnalyticsRepository fakeAnalyticsRepo = (com.smartsearch.repository.SearchAnalyticsRepository) 
            java.lang.reflect.Proxy.newProxyInstance(
                com.smartsearch.repository.SearchAnalyticsRepository.class.getClassLoader(),
                new Class<?>[]{com.smartsearch.repository.SearchAnalyticsRepository.class},
                (proxy, method, args) -> {
                    if ("findById".equals(method.getName())) {
                        Long id = (Long) args[0];
                        if (Long.valueOf(99L).equals(id)) {
                            return java.util.Optional.of(sample);
                        }
                        return java.util.Optional.empty();
                    }
                    if ("deleteById".equals(method.getName())) {
                        deletedIds.add((Long) args[0]);
                        return null;
                    }
                    if ("deleteAll".equals(method.getName())) {
                        clearedAll.set(true);
                        return null;
                    }
                    return null;
                }
            );

        com.smartsearch.repository.SearchHistoryRepository fakeHistoryRepo = (com.smartsearch.repository.SearchHistoryRepository) 
            java.lang.reflect.Proxy.newProxyInstance(
                com.smartsearch.repository.SearchHistoryRepository.class.getClassLoader(),
                new Class<?>[]{com.smartsearch.repository.SearchHistoryRepository.class},
                (proxy, method, args) -> null
            );

        com.smartsearch.service.AnalyticsService analyticsService = new com.smartsearch.service.AnalyticsService(fakeAnalyticsRepo, fakeHistoryRepo);

        // 1. Successful delete
        java.util.Optional<String> deleted = analyticsService.deleteQueryAnalytics(99L);
        assertTrue(deleted.isPresent());
        assertEquals("spring boot", deleted.get());
        assertEquals(1, deletedIds.size());
        assertEquals(99L, deletedIds.get(0));

        // 2. Not found delete
        java.util.Optional<String> notFound = analyticsService.deleteQueryAnalytics(404L);
        assertTrue(notFound.isEmpty());

        // 3. Clear all
        analyticsService.clearAllAnalytics();
        assertTrue(clearedAll.get());
    }

    @Test
    void testUrlSecurityValidatorBlocksSSRF() {
        com.smartsearch.crawler.UrlSecurityValidator validator = new com.smartsearch.crawler.UrlSecurityValidator();

        // Should block loopback and local
        assertFalse(validator.isSafeToCrawl("http://127.0.0.1/admin"));
        assertFalse(validator.isSafeToCrawl("http://localhost:8080/sensitive"));
        assertFalse(validator.isSafeToCrawl("http://10.0.0.5/api"));
        assertFalse(validator.isSafeToCrawl("http://192.168.1.100/router"));
        assertFalse(validator.isSafeToCrawl("http://169.254.169.254/latest/meta-data/"));

        // Should block non-http schemes
        assertFalse(validator.isSafeToCrawl("file:///etc/passwd"));
        assertFalse(validator.isSafeToCrawl("ftp://ftp.example.com/file"));

        // Should block executable and archive extensions
        assertFalse(validator.isSafeToCrawl("https://example.com/archive.zip"));
        assertFalse(validator.isSafeToCrawl("https://example.com/setup.exe"));

        // Normal public website should be accepted
        assertTrue(validator.isSafeToCrawl("https://en.wikipedia.org/wiki/Java_(programming_language)"));
    }

    @Test
    void testRobotsTxtParser() {
        com.smartsearch.crawler.RobotsTxtEvaluator evaluator = new com.smartsearch.crawler.RobotsTxtEvaluator();
        String robotsContent = "User-agent: *\n" +
                "Disallow: /private/\n" +
                "Disallow: /admin\n" +
                "Crawl-delay: 2\n";

        com.smartsearch.crawler.RobotsTxtEvaluator.RobotsRules rules = evaluator.parseRobotsContent(robotsContent);
        assertNotNull(rules);
        assertFalse(rules.isAllowed("/private/secret.html"));
        assertFalse(rules.isAllowed("/admin/login"));
        assertTrue(rules.isAllowed("/public/about.html"));
        assertEquals(2, rules.getCrawlDelay());
    }

    @Test
    void testWebContentExtractorAndFingerprint() {
        com.smartsearch.extractor.WebContentExtractor extractor = new com.smartsearch.extractor.WebContentExtractor();
        String html = "<!DOCTYPE html><html><head><title>Spring Framework Architecture</title>" +
                "<meta name=\"description\" content=\"Comprehensive guide to Spring Framework.\">" +
                "<link rel=\"canonical\" href=\"https://spring.io/projects/spring-framework\">" +
                "</head><body>" +
                "<h1>Core Technologies</h1>" +
                "<h2>Dependency Injection</h2>" +
                "<p>Spring makes programming in Java quicker, easier, and safer for everybody.</p>" +
                "<footer>Copyright 2026</footer>" +
                "</body></html>";

        org.jsoup.nodes.Document doc = org.jsoup.Jsoup.parse(html, "https://spring.io/projects/spring-framework");
        com.smartsearch.extractor.ExtractedPageData data = extractor.extract(doc, "https://spring.io/projects/spring-framework");

        assertEquals("Spring Framework Architecture", data.getTitle());
        assertEquals("Comprehensive guide to Spring Framework.", data.getDescription());
        assertEquals("https://spring.io/projects/spring-framework", data.getCanonicalUrl());
        assertTrue(data.getHeadings().contains("Core Technologies"));
        assertTrue(data.getContent().contains("Spring makes programming in Java quicker"));
        assertNotNull(data.getContentHash());
        assertEquals(64, data.getContentHash().length()); // SHA-256 hex length
        assertTrue(data.getQualityScore() >= 50.0);
    }

    @Test
    void testSearchRankingService() {
        com.smartsearch.ranking.SearchRankingService rankingService = new com.smartsearch.ranking.SearchRankingService();

        SearchResultItemDto localItem = new SearchResultItemDto("Java Tutorial", "https://java.com/tutorial", "Learn Java from scratch", "ALL", "java.com");
        SearchResultItemDto externalItem = new SearchResultItemDto("Spring Boot Microservices", "https://spring.io/microservices", "Production ready microservices", "ALL", "spring.io");

        List<SearchResultItemDto> ranked = rankingService.rankAndMerge("Java", List.of(localItem), List.of(externalItem));
        assertNotNull(ranked);
        assertEquals(2, ranked.size());
        assertEquals("Java Tutorial", ranked.get(0).getTitle());
        assertTrue(ranked.get(0).getScore() > ranked.get(1).getScore());
    }

    @Test
    void testUrlSecurityValidatorBlocksSSRFAndPrivateIPs() {
        com.smartsearch.crawler.UrlSecurityValidator validator = new com.smartsearch.crawler.UrlSecurityValidator();

        // Localhost / internal hostnames
        var res1 = validator.validate("http://localhost:8080/admin");
        assertFalse(res1.isSafe());
        assertEquals("SSRF_BLOCKED", res1.getErrorType());

        // Loopback IP
        var res2 = validator.validate("http://127.0.0.1:8080/api");
        assertFalse(res2.isSafe());
        assertEquals("PRIVATE_IP_BLOCKED", res2.getErrorType());

        // Cloud Metadata IP
        var res3 = validator.validate("http://169.254.169.254/latest/meta-data/");
        assertFalse(res3.isSafe());
        assertEquals("PRIVATE_IP_BLOCKED", res3.getErrorType());

        // Private RFC 1918 IPs
        var res4 = validator.validate("http://192.168.1.1/admin");
        assertFalse(res4.isSafe());
        assertEquals("PRIVATE_IP_BLOCKED", res4.getErrorType());

        var res5 = validator.validate("http://10.0.1.5/status");
        assertFalse(res5.isSafe());
        assertEquals("PRIVATE_IP_BLOCKED", res5.getErrorType());

        // Restricted Port
        var res6 = validator.validate("http://example.com:22/ssh");
        assertFalse(res6.isSafe());
        assertEquals("RESTRICTED_PORT", res6.getErrorType());

        // Invalid Scheme
        var res7 = validator.validate("ftp://example.com/download");
        assertFalse(res7.isSafe());
        assertEquals("INVALID_SCHEME", res7.getErrorType());

        // Disallowed Extension
        var res8 = validator.validate("https://example.com/payload.exe");
        assertFalse(res8.isSafe());
        assertEquals("DISALLOWED_EXTENSION", res8.getErrorType());

        // Valid Public URL
        var res9 = validator.validate("https://spring.io/projects/spring-boot");
        assertTrue(res9.isSafe());
        assertTrue(validator.isSafeToCrawl("https://spring.io/projects/spring-boot"));
    }

    @Test
    void testCrawlErrorLogBadgeClass() {
        com.smartsearch.model.CrawlErrorLog ssrfLog = new com.smartsearch.model.CrawlErrorLog(
                "http://127.0.0.1", "127.0.0.1", "SSRF_BLOCKED", 400, "Blocked", 1L);
        assertEquals("bg-danger text-white", ssrfLog.getBadgeClass());

        com.smartsearch.model.CrawlErrorLog robotsLog = new com.smartsearch.model.CrawlErrorLog(
                "https://example.com/private", "example.com", "ROBOTS_DISALLOWED", 403, "Blocked", 1L);
        assertEquals("bg-warning-subtle text-warning-emphasis border border-warning", robotsLog.getBadgeClass());

        com.smartsearch.model.CrawlErrorLog httpLog = new com.smartsearch.model.CrawlErrorLog(
                "https://example.com/missing", "example.com", "HTTP_STATUS", 404, "Not Found", 1L);
        assertEquals("bg-danger-subtle text-danger", httpLog.getBadgeClass());
    }

    @Test
    void testCrawlJobCompletionStatus() {
        com.smartsearch.model.CrawlJob job = new com.smartsearch.model.CrawlJob("AUTO_SEARCH", "Spring Framework");
        assertEquals("RUNNING", job.getStatus());
        assertNull(job.getCompletedAt());

        job.incrementCrawled();
        job.incrementIndexed();
        job.complete("Completed: 1 crawled, 1 indexed.");

        assertEquals("COMPLETED", job.getStatus());
        assertNotNull(job.getCompletedAt());
        assertEquals("Completed: 1 crawled, 1 indexed.", job.getSummaryMessage());
    }

    @Test
    void testEmploymentServiceJobListingsAndFilters() {
        com.smartsearch.service.EmploymentService employmentService = new com.smartsearch.service.EmploymentService();
        var allJobs = employmentService.getJobListings(null, "ALL", "ALL");
        assertNotNull(allJobs);
        assertFalse(allJobs.isEmpty());
        assertTrue(allJobs.size() >= 5);

        // Filter by keyword "Java"
        var javaJobs = employmentService.getJobListings("Java", "ALL", "ALL");
        assertFalse(javaJobs.isEmpty());
        assertTrue(javaJobs.stream().allMatch(j -> 
            j.getTitle().toLowerCase().contains("java") || 
            j.getDescription().toLowerCase().contains("java") ||
            j.getTags().stream().anyMatch(t -> t.toLowerCase().contains("java"))
        ));

        // Filter by category "ENGINEERING"
        var engineeringJobs = employmentService.getJobListings(null, "ENGINEERING", "ALL");
        assertFalse(engineeringJobs.isEmpty());
        assertTrue(engineeringJobs.stream().allMatch(j -> "ENGINEERING".equalsIgnoreCase(j.getCategory())));

        // Stats
        var stats = employmentService.getEmploymentStats();
        assertNotNull(stats);
        assertTrue(stats.containsKey("totalActiveJobs"));
        assertTrue(stats.containsKey("partnerCompanies"));
    }

    @Test
    void testEmploymentServiceJobApplicationAndPosting() {
        com.smartsearch.service.EmploymentService employmentService = new com.smartsearch.service.EmploymentService();
        var initialJob = employmentService.getJobById(1L);
        assertTrue(initialJob.isPresent());
        int initialApplicants = initialJob.get().getApplicantCount();

        boolean applied = employmentService.applyToJob(1L, "Alex Dev", "alex@example.com", "9998887776", "3 Years", "Profile link");
        assertTrue(applied);
        assertEquals(initialApplicants + 1, initialJob.get().getApplicantCount());

        // Test posting without certificates - should fail with IllegalArgumentException
        com.smartsearch.dto.JobListingDto unverifiedJob = new com.smartsearch.dto.JobListingDto();
        unverifiedJob.setTitle("Principal Search Architect");
        unverifiedJob.setCompany("DeepTech Engines");
        unverifiedJob.setLocation("Remote");
        unverifiedJob.setDescription("Leading advanced search algorithms and crawling subsystems.");

        assertThrows(IllegalArgumentException.class, () -> employmentService.postJob(unverifiedJob),
                "Posting a job without minimum 2 business certificates must throw IllegalArgumentException");

        // Test posting with only 1 certificate - should fail
        unverifiedJob.setShopActCert("MH/PUN/SHOP/10294/2021");
        assertThrows(IllegalArgumentException.class, () -> employmentService.postJob(unverifiedJob),
                "Posting a job with only 1 certificate must throw IllegalArgumentException");

        // Test posting with 2 certificates (Shop Act + GST) - must succeed
        unverifiedJob.setGstCert("27AABCT1334M1Z2");
        employmentService.postJob(unverifiedJob);
        assertNotNull(unverifiedJob.getId());
        assertTrue(unverifiedJob.hasMinimumCertificates());
        assertEquals(2, unverifiedJob.getCertificateCount());
        assertTrue(unverifiedJob.getVerifiedCertificates().contains("Shop Act"));
        assertTrue(unverifiedJob.getVerifiedCertificates().contains("GST"));

        var fetched = employmentService.getJobById(unverifiedJob.getId());
        assertTrue(fetched.isPresent());
        assertEquals("Principal Search Architect", fetched.get().getTitle());
        assertEquals("DeepTech Engines", fetched.get().getCompany());
    }

    @Test
    void testEmployerRegistrationAndCertificateValidation() {
        // 1. Job Seeker registration DTO without certificates has count 0 and hasMinimumCertificates=false
        com.smartsearch.dto.UserRegistrationDto jobSeekerDto = new com.smartsearch.dto.UserRegistrationDto();
        jobSeekerDto.setUsername("jobseeker1");
        jobSeekerDto.setEmail("seeker@example.com");
        jobSeekerDto.setFullName("John Seeker");
        jobSeekerDto.setAccountType("JOB_SEEKER");

        assertEquals(0, jobSeekerDto.getCertificateCount());
        assertFalse(jobSeekerDto.hasMinimumCertificates());

        // 2. Employer DTO with 0 certificates
        com.smartsearch.dto.UserRegistrationDto employerDto = new com.smartsearch.dto.UserRegistrationDto();
        employerDto.setAccountType("EMPLOYER");
        employerDto.setCompanyName("Tech Innovators Pvt Ltd");
        assertEquals(0, employerDto.getCertificateCount());
        assertFalse(employerDto.hasMinimumCertificates());

        // 3. Employer DTO with only 1 certificate (MSME only)
        employerDto.setMsmeCert("UDYAM-MH-19-0099881");
        assertEquals(1, employerDto.getCertificateCount());
        assertFalse(employerDto.hasMinimumCertificates());

        // 4. Employer DTO with 2 certificates (MSME + RERA) meets requirement
        employerDto.setReraCert("RERA-MH-2022-8812");
        assertEquals(2, employerDto.getCertificateCount());
        assertTrue(employerDto.hasMinimumCertificates());
        assertEquals(List.of("RERA", "MSME"), employerDto.getVerifiedCertificates());

        // 5. Test User entity certificate helper methods & Mobile/WhatsApp numbers
        User user = new User();
        user.setAccountType("EMPLOYER");
        user.setMobileNumber("+91 9876543210");
        user.setWhatsappNumber("+91 9876543210");
        user.setGstCert("27AAACV9204P1Z3");
        user.setShopActCert("MH/PUN/SHOP/91024/2019");
        assertEquals("+91 9876543210", user.getMobileNumber());
        assertEquals("+91 9876543210", user.getWhatsappNumber());
        assertEquals(2, user.getCertificateCount());
        assertTrue(user.hasMinimumCertificates());
        user.setBusinessVerified(true);
        assertTrue(user.isBusinessVerified());

        // 6. Test DTO Mobile & WhatsApp number support
        jobSeekerDto.setMobileNumber("+91 9876500000");
        jobSeekerDto.setWhatsappNumber("+91 9876500000");
        assertEquals("+91 9876500000", jobSeekerDto.getMobileNumber());
        assertEquals("+91 9876500000", jobSeekerDto.getWhatsappNumber());
    }
}
