package com.smartsearch.service;

import com.smartsearch.model.IndexedPage;
import com.smartsearch.model.Role;
import com.smartsearch.model.User;
import com.smartsearch.repository.IndexedPageRepository;
import com.smartsearch.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DataInitializerService implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataInitializerService.class);

    private final UserRepository userRepository;
    private final IndexedPageRepository indexedPageRepository;
    private final LuceneService luceneService;
    private final PasswordEncoder passwordEncoder;
    private final com.smartsearch.repository.SearchProviderConfigRepository providerConfigRepository;
    private final SystemSettingService settingService;

    public DataInitializerService(UserRepository userRepository,
                                  IndexedPageRepository indexedPageRepository,
                                  LuceneService luceneService,
                                  PasswordEncoder passwordEncoder,
                                  com.smartsearch.repository.SearchProviderConfigRepository providerConfigRepository,
                                  SystemSettingService settingService) {
        this.userRepository = userRepository;
        this.indexedPageRepository = indexedPageRepository;
        this.luceneService = luceneService;
        this.passwordEncoder = passwordEncoder;
        this.providerConfigRepository = providerConfigRepository;
        this.settingService = settingService;
    }

    @Override
    public void run(ApplicationArguments args) {
        initUsers();
        initIndexedPages();
        initSearchProviders();
        initCrawlerSettings();
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
        if (providerConfigRepository.count() == 0) {
            List<com.smartsearch.model.SearchProviderConfig> configs = new ArrayList<>();
            configs.add(new com.smartsearch.model.SearchProviderConfig(
                    "GOOGLE", "Google Custom Search JSON API", true,
                    settingService.getSetting("GOOGLE_API_KEY", ""),
                    settingService.getSetting("GOOGLE_SEARCH_ENGINE_ID", ""),
                    "https://www.googleapis.com/customsearch/v1", 1
            ));
            configs.add(new com.smartsearch.model.SearchProviderConfig(
                    "BING", "Microsoft Bing Web Search API v7", true,
                    settingService.getSetting("BING_API_KEY", ""), "",
                    "https://api.bing.microsoft.com/v7.0/search", 2
            ));
            configs.add(new com.smartsearch.model.SearchProviderConfig(
                    "BRAVE", "Brave Search API", true,
                    settingService.getSetting("BRAVE_API_KEY", ""), "",
                    "https://api.search.brave.com/res/v1/web/search", 3
            ));
            configs.add(new com.smartsearch.model.SearchProviderConfig(
                    "WIKIPEDIA", "Wikimedia Open Search API", true,
                    "", "", "https://en.wikipedia.org/w/api.php", 4
            ));

            providerConfigRepository.saveAll(configs);
            logger.info("Initialized 4 default search providers (Google, Bing, Brave, Wikipedia).");
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
}
