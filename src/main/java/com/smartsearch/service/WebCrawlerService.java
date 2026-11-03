package com.smartsearch.service;

import com.smartsearch.crawler.UrlSecurityValidator;
import com.smartsearch.dto.IndexPageDto;
import com.smartsearch.model.CrawlErrorLog;
import com.smartsearch.model.IndexedPage;
import com.smartsearch.repository.CrawlErrorLogRepository;
import com.smartsearch.repository.IndexedPageRepository;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.net.ssl.SSLException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class WebCrawlerService {

    private static final Logger logger = LoggerFactory.getLogger(WebCrawlerService.class);

    private final IndexedPageRepository indexedPageRepository;
    private final LuceneService luceneService;
    private final UrlSecurityValidator securityValidator;
    private final CrawlErrorLogRepository crawlErrorLogRepository;

    public WebCrawlerService(IndexedPageRepository indexedPageRepository,
                             LuceneService luceneService,
                             UrlSecurityValidator securityValidator,
                             CrawlErrorLogRepository crawlErrorLogRepository) {
        this.indexedPageRepository = indexedPageRepository;
        this.luceneService = luceneService;
        this.securityValidator = securityValidator;
        this.crawlErrorLogRepository = crawlErrorLogRepository;
    }

    public IndexedPage crawlAndIndex(IndexPageDto dto) {
        String url = dto.getUrl().trim();
        String domain = extractDomain(url);

        // Enforce SSRF & Crawl Safety Policy
        UrlSecurityValidator.ValidationResult valResult = securityValidator.validate(url);
        if (!valResult.isSafe()) {
            logger.warn("Crawl safety rejection for manual indexing on {}: {}", url, valResult.getReason());
            CrawlErrorLog safetyError = new CrawlErrorLog(
                    url,
                    domain,
                    valResult.getErrorType(),
                    400,
                    valResult.getReason(),
                    null
            );
            crawlErrorLogRepository.save(safetyError);
            throw new IllegalArgumentException("Crawl Safety Block: " + valResult.getReason());
        }

        try {
            logger.info("Crawling URL: {}", url);
            Document doc = Jsoup.connect(url)
                    .userAgent("SmartSearchBot/1.0 (+http://smartsearch.local/bot)")
                    .timeout(8000)
                    .followRedirects(true)
                    .get();

            String title = (dto.getTitle() != null && !dto.getTitle().isBlank()) 
                    ? dto.getTitle() 
                    : doc.title();
            if (title.isBlank()) {
                title = domain;
            }

            // Extract meta description
            String description = dto.getDescription();
            if (description == null || description.isBlank()) {
                Element metaDesc = doc.selectFirst("meta[name=description], meta[property=og:description]");
                if (metaDesc != null) {
                    description = metaDesc.attr("content");
                }
            }
            if (description == null || description.isBlank()) {
                Element p = doc.selectFirst("p");
                description = (p != null) ? p.text() : "";
            }
            if (description.length() > 500) {
                description = description.substring(0, 500);
            }

            // Extract keywords
            String keywords = dto.getKeywords();
            if (keywords == null || keywords.isBlank()) {
                Element metaKeywords = doc.selectFirst("meta[name=keywords]");
                if (metaKeywords != null) {
                    keywords = metaKeywords.attr("content");
                }
            }

            // Extract image
            Element ogImage = doc.selectFirst("meta[property=og:image], meta[name=twitter:image]");
            String imageUrl = (ogImage != null) ? ogImage.attr("content") : null;

            // Extract clean body text
            String bodyText = doc.body().text();
            if (bodyText.length() > 8000) {
                bodyText = bodyText.substring(0, 8000);
            }

            String category = (dto.getCategory() != null && !dto.getCategory().isBlank()) 
                    ? dto.getCategory().toUpperCase() 
                    : "ALL";

            Optional<IndexedPage> existingOpt = indexedPageRepository.findByUrl(url);
            IndexedPage page;
            if (existingOpt.isPresent()) {
                page = existingOpt.get();
                page.setTitle(title);
                page.setDescription(description);
                page.setKeywords(keywords);
                page.setContent(bodyText);
                page.setImageUrl(imageUrl);
                page.setCategory(category);
                page.setDomain(domain);
                page.setStatus("INDEXED");
                page.setLastCrawledAt(LocalDateTime.now());
            } else {
                page = new IndexedPage(url, title, description, keywords, category, bodyText, imageUrl, domain);
            }

            IndexedPage saved = indexedPageRepository.save(page);
            luceneService.indexDocument(saved);
            logger.info("Successfully crawled and indexed: {}", url);
            return saved;

        } catch (HttpStatusException httpEx) {
            int statusCode = httpEx.getStatusCode();
            logger.error("HTTP error {} while crawling URL {}: {}", statusCode, url, httpEx.getMessage());
            crawlErrorLogRepository.save(new CrawlErrorLog(
                    url, domain, "HTTP_STATUS", statusCode, "HTTP " + statusCode + ": " + httpEx.getMessage(), null
            ));
            recordFailedPage(url, domain, "HTTP " + statusCode + ": " + httpEx.getMessage());
            throw new RuntimeException("Crawling failed with HTTP " + statusCode + " for " + url, httpEx);
        } catch (SocketTimeoutException timeoutEx) {
            logger.error("Timeout while crawling URL {}: {}", url, timeoutEx.getMessage());
            crawlErrorLogRepository.save(new CrawlErrorLog(
                    url, domain, "TIMEOUT", 408, "Connection or read timed out after 8s: " + timeoutEx.getMessage(), null
            ));
            recordFailedPage(url, domain, "Timeout 408: " + timeoutEx.getMessage());
            throw new RuntimeException("Crawling timed out for " + url, timeoutEx);
        } catch (UnknownHostException uhe) {
            logger.error("DNS failure resolving {}: {}", url, uhe.getMessage());
            crawlErrorLogRepository.save(new CrawlErrorLog(
                    url, domain, "DNS_FAILURE", 0, "Host DNS could not be resolved: " + uhe.getMessage(), null
            ));
            recordFailedPage(url, domain, "DNS Failure: " + uhe.getMessage());
            throw new RuntimeException("Could not resolve host DNS for " + url, uhe);
        } catch (SSLException sslEx) {
            logger.error("SSL/TLS error crawling {}: {}", url, sslEx.getMessage());
            crawlErrorLogRepository.save(new CrawlErrorLog(
                    url, domain, "SSL_ERROR", 0, "SSL certificate or TLS handshake failed: " + sslEx.getMessage(), null
            ));
            recordFailedPage(url, domain, "SSL Error: " + sslEx.getMessage());
            throw new RuntimeException("SSL handshake failed for " + url, sslEx);
        } catch (Exception e) {
            logger.error("Failed to crawl URL: " + url, e);
            crawlErrorLogRepository.save(new CrawlErrorLog(
                    url, domain, "PARSING_ERROR", 0, e.getMessage(), null
            ));
            recordFailedPage(url, domain, "Crawling failed: " + e.getMessage());
            throw new RuntimeException("Crawling failed for " + url + ": " + e.getMessage(), e);
        }
    }

    private void recordFailedPage(String url, String domain, String reason) {
        IndexedPage failedPage = indexedPageRepository.findByUrl(url)
                .orElseGet(() -> new IndexedPage(url, domain, reason, "", "ALL", "", null, domain));
        failedPage.setStatus("FAILED");
        indexedPageRepository.save(failedPage);
    }

    private String extractDomain(String url) {
        try {
            URI uri = new URI(url);
            String host = uri.getHost();
            if (host != null && host.startsWith("www.")) {
                return host.substring(4);
            }
            return host != null ? host : url;
        } catch (Exception e) {
            return url;
        }
    }
}
