package com.smartsearch.service;

import com.smartsearch.model.*;
import com.smartsearch.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Transactional
public class BlogService {

    private static final Logger logger = LoggerFactory.getLogger(BlogService.class);
    private static final Pattern NONLATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");

    private final BlogPostRepository blogRepository;
    private final BlogPublishingPermissionRepository permissionRepository;
    private final UserBlogLimitRepository limitRepository;
    private final UserRepository userRepository;
    private final AdminActivityLogRepository adminLogRepository;
    private final BlogEditHistoryRepository editHistoryRepository;
    private final AiBlogSeoService aiSeoService;

    @Autowired
    public BlogService(BlogPostRepository blogRepository,
                       BlogPublishingPermissionRepository permissionRepository,
                       UserBlogLimitRepository limitRepository,
                       UserRepository userRepository,
                       AdminActivityLogRepository adminLogRepository,
                       BlogEditHistoryRepository editHistoryRepository,
                       @Lazy AiBlogSeoService aiSeoService) {
        this.blogRepository = blogRepository;
        this.permissionRepository = permissionRepository;
        this.limitRepository = limitRepository;
        this.userRepository = userRepository;
        this.adminLogRepository = adminLogRepository;
        this.editHistoryRepository = editHistoryRepository;
        this.aiSeoService = aiSeoService;
    }

    // --- Master Permissions ---

    public BlogPublishingPermission getPermissions() {
        return permissionRepository.findFirstByOrderByIdAsc().orElseGet(() -> {
            BlogPublishingPermission perm = new BlogPublishingPermission();
            return permissionRepository.save(perm);
        });
    }

    public BlogPublishingPermission updatePermissions(BlogPublishingPermission updated) {
        BlogPublishingPermission existing = getPermissions();
        existing.setAllowAllUsersCreate(updated.getAllowAllUsersCreate());
        existing.setAllowAgentsCreate(updated.getAllowAgentsCreate());
        existing.setAllowPropertyOwnersCreate(updated.getAllowPropertyOwnersCreate());
        existing.setAllowAdminsCreate(updated.getAllowAdminsCreate());
        existing.setAllowAiGenerate(updated.getAllowAiGenerate());
        existing.setAllowAiAutoPublish(updated.getAllowAiAutoPublish());
        existing.setRequireAdminApproval(updated.getRequireAdminApproval());

        existing.setDefaultValidityDays(updated.getDefaultValidityDays() != null ? updated.getDefaultValidityDays() : 90);
        existing.setAllowCustomValidity(updated.getAllowCustomValidity());
        existing.setAutoExpireAndUnpublish(updated.getAutoExpireAndUnpublish());
        existing.setAllowBlogRenewal(updated.getAllowBlogRenewal());

        existing.setAutomaticSeoMasterSwitch(updated.getAutomaticSeoMasterSwitch());
        existing.setAutoMetaTitleOpt(updated.getAutoMetaTitleOpt());
        existing.setAutoMetaDescOpt(updated.getAutoMetaDescOpt());
        existing.setAutoKeywordSuggestions(updated.getAutoKeywordSuggestions());
        existing.setAutoInternalLinking(updated.getAutoInternalLinking());
        existing.setAutoImageAltText(updated.getAutoImageAltText());
        existing.setAutoSchemaMarkup(updated.getAutoSchemaMarkup());
        existing.setAutoSitemapUpdate(updated.getAutoSitemapUpdate());
        existing.setAutoSeoContentAnalysis(updated.getAutoSeoContentAnalysis());
        existing.setAutoIndexingSubmission(updated.getAutoIndexingSubmission());
        existing.setAutoSeoMonitoring(updated.getAutoSeoMonitoring());

        logActivity("SYSTEM", "UPDATE_PERMISSIONS", "Blog permissions & SEO authorities updated");
        return permissionRepository.save(existing);
    }

    // --- Role Identification ---

    public String resolveUserCategory(User user) {
        if (user == null) return "AI";
        if (user.getRole() == Role.ROLE_ADMIN) return "ADMIN";
        String acc = user.getAccountType();
        if (acc != null) {
            String u = acc.toUpperCase();
            if (u.contains("AGENT") || u.contains("EMPLOYER")) return "AGENT";
            if (u.contains("OWNER") || u.contains("PROPERTY")) return "PROPERTY_OWNER";
        }
        return "USER";
    }

    // --- Quota & Limit Checks ---

    public boolean canUserCreateBlog(User user) {
        BlogPublishingPermission perms = getPermissions();
        String category = resolveUserCategory(user);
        if (!perms.canRoleCreate(category)) {
            return false;
        }
        return checkUserQuota(user, category);
    }

    public boolean canAiGenerate() {
        BlogPublishingPermission perms = getPermissions();
        if (!perms.getAllowAiGenerate()) return false;
        return checkCategoryQuota("AI");
    }

    public boolean checkUserQuota(User user, String category) {
        if (user != null) {
            Optional<UserBlogLimit> customLimit = limitRepository.findByUser(user);
            if (customLimit.isPresent()) {
                return customLimit.get().canCreateBlog();
            }
        }
        return checkCategoryQuota(category);
    }

    public boolean checkCategoryQuota(String category) {
        Optional<UserBlogLimit> optLimit = limitRepository.findByUserCategoryAndIsCustomForUserFalse(category);
        if (optLimit.isPresent()) {
            return optLimit.get().canCreateBlog();
        }
        return true; // if not defined, default allow
    }

    public int getRemainingQuota(User user, String category) {
        if (user != null) {
            Optional<UserBlogLimit> customLimit = limitRepository.findByUser(user);
            if (customLimit.isPresent()) {
                return customLimit.get().getRemainingBlogs();
            }
        }
        Optional<UserBlogLimit> optLimit = limitRepository.findByUserCategoryAndIsCustomForUserFalse(category);
        return optLimit.map(UserBlogLimit::getRemainingBlogs).orElse(999);
    }

    public void incrementQuotaUsage(User user, String category) {
        if (user != null) {
            Optional<UserBlogLimit> customLimit = limitRepository.findByUser(user);
            if (customLimit.isPresent()) {
                customLimit.get().incrementCreated();
                limitRepository.save(customLimit.get());
                return;
            }
        }
        Optional<UserBlogLimit> optLimit = limitRepository.findByUserCategoryAndIsCustomForUserFalse(category);
        optLimit.ifPresent(limit -> {
            limit.incrementCreated();
            limitRepository.save(limit);
        });
    }

    // --- Blog CRUD & Lifecycle ---

    public BlogPost createBlog(BlogPost blog, User author, boolean isAi) {
        BlogPublishingPermission perms = getPermissions();
        String category = isAi ? "AI" : resolveUserCategory(author);

        if (isAi) {
            if (!perms.getAllowAiGenerate()) {
                throw new IllegalStateException("AI blog generation is currently disabled by administrator.");
            }
        } else {
            if (!perms.canRoleCreate(category)) {
                throw new IllegalStateException("Blog creation is disabled for category: " + category);
            }
        }

        if (!checkUserQuota(author, category)) {
            throw new IllegalStateException("Blog creation limit reached for this period. Please try again later or contact admin.");
        }

        blog.setAuthor(author);
        blog.setAuthorRole(category);
        blog.setIsAiGenerated(isAi);
        if (author != null) {
            blog.setAuthorName(author.getFullName() != null ? author.getFullName() : author.getUsername());
        } else if (isAi) {
            blog.setAuthorName("SmartSearch AI Content Bot");
        }

        // Slug generation
        if (blog.getSlug() == null || blog.getSlug().trim().isEmpty()) {
            blog.setSlug(generateUniqueSlug(blog.getTitle()));
        } else {
            blog.setSlug(sanitizeSlug(blog.getSlug()));
        }

        // Validity configuration
        int validity = perms.getDefaultValidityDays();
        if (perms.getAllowCustomValidity() && blog.getValidityDays() != null && blog.getValidityDays() > 0) {
            validity = blog.getValidityDays();
        }
        blog.setValidityDays(validity);

        // Status configuration
        if (isAi && perms.getAllowAiAutoPublish()) {
            blog.setStatus("PUBLISHED");
            blog.setPublishedAt(LocalDateTime.now());
            blog.setExpiresAt(LocalDateTime.now().plusDays(validity));
        } else if ("ADMIN".equals(category) || !perms.getRequireAdminApproval()) {
            if ("PUBLISHED".equalsIgnoreCase(blog.getStatus())) {
                blog.setPublishedAt(LocalDateTime.now());
                blog.setExpiresAt(LocalDateTime.now().plusDays(validity));
            } else if (blog.getStatus() == null) {
                blog.setStatus("PUBLISHED");
                blog.setPublishedAt(LocalDateTime.now());
                blog.setExpiresAt(LocalDateTime.now().plusDays(validity));
            }
        } else {
            blog.setStatus("PENDING_APPROVAL");
        }

        BlogPost saved = blogRepository.save(blog);
        incrementQuotaUsage(author, category);
        logActivity(author != null ? author.getUsername() : "AI_ENGINE", "BLOG_CREATED", "Created blog: " + saved.getTitle());

        // Trigger AI SEO analysis
        try {
            aiSeoService.analyzeBlog(saved);
        } catch (Exception e) {
            logger.warn("AI SEO analysis failed on creation: {}", e.getMessage());
        }

        return saved;
    }

    public BlogPost updateBlog(BlogPost blog, String editorUsername) {
        return updateBlog(blog, editorUsername, "Administrator edited blog content & SEO metadata");
    }

    public BlogPost updateBlog(BlogPost blog, String editorUsername, String changeSummary) {
        BlogPost existing = blogRepository.findById(blog.getId())
                .orElseThrow(() -> new IllegalArgumentException("Blog post not found with ID: " + blog.getId()));

        String prevTitle = existing.getTitle();
        String prevContent = existing.getContent();
        int prevSeoScore = existing.getSeoScore() != null ? existing.getSeoScore() : 0;

        existing.setTitle(blog.getTitle());
        if (blog.getSlug() != null && !blog.getSlug().trim().isEmpty() && !blog.getSlug().equals(existing.getSlug())) {
            existing.setSlug(sanitizeSlug(blog.getSlug()));
        }
        existing.setSummary(blog.getSummary());
        existing.setContent(blog.getContent());
        existing.setCategory(blog.getCategory());
        existing.setTags(blog.getTags());
        existing.setCoverImageUrl(blog.getCoverImageUrl());
        existing.setCoverImageAlt(blog.getCoverImageAlt());
        existing.setVideoUrl(blog.getVideoUrl());
        existing.setVideoEmbedCode(blog.getVideoEmbedCode());
        existing.setTargetAudience(blog.getTargetAudience());
        existing.setMetaTitle(blog.getMetaTitle());
        existing.setMetaDescription(blog.getMetaDescription());
        existing.setPrimaryKeyword(blog.getPrimaryKeyword());
        existing.setSecondaryKeywords(blog.getSecondaryKeywords());
        existing.setBacklinkUrl(blog.getBacklinkUrl());
        existing.setTargetWebsite(blog.getTargetWebsite());
        existing.setLanguage(blog.getLanguage() != null ? blog.getLanguage() : "English");
        existing.setTargetLocation(blog.getTargetLocation());
        existing.setSchemaMarkup(blog.getSchemaMarkup());

        // Status update
        if (blog.getStatus() != null && !blog.getStatus().equals(existing.getStatus())) {
            existing.setStatus(blog.getStatus());
            if ("PUBLISHED".equalsIgnoreCase(blog.getStatus()) && existing.getPublishedAt() == null) {
                existing.setPublishedAt(LocalDateTime.now());
                int days = blog.getValidityDays() != null ? blog.getValidityDays() : getPermissions().getDefaultValidityDays();
                existing.setExpiresAt(LocalDateTime.now().plusDays(days));
            }
        }

        // Validity days update & expiration recalculation
        if (blog.getValidityDays() != null && !blog.getValidityDays().equals(existing.getValidityDays())) {
            existing.setValidityDays(blog.getValidityDays());
            if (existing.getPublishedAt() != null) {
                existing.setExpiresAt(existing.getPublishedAt().plusDays(blog.getValidityDays()));
            }
        }
        if (blog.getExpiresAt() != null) {
            existing.setExpiresAt(blog.getExpiresAt());
        }

        BlogPost updated = blogRepository.save(existing);

        // Run AI SEO analysis
        try {
            aiSeoService.analyzeBlog(updated);
        } catch (Exception e) {
            logger.warn("AI SEO analysis error on update: {}", e.getMessage());
        }

        // Record Audit History
        BlogEditHistory history = new BlogEditHistory(
                updated,
                existing.getAuthorName(),
                existing.getAuthorRole(),
                editorUsername != null ? editorUsername : "ADMIN",
                changeSummary != null && !changeSummary.trim().isEmpty() ? changeSummary : "Admin updated blog details and SEO attributes",
                prevTitle,
                updated.getTitle(),
                prevContent,
                updated.getContent(),
                prevSeoScore,
                updated.getSeoScore()
        );
        editHistoryRepository.save(history);

        logActivity(editorUsername, "BLOG_UPDATED", "Admin updated blog ID " + updated.getId() + " (" + updated.getTitle() + ")");
        return updated;
    }

    public List<BlogEditHistory> getBlogEditHistory(Long blogId) {
        return editHistoryRepository.findByBlogIdOrderByEditedAtDesc(blogId);
    }

    public void approveBlog(Long id, String adminUsername) {
        BlogPost blog = blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Blog not found: " + id));
        blog.setStatus("PUBLISHED");
        blog.setPublishedAt(LocalDateTime.now());
        int days = blog.getValidityDays() != null ? blog.getValidityDays() : getPermissions().getDefaultValidityDays();
        blog.setExpiresAt(LocalDateTime.now().plusDays(days));
        blogRepository.save(blog);
        logActivity(adminUsername, "BLOG_APPROVED", "Approved & published blog: " + blog.getTitle());
    }

    public void unpublishBlog(Long id, String adminUsername) {
        BlogPost blog = blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Blog not found: " + id));
        blog.setStatus("UNPUBLISHED");
        blogRepository.save(blog);
        logActivity(adminUsername, "BLOG_UNPUBLISHED", "Unpublished blog: " + blog.getTitle());
    }

    public void deleteBlog(Long id, String adminUsername) {
        BlogPost blog = blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Blog not found: " + id));
        blogRepository.delete(blog);
        logActivity(adminUsername, "BLOG_DELETED", "Deleted blog ID: " + id);
    }

    public void renewBlog(Long id, Integer customDays, String username) {
        BlogPublishingPermission perms = getPermissions();
        if (!perms.getAllowBlogRenewal()) {
            throw new IllegalStateException("Blog renewal is currently disabled by administrator.");
        }
        BlogPost blog = blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Blog not found: " + id));

        int daysToAdd = (customDays != null && customDays > 0) ? customDays :
                (blog.getValidityDays() != null ? blog.getValidityDays() : perms.getDefaultValidityDays());

        blog.setRenewalCount(blog.getRenewalCount() + 1);
        blog.setExpiresAt(LocalDateTime.now().plusDays(daysToAdd));
        blog.setStatus("PUBLISHED");
        blogRepository.save(blog);

        logActivity(username, "BLOG_RENEWED", "Renewed blog '" + blog.getTitle() + "' for " + daysToAdd + " days");
    }

    public void extendValidity(Long id, int additionalDays, String adminUsername) {
        BlogPost blog = blogRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Blog not found: " + id));

        LocalDateTime baseDate = (blog.getExpiresAt() != null && blog.getExpiresAt().isAfter(LocalDateTime.now()))
                ? blog.getExpiresAt()
                : LocalDateTime.now();

        blog.setExpiresAt(baseDate.plusDays(additionalDays));
        if ("EXPIRED".equalsIgnoreCase(blog.getStatus())) {
            blog.setStatus("PUBLISHED");
        }
        blogRepository.save(blog);

        logActivity(adminUsername, "BLOG_EXTENDED", "Extended validity for blog '" + blog.getTitle() + "' by " + additionalDays + " days");
    }

    @Scheduled(fixedRate = 600000) // Every 10 minutes
    public void autoExpireBlogs() {
        BlogPublishingPermission perms = getPermissions();
        if (!perms.getAutoExpireAndUnpublish()) {
            return;
        }
        List<BlogPost> expiredPosts = blogRepository.findByStatusAndExpiresAtBefore("PUBLISHED", LocalDateTime.now());
        for (BlogPost post : expiredPosts) {
            post.setStatus("EXPIRED");
            blogRepository.save(post);
            logger.info("Auto-expired blog post ID {}: {}", post.getId(), post.getTitle());
        }
    }

    // --- Search & Fetch for Public & Admin ---

    public Page<BlogPost> getLiveBlogs(Pageable pageable) {
        autoExpireBlogs();
        return blogRepository.findByStatusOrderByPublishedAtDesc("PUBLISHED", pageable);
    }

    public Page<BlogPost> getLiveBlogsByCategory(String category, Pageable pageable) {
        autoExpireBlogs();
        return blogRepository.findByCategoryAndStatusOrderByPublishedAtDesc(category, "PUBLISHED", pageable);
    }

    public Optional<BlogPost> getBlogBySlug(String slug) {
        autoExpireBlogs();
        return blogRepository.findBySlug(slug);
    }

    public List<BlogPost> getAllBlogs() {
        autoExpireBlogs();
        return blogRepository.findAllByOrderByCreatedAtDesc();
    }

    public BlogPost getBlogById(Long id) {
        return blogRepository.findById(id).orElse(null);
    }

    public void recordView(BlogPost blog) {
        blog.setViewsCount(blog.getViewsCount() + 1);
        blog.setOrganicImpressions(blog.getOrganicImpressions() + 2);
        blogRepository.save(blog);
    }

    public void recordClick(BlogPost blog) {
        blog.setOrganicClicks(blog.getOrganicClicks() + 1);
        blogRepository.save(blog);
    }

    // --- Limits Management ---

    public List<UserBlogLimit> getCategoryLimits() {
        return limitRepository.findByIsCustomForUserFalse();
    }

    public List<UserBlogLimit> getUserCustomLimits() {
        return limitRepository.findByIsCustomForUserTrue();
    }

    public void updateCategoryLimit(String category, int maxBlogs, int periodDays) {
        UserBlogLimit limit = limitRepository.findByUserCategoryAndIsCustomForUserFalse(category)
                .orElseGet(() -> new UserBlogLimit(category, maxBlogs, periodDays));
        limit.setMaxBlogs(maxBlogs);
        limit.setPeriodDays(periodDays);
        limit.setCustomPeriodName(periodDays + " Days");
        limitRepository.save(limit);
        logActivity("ADMIN", "UPDATE_LIMIT", "Updated blog limit for " + category + " to " + maxBlogs + " per " + periodDays + " days");
    }

    public void setCustomUserLimit(Long userId, int maxBlogs, int periodDays) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        UserBlogLimit limit = limitRepository.findByUser(user)
                .orElseGet(() -> {
                    UserBlogLimit l = new UserBlogLimit(resolveUserCategory(user), maxBlogs, periodDays);
                    l.setUser(user);
                    l.setIsCustomForUser(true);
                    return l;
                });
        limit.setMaxBlogs(maxBlogs);
        limit.setPeriodDays(periodDays);
        limit.setCustomPeriodName(periodDays + " Days");
        limitRepository.save(limit);
        logActivity("ADMIN", "SET_CUSTOM_LIMIT", "Set custom blog limit for user " + user.getUsername() + ": " + maxBlogs + "/" + periodDays + "d");
    }

    public void deleteCustomLimit(Long limitId) {
        limitRepository.deleteById(limitId);
    }

    // --- Helper Methods ---

    public String generateUniqueSlug(String input) {
        if (input == null || input.trim().isEmpty()) {
            input = "blog-post-" + System.currentTimeMillis();
        }
        String base = sanitizeSlug(input);
        if (base.isEmpty()) base = "blog-post";

        String candidate = base;
        int counter = 2;
        while (blogRepository.existsBySlug(candidate)) {
            candidate = base + "-" + counter;
            counter++;
        }
        return candidate;
    }

    public String sanitizeSlug(String input) {
        String nowhitespace = WHITESPACE.matcher(input.trim().toLowerCase()).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NONLATIN.matcher(normalized).replaceAll("");
        slug = slug.replaceAll("-{2,}", "-").replaceAll("^-|-$", "");
        if (slug.length() > 200) {
            slug = slug.substring(0, 200).replaceAll("-$", "");
        }
        return slug;
    }

    private void logActivity(String username, String action, String details) {
        try {
            AdminActivityLog log = new AdminActivityLog();
            log.setAdminUsername(username != null ? username : "SYSTEM");
            log.setAction(action);
            log.setDetails(details);
            adminLogRepository.save(log);
        } catch (Exception e) {
            logger.warn("Failed to save admin activity log: {}", e.getMessage());
        }
    }
}
