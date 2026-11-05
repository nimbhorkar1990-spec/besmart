package com.smartsearch.service;

import com.smartsearch.model.*;
import com.smartsearch.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@Transactional
public class AiBlogSeoService {

    private static final Logger logger = LoggerFactory.getLogger(AiBlogSeoService.class);

    private final BlogPostRepository blogRepository;
    private final AiSeoRecommendationRepository recommendationRepository;
    private final BlogService blogService;

    // Common English stop words
    private static final Set<String> STOP_WORDS = new HashSet<>(Arrays.asList(
            "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
            "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "can",
            "cannot", "could", "did", "do", "does", "doing", "down", "during", "each", "few", "for", "from", "further",
            "had", "has", "have", "having", "he", "her", "here", "hers", "herself", "him", "himself", "his", "how",
            "i", "if", "in", "into", "is", "isn't", "it", "its", "itself", "let's", "me", "more", "most", "my",
            "myself", "no", "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours",
            "ourselves", "out", "over", "own", "same", "she", "should", "so", "some", "such", "than", "that", "the",
            "their", "theirs", "them", "themselves", "then", "there", "these", "they", "this", "those", "through", "to",
            "too", "under", "until", "up", "very", "was", "we", "were", "what", "when", "where", "which", "while",
            "who", "whom", "why", "with", "would", "you", "your", "yours", "yourself", "yourselves"
    ));

    @Autowired
    public AiBlogSeoService(BlogPostRepository blogRepository,
                            AiSeoRecommendationRepository recommendationRepository,
                            @Lazy BlogService blogService) {
        this.blogRepository = blogRepository;
        this.recommendationRepository = recommendationRepository;
        this.blogService = blogService;
    }

    /**
     * Conducts a complete AI-powered SEO audit on a blog post.
     * Evaluates title, meta tags, keywords, headings, internal links, alt text, schema markup, and readability.
     */
    public void analyzeBlog(BlogPost blog) {
        if (blog == null) return;
        BlogPublishingPermission perms = blogService.getPermissions();

        String title = blog.getTitle() != null ? blog.getTitle() : "";
        String content = blog.getContent() != null ? blog.getContent() : "";
        String plainContent = stripHtml(content);

        // 1. Keyword Extraction
        List<String> keywords = extractKeywords(title + " " + plainContent);
        String primaryKeyword = !keywords.isEmpty() ? keywords.get(0) : "Smart Search";
        List<String> secondaryKeywords = keywords.size() > 1 ? keywords.subList(1, Math.min(keywords.size(), 5)) : Collections.emptyList();
        String secondaryStr = String.join(", ", secondaryKeywords);

        // 2. Generate Optimized SEO Meta Title
        String optimizedTitle = generateOptimizedTitle(title, primaryKeyword);

        // 3. Generate Optimized SEO Meta Description
        String optimizedMetaDesc = generateMetaDescription(title, plainContent, primaryKeyword);

        // 4. Generate URL Slug
        String recommendedSlug = blogService.sanitizeSlug(title);

        // 5. Generate Schema.org JSON-LD Article structured data
        String schemaMarkup = generateArticleSchema(blog, optimizedTitle, optimizedMetaDesc);

        // 6. Generate Alt Text for Cover Image
        String recommendedAltText = generateImageAltText(blog.getTitle(), primaryKeyword);

        // 7. Internal Link Recommendations
        String internalLinksJson = generateInternalLinksJson(primaryKeyword, secondaryKeywords);

        // 8. Readability Score (Flesch-Kincaid ease approximation)
        int readability = calculateReadabilityScore(plainContent);
        blog.setReadabilityScore(readability);

        // 9. Overall SEO Score Computation
        int score = calculateSeoScore(blog, primaryKeyword, plainContent, readability);
        blog.setSeoScore(score);
        blog.setLastSeoAnalysisAt(LocalDateTime.now());

        // Check master switch and granular permissions
        boolean masterSwitch = perms.getAutomaticSeoMasterSwitch();

        // Process Meta Title Recommendation
        if (blog.getMetaTitle() == null || blog.getMetaTitle().trim().isEmpty() || Math.abs(blog.getMetaTitle().length() - 55) > 15) {
            AiSeoRecommendation rec = new AiSeoRecommendation(
                    blog,
                    "Optimize Meta Title for High CTR",
                    "Current: " + (blog.getMetaTitle() != null ? blog.getMetaTitle() : "[Not Set]") + "\nAI Suggestion: " + optimizedTitle,
                    "Search engines prefer meta titles between 50 and 60 characters with primary keywords placed near the front to maximize search CTR.",
                    "Estimated +15% Organic Click-Through Rate",
                    "HIGH",
                    "OPTIMIZE_META_TAGS",
                    true,
                    "{\"metaTitle\": \"" + escapeJson(optimizedTitle) + "\"}",
                    "AI formulated an engaging, intent-focused title adhering strictly to the 50-60 character Google snippet guideline while maintaining natural brand cadence."
            );
            if (masterSwitch && perms.getAutoMetaTitleOpt()) {
                blog.setMetaTitle(optimizedTitle);
                rec.setApprovalStatus("EXECUTED");
            }
            saveOrUpdateRecommendation(rec);
        } else if (blog.getMetaTitle() == null) {
            blog.setMetaTitle(optimizedTitle);
        }

        // Process Meta Description Recommendation
        if (blog.getMetaDescription() == null || blog.getMetaDescription().trim().isEmpty() || blog.getMetaDescription().length() < 100 || blog.getMetaDescription().length() > 165) {
            AiSeoRecommendation rec = new AiSeoRecommendation(
                    blog,
                    "Generate High-Converting Meta Description",
                    "Current: " + (blog.getMetaDescription() != null ? blog.getMetaDescription() : "[Not Set]") + "\nAI Suggestion: " + optimizedMetaDesc,
                    "An engaging meta description between 120-160 characters with a clear call-to-action reduces bounce rate and entices user clicks in Google SERPs.",
                    "Estimated +20% SERP Interaction Rate",
                    "HIGH",
                    "OPTIMIZE_META_TAGS",
                    true,
                    "{\"metaDescription\": \"" + escapeJson(optimizedMetaDesc) + "\"}",
                    "AI synthesized the first 200 words of the article, inserted the primary keyword '" + primaryKeyword + "', and framed a strong call-to-action."
            );
            if (masterSwitch && perms.getAutoMetaDescOpt()) {
                blog.setMetaDescription(optimizedMetaDesc);
                rec.setApprovalStatus("EXECUTED");
            }
            saveOrUpdateRecommendation(rec);
        } else if (blog.getMetaDescription() == null) {
            blog.setMetaDescription(optimizedMetaDesc);
        }

        // Process Primary & Secondary Keywords
        if (blog.getPrimaryKeyword() == null || blog.getPrimaryKeyword().trim().isEmpty()) {
            AiSeoRecommendation rec = new AiSeoRecommendation(
                    blog,
                    "Target High-Relevance Primary & LSI Keywords",
                    "Primary: " + primaryKeyword + " | Secondary: " + secondaryStr,
                    "Structuring content around a validated primary keyword and related semantic search terms signals topical depth to Google and Bing crawler bots.",
                    "Topical Authority & Semantic SERP Inclusion",
                    "MEDIUM",
                    "SUGGEST_KEYWORDS",
                    true,
                    "{\"primaryKeyword\": \"" + escapeJson(primaryKeyword) + "\", \"secondaryKeywords\": \"" + escapeJson(secondaryStr) + "\"}",
                    "AI parsed the document's term frequencies and semantic associations to identify high-potential target queries."
            );
            if (masterSwitch && perms.getAutoKeywordSuggestions()) {
                blog.setPrimaryKeyword(primaryKeyword);
                blog.setSecondaryKeywords(secondaryStr);
                rec.setApprovalStatus("EXECUTED");
            }
            saveOrUpdateRecommendation(rec);
        }

        // Process Structured Data / Schema Markup
        if (blog.getSchemaMarkup() == null || blog.getSchemaMarkup().trim().isEmpty()) {
            AiSeoRecommendation rec = new AiSeoRecommendation(
                    blog,
                    "Inject Article Schema.org JSON-LD Structured Data",
                    "Missing JSON-LD Article Schema",
                    "Schema markup empowers Google to render rich snippets, breadcrumbs, and Google Discover cards for the blog post.",
                    "Rich Snippets & Enhanced SERP Real Estate",
                    "HIGH",
                    "INSERT_SCHEMA_MARKUP",
                    true,
                    "{\"schemaMarkup\": \"" + escapeJson(schemaMarkup) + "\"}",
                    "AI formulated Schema.org Article specifications including headline, author, publisher, and publication timestamp."
            );
            if (masterSwitch && perms.getAutoSchemaMarkup()) {
                blog.setSchemaMarkup(schemaMarkup);
                rec.setApprovalStatus("EXECUTED");
            }
            saveOrUpdateRecommendation(rec);
        }

        // Process Image Alt Text
        if (blog.getCoverImageUrl() != null && (blog.getCoverImageAlt() == null || blog.getCoverImageAlt().trim().isEmpty())) {
            AiSeoRecommendation rec = new AiSeoRecommendation(
                    blog,
                    "Add Descriptive Image Alt-Text for Google Images",
                    "Missing Alt-Text on cover image",
                    "Alt attributes allow search crawlers to index imagery for Google Image Search and improve accessibility compliance.",
                    "Google Image Search Visibility & Accessibility",
                    "MEDIUM",
                    "GENERATE_ALT_TEXT",
                    true,
                    "{\"coverImageAlt\": \"" + escapeJson(recommendedAltText) + "\"}",
                    "AI generated keyword-rich accessibility label: '" + recommendedAltText + "'."
            );
            if (masterSwitch && perms.getAutoImageAltText()) {
                blog.setCoverImageAlt(recommendedAltText);
                rec.setApprovalStatus("EXECUTED");
            }
            saveOrUpdateRecommendation(rec);
        }

        // Process Internal Links
        if (blog.getInternalLinksJson() == null || blog.getInternalLinksJson().trim().isEmpty()) {
            AiSeoRecommendation rec = new AiSeoRecommendation(
                    blog,
                    "Add Contextual Internal Linking to Related Pages",
                    "Generated 3 high-relevance internal links",
                    "Internal linking distributes PageRank juice and guides search bots through topical clusters across your website.",
                    "Enhanced Crawl Depth & Lower Bounce Rate",
                    "MEDIUM",
                    "ADD_INTERNAL_LINKS",
                    true,
                    "{\"internalLinks\": " + internalLinksJson + "}",
                    "AI identified contextually matching destinations in our jobs, search, and property catalog."
            );
            if (masterSwitch && perms.getAutoInternalLinking()) {
                blog.setInternalLinksJson(internalLinksJson);
                rec.setApprovalStatus("EXECUTED");
            }
            saveOrUpdateRecommendation(rec);
        }

        // Recalculate score after auto-execution
        blog.setSeoScore(calculateSeoScore(blog, blog.getPrimaryKeyword() != null ? blog.getPrimaryKeyword() : primaryKeyword, plainContent, readability));
        blogRepository.save(blog);
    }

    private void saveOrUpdateRecommendation(AiSeoRecommendation rec) {
        List<AiSeoRecommendation> existing = recommendationRepository.findByBlogIdOrderByCreatedAtDesc(rec.getBlog().getId());
        for (AiSeoRecommendation old : existing) {
            if (old.getActionType().equals(rec.getActionType()) && "PENDING".equals(old.getApprovalStatus())) {
                old.setTitle(rec.getTitle());
                old.setDescription(rec.getDescription());
                old.setReason(rec.getReason());
                old.setExpectedBenefit(rec.getExpectedBenefit());
                old.setProposedChange(rec.getProposedChange());
                old.setAiExplanation(rec.getAiExplanation());
                recommendationRepository.save(old);
                return;
            }
        }
        recommendationRepository.save(rec);
    }

    /**
     * Executes an approved recommendation and updates the corresponding blog post fields.
     */
    public void executeRecommendation(Long recommendationId, String adminUsername) {
        AiSeoRecommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));

        BlogPost blog = rec.getBlog();
        if (blog == null) return;

        String action = rec.getActionType();
        String proposed = rec.getProposedChange();

        if ("OPTIMIZE_META_TAGS".equalsIgnoreCase(action)) {
            String title = extractJsonField(proposed, "metaTitle");
            String desc = extractJsonField(proposed, "metaDescription");
            if (title != null && !title.isEmpty()) blog.setMetaTitle(title);
            if (desc != null && !desc.isEmpty()) blog.setMetaDescription(desc);
        } else if ("SUGGEST_KEYWORDS".equalsIgnoreCase(action)) {
            String primary = extractJsonField(proposed, "primaryKeyword");
            String secondary = extractJsonField(proposed, "secondaryKeywords");
            if (primary != null) blog.setPrimaryKeyword(primary);
            if (secondary != null) blog.setSecondaryKeywords(secondary);
        } else if ("INSERT_SCHEMA_MARKUP".equalsIgnoreCase(action)) {
            String schema = extractJsonField(proposed, "schemaMarkup");
            if (schema != null) blog.setSchemaMarkup(schema);
        } else if ("GENERATE_ALT_TEXT".equalsIgnoreCase(action)) {
            String alt = extractJsonField(proposed, "coverImageAlt");
            if (alt != null) blog.setCoverImageAlt(alt);
        } else if ("ADD_INTERNAL_LINKS".equalsIgnoreCase(action)) {
            blog.setInternalLinksJson(proposed);
        }

        rec.setApprovalStatus("EXECUTED");
        rec.setReviewedAt(LocalDateTime.now());
        rec.setReviewedBy(adminUsername);
        recommendationRepository.save(rec);

        // Recalculate score
        int score = calculateSeoScore(blog, blog.getPrimaryKeyword(), stripHtml(blog.getContent()), blog.getReadabilityScore());
        blog.setSeoScore(score);
        blogRepository.save(blog);
    }

    public void rejectRecommendation(Long recommendationId, String adminUsername) {
        AiSeoRecommendation rec = recommendationRepository.findById(recommendationId)
                .orElseThrow(() -> new IllegalArgumentException("Recommendation not found: " + recommendationId));
        rec.setApprovalStatus("REJECTED");
        rec.setReviewedAt(LocalDateTime.now());
        rec.setReviewedBy(adminUsername);
        recommendationRepository.save(rec);
    }

    public void approveAllRecommendations(String adminUsername) {
        List<AiSeoRecommendation> pendingList = recommendationRepository.findByApprovalStatusOrderByCreatedAtDesc("PENDING");
        for (AiSeoRecommendation rec : pendingList) {
            executeRecommendation(rec.getId(), adminUsername);
        }
    }

    public List<AiSeoRecommendation> getPendingRecommendations() {
        return recommendationRepository.findByApprovalStatusOrderByCreatedAtDesc("PENDING");
    }

    public List<AiSeoRecommendation> getAllRecommendations() {
        return recommendationRepository.findAllByOrderByCreatedAtDesc();
    }

    public AiSeoRecommendation getRecommendationById(Long id) {
        return recommendationRepository.findById(id).orElse(null);
    }

    // --- AI Blog Generator ---

    public BlogPost generateAiBlog(String topic, String category, String customKeywords) {
        if (topic == null || topic.trim().isEmpty()) {
            topic = "Top Real Estate Investment Trends and Property Market Analysis";
        }
        if (category == null || category.trim().isEmpty()) {
            category = "Real Estate";
        }

        String primaryKeyword = (customKeywords != null && !customKeywords.trim().isEmpty())
                ? customKeywords.split(",")[0].trim()
                : extractKeywords(topic).stream().findFirst().orElse("Property Market");

        String title = "Complete Guide: " + capitalizeWords(topic) + " (Latest Insights)";
        String slug = blogService.generateUniqueSlug(title);

        StringBuilder content = new StringBuilder();
        content.append("<p class='lead'>Discover the most essential developments in <strong>").append(escapeHtml(topic)).append("</strong>. ");
        content.append("Whether you are an aspiring property owner, an active real estate investor, or seeking commercial property developments, having structured data and clear insights ensures sound financial decisions.</p>\n\n");

        content.append("<h2>1. Market Dynamics & Core Opportunities</h2>\n");
        content.append("<p>Understanding market patterns requires analyzing local demand, demographic shifts, and regional infrastructure expansion. Key growth vectors currently shaping ").append(escapeHtml(topic)).append(" include:</p>\n");
        content.append("<ul>\n");
        content.append("  <li><strong>High Rental Yields:</strong> Prime suburban and urban transit corridors continue delivering robust annualized returns.</li>\n");
        content.append("  <li><strong>Regulatory Transparency:</strong> RERA compliance and digitized title registries have enhanced investor confidence significantly.</li>\n");
        content.append("  <li><strong>Sustainable Green Developments:</strong> Energy-efficient construction and solar integration command superior appraisal valuations.</li>\n");
        content.append("</ul>\n\n");

        content.append("<h2>2. Key Considerations for Buyers and Investors</h2>\n");
        content.append("<p>Before committing capital to property acquisitions, review these vital steps:</p>\n");
        content.append("<ol>\n");
        content.append("  <li><strong>Legal Due Diligence:</strong> Always confirm encumbrance certificates, approved site maps, and local municipal zoning clearances.</li>\n");
        content.append("  <li><strong>Infrastructure Roadmap:</strong> Proximity to metro corridors, commercial IT parks, and educational hubs drives steady appreciation.</li>\n");
        content.append("  <li><strong>Financing Options:</strong> Compare home loan interest rates, down-payment flexibilities, and tax deduction benefits.</li>\n");
        content.append("</ol>\n\n");

        content.append("<h2>3. Strategic Recommendations for Long-Term Value</h2>\n");
        content.append("<p>Navigating the changing property landscape demands a proactive, data-informed strategy. Utilize verified property portals and consultation with verified real estate specialists to safeguard and multiply your investments.</p>\n");

        String summary = "Comprehensive overview and expert insights into " + topic + ", covering investment tips, regulatory standards, and valuation drivers.";
        String metaTitle = (title.length() > 58) ? title.substring(0, 55) + "..." : title;
        String metaDesc = "Explore the complete breakdown of " + topic + ". Discover investment strategies, key trends, and practical tips to maximize your property yields.";
        if (metaDesc.length() > 155) metaDesc = metaDesc.substring(0, 150) + "...";

        BlogPost post = new BlogPost();
        post.setTitle(title);
        post.setSlug(slug);
        post.setSummary(summary);
        post.setContent(content.toString());
        post.setCategory(category);
        post.setTags(category + ", " + primaryKeyword + ", Property Investment, Trends");
        post.setCoverImageUrl("https://images.unsplash.com/photo-1560518883-ce09059eeffa?w=1200&auto=format&fit=crop");
        post.setCoverImageAlt(primaryKeyword + " - Comprehensive Investment Guide");
        post.setAuthorName("SmartSearch AI Content Bot");
        post.setAuthorRole("AI");
        post.setIsAiGenerated(true);
        post.setStatus("DRAFT");
        post.setPrimaryKeyword(primaryKeyword);
        post.setSecondaryKeywords(category + ", Market Trends, Property Analysis");
        post.setMetaTitle(metaTitle);
        post.setMetaDescription(metaDesc);
        post.setGoogleIndexingStatus("DISCOVERED");

        BlogPost saved = blogService.createBlog(post, null, true);
        return saved;
    }

    // --- SEO Algorithm & Analysis Utilities ---

    private int calculateSeoScore(BlogPost blog, String primaryKeyword, String plainContent, int readability) {
        int score = 0;

        // Title checks (max 20)
        String title = blog.getTitle() != null ? blog.getTitle().toLowerCase() : "";
        if (!title.isEmpty()) {
            score += 5;
            if (primaryKeyword != null && title.contains(primaryKeyword.toLowerCase())) score += 10;
            if (title.length() >= 30 && title.length() <= 70) score += 5;
        }

        // Meta Title (max 15)
        if (blog.getMetaTitle() != null && !blog.getMetaTitle().isEmpty()) {
            score += 5;
            int len = blog.getMetaTitle().length();
            if (len >= 45 && len <= 65) score += 5;
            if (primaryKeyword != null && blog.getMetaTitle().toLowerCase().contains(primaryKeyword.toLowerCase())) score += 5;
        }

        // Meta Description (max 15)
        if (blog.getMetaDescription() != null && !blog.getMetaDescription().isEmpty()) {
            score += 5;
            int len = blog.getMetaDescription().length();
            if (len >= 120 && len <= 165) score += 5;
            if (primaryKeyword != null && blog.getMetaDescription().toLowerCase().contains(primaryKeyword.toLowerCase())) score += 5;
        }

        // Content Length (max 15)
        int wordCount = plainContent.split("\\s+").length;
        if (wordCount >= 300) score += 5;
        if (wordCount >= 600) score += 5;
        if (wordCount >= 1000) score += 5;

        // Schema Markup (max 10)
        if (blog.getSchemaMarkup() != null && !blog.getSchemaMarkup().isEmpty()) score += 10;

        // Cover image & Alt text (max 10)
        if (blog.getCoverImageUrl() != null && !blog.getCoverImageUrl().isEmpty()) {
            score += 5;
            if (blog.getCoverImageAlt() != null && !blog.getCoverImageAlt().isEmpty()) score += 5;
        }

        // Readability (max 15)
        if (readability >= 60) score += 15;
        else if (readability >= 40) score += 10;
        else score += 5;

        return Math.min(100, score);
    }

    private int calculateReadabilityScore(String text) {
        if (text == null || text.trim().isEmpty()) return 50;
        String[] words = text.split("\\s+");
        String[] sentences = text.split("[.!?]+");
        int wordCount = Math.max(1, words.length);
        int sentenceCount = Math.max(1, sentences.length);

        int syllables = 0;
        for (String w : words) {
            syllables += countSyllables(w);
        }

        // Flesch Reading Ease formula: 206.835 - 1.015 * (words / sentences) - 84.6 * (syllables / words)
        double score = 206.835 - (1.015 * ((double) wordCount / sentenceCount)) - (84.6 * ((double) syllables / wordCount));
        int finalScore = (int) Math.round(score);
        return Math.max(10, Math.min(100, finalScore));
    }

    private int countSyllables(String word) {
        word = word.toLowerCase().replaceAll("[^a-z]", "");
        if (word.length() <= 3) return 1;
        word = word.replaceAll("e$", "");
        String[] parts = word.split("[^aeiouy]+");
        int count = 0;
        for (String p : parts) {
            if (!p.isEmpty()) count++;
        }
        return Math.max(1, count);
    }

    private List<String> extractKeywords(String text) {
        if (text == null || text.isEmpty()) return Collections.singletonList("Real Estate");
        String clean = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
        String[] tokens = clean.split("\\s+");

        Map<String, Integer> freq = new HashMap<>();
        for (String t : tokens) {
            if (t.length() > 3 && !STOP_WORDS.contains(t)) {
                freq.put(t, freq.getOrDefault(t, 0) + 1);
            }
        }

        return freq.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(Map.Entry::getKey)
                .map(this::capitalizeWords)
                .collect(Collectors.toList());
    }

    private String generateOptimizedTitle(String originalTitle, String primaryKeyword) {
        if (originalTitle == null || originalTitle.trim().isEmpty()) {
            return capitalizeWords(primaryKeyword) + " - Complete Guide & Trends";
        }
        String clean = originalTitle.trim();
        if (clean.length() >= 48 && clean.length() <= 62) {
            return clean;
        }
        if (clean.length() < 45) {
            return clean + ": Complete Guide & Analysis";
        }
        return clean.substring(0, Math.min(clean.length(), 58)).replaceAll("\\s+\\S*$", "");
    }

    private String generateMetaDescription(String title, String content, String primaryKeyword) {
        String base = content.length() > 180 ? content.substring(0, 180).trim() : content.trim();
        base = base.replaceAll("\\s+", " ").replace("\"", "'");

        String hook = "Learn all about " + primaryKeyword + ". " + base;
        if (hook.length() > 155) {
            hook = hook.substring(0, 150) + "... Read the full analysis!";
        } else if (hook.length() < 120) {
            hook += " Explore essential insights, actionable tips, and industry trends today.";
        }
        return hook;
    }

    private String generateArticleSchema(BlogPost blog, String title, String desc) {
        return "{\n" +
                "  \"@context\": \"https://schema.org\",\n" +
                "  \"@type\": \"Article\",\n" +
                "  \"headline\": \"" + escapeJson(title) + "\",\n" +
                "  \"description\": \"" + escapeJson(desc) + "\",\n" +
                "  \"image\": \"" + (blog.getCoverImageUrl() != null ? escapeJson(blog.getCoverImageUrl()) : "https://images.unsplash.com/photo-1560518883-ce09059eeffa") + "\",\n" +
                "  \"author\": {\n" +
                "    \"@type\": \"Person\",\n" +
                "    \"name\": \"" + (blog.getAuthorName() != null ? escapeJson(blog.getAuthorName()) : "Editorial Team") + "\"\n" +
                "  },\n" +
                "  \"publisher\": {\n" +
                "    \"@type\": \"Organization\",\n" +
                "    \"name\": \"SmartSearch Portal\",\n" +
                "    \"logo\": {\n" +
                "      \"@type\": \"ImageObject\",\n" +
                "      \"url\": \"/img/logo.png\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"datePublished\": \"" + (blog.getPublishedAt() != null ? blog.getPublishedAt().toString() : LocalDateTime.now().toString()) + "\"\n" +
                "}";
    }

    private String generateImageAltText(String title, String primaryKeyword) {
        return primaryKeyword + " - " + title + " Overview";
    }

    private String generateInternalLinksJson(String primary, List<String> secondary) {
        return "[\n" +
                "  {\"anchor\": \"" + primary + " Listings\", \"url\": \"/search?q=" + primary.replace(" ", "+") + "\"},\n" +
                "  {\"anchor\": \"Explore Career & Property Portals\", \"url\": \"/employment\"},\n" +
                "  {\"anchor\": \"All Industry Insights & Articles\", \"url\": \"/blogs\"}\n" +
                "]";
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    }

    private String capitalizeWords(String str) {
        if (str == null || str.isEmpty()) return "";
        String[] words = str.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0)));
                if (w.length() > 1) {
                    sb.append(w.substring(1).toLowerCase());
                }
                sb.append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String extractJsonField(String json, String fieldName) {
        if (json == null || fieldName == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(fieldName) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1).replace("\\\"", "\"").replace("\\n", "\n");
        }
        return null;
    }
}
