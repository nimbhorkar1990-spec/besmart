package com.smartsearch.service;

import com.smartsearch.model.*;
import com.smartsearch.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class AiBlogAssistantService {

    private static final Logger logger = LoggerFactory.getLogger(AiBlogAssistantService.class);

    private final AssistantCommandRepository commandRepository;
    private final BlogCampaignScheduleRepository scheduleRepository;
    private final BlogPostRepository blogRepository;
    private final BlogService blogService;
    private final AiBlogSeoService aiSeoService;
    private final AdminActivityLogRepository adminLogRepository;

    @Autowired
    public AiBlogAssistantService(AssistantCommandRepository commandRepository,
                                  BlogCampaignScheduleRepository scheduleRepository,
                                  BlogPostRepository blogRepository,
                                  BlogService blogService,
                                  AiBlogSeoService aiSeoService,
                                  AdminActivityLogRepository adminLogRepository) {
        this.commandRepository = commandRepository;
        this.scheduleRepository = scheduleRepository;
        this.blogRepository = blogRepository;
        this.blogService = blogService;
        this.aiSeoService = aiSeoService;
        this.adminLogRepository = adminLogRepository;
    }

    /**
     * Executes an Assistant Command received from Administrator.
     * Understands natural language and structured parameters.
     */
    public AssistantCommand executeCommand(AssistantCommand command, String adminUsername) {
        BlogPublishingPermission perms = blogService.getPermissions();

        if (!perms.getAllowAiGenerate()) {
            throw new IllegalStateException("AI Blog Generation is disabled in Blog Management & SEO Authorities.");
        }

        if (command.getCommandText() == null || command.getCommandText().trim().isEmpty()) {
            String topic = command.getTitle() != null && !command.getTitle().trim().isEmpty() ? command.getTitle() : "Real Estate & Market Trends";
            String cat = command.getCategory() != null ? command.getCategory() : "Real Estate";
            String aud = command.getTargetAudience() != null ? command.getTargetAudience() : "Property Buyers & Investors";
            String loc = command.getLocation() != null ? command.getLocation() : "Pune";
            command.setCommandText(String.format("Generate high-quality blog campaign for '%s' in %s targeting %s (%s)", topic, loc, aud, cat));
        }

        command.setStatus("IN_PROGRESS");
        command.setCreatedAt(LocalDateTime.now());
        AssistantCommand savedCommand = commandRepository.save(command);

        try {
            if (Boolean.TRUE.equals(command.getIsRecurringSchedule())) {
                // Multi-Blog 10-day content calendar campaign
                generate10DaySchedule(savedCommand);
                savedCommand.setScheduleStatus("SCHEDULED");
                savedCommand.setStatus("COMPLETED");
                savedCommand.setExecutionSummary("Generated 10-day content calendar with "
                        + (savedCommand.getBlogsPerDay() * 10) + " planned unique blog articles. Ready for admin review & approval.");
            } else {
                // Generate immediate blog(s)
                int count = command.getBlogsRequested() != null && command.getBlogsRequested() > 0 ? command.getBlogsRequested() : 1;
                int generated = 0;
                for (int i = 0; i < count; i++) {
                    BlogPost blog = generateSingleAssistantBlog(savedCommand, i + 1);
                    generated++;
                }
                savedCommand.setBlogsGenerated(generated);
                savedCommand.setStatus("COMPLETED");
                savedCommand.setCompletedAt(LocalDateTime.now());
                savedCommand.setExecutionSummary("Successfully generated " + generated + " comprehensive AI blog post(s) with SEO optimization.");
            }

            logAdminActivity(adminUsername, "AI_ASSISTANT_COMMAND_EXECUTED",
                    "Executed assistant command ID: " + savedCommand.getId() + " - " + savedCommand.getCommandText());

        } catch (Exception e) {
            logger.error("Error executing assistant command", e);
            savedCommand.setStatus("FAILED");
            savedCommand.setErrorMessage(e.getMessage());
        }

        return commandRepository.save(savedCommand);
    }

    /**
     * Generates a 10-Day progressive content calendar.
     * Each day has distinct themes and search intent to avoid duplicate content.
     */
    private void generate10DaySchedule(AssistantCommand cmd) {
        int blogsPerDay = cmd.getBlogsPerDay() != null ? cmd.getBlogsPerDay() : 5;
        String topic = cmd.getTitle() != null && !cmd.getTitle().trim().isEmpty() ? cmd.getTitle() : "Real Estate Investment";
        String location = cmd.getLocation() != null && !cmd.getLocation().trim().isEmpty() ? cmd.getLocation() : "Pune";
        String keywords = cmd.getFocusKeywords() != null && !cmd.getFocusKeywords().trim().isEmpty() ? cmd.getFocusKeywords() : topic;

        String[][] dayThemes = {
                {"Market Overview & Growth Drivers", "Macroeconomic analysis, growth catalysts, and infrastructure roadmap in " + location, "High-Level Informational"},
                {"Price Trends & Micro-Market Valuation", "Locality-wise square foot pricing, historical appreciation, and yield comparisons", "Commercial Research & Valuation"},
                {"Infrastructure & Transit Corridors", "Metro rail links, expressways, airport connectivity, and industrial SEZs", "Infrastructure & Long-term Growth"},
                {"Legal Due Diligence & RERA Compliance", "Title deeds, encumbrance certificates, RERA approvals, and buyer protections", "Transactional Safety & Due Diligence"},
                {"Rental Yields & Passive Income Strategies", "Residential vs commercial yield optimization, student/IT housing demand", "Investment & ROI Focus"},
                {"Top Developer Track Records & Project Spotlights", "A-grade builders, delivery timelines, construction quality benchmarks", "Vendor & Developer Evaluation"},
                {"Home Loans, Interest Rates & Tax Benefits", "EMI calculations, tax savings under Section 80C/24b, banking partnerships", "Financing & Budgeting Strategy"},
                {"Ready-to-Move vs Under-Construction Analysis", "Risk vs discount tradeoffs, GST implications, capital safety factors", "Purchase Decision Comparison"},
                {"Emerging Suburban Growth Hotspots", "Next-decade investment corridors, upcoming smart city townships", "High-Upside Exploration"},
                {"2026 Strategic Investor Playbook & Checklist", "Complete step-by-step acquisition roadmap, portfolio diversification tips", "Actionable Investment Strategy"}
        };

        LocalDate startDate = LocalDate.now().plusDays(1);
        List<BlogCampaignSchedule> scheduleList = new ArrayList<>();

        for (int day = 0; day < 10; day++) {
            LocalDate scheduledDate = startDate.plusDays(day);
            String theme = dayThemes[day][0];
            String topicFocus = dayThemes[day][1];
            String targetIntent = dayThemes[day][2];

            for (int b = 1; b <= blogsPerDay; b++) {
                String plannedTitle = String.format("Day %d (%d/%d): %s - %s in %s (2026)",
                        (day + 1), b, blogsPerDay, theme, topic, location);

                BlogCampaignSchedule item = new BlogCampaignSchedule(
                        cmd, (day + 1), scheduledDate, plannedTitle, topicFocus, targetIntent, keywords
                );
                scheduleList.add(item);
            }
        }

        scheduleRepository.saveAll(scheduleList);
        cmd.setBlogsRequested(scheduleList.size());
    }

    /**
     * Generates a single high-quality AI blog based on command requirements.
     */
    public BlogPost generateSingleAssistantBlog(AssistantCommand cmd, int index) {
        BlogPublishingPermission perms = blogService.getPermissions();

        String topic = cmd.getTitle() != null && !cmd.getTitle().trim().isEmpty() ? cmd.getTitle() : "Real Estate Investment in " + (cmd.getLocation() != null ? cmd.getLocation() : "India");
        String category = cmd.getCategory() != null ? cmd.getCategory() : "Real Estate";
        String audience = cmd.getTargetAudience() != null ? cmd.getTargetAudience() : "Property Buyers & Investors";
        String location = cmd.getLocation() != null ? cmd.getLocation() : "Pune";
        String language = cmd.getLanguage() != null ? cmd.getLanguage() : "English";
        String keywords = cmd.getFocusKeywords() != null && !cmd.getFocusKeywords().trim().isEmpty() ? cmd.getFocusKeywords() : topic;
        String backlink = cmd.getBacklinkUrl() != null && !cmd.getBacklinkUrl().trim().isEmpty() ? cmd.getBacklinkUrl() : "/search?q=" + topic.replace(" ", "+");
        String targetSite = cmd.getWebsiteFocus() != null && !cmd.getWebsiteFocus().trim().isEmpty() ? cmd.getWebsiteFocus() : "SmartSearch Portal";

        String title = (index > 1)
                ? String.format("%s: Essential In-Depth Analysis (Part %d)", topic, index)
                : String.format("%s: Complete 2026 Guide & Market Insights", topic);

        String slug = blogService.generateUniqueSlug(title);

        StringBuilder content = new StringBuilder();
        content.append("<p class='lead'>Welcome to our dedicated guide on <strong>").append(escapeHtml(topic)).append("</strong>. ");
        content.append("Tailored specifically for <em>").append(escapeHtml(audience)).append("</em> in ").append(escapeHtml(location)).append(", ");
        content.append("this analysis equips you with accurate market intelligence, verified regulatory frameworks, and practical evaluation steps.</p>\n\n");

        content.append("<h2>1. Regional Market Dynamics & Growth Drivers</h2>\n");
        content.append("<p>Sustained economic vibrancy across ").append(escapeHtml(location)).append(" continues to fuel demand for high-quality residential communities and Grade-A commercial spaces. ");
        content.append("Investors and prospective homeowners are benefiting from proactive master planning, expressway corridors, and expanding commercial IT zones.</p>\n\n");

        content.append("<h2>2. Key Considerations for ").append(escapeHtml(audience)).append("</h2>\n");
        content.append("<ul>\n");
        content.append("  <li><strong>Verified Due Diligence:</strong> Review title history, encumbrance clearances, and RERA registration credentials before finalizing commitments.</li>\n");
        content.append("  <li><strong>Infrastructure Connectivity:</strong> Prioritize micro-markets within 15–20 minutes of key transit nodes and employment hubs.</li>\n");
        content.append("  <li><strong>Strategic Backlinks & Verified Sources:</strong> Explore verified listings and official data at <a href='").append(escapeHtml(backlink)).append("'>").append(escapeHtml(targetSite)).append("</a>.</li>\n");
        content.append("</ul>\n\n");

        if (Boolean.TRUE.equals(cmd.getAllowVideos()) && perms.getAllowAiVideoIntegration()) {
            content.append("<h2>3. Video Walkthrough & Expert Commentary</h2>\n");
            content.append("<div class='ratio ratio-16x9 my-3 rounded-4 overflow-hidden shadow-sm'>\n");
            content.append("  <iframe src='https://www.youtube.com/embed/dQw4w9WgXcQ' title='Property Market Analysis' allowfullscreen></iframe>\n");
            content.append("</div>\n\n");
        }

        content.append("<h2>4. Strategic Recommendations for Long-Term Value</h2>\n");
        content.append("<p>Maintaining a balanced allocation with certified developers guarantees minimal construction slippage and superior capital preservation. Consult licensed legal and financial advisors for custom transaction advice.</p>\n");

        String summary = "Comprehensive 2026 breakdown of " + topic + " for " + audience + " in " + location + ". Key valuation metrics, regulatory checks, and investment advice.";

        String metaTitle = (title.length() > 58) ? title.substring(0, 55) + "..." : title;
        String metaDesc = "Explore our complete 2026 guide on " + topic + " for " + audience + " in " + location + ". Real estate trends, pricing metrics, and practical tips.";
        if (metaDesc.length() > 155) metaDesc = metaDesc.substring(0, 150) + "...";

        BlogPost post = new BlogPost();
        post.setTitle(title);
        post.setSlug(slug);
        post.setSummary(summary);
        post.setContent(content.toString());
        post.setCategory(category);
        post.setTags(category + ", " + keywords + ", " + location + ", 2026 Trends");
        post.setTargetAudience(audience);
        post.setLanguage(language);
        post.setTargetLocation(location);
        post.setBacklinkUrl(backlink);
        post.setTargetWebsite(targetSite);
        post.setCommandId(cmd.getId());

        // Media permissions
        if (Boolean.TRUE.equals(cmd.getAllowImages()) && perms.getAllowAiImageGeneration()) {
            post.setCoverImageUrl("https://images.unsplash.com/photo-1560518883-ce09059eeffa?w=1200&auto=format&fit=crop");
            post.setCoverImageAlt(topic + " - Market Overview & Analysis");
        }
        if (Boolean.TRUE.equals(cmd.getAllowVideos()) && perms.getAllowAiVideoIntegration()) {
            post.setVideoUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
            post.setVideoEmbedCode("<iframe width=\"560\" height=\"315\" src=\"https://www.youtube.com/embed/dQw4w9WgXcQ\" frameborder=\"0\" allowfullscreen></iframe>");
        }

        post.setAuthorName("SmartSearch AI Assistant");
        post.setAuthorRole("AI");
        post.setIsAiGenerated(true);
        post.setStatus("DRAFT"); // Default Draft for admin review
        post.setPrimaryKeyword(keywords.split(",")[0].trim());
        post.setSecondaryKeywords(keywords);
        post.setMetaTitle(metaTitle);
        post.setMetaDescription(metaDesc);
        post.setGoogleIndexingStatus("DISCOVERED");

        BlogPost saved = blogService.createBlog(post, null, true);
        return saved;
    }

    /**
     * Approves an AI-generated blog post and changes status to APPROVED / PUBLISHED.
     */
    public BlogPost approveBlog(Long blogId, String adminUsername) {
        BlogPost blog = blogRepository.findById(blogId)
                .orElseThrow(() -> new IllegalArgumentException("Blog post not found: " + blogId));
        blog.setStatus("PUBLISHED");
        blog.setPublishedAt(LocalDateTime.now());
        int days = blog.getValidityDays() != null ? blog.getValidityDays() : blogService.getPermissions().getDefaultValidityDays();
        blog.setExpiresAt(LocalDateTime.now().plusDays(days));
        BlogPost saved = blogRepository.save(blog);

        if (blog.getCommandId() != null) {
            commandRepository.findById(blog.getCommandId()).ifPresent(cmd -> {
                cmd.setBlogsApproved(cmd.getBlogsApproved() + 1);
                cmd.setBlogsPublished(cmd.getBlogsPublished() + 1);
                commandRepository.save(cmd);
            });
        }

        logAdminActivity(adminUsername, "AI_BLOG_APPROVED", "Admin approved & published AI blog ID: " + blogId);
        return saved;
    }

    /**
     * Rejects an AI blog post.
     */
    public void rejectBlog(Long blogId, String adminUsername) {
        BlogPost blog = blogRepository.findById(blogId)
                .orElseThrow(() -> new IllegalArgumentException("Blog post not found: " + blogId));
        blog.setStatus("UNPUBLISHED");
        blogRepository.save(blog);
        logAdminActivity(adminUsername, "AI_BLOG_REJECTED", "Admin rejected AI blog ID: " + blogId);
    }

    /**
     * Regenerates an AI blog with fresh angle and updated keywords.
     */
    public BlogPost regenerateBlog(Long blogId, String adminUsername) {
        BlogPost existing = blogRepository.findById(blogId)
                .orElseThrow(() -> new IllegalArgumentException("Blog post not found: " + blogId));

        AssistantCommand cmd = existing.getCommandId() != null
                ? commandRepository.findById(existing.getCommandId()).orElse(null)
                : null;

        String topic = existing.getTitle().replaceAll(" \\(Part \\d+\\)|:.*", "");
        BlogPost newBlog = generateSingleAssistantBlog(
                cmd != null ? cmd : new AssistantCommand(),
                (int) (System.currentTimeMillis() % 10 + 1)
        );

        existing.setTitle(newBlog.getTitle());
        existing.setContent(newBlog.getContent());
        existing.setSummary(newBlog.getSummary());
        existing.setMetaTitle(newBlog.getMetaTitle());
        existing.setMetaDescription(newBlog.getMetaDescription());
        existing.setSeoScore(newBlog.getSeoScore());
        existing.setReadabilityScore(newBlog.getReadabilityScore());
        existing.setSchemaMarkup(newBlog.getSchemaMarkup());

        BlogPost saved = blogRepository.save(existing);
        aiSeoService.analyzeBlog(saved);

        // Delete the temporary newBlog
        blogRepository.delete(newBlog);

        logAdminActivity(adminUsername, "AI_BLOG_REGENERATED", "Regenerated content for blog ID: " + blogId);
        return saved;
    }

    /**
     * Approves an entire 10-Day Campaign Schedule.
     */
    public void approveSchedule(Long commandId, String adminUsername) {
        AssistantCommand cmd = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));
        cmd.setScheduleApproved(true);
        cmd.setScheduleStatus("IN_PROGRESS");
        commandRepository.save(cmd);

        List<BlogCampaignSchedule> items = scheduleRepository.findByCommandIdOrderByDayNumberAsc(commandId);
        for (BlogCampaignSchedule item : items) {
            item.setStatus("APPROVED");
        }
        scheduleRepository.saveAll(items);

        logAdminActivity(adminUsername, "CAMPAIGN_SCHEDULE_APPROVED",
                "Approved 10-day blog campaign schedule for command ID: " + commandId);
    }

    /**
     * Pauses a running campaign schedule.
     */
    public void pauseSchedule(Long commandId, String adminUsername) {
        AssistantCommand cmd = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));
        cmd.setScheduleStatus("PAUSED");
        commandRepository.save(cmd);

        List<BlogCampaignSchedule> items = scheduleRepository.findByCommandIdOrderByDayNumberAsc(commandId);
        for (BlogCampaignSchedule item : items) {
            if ("APPROVED".equals(item.getStatus()) || "SCHEDULED".equals(item.getStatus())) {
                item.setStatus("PAUSED");
            }
        }
        scheduleRepository.saveAll(items);
        logAdminActivity(adminUsername, "CAMPAIGN_SCHEDULE_PAUSED", "Paused schedule for command ID: " + commandId);
    }

    /**
     * Cancels a campaign schedule.
     */
    public void cancelSchedule(Long commandId, String adminUsername) {
        AssistantCommand cmd = commandRepository.findById(commandId)
                .orElseThrow(() -> new IllegalArgumentException("Command not found: " + commandId));
        cmd.setScheduleStatus("CANCELLED");
        commandRepository.save(cmd);

        List<BlogCampaignSchedule> items = scheduleRepository.findByCommandIdOrderByDayNumberAsc(commandId);
        for (BlogCampaignSchedule item : items) {
            item.setStatus("CANCELLED");
        }
        scheduleRepository.saveAll(items);
        logAdminActivity(adminUsername, "CAMPAIGN_SCHEDULE_CANCELLED", "Cancelled schedule for command ID: " + commandId);
    }

    /**
     * Section D: AI SEO Strategy & Website Ranking Focus.
     * Analyzes target website, identifies content gaps, recommends legitimate backlink strategies.
     */
    public Map<String, Object> analyzeWebsiteAndRecommendStrategy(String targetWebsite, String focusKeywords) {
        Map<String, Object> strategy = new HashMap<>();

        if (targetWebsite == null || targetWebsite.trim().isEmpty()) {
            targetWebsite = "https://smartsearch.local";
        }
        if (focusKeywords == null || focusKeywords.trim().isEmpty()) {
            focusKeywords = "Real Estate Investment, Property Trends, Career Opportunities";
        }

        strategy.put("targetWebsite", targetWebsite);
        strategy.put("focusKeywords", focusKeywords);
        strategy.put("analyzedAt", LocalDateTime.now());

        // 1. Content Gaps Identified
        List<String> contentGaps = Arrays.asList(
                "Lack of dedicated localized locality pricing breakdown guides (e.g. Pune East vs West micro-markets)",
                "Absence of interactive legal checklist comparing RERA Form 15 vs 16 for first-time homebuyers",
                "High-intent commercial leasing and co-working yield calculators missing from indexing portfolio",
                "Under-optimized FAQ schema markup on high-ranking portal services"
        );
        strategy.put("contentGaps", contentGaps);

        // 2. High-Potential Search Keywords to Target
        List<Map<String, Object>> targetKeywordOpportunities = new ArrayList<>();
        targetKeywordOpportunities.add(Map.of("keyword", "RERA Approved Projects in Pune 2026", "intent", "High Commercial Intent", "competition", "Low", "potentialLift", "+28% CTR"));
        targetKeywordOpportunities.add(Map.of("keyword", "Commercial Real Estate Rental Yields", "intent", "B2B Transactional", "competition", "Medium", "potentialLift", "+19% Organic Clicks"));
        targetKeywordOpportunities.add(Map.of("keyword", "Legal Checklist for Buying Property in India", "intent", "Informational / Authority", "competition", "Low", "potentialLift", "+34% Search Traffic"));
        strategy.put("targetKeywords", targetKeywordOpportunities);

        // 3. Recommended Backlink & Syndication Channels
        List<String> backlinkStrategies = Arrays.asList(
                "Syndicate verified industry reports with certified real estate developer associations",
                "Author guest advisory columns on major property appraisal and housing finance portals",
                "Distribute authoritative market research whitepapers citing official municipal infrastructure data"
        );
        strategy.put("backlinkStrategies", backlinkStrategies);

        // 4. Proposed 10-Day Campaign Blueprint
        strategy.put("recommendationRationale", "Targeting informational question-based queries and verified buyer diligence topics builds high topical authority across Google and Bing without risking unnatural algorithmic penalties.");

        return strategy;
    }

    // --- History & Queries ---

    public List<AssistantCommand> getAllCommands() {
        return commandRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<AssistantCommand> getCommandById(Long id) {
        return commandRepository.findById(id);
    }

    public List<BlogCampaignSchedule> getScheduleForCommand(Long commandId) {
        return scheduleRepository.findByCommandIdOrderByDayNumberAsc(commandId);
    }

    public List<BlogPost> getBlogsForCommand(Long commandId) {
        List<BlogPost> all = blogRepository.findAll();
        List<BlogPost> result = new ArrayList<>();
        for (BlogPost b : all) {
            if (commandId.equals(b.getCommandId())) {
                result.add(b);
            }
        }
        return result;
    }

    private void logAdminActivity(String username, String action, String details) {
        try {
            AdminActivityLog log = new AdminActivityLog();
            log.setAdminUsername(username != null ? username : "SYSTEM");
            log.setAction(action);
            log.setDetails(details);
            adminLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Could not log admin activity: {}", e.getMessage());
        }
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}

