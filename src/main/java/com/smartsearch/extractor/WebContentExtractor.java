package com.smartsearch.extractor;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Component
public class WebContentExtractor {

    public ExtractedPageData extract(Document doc, String sourceUrl) {
        ExtractedPageData data = new ExtractedPageData();
        data.setUrl(sourceUrl);
        data.setDomain(extractDomain(sourceUrl));

        // 1. Title
        String title = doc.title();
        if (title.isBlank()) {
            Element ogTitle = doc.selectFirst("meta[property=og:title], meta[name=twitter:title]");
            if (ogTitle != null && !ogTitle.attr("content").isBlank()) {
                title = ogTitle.attr("content");
            } else {
                Element h1 = doc.selectFirst("h1");
                title = (h1 != null && !h1.text().isBlank()) ? h1.text() : data.getDomain();
            }
        }
        data.setTitle(title.trim());

        // 2. Canonical URL
        Element canonical = doc.selectFirst("link[rel=canonical]");
        if (canonical != null && !canonical.attr("href").isBlank()) {
            data.setCanonicalUrl(canonical.attr("abs:href"));
        } else {
            data.setCanonicalUrl(sourceUrl);
        }

        // 3. Meta Description
        String description = "";
        Element metaDesc = doc.selectFirst("meta[name=description], meta[property=og:description]");
        if (metaDesc != null && !metaDesc.attr("content").isBlank()) {
            description = metaDesc.attr("content").trim();
        } else {
            Element p = doc.selectFirst("article p, main p, p");
            if (p != null) {
                description = p.text().trim();
            }
        }
        if (description.length() > 500) {
            description = description.substring(0, 500);
        }
        data.setDescription(description);

        // 4. Headings (h1, h2, h3)
        List<String> headingTexts = new ArrayList<>();
        Elements headings = doc.select("h1, h2, h3");
        for (Element h : headings) {
            String text = h.text().trim();
            if (!text.isBlank() && text.length() < 120) {
                headingTexts.add(text);
                if (headingTexts.size() >= 12) break;
            }
        }
        String combinedHeadings = String.join(" | ", headingTexts);
        if (combinedHeadings.length() > 1900) {
            combinedHeadings = combinedHeadings.substring(0, 1900);
        }
        data.setHeadings(combinedHeadings);

        // 5. Keywords
        Element metaKeywords = doc.selectFirst("meta[name=keywords]");
        if (metaKeywords != null && !metaKeywords.attr("content").isBlank()) {
            String kw = metaKeywords.attr("content").trim();
            data.setKeywords(kw.length() > 490 ? kw.substring(0, 490) : kw);
        } else {
            data.setKeywords(combinedHeadings.length() > 490 ? combinedHeadings.substring(0, 490) : combinedHeadings);
        }

        // 6. Image
        Element ogImage = doc.selectFirst("meta[property=og:image], meta[name=twitter:image]");
        if (ogImage != null && !ogImage.attr("content").isBlank()) {
            data.setImageUrl(ogImage.attr("content").trim());
        }

        // 7. Publication Date
        data.setPublishedDate(extractPublicationDate(doc));

        // 8. Clean Content Text (exclude scripts, styles, nav, footer)
        doc.select("script, style, nav, footer, noscript, svg, header, form").remove();
        String bodyText = doc.body() != null ? doc.body().text() : "";
        if (bodyText.length() > 8000) {
            bodyText = bodyText.substring(0, 8000);
        }
        data.setContent(bodyText);

        // 9. SHA-256 Content Fingerprint
        data.setContentHash(generateFingerprint(bodyText));

        // 10. Quality Score (0 to 100)
        data.setQualityScore(calculateQualityScore(data, bodyText));

        // 11. Extract internal hyperlinks
        List<String> internalLinks = new ArrayList<>();
        Elements links = doc.select("a[href]");
        String baseDomain = data.getDomain();
        for (Element link : links) {
            String absUrl = link.attr("abs:href");
            if (absUrl.startsWith("http://") || absUrl.startsWith("https://")) {
                int hashIdx = absUrl.indexOf('#');
                if (hashIdx != -1) {
                    absUrl = absUrl.substring(0, hashIdx);
                }
                String linkDomain = extractDomain(absUrl);
                if (linkDomain.equalsIgnoreCase(baseDomain) && !absUrl.equalsIgnoreCase(sourceUrl)) {
                    internalLinks.add(absUrl);
                    if (internalLinks.size() >= 15) break;
                }
            }
        }
        data.setDiscoveredInternalLinks(internalLinks);

        return data;
    }

    private LocalDateTime extractPublicationDate(Document doc) {
        try {
            Element timeMeta = doc.selectFirst("meta[property=article:published_time], meta[name=date], meta[name=pubdate]");
            if (timeMeta != null && !timeMeta.attr("content").isBlank()) {
                String dateStr = timeMeta.attr("content").trim();
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
            }
        } catch (Exception ignored) {
        }
        return LocalDateTime.now();
    }

    public String generateFingerprint(String text) {
        if (text == null || text.isBlank()) {
            return "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"; // Empty hash
        }
        try {
            // Normalize: lower case and collapse whitespace
            String normalized = text.toLowerCase().replaceAll("\\s+", " ").trim();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(normalized.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return Integer.toHexString(text.hashCode());
        }
    }

    private double calculateQualityScore(ExtractedPageData data, String bodyText) {
        double score = 40.0;
        if (bodyText.length() > 500) score += 20;
        if (bodyText.length() > 2000) score += 10;
        if (data.getDescription() != null && !data.getDescription().isBlank()) score += 10;
        if (data.getHeadings() != null && !data.getHeadings().isBlank()) score += 10;
        if (data.getImageUrl() != null && !data.getImageUrl().isBlank()) score += 10;
        return Math.min(100.0, score);
    }

    private String extractDomain(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null ? host.replaceFirst("^www\\.", "").toLowerCase() : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }
}

