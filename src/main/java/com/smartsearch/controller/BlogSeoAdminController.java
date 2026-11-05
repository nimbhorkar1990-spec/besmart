package com.smartsearch.controller;

import com.smartsearch.model.*;
import com.smartsearch.repository.*;
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
    private final BlogPostRepository blogRepository;
    private final UserRepository userRepository;

    @Autowired
    public BlogSeoAdminController(BlogService blogService,
                                  AiBlogSeoService aiSeoService,
                                  BlogPostRepository blogRepository,
                                  UserRepository userRepository) {
        this.blogService = blogService;
        this.aiSeoService = aiSeoService;
        this.blogRepository = blogRepository;
        this.userRepository = userRepository;
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
