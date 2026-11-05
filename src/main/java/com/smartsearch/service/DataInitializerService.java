package com.smartsearch.service;

import com.smartsearch.model.*;
import com.smartsearch.repository.IndexedPageRepository;
import com.smartsearch.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class DataInitializerService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializerService.class);

    private final UserRepository userRepository;
    private final IndexedPageRepository indexedPageRepository;
    private final LuceneService luceneService;
    private final PasswordEncoder passwordEncoder;
    private final com.smartsearch.repository.SearchProviderConfigRepository providerConfigRepository;
    private final com.smartsearch.repository.SearchProviderRecommendationRepository recommendationRepository;
    private final SystemSettingService settingService;
    private final com.smartsearch.repository.BlogPublishingPermissionRepository blogPermissionRepository;
    private final com.smartsearch.repository.UserBlogLimitRepository userBlogLimitRepository;
    private final com.smartsearch.repository.BlogPostRepository blogPostRepository;
    private final com.smartsearch.repository.AiSeoRecommendationRepository aiSeoRecommendationRepository;

    public DataInitializerService(UserRepository userRepository,
                                  IndexedPageRepository indexedPageRepository,
                                  LuceneService luceneService,
                                  PasswordEncoder passwordEncoder,
                                  com.smartsearch.repository.SearchProviderConfigRepository providerConfigRepository,
                                  com.smartsearch.repository.SearchProviderRecommendationRepository recommendationRepository,
                                  SystemSettingService settingService,
                                  com.smartsearch.repository.BlogPublishingPermissionRepository blogPermissionRepository,
                                  com.smartsearch.repository.UserBlogLimitRepository userBlogLimitRepository,
                                  com.smartsearch.repository.BlogPostRepository blogPostRepository,
                                  com.smartsearch.repository.AiSeoRecommendationRepository aiSeoRecommendationRepository) {
        this.userRepository = userRepository;
        this.indexedPageRepository = indexedPageRepository;
        this.luceneService = luceneService;
        this.passwordEncoder = passwordEncoder;
        this.providerConfigRepository = providerConfigRepository;
        this.recommendationRepository = recommendationRepository;
        this.settingService = settingService;
        this.blogPermissionRepository = blogPermissionRepository;
        this.userBlogLimitRepository = userBlogLimitRepository;
        this.blogPostRepository = blogPostRepository;
        this.aiSeoRecommendationRepository = aiSeoRecommendationRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        initUsers();
        initIndexedPages();
        initSearchProviders();
        initProviderRecommendations();
        initCrawlerSettings();
        initBlogAndSeoData();
    }

    private void initUsers() {
        // Admin user
        if (!userRepository.existsByUsername("admin")) {
            User admin = new User(
                    "admin",
                    "admin@smartsearch.local",
                    passwordEncoder.encode("Admin@123"),
                    "SmartSearch Administrator",
                    Role.ROLE_ADMIN
            );
            admin.setAvatarUrl("https://ui-avatars.com/api/?name=Admin+User&background=0b57d0&color=fff");
            admin.setMobileNumber("+91 98000 00001");
            admin.setWhatsappNumber("+91 98000 00001");
            userRepository.save(admin);
            logger.info("Created default administrator: admin / Admin@123");
        }

        // Demo user
        if (!userRepository.existsByUsername("demo")) {
            User demo = new User(
                    "demo",
                    "demo@smartsearch.local",
                    passwordEncoder.encode("Demo@123"),
                    "Alex Morgan",
                    Role.ROLE_USER
            );
            demo.setAvatarUrl("https://ui-avatars.com/api/?name=Alex+Morgan&background=1a73e8&color=fff");
            demo.setMobileNumber("+91 98000 00002");
            demo.setWhatsappNumber("+91 98000 00002");
            userRepository.save(demo);
            logger.info("Created default demo user: demo / Demo@123");
        }

        // Default Employer user
        if (!userRepository.existsByUsername("employer")) {
            User employer = new User(
                    "employer",
                    "recruiter@acmetech.local",
                    passwordEncoder.encode("Employer@123"),
                    "Acme Talent Recruiter",
                    Role.ROLE_USER
            );
            employer.setAccountType("EMPLOYER");
            employer.setCompanyName("Acme Tech Solutions");
            employer.setMobileNumber("+91 98000 00003");
            employer.setWhatsappNumber("+91 98000 00003");
            employer.setShopActCert("MH/PUN/SHOP/91024/2019");
            employer.setGstCert("27AAACV9204P1Z3");
            employer.setMsmeCert("UDYAM-MH-19-0012984");
            employer.setBusinessVerified(true);
            employer.setAvatarUrl("https://ui-avatars.com/api/?name=Acme+Tech&background=0f9d58&color=fff");
            userRepository.save(employer);
            logger.info("Created default employer: employer / Employer@123");
        }
    }

    private void initIndexedPages() {
        if (indexedPageRepository.count() > 0) {
            // Check if Lucene has documents, if not re-index database records
            var stats = luceneService.getIndexStats();
            int docCount = (int) stats.getOrDefault("totalDocuments", 0);
            if (docCount == 0) {
                logger.info("Syncing existing database pages into Lucene index...");
                luceneService.indexDocuments(indexedPageRepository.findAll());
            }
            return;
        }

        logger.info("Seeding initial indexed corpus into database and Lucene...");
        List<IndexedPage> seedPages = new ArrayList<>();

        seedPages.add(new IndexedPage(
                "https://spring.io/projects/spring-boot",
                "Spring Boot - Rapid Application Development Framework for Java",
                "Spring Boot makes it easy to create stand-alone, production-grade Spring based Applications that you can 'just run'. Build robust microservices, web apps, and enterprise backends with minimal configuration.",
                "spring boot java microservices rest api framework backend",
                "ALL",
                "Spring Boot framework provides opinionated dependencies, embedded Tomcat servlet containers, automatic configuration, production ready metrics, health checks and externalized configuration.",
                "https://images.unsplash.com/photo-1517694712202-14dd9538aa97?auto=format&fit=crop&w=600&q=80",
                "spring.io"
        ));

        seedPages.add(new IndexedPage(
                "https://www.oracle.com/java/",
                "Java Platform - The World's Most Popular Enterprise Programming Language",
                "Java is the #1 programming language and development platform. It reduces costs, shortens development timeframes, drives innovation, and improves enterprise application services.",
                "java jdk jvm oracle object oriented programming enterprise backend",
                "ALL",
                "Java 17 LTS and Java 21 LTS deliver virtual threads, pattern matching, records, garbage collection improvements, security enhancements, and high performance cloud computing.",
                "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?auto=format&fit=crop&w=600&q=80",
                "oracle.com"
        ));

        seedPages.add(new IndexedPage(
                "https://lucene.apache.org/",
                "Apache Lucene - High-Performance, Full-Featured Search Engine Library",
                "Apache Lucene Core is a high-performance, full-featured search engine library written entirely in Java. It is a technology suitable for nearly any application that requires structured and full-text search.",
                "apache lucene search engine indexing information retrieval inverted index bm25 full text",
                "ALL",
                "Lucene provides scalable, high-performance indexing, powerful search algorithms, ranked searching, field searching, sorting by any field, and multiple-index searching with combined results.",
                "https://images.unsplash.com/photo-1498050108023-c5249f4df085?auto=format&fit=crop&w=600&q=80",
                "apache.org"
        ));

        seedPages.add(new IndexedPage(
                "https://developer.mozilla.org/en-US/",
                "MDN Web Docs - Resources for Developers, by Developers",
                "The MDN Web Docs site provides information about Open Web technologies including HTML, CSS, JavaScript, Web APIs, and progressive web applications.",
                "mdn html css javascript web development frontend web apis dom",
                "ALL",
                "Learn web development with structured tutorials, reference specifications, cross-browser compatibility guides, and interactive code sandboxes covering HTML5, CSS Grid, Flexbox, and modern JavaScript.",
                "https://images.unsplash.com/photo-1461749280684-dccba630e2f6?auto=format&fit=crop&w=600&q=80",
                "developer.mozilla.org"
        ));

        seedPages.add(new IndexedPage(
                "https://www.mysql.com/",
                "MySQL - The World's Most Popular Open Source Relational Database",
                "MySQL is an open-source relational database management system (RDBMS) that provides reliability, high performance, and ease of use for modern web applications.",
                "mysql sql relational database rdbms acid transactions innodb",
                "ALL",
                "MySQL enterprise edition delivers advanced security, auditing, high availability clustering, JSON support, query optimization, and scalability for cloud and on-premise infrastructure.",
                "https://images.unsplash.com/photo-1504639725590-34d0984388bd?auto=format&fit=crop&w=600&q=80",
                "mysql.com"
        ));

        seedPages.add(new IndexedPage(
                "https://github.com/",
                "GitHub - Where the World Builds Software",
                "GitHub is where over 100 million developers shape the future of software, together. Contribute to open source, manage Git repositories, review code, and automate CI/CD workflows.",
                "github git version control open source pull request repository actions ci cd",
                "ALL",
                "Explore open source repositories, host private code, automate workflows with GitHub Actions, collaborate with pull requests, discussions, and AI-powered coding assistance.",
                "https://images.unsplash.com/photo-1556075798-4825dfaaf498?auto=format&fit=crop&w=600&q=80",
                "github.com"
        ));

        seedPages.add(new IndexedPage(
                "https://en.wikipedia.org/wiki/Artificial_intelligence",
                "Artificial Intelligence - Modern Machine Learning and Cognitive Systems",
                "Artificial intelligence (AI) is the intelligence of machines or software, as opposed to the intelligence of living beings, primarily of humans. Key subfields include deep learning, neural networks, and NLP.",
                "artificial intelligence ai machine learning deep learning neural networks generative ai llm",
                "ALL",
                "Explore the history, applications, ethics, neural network architectures, transformers, large language models, computer vision, and cognitive systems reshaping industries worldwide.",
                "https://images.unsplash.com/photo-1620712943543-bcc4688e7485?auto=format&fit=crop&w=600&q=80",
                "wikipedia.org"
        ));

        seedPages.add(new IndexedPage(
                "https://news.ycombinator.com/",
                "Hacker News - Technology, Startups, and Computer Science Discourse",
                "Hacker News is a social news website focusing on computer science, entrepreneurship, cybersecurity, software engineering, and emerging technologies.",
                "hacker news tech news silicon valley startups software engineering y combinator",
                "NEWS",
                "Read high signal discussions on programming languages, engineering breakthroughs, venture capital, distributed systems, cryptography, and artificial intelligence innovations.",
                "https://images.unsplash.com/photo-1504384308090-c894fdcc538d?auto=format&fit=crop&w=600&q=80",
                "news.ycombinator.com"
        ));

        seedPages.add(new IndexedPage(
                "https://www.apple.com/macbook-pro/",
                "MacBook Pro 16-inch M3 Max - High Performance Laptop for Engineers",
                "MacBook Pro blasts forward with M3, M3 Pro, and M3 Max chips. Packed with tremendous compute power, extreme dynamic range Liquid Retina XDR display, and up to 22 hours of battery life.",
                "apple macbook pro m3 laptop hardware developer machine apple silicon",
                "SHOPPING",
                "Features next generation CPU and GPU performance, ray tracing, unified memory architecture, studio quality microphones, HDMI port, SDXC card slot, and MagSafe 3 charging.",
                "https://images.unsplash.com/photo-1517336714731-489689fd1ca8?auto=format&fit=crop&w=600&q=80",
                "apple.com"
        ));

        seedPages.add(new IndexedPage(
                "https://www.youtube.com/watch?v=JavaIn100Seconds",
                "Java in 100 Seconds - Fast Paced Architecture and History Overview",
                "Learn the Java programming language in 100 seconds! Explore its history from James Gosling and Sun Microsystems, object oriented paradigm, JVM bytecode, garbage collection, and ecosystem.",
                "java video fireship programming crash course tutorial learning",
                "VIDEOS",
                "Quick video summary explaining bytecode, JVM, JIT compilation, Write Once Run Anywhere philosophy, enterprise adoption, and modern language features.",
                "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?auto=format&fit=crop&w=600&q=80",
                "youtube.com"
        ));

        List<IndexedPage> saved = indexedPageRepository.saveAll(seedPages);
        luceneService.indexDocuments(saved);
        logger.info("Successfully seeded {} pages into Lucene and database", saved.size());
    }

    private void initSearchProviders() {
        seedProvider("GOOGLE", "Google Custom Search JSON API", true,
                "https://www.google.com", "https://www.googleapis.com/customsearch/v1",
                "https://developers.google.com/custom-search/v1/overview",
                settingService.getSetting("GOOGLE_API_KEY", ""),
                settingService.getSetting("GOOGLE_SEARCH_ENGINE_ID", ""),
                "API_KEY_QUERY", null, "Worldwide (220+ Countries)", "100+ Languages", 1, "FREEMIUM", false);

        seedProvider("BING", "Microsoft Bing Web Search API v7", true,
                "https://www.bing.com", "https://api.bing.microsoft.com/v7.0/search",
                "https://learn.microsoft.com/en-us/bing/search-apis/bing-web-search",
                settingService.getSetting("BING_API_KEY", ""), "",
                "API_KEY_HEADER", "Ocp-Apim-Subscription-Key", "Worldwide (200+ Countries)", "40+ Languages", 2, "PAID", false);

        seedProvider("YAHOO", "Yahoo Search Provider (API & RSS)", true,
                "https://search.yahoo.com", "https://news.search.yahoo.com/rss",
                "https://developer.yahoo.com/boss/search/",
                settingService.getSetting("YAHOO_API_KEY", ""), "",
                "OPEN_INTERFACE", null, "Worldwide (50+ Countries)", "30+ Languages", 3, "FREE", false);

        seedProvider("DUCKDUCKGO", "DuckDuckGo Search (API & Web)", true,
                "https://duckduckgo.com", "https://api.duckduckgo.com",
                "https://duckduckgo.com/api",
                "", "", "OPEN_INTERFACE", null, "Worldwide (Privacy Focus)", "50+ Languages", 4, "FREE", false);

        seedProvider("BRAVE", "Brave Search API", true,
                "https://search.brave.com", "https://api.search.brave.com/res/v1/web/search",
                "https://api.search.brave.com/app/documentation",
                settingService.getSetting("BRAVE_API_KEY", ""), "",
                "API_KEY_HEADER", "X-Subscription-Token", "Worldwide (Independent)", "50+ Languages", 5, "FREEMIUM", false);

        seedProvider("WIKIPEDIA", "Wikimedia Open Search API", true,
                "https://www.wikipedia.org", "https://en.wikipedia.org/w/api.php",
                "https://www.mediawiki.org/wiki/API:Opensearch",
                "", "", "OPEN_INTERFACE", null, "Worldwide (Open Knowledge)", "300+ Languages", 6, "FREE", false);

        seedProvider("MOJEEK", "Mojeek Independent Index", true,
                "https://www.mojeek.com", "https://www.mojeek.com/search",
                "https://www.mojeek.com/services/search/api/",
                settingService.getSetting("MOJEEK_API_KEY", ""), "",
                "API_KEY_QUERY", null, "UK, US, EU, Worldwide", "Multilingual (No Tracking)", 7, "FREEMIUM", false);

        seedProvider("YANDEX", "Yandex Multilingual Search", false,
                "https://yandex.com", "https://searchapi.yandex.net/v2/web/search",
                "https://yandex.com/dev/xml/",
                settingService.getSetting("YANDEX_API_KEY", ""), "",
                "API_KEY_HEADER", "Authorization", "Eastern Europe, CIS, Central Asia", "40+ Languages", 8, "FREEMIUM", false);

        seedProvider("SEARXNG", "SearXNG Metasearch API", false,
                "https://searx.be", "https://searx.be/search",
                "https://docs.searxng.org/",
                "", "", "OPEN_INTERFACE", null, "Worldwide Metasearch", "All Languages", 9, "FREE", true);
    }

    private void seedProvider(String providerId, String displayName, boolean enabled,
                              String websiteUrl, String endpointUrl, String docUrl,
                              String apiKey, String searchEngineId, String authMethod, String authHeaderName,
                              String supportedRegions, String supportedLanguages, int priority, String pricingTier, boolean isFallback) {
        Optional<com.smartsearch.model.SearchProviderConfig> opt = providerConfigRepository.findByProviderId(providerId);
        com.smartsearch.model.SearchProviderConfig cfg = opt.orElseGet(com.smartsearch.model.SearchProviderConfig::new);

        cfg.setProviderId(providerId);
        cfg.setDisplayName(displayName);
        if (opt.isEmpty()) {
            cfg.setEnabled(enabled);
            cfg.setPriority(priority);
        }
        cfg.setWebsiteUrl(websiteUrl);
        cfg.setEndpointUrl(endpointUrl);
        cfg.setDocUrl(docUrl);
        if (cfg.getApiKey() == null || cfg.getApiKey().isBlank()) {
            cfg.setApiKey(apiKey);
        }
        if (cfg.getSearchEngineId() == null || cfg.getSearchEngineId().isBlank()) {
            cfg.setSearchEngineId(searchEngineId);
        }
        cfg.setAuthMethod(authMethod);
        cfg.setAuthHeaderName(authHeaderName);
        cfg.setSupportedRegions(supportedRegions);
        cfg.setSupportedLanguages(supportedLanguages);
        cfg.setPricingTier(pricingTier);
        cfg.setIsFallback(isFallback);
        cfg.setIntegrationStatus("INTEGRATED");

        if ("WIKIPEDIA".equals(providerId) || "DUCKDUCKGO".equals(providerId) || "YAHOO".equals(providerId) || "MOJEEK".equals(providerId)) {
            cfg.setConnectionStatus("CONNECTED");
            cfg.setApiStatus("READY");
        } else if (cfg.getApiKey() != null && !cfg.getApiKey().isBlank()) {
            cfg.setConnectionStatus("CONNECTED");
            cfg.setApiStatus("READY");
        } else {
            cfg.setConnectionStatus("UNTESTED");
            cfg.setApiStatus("KEY_REQUIRED");
        }

        providerConfigRepository.save(cfg);
    }

    private void initProviderRecommendations() {
        if (recommendationRepository.count() == 0) {
            List<com.smartsearch.model.SearchProviderRecommendation> recs = new ArrayList<>();

            recs.add(new com.smartsearch.model.SearchProviderRecommendation(
                    "MOJEEK", "Mojeek Search", "https://www.mojeek.com",
                    "https://www.mojeek.com/search", "https://www.mojeek.com/services/search/api/",
                    "FREEMIUM", "UK, US, European Union, Worldwide", "Multilingual (English, German, French, etc.)",
                    "One of the few search engines globally with its own 6-billion-page independent web index. Zero user tracking, algorithmic bias resistance.",
                    4.8, true, "Independent corpus with no reliance on Google/Bing infrastructure."
            ));

            recs.add(new com.smartsearch.model.SearchProviderRecommendation(
                    "YANDEX", "Yandex Search", "https://yandex.com",
                    "https://searchapi.yandex.net/v2/web/search", "https://yandex.com/dev/xml/",
                    "FREEMIUM", "Eastern Europe, CIS, Central Asia, Worldwide", "40+ Languages (Cyrillic & Latin specializations)",
                    "Leading search provider across Eastern Europe and Eurasia with advanced morphological language parsing and local commerce data.",
                    4.7, true, "Unmatched relevance for Eastern European and Central Asian web coverage."
            ));

            recs.add(new com.smartsearch.model.SearchProviderRecommendation(
                    "QWANT", "Qwant European Search", "https://www.qwant.com",
                    "https://api.qwant.com/v3/search/web", "https://about.qwant.com/",
                    "FREE", "European Union & Worldwide", "26+ Languages",
                    "Leading European privacy-respecting search engine, based in France. Fully compliant with European GDPR regulations.",
                    4.6, true, "Ideal for EU compliance, European regional news and privacy-conscious users."
            ));

            recs.add(new com.smartsearch.model.SearchProviderRecommendation(
                    "BAIDU", "Baidu Web Search", "https://www.baidu.com",
                    "https://api.baidu.com/search", "https://developer.baidu.com/",
                    "FREEMIUM", "East Asia, China & Global Chinese Communities", "Chinese (Simplified/Traditional), English",
                    "The dominant search engine in East Asia. Essential provider for accessing Chinese web documents, technical papers, and regional portals.",
                    4.5, true, "Essential for comprehensive Asian regional web reach."
            ));

            recs.add(new com.smartsearch.model.SearchProviderRecommendation(
                    "KAGI", "Kagi Human Search", "https://kagi.com",
                    "https://kagi.com/api/v1/search", "https://help.kagi.com/kagi/api/search.html",
                    "PAID", "Worldwide", "All Major Languages",
                    "Ad-free human-centric search engine emphasizing academic, non-commercial, and high-signal engineering documentation.",
                    4.9, true, "Premium quality results with zero SEO spam or commercial clutter."
            ));

            recs.add(new com.smartsearch.model.SearchProviderRecommendation(
                    "STARTPAGE", "Startpage Privacy Search", "https://www.startpage.com",
                    "https://www.startpage.com/sp/search", "https://support.startpage.com/",
                    "FREE", "Worldwide (Servers in Netherlands)", "English, European & Global",
                    "Combines high-relevance search quality with European data protection standards and complete IP address anonymization.",
                    4.7, true, "High relevance with rigorous IP protection."
            ));

            recs.add(new com.smartsearch.model.SearchProviderRecommendation(
                    "ECOSIA", "Ecosia Tree-Planting Search", "https://www.ecosia.org",
                    "https://api.ecosia.org/search", "https://www.ecosia.org/",
                    "FREE", "Worldwide", "47+ Languages",
                    "Social enterprise search engine that uses ad revenues to plant trees in biodiversity hotspots across 35+ countries.",
                    4.5, true, "Environmentally certified search syndication."
            ));

            recommendationRepository.saveAll(recs);
            logger.info("Seeded 7 global search provider recommendations.");
        }
    }

    private void initCrawlerSettings() {
        if (settingService.getSetting("CRAWLER_MAX_DEPTH", "").isBlank()) {
            settingService.updateSetting("CRAWLER_MAX_DEPTH", "2", "Maximum link crawling depth for discovery");
        }
        if (settingService.getSetting("CRAWLER_MAX_PAGES_PER_DOMAIN", "").isBlank()) {
            settingService.updateSetting("CRAWLER_MAX_PAGES_PER_DOMAIN", "10", "Maximum pages crawled per domain");
        }
        if (settingService.getSetting("CRAWLER_DELAY_MS", "").isBlank()) {
            settingService.updateSetting("CRAWLER_DELAY_MS", "500", "Polite crawl delay between requests to same domain (ms)");
        }
        if (settingService.getSetting("CRAWLER_ENABLED", "").isBlank()) {
            settingService.updateSetting("CRAWLER_ENABLED", "true", "Whether background web crawler is active");
        }
    }

    private void initBlogAndSeoData() {
        // 1. Master Permissions
        if (blogPermissionRepository.count() == 0) {
            BlogPublishingPermission perm = new BlogPublishingPermission();
            perm.setAllowAllUsersCreate(true);
            perm.setAllowAgentsCreate(true);
            perm.setAllowPropertyOwnersCreate(true);
            perm.setAllowAdminsCreate(true);
            perm.setAllowAiGenerate(true);
            perm.setAllowAiAutoPublish(false);
            perm.setRequireAdminApproval(false);
            perm.setDefaultValidityDays(90);
            perm.setAllowCustomValidity(true);
            perm.setAutoExpireAndUnpublish(true);
            perm.setAllowBlogRenewal(true);
            perm.setAutomaticSeoMasterSwitch(true);
            perm.setAutoMetaTitleOpt(true);
            perm.setAutoMetaDescOpt(true);
            perm.setAutoKeywordSuggestions(true);
            perm.setAutoInternalLinking(true);
            perm.setAutoImageAltText(true);
            perm.setAutoSchemaMarkup(true);
            perm.setAutoSitemapUpdate(true);
            perm.setAutoSeoContentAnalysis(true);
            perm.setAutoIndexingSubmission(false);
            perm.setAutoSeoMonitoring(true);
            blogPermissionRepository.save(perm);
            logger.info("Initialized default blog publishing permissions & SEO authority rules.");
        }

        // 2. Default Category Limits (Admin: 50/30d, Agents: 10/30d, Owners: 5/30d, Users: 3/30d, AI: 20/30d)
        if (userBlogLimitRepository.count() == 0) {
            List<UserBlogLimit> defaultLimits = new ArrayList<>();
            defaultLimits.add(new UserBlogLimit("ADMIN", 50, 30));
            defaultLimits.add(new UserBlogLimit("AGENT", 10, 30));
            defaultLimits.add(new UserBlogLimit("PROPERTY_OWNER", 5, 30));
            defaultLimits.add(new UserBlogLimit("USER", 3, 30));
            defaultLimits.add(new UserBlogLimit("AI", 20, 30));
            userBlogLimitRepository.saveAll(defaultLimits);
            logger.info("Initialized role-based blog publishing limits.");
        }

        // 3. Sample Blog Posts
        if (blogPostRepository.count() == 0) {
            User admin = userRepository.findByUsername("admin").orElse(null);

            // Blog 1
            BlogPost b1 = new BlogPost();
            b1.setTitle("Top 10 High-Growth Real Estate Investment Hubs in 2026");
            b1.setSlug("top-10-high-growth-real-estate-investment-hubs-2026");
            b1.setSummary("Explore the most lucrative urban and suburban micro-markets experiencing explosive capital appreciation and sustained high rental yields in 2026.");
            b1.setContent("<h2>1. The Shifting Landscape of Real Estate Returns</h2>"
                    + "<p>As transit networks expand and decentralized commercial hubs emerge, Tier-1 and Tier-2 suburban growth corridors are capturing record institutional and retail investment. Investors seeking resilient long-term capital appreciation are prioritizing integrated townships, metro-connected suburban enclaves, and sustainable smart-living communities.</p>"
                    + "<h2>2. Prime Micro-Markets to Watch</h2>"
                    + "<p>Leading micro-markets are characterized by robust infrastructural pipelines, including high-speed expressway interchanges, upcoming international airports, and modern technology SEZs. Key growth metrics highlight:</p>"
                    + "<ul>"
                    + "<li><strong>High Rental Yields:</strong> Outperforming central business districts by an average of 180 to 240 basis points annually.</li>"
                    + "<li><strong>Capital Value Growth:</strong> Anticipated 12-15% CAGR across organized residential gated layouts.</li>"
                    + "<li><strong>Lifestyle Amenities:</strong> Proximity to world-class educational institutions, multispecialty healthcare, and lifestyle shopping centers.</li>"
                    + "</ul>"
                    + "<h2>3. Key Takeaway for Savvy Investors</h2>"
                    + "<p>Early-stage allocation into verified projects with transparent regulatory approvals guarantees the best risk-adjusted upside. Always verify land titles, developer balance sheets, and municipal zoning permits before contract execution.</p>");
            b1.setCategory("Real Estate");
            b1.setTags("Real Estate, Investment, Property Trends, Capital Growth, Rental Yields");
            b1.setCoverImageUrl("https://images.unsplash.com/photo-1560518883-ce09059eeffa?w=1200&auto=format&fit=crop");
            b1.setCoverImageAlt("Modern urban architecture and high-growth real estate investments in 2026");
            b1.setAuthor(admin);
            b1.setAuthorName("SmartSearch Editorial Team");
            b1.setAuthorRole("ADMIN");
            b1.setIsAiGenerated(false);
            b1.setStatus("PUBLISHED");
            b1.setValidityDays(90);
            b1.setPublishedAt(LocalDateTime.now().minusDays(5));
            b1.setExpiresAt(LocalDateTime.now().plusDays(85));
            b1.setViewsCount(1420L);
            b1.setOrganicClicks(385L);
            b1.setOrganicImpressions(5200L);
            b1.setMetaTitle("Top 10 High-Growth Real Estate Investment Hubs (2026)");
            b1.setMetaDescription("Discover top real estate investment micro-markets in 2026 delivering superior capital appreciation and high rental yields. Read our comprehensive market analysis.");
            b1.setPrimaryKeyword("Real Estate Investment Hubs");
            b1.setSecondaryKeywords("Property Trends, Rental Yields, Capital Growth");
            b1.setSeoScore(94);
            b1.setReadabilityScore(78);
            b1.setGoogleIndexingStatus("INDEXED");
            b1.setSchemaMarkup("{\n  \"@context\": \"https://schema.org\",\n  \"@type\": \"Article\",\n  \"headline\": \"Top 10 High-Growth Real Estate Investment Hubs in 2026\",\n  \"author\": {\"@type\": \"Person\", \"name\": \"SmartSearch Editorial Team\"}\n}");
            blogPostRepository.save(b1);

            // Blog 2
            BlogPost b2 = new BlogPost();
            b2.setTitle("Essential Legal Checklist for Property Buyers: Title Deeds & RERA Compliance");
            b2.setSlug("essential-legal-checklist-for-property-buyers-rera-compliance");
            b2.setSummary("Ensure foolproof property transactions with this comprehensive step-by-step legal diligence roadmap covering title verification, encumbrance certificates, and statutory compliance.");
            b2.setContent("<h2>1. The Importance of Legal Due Diligence</h2>"
                    + "<p>Purchasing residential or commercial real estate represents one of the largest capital outlays an individual or business will ever make. Conducting meticulous legal scrutiny mitigates ownership disputes, fraudulent encumbrances, and municipal violation penalties.</p>"
                    + "<h2>2. Critical Documents Every Buyer Must Scrutinize</h2>"
                    + "<ol>"
                    + "<li><strong>Mother Deed & Chain of Title:</strong> Traces ownership lineage without gaps for at least the preceding 30 years.</li>"
                    + "<li><strong>RERA Registration Certificate:</strong> Guarantees that the project adheres to statutory escrow funding norms and milestone schedules.</li>"
                    + "<li><strong>Non-Encumbrance Certificate (Form 15 & 16):</strong> Confirms the property is free of unpaid mortgages or legal court attachments.</li>"
                    + "<li><strong>Commencement & Occupancy Certificates:</strong> Validates building layout sanctions and official fitness for human occupation.</li>"
                    + "</ol>"
                    + "<h2>3. Practical Buyer Recommendations</h2>"
                    + "<p>Never compromise on hiring an independent property attorney to review original documentation before making token deposits.</p>");
            b2.setCategory("Real Estate");
            b2.setTags("Real Estate, Legal Diligence, RERA, Property Law, Title Verification");
            b2.setCoverImageUrl("https://images.unsplash.com/photo-1450133064473-71024230f91b?w=1200&auto=format&fit=crop");
            b2.setCoverImageAlt("Property legal documentation and RERA compliance checklist");
            b2.setAuthor(admin);
            b2.setAuthorName("Legal & Real Estate Advisory Board");
            b2.setAuthorRole("ADMIN");
            b2.setIsAiGenerated(false);
            b2.setStatus("PUBLISHED");
            b2.setValidityDays(180);
            b2.setPublishedAt(LocalDateTime.now().minusDays(12));
            b2.setExpiresAt(LocalDateTime.now().plusDays(168));
            b2.setViewsCount(2180L);
            b2.setOrganicClicks(590L);
            b2.setOrganicImpressions(7450L);
            b2.setMetaTitle("Legal Checklist for Property Buyers: Title Deeds & RERA");
            b2.setMetaDescription("Step-by-step legal guide for buying property safely. Learn how to verify title deeds, RERA certificates, encumbrance records, and occupancy approvals.");
            b2.setPrimaryKeyword("Legal Checklist Property Buyers");
            b2.setSecondaryKeywords("RERA Compliance, Title Verification, Encumbrance Certificate");
            b2.setSeoScore(92);
            b2.setReadabilityScore(74);
            b2.setGoogleIndexingStatus("INDEXED");
            b2.setSchemaMarkup("{\n  \"@context\": \"https://schema.org\",\n  \"@type\": \"Article\",\n  \"headline\": \"Essential Legal Checklist for Property Buyers: Title Deeds & RERA Compliance\"\n}");
            blogPostRepository.save(b2);

            // Blog 3 (AI Generated)
            BlogPost b3 = new BlogPost();
            b3.setTitle("Future of AI-Powered Semantic Search & Multi-Engine Syndication");
            b3.setSlug("future-of-ai-powered-semantic-search-multi-engine-syndication");
            b3.setSummary("How natural language vector embeddings, cross-engine result aggregation, and smart de-duplication are redefining how modern users find information.");
            b3.setContent("<h2>1. Beyond Exact Keyword Matching</h2>"
                    + "<p>Traditional lexical information retrieval relies heavily on BM25 frequency algorithms. The advent of semantic embeddings and transformer-based understanding allows modern search platforms to parse user intent, context, and nuance.</p>"
                    + "<h2>2. The Architecture of Multi-Source Syndication</h2>"
                    + "<p>By aggregating verified API signals across global providers such as Google, Bing, Yahoo, DuckDuckGo, and Brave, users receive a unified, de-duplicated, and unbiased index of answers.</p>"
                    + "<h2>3. Relevance & Speed at Scale</h2>"
                    + "<p>Sub-100ms response latencies achieved via distributed Lucene index sharding and parallel HTTP client pipelines guarantee a seamless user experience.</p>");
            b3.setCategory("Technology");
            b3.setTags("Technology, Artificial Intelligence, Semantic Search, Search Engine, Multi-Provider");
            b3.setCoverImageUrl("https://images.unsplash.com/photo-1518770660439-4636190af475?w=1200&auto=format&fit=crop");
            b3.setCoverImageAlt("AI Neural Network and semantic search data visualization");
            b3.setAuthor(null);
            b3.setAuthorName("SmartSearch AI Content Bot");
            b3.setAuthorRole("AI");
            b3.setIsAiGenerated(true);
            b3.setStatus("PUBLISHED");
            b3.setValidityDays(90);
            b3.setPublishedAt(LocalDateTime.now().minusDays(2));
            b3.setExpiresAt(LocalDateTime.now().plusDays(88));
            b3.setViewsCount(890L);
            b3.setOrganicClicks(230L);
            b3.setOrganicImpressions(3100L);
            b3.setMetaTitle("AI Semantic Search & Multi-Engine Syndication Future");
            b3.setMetaDescription("Explore the future of semantic search engines powered by AI vector embeddings, multi-provider aggregation, and smart deduplication algorithms.");
            b3.setPrimaryKeyword("AI Semantic Search");
            b3.setSecondaryKeywords("Search Engines, Multi-Provider, Vector Search");
            b3.setSeoScore(96);
            b3.setReadabilityScore(82);
            b3.setGoogleIndexingStatus("CRAWLED_PENDING");
            blogPostRepository.save(b3);

            // Seed sample AI Recommendations for pending review
            AiSeoRecommendation rec1 = new AiSeoRecommendation(
                    b1,
                    "Target High-Opportunity LSI Keyword 'Commercial Real Estate Yields'",
                    "Current keywords focus on residential hubs. Adding commercial sub-sector terms captures high-intent B2B search traffic.",
                    "Commercial lease terms are surging in target micro-markets. Adding this term enhances topical authority for enterprise buyers.",
                    "Estimated +18% B2B Search Discovery",
                    "HIGH",
                    "SUGGEST_KEYWORDS",
                    true,
                    "{\"secondaryKeywords\": \"Property Trends, Rental Yields, Capital Growth, Commercial Real Estate Yields\"}",
                    "AI term analysis detected high search volume and low competition for 'Commercial Real Estate Yields' across Google search logs."
            );
            aiSeoRecommendationRepository.save(rec1);

            AiSeoRecommendation rec2 = new AiSeoRecommendation(
                    b2,
                    "Optimize Headline for Featured Snippet Question-Intent",
                    "Current: 'Essential Legal Checklist for Property Buyers...'\nAI Suggestion: 'What Legal Documents Do You Need to Buy Property? (2026 Checklist)'",
                    "Structuring title as a direct question increases the likelihood of securing the Position Zero Google Featured Snippet box.",
                    "Estimated +25% Featured Snippet Placement Probability",
                    "HIGH",
                    "OPTIMIZE_META_TAGS",
                    true,
                    "{\"metaTitle\": \"What Legal Documents Do You Need to Buy Property? (2026 Checklist)\"}",
                    "Question-based queries with numbered checklists trigger Google Rich Snippet answers in 43% of property advisory searches."
            );
            aiSeoRecommendationRepository.save(rec2);

            logger.info("Seeded initial blog posts, SEO metrics, and AI recommendations.");
        }
    }
}
