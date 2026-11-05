package com.smartsearch.controller;

import com.smartsearch.model.*;
import com.smartsearch.repository.*;
import com.smartsearch.service.AiBlogAssistantService;
import com.smartsearch.service.AiBlogSeoService;
import com.smartsearch.service.BlogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/admin/blog-seo")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class BlogSeoAdminController {

    private final BlogService blogService;
    private final AiBlogSeoService aiSeoService;
    private final AiBlogAssistantService assistantService;
    private final BlogPostRepository blogRepository;
    private final UserRepository userRepository;
    private final BlogCampaignScheduleRepository scheduleRepository;

    @Autowired
    public BlogSeoAdminController(BlogService blogService,
                                  AiBlogSeoService aiSeoService,
                                  AiBlogAssistantService assistantService,
                                  BlogPostRepository blogRepository,
                                  UserRepository userRepository,
                                  BlogCampaignScheduleRepository scheduleRepository) {
        this.blogService = blogService;
        this.aiSeoService = aiSeoService;
        this.assistantService = assistantService;
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
        this.scheduleRepository = scheduleRepository;
    }

    @GetMapping
    public String showBlogSeoDashboard(@RequestParam(value = "tab", required = false, defaultValue = "dashboard") String tab,
                                       Model model) {
        model.addAttribute("activeTab", tab);

        // Permissions & Master Settings
        BlogPublishingPermission permissions = blogService.getPermissions();
        model.addAttribute("permissions", permissions);

        // Publishing Limits
        model.addAttribute("categoryLimits", blogService.getCategoryLimits());
        model.addAttribute("customLimits", blogService.getUserCustomLimits());
        model.addAttribute("allUsers", userRepository.findAll());

        // Blog Posts & Performance
        List<BlogPost> allBlogs = blogService.getAllBlogs();
        model.addAttribute("blogs", allBlogs);
        model.addAttribute("topBlogs", blogRepository.findTop5ByStatusOrderByViewsCountDesc("PUBLISHED"));

        // Recommendations
        model.addAttribute("recommendations", aiSeoService.getAllRecommendations());
        model.addAttribute("pendingRecommendations", aiSeoService.getPendingRecommendations());

        // Task Two: Assistant Commands & 10-Day Schedules
        model.addAttribute("assistantCommands", assistantService.getAllCommands());
        model.addAttribute("allSchedules", scheduleRepository.findAllByOrderByScheduledDateAscDayNumberAsc());
        model.addAttribute("assistantStrategy", assistantService.analyzeWebsiteAndRecommendStrategy(null, null));

        // KPIs
        long total = allBlogs.size();
        long published = allBlogs.stream().filter(b -> "PUBLISHED".equalsIgnoreCase(b.getStatus()) && !b.isExpired()).count();
        long pending = allBlogs.stream().filter(b -> "PENDING_APPROVAL".equalsIgnoreCase(b.getStatus())).count();
        long draft = allBlogs.stream().filter(b -> "DRAFT".equalsIgnoreCase(b.getStatus())).count();
        long expired = allBlogs.stream().filter(BlogPost::isExpired).count();
        long aiGenerated = allBlogs.stream().filter(b -> Boolean.TRUE.equals(b.getIsAiGenerated())).count();

        Double avgScore = blogRepository.findAverageSeoScore();
        Long totalClicks = blogRepository.sumOrganicClicks();
        Long totalImpressions = blogRepository.sumOrganicImpressions();
        Long totalViews = blogRepository.sumViewsCount();

        model.addAttribute("totalBlogs", total);
        model.addAttribute("publishedBlogs", published);
        model.addAttribute("pendingBlogs", pending);
        model.addAttribute("draftBlogs", draft);
        model.addAttribute("expiredBlogs", expired);
        model.addAttribute("aiGeneratedBlogs", aiGenerated);
        model.addAttribute("avgSeoScore", avgScore != null ? Math.round(avgScore * 10.0) / 10.0 : 0.0);
        model.addAttribute("totalClicks", totalClicks != null ? totalClicks : 0L);
        model.addAttribute("totalImpressions", totalImpressions != null ? totalImpressions : 0L);
        model.addAttribute("totalViews", totalViews != null ? totalViews : 0L);

        return "admin/blog-seo";
    }

    // --- Task One: Universal Admin Edit & Audit History Endpoints ---

    @GetMapping("/posts/{id}/edit")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getBlogForEdit(@PathVariable("id") Long id) {
        BlogPost blog = blogRepository.findById(id).orElse(null);
        if (blog == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", blog.getId());
        data.put("title", blog.getTitle());
        data.put("slug", blog.getSlug());
        data.put("summary", blog.getSummary());
        data.put("content", blog.getContent());
        data.put("category", blog.getCategory());
        data.put("tags", blog.getTags());
        data.put("targetAudience", blog.getTargetAudience());
        data.put("coverImageUrl", blog.getCoverImageUrl());
        data.put("coverImageAlt", blog.getCoverImageAlt());
        data.put("videoUrl", blog.getVideoUrl());
        data.put("videoEmbedCode", blog.getVideoEmbedCode());
        data.put("targetWebsite", blog.getTargetWebsite());
        data.put("backlinkUrl", blog.getBacklinkUrl());
        data.put("language", blog.getLanguage());
        data.put("targetLocation", blog.getTargetLocation());
        data.put("metaTitle", blog.getMetaTitle());
        data.put("metaDescription", blog.getMetaDescription());
        data.put("primaryKeyword", blog.getPrimaryKeyword());
        data.put("secondaryKeywords", blog.getSecondaryKeywords());
        data.put("status", blog.getStatus());
        data.put("validityDays", blog.getValidityDays());
        data.put("expiresAt", blog.getExpiresAt() != null ? blog.getExpiresAt().toString() : "");
        data.put("authorName", blog.getAuthorName());
        data.put("authorRole", blog.getAuthorRole());
        data.put("seoScore", blog.getSeoScore());
        data.put("readabilityScore", blog.getReadabilityScore());
        return ResponseEntity.ok(data);
    }

    @PostMapping("/posts/edit")
    public String editBlogPost(@ModelAttribute BlogPost blog,
                               @RequestParam(value = "changeSummary", required = false) String changeSummary,
                               Authentication auth,
                               RedirectAttributes redirectAttributes) {
        try {
            if (blog.getId() == null) {
                redirectAttributes.addFlashAttribute("errorMessage", "Blog ID is missing. Cannot perform edit.");
                return "redirect:/admin/blog-seo?tab=posts";
            }
            blogService.updateBlog(blog, auth != null ? auth.getName() : "ADMIN", changeSummary);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Blog post #" + blog.getId() + " updated successfully. AI SEO analysis re-evaluated and audit history recorded.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error editing blog post: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=posts";
    }

    @GetMapping("/posts/{id}/history")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> getBlogHistory(@PathVariable("id") Long id) {
        List<BlogEditHistory> histories = blogService.getBlogEditHistory(id);
        List<Map<String, Object>> result = new ArrayList<>();
        for (BlogEditHistory h : histories) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", h.getId());
            map.put("editorUsername", h.getEditorUsername());
            map.put("editedAt", h.getEditedAt() != null ? h.getEditedAt().toString() : "");
            map.put("changeSummary", h.getChangeSummary());
            map.put("previousTitle", h.getPreviousTitle());
            map.put("newTitle", h.getNewTitle());
            map.put("previousSeoScore", h.getPreviousSeoScore());
            map.put("newSeoScore", h.getNewSeoScore());
            map.put("originalAuthorName", h.getOriginalAuthorName());
            map.put("originalAuthorRole", h.getOriginalAuthorRole());
            result.add(map);
        }
        return ResponseEntity.ok(result);
    }

    // --- Task Two: AI Blog Assistant Endpoints ---

    @PostMapping("/assistant/command")
    public String executeAssistantCommand(@ModelAttribute AssistantCommand command,
                                          Authentication auth,
                                          RedirectAttributes redirectAttributes) {
        try {
            AssistantCommand executed = assistantService.executeCommand(command, auth != null ? auth.getName() : "ADMIN");
            if (Boolean.TRUE.equals(executed.getIsRecurringSchedule())) {
                redirectAttributes.addFlashAttribute("successMessage",
                        "AI Assistant created 10-day content calendar (" + executed.getBlogsRequested()
                                + " unique planned blogs). Review and approve the schedule below.");
            } else {
                redirectAttributes.addFlashAttribute("successMessage",
                        "AI Assistant successfully executed your command and prepared " + executed.getBlogsGenerated()
                                + " comprehensive blog post(s) with SEO optimization.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "AI Assistant Error: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=assistant";
    }

    @PostMapping("/assistant/schedule/{id}/approve")
    public String approveSchedule(@PathVariable("id") Long id,
                                  Authentication auth,
                                  RedirectAttributes redirectAttributes) {
        try {
            assistantService.approveSchedule(id, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "10-Day Campaign Schedule approved. Recurring AI blog generation activated.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error approving schedule: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=assistant";
    }

    @PostMapping("/assistant/schedule/{id}/pause")
    public String pauseSchedule(@PathVariable("id") Long id,
                                Authentication auth,
                                RedirectAttributes redirectAttributes) {
        try {
            assistantService.pauseSchedule(id, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "Campaign schedule paused.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error pausing schedule: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=assistant";
    }

    @PostMapping("/assistant/schedule/{id}/cancel")
    public String cancelSchedule(@PathVariable("id") Long id,
                                 Authentication auth,
                                 RedirectAttributes redirectAttributes) {
        try {
            assistantService.cancelSchedule(id, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "Campaign schedule cancelled.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error cancelling schedule: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=assistant";
    }

    @PostMapping("/assistant/blogs/{id}/approve")
    public String approveAiBlog(@PathVariable("id") Long id,
                                Authentication auth,
                                RedirectAttributes redirectAttributes) {
        try {
            assistantService.approveBlog(id, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "AI Blog approved and published live.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error approving AI blog: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=assistant";
    }

    @PostMapping("/assistant/blogs/{id}/reject")
    public String rejectAiBlog(@PathVariable("id") Long id,
                               Authentication auth,
                               RedirectAttributes redirectAttributes) {
        try {
            assistantService.rejectBlog(id, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "AI Blog marked as rejected / unpublished.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error rejecting AI blog: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=assistant";
    }

    @PostMapping("/assistant/blogs/{id}/regenerate")
    public String regenerateAiBlog(@PathVariable("id") Long id,
                                   Authentication auth,
                                   RedirectAttributes redirectAttributes) {
        try {
            BlogPost regenerated = assistantService.regenerateBlog(id, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "AI Blog regenerated with refreshed angles: " + regenerated.getTitle());
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error regenerating AI blog: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=assistant";
    }

    @GetMapping("/assistant/commands/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCommandDetails(@PathVariable("id") Long id) {
        AssistantCommand cmd = assistantService.getCommandById(id).orElse(null);
        if (cmd == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> data = new HashMap<>();
        data.put("id", cmd.getId());
        data.put("commandText", cmd.getCommandText());
        data.put("title", cmd.getTitle());
        data.put("category", cmd.getCategory());
        data.put("targetAudience", cmd.getTargetAudience());
        data.put("language", cmd.getLanguage());
        data.put("location", cmd.getLocation());
        data.put("focusKeywords", cmd.getFocusKeywords());
        data.put("otherTopics", cmd.getOtherTopics());
        data.put("allowImages", cmd.getAllowImages());
        data.put("allowVideos", cmd.getAllowVideos());
        data.put("allowAiImages", cmd.getAllowAiImages());
        data.put("allowVideoEmbed", cmd.getAllowVideoEmbed());
        data.put("blogTarget", cmd.getBlogTarget());
        data.put("backlinkUrl", cmd.getBacklinkUrl());
        data.put("websiteFocus", cmd.getWebsiteFocus());
        data.put("websiteRankingFocus", cmd.getWebsiteRankingFocus());
        data.put("targetEngines", cmd.getTargetEngines());
        data.put("isRecurringSchedule", cmd.getIsRecurringSchedule());
        data.put("blogsPerDay", cmd.getBlogsPerDay());
        data.put("status", cmd.getStatus());
        data.put("blogsRequested", cmd.getBlogsRequested());
        data.put("blogsGenerated", cmd.getBlogsGenerated());
        data.put("blogsApproved", cmd.getBlogsApproved());
        data.put("blogsPublished", cmd.getBlogsPublished());
        data.put("executionSummary", cmd.getExecutionSummary());
        data.put("createdAt", cmd.getCreatedAt() != null ? cmd.getCreatedAt().toString() : "");
        return ResponseEntity.ok(data);
    }

    // --- Standard Settings & Permissions POST Handlers ---

    @PostMapping("/permissions")
    public String updatePermissions(@ModelAttribute BlogPublishingPermission updated,
                                    RedirectAttributes redirectAttributes) {
        try {
            blogService.updatePermissions(updated);
            redirectAttributes.addFlashAttribute("successMessage", "Blog creation permissions, validity, and SEO automation settings updated successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating settings: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=permissions";
    }

    @PostMapping("/limits/category")
    public String updateCategoryLimit(@RequestParam("userCategory") String userCategory,
                                      @RequestParam("maxBlogs") Integer maxBlogs,
                                      @RequestParam("periodDays") Integer periodDays,
                                      RedirectAttributes redirectAttributes) {
        try {
            blogService.updateCategoryLimit(userCategory, maxBlogs, periodDays);
            redirectAttributes.addFlashAttribute("successMessage", "Publishing limit for " + userCategory + " updated to " + maxBlogs + " blogs per " + periodDays + " days.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating category limit: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=limits";
    }

    @PostMapping("/limits/user")
    public String setUserCustomLimit(@RequestParam("userId") Long userId,
                                     @RequestParam("maxBlogs") Integer maxBlogs,
                                     @RequestParam("periodDays") Integer periodDays,
                                     RedirectAttributes redirectAttributes) {
        try {
            blogService.setCustomUserLimit(userId, maxBlogs, periodDays);
            redirectAttributes.addFlashAttribute("successMessage", "Custom blog publishing limit assigned to user successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error configuring custom user limit: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=limits";
    }

    @PostMapping("/limits/user/{id}/delete")
    public String deleteCustomLimit(@PathVariable("id") Long id,
                                    RedirectAttributes redirectAttributes) {
        try {
            blogService.deleteCustomLimit(id);
            redirectAttributes.addFlashAttribute("successMessage", "Custom limit removed successfully. User restored to category default.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error removing limit: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=limits";
    }

    @PostMapping("/posts/save")
    public String saveBlogPost(@ModelAttribute BlogPost blog,
                               Authentication auth,
                               RedirectAttributes redirectAttributes) {
        try {
            User currentAdmin = auth != null ? userRepository.findByUsername(auth.getName()).orElse(null) : null;
            if (blog.getId() == null) {
                blogService.createBlog(blog, currentAdmin, false);
                redirectAttributes.addFlashAttribute("successMessage", "Blog post created and analyzed by AI SEO engine successfully.");
            } else {
                blogService.updateBlog(blog, auth != null ? auth.getName() : "ADMIN");
                redirectAttributes.addFlashAttribute("successMessage", "Blog post updated and re-analyzed by AI SEO engine successfully.");
            }
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error saving blog post: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=posts";
    }

    @PostMapping("/posts/{id}/approve")
    public String approvePost(@PathVariable("id") Long id,
                              Authentication auth,
                              RedirectAttributes redirectAttributes) {
        try {
            blogService.approveBlog(id, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "Blog post approved and published live.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error approving blog: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=posts";
    }

    @PostMapping("/posts/{id}/unpublish")
    public String unpublishPost(@PathVariable("id") Long id,
                                Authentication auth,
                                RedirectAttributes redirectAttributes) {
        try {
            blogService.unpublishBlog(id, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "Blog post unpublished from live view.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error unpublishing blog: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=posts";
    }

    @PostMapping("/posts/{id}/renew")
    public String renewPost(@PathVariable("id") Long id,
                            @RequestParam(value = "customDays", required = false) Integer customDays,
                            Authentication auth,
                            RedirectAttributes redirectAttributes) {
        try {
            blogService.renewBlog(id, customDays, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "Blog post validity renewed successfully.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error renewing blog: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=validity";
    }

    @PostMapping("/posts/{id}/extend")
    public String extendPostValidity(@PathVariable("id") Long id,
                                     @RequestParam("additionalDays") int additionalDays,
                                     Authentication auth,
                                     RedirectAttributes redirectAttributes) {
        try {
            blogService.extendValidity(id, additionalDays, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "Blog validity extended by " + additionalDays + " days.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error extending validity: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=validity";
    }

    @PostMapping("/posts/{id}/delete")
    public String deletePost(@PathVariable("id") Long id,
                             Authentication auth,
                             RedirectAttributes redirectAttributes) {
        try {
            blogService.deleteBlog(id, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "Blog post deleted permanently.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting blog: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=posts";
    }

    @PostMapping("/recommendations/{id}/approve")
    public String approveRecommendation(@PathVariable("id") Long id,
                                        Authentication auth,
                                        RedirectAttributes redirectAttributes) {
        try {
            aiSeoService.executeRecommendation(id, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "AI SEO Recommendation approved and changes applied successfully to the blog.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error executing recommendation: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=recommendations";
    }

    @PostMapping("/recommendations/{id}/reject")
    public String rejectRecommendation(@PathVariable("id") Long id,
                                       Authentication auth,
                                       RedirectAttributes redirectAttributes) {
        try {
            aiSeoService.rejectRecommendation(id, auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "AI Recommendation marked as rejected.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error rejecting recommendation: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=recommendations";
    }

    @PostMapping("/recommendations/approve-all")
    public String approveAllRecommendations(Authentication auth,
                                            RedirectAttributes redirectAttributes) {
        try {
            aiSeoService.approveAllRecommendations(auth != null ? auth.getName() : "ADMIN");
            redirectAttributes.addFlashAttribute("successMessage", "All pending AI SEO recommendations approved and applied.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error approving all recommendations: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=recommendations";
    }

    @GetMapping("/recommendations/{id}/details")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRecommendationDetails(@PathVariable("id") Long id) {
        AiSeoRecommendation rec = aiSeoService.getRecommendationById(id);
        if (rec == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> details = new HashMap<>();
        details.put("id", rec.getId());
        details.put("title", rec.getTitle());
        details.put("blogTitle", rec.getBlog() != null ? rec.getBlog().getTitle() : "");
        details.put("actionType", rec.getActionType());
        details.put("priority", rec.getPriority());
        details.put("description", rec.getDescription());
        details.put("reason", rec.getReason());
        details.put("expectedBenefit", rec.getExpectedBenefit());
        details.put("aiExplanation", rec.getAiExplanation());
        details.put("proposedChange", rec.getProposedChange());
        details.put("approvalStatus", rec.getApprovalStatus());
        return ResponseEntity.ok(details);
    }

    @PostMapping("/generate-ai-blog")
    public String generateAiBlog(@RequestParam("topic") String topic,
                                 @RequestParam(value = "category", defaultValue = "Real Estate") String category,
                                 @RequestParam(value = "keywords", required = false) String keywords,
                                 RedirectAttributes redirectAttributes) {
        try {
            BlogPost post = aiSeoService.generateAiBlog(topic, category, keywords);
            redirectAttributes.addFlashAttribute("successMessage", "AI generated new blog post '" + post.getTitle() + "' with automated SEO optimization. Review and publish below.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "AI Blog Generation Error: " + e.getMessage());
        }
        return "redirect:/admin/blog-seo?tab=posts";
    }
}
