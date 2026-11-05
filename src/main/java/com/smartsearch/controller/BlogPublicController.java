package com.smartsearch.controller;

import com.smartsearch.model.BlogPost;
import com.smartsearch.service.BlogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/blogs")
public class BlogPublicController {

    private final BlogService blogService;

    @Autowired
    public BlogPublicController(BlogService blogService) {
        this.blogService = blogService;
    }

    @GetMapping
    public String listBlogs(@RequestParam(value = "category", required = false) String category,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        Pageable pageable = PageRequest.of(page, 9);
        Page<BlogPost> blogPage;

        if (category != null && !category.trim().isEmpty() && !"All".equalsIgnoreCase(category)) {
            blogPage = blogService.getLiveBlogsByCategory(category, pageable);
        } else {
            blogPage = blogService.getLiveBlogs(pageable);
        }

        List<String> categories = Arrays.asList("All", "Real Estate", "Property Trends", "Career & Employment", "Technology", "Search Insights");

        model.addAttribute("blogPage", blogPage);
        model.addAttribute("categories", categories);
        model.addAttribute("selectedCategory", category != null ? category : "All");

        return "blog/list";
    }

    @GetMapping("/{slug}")
    public String viewBlog(@PathVariable("slug") String slug, Model model) {
        Optional<BlogPost> optBlog = blogService.getBlogBySlug(slug);
        if (optBlog.isEmpty()) {
            return "redirect:/blogs?error=notfound";
        }

        BlogPost blog = optBlog.get();
        if (blog.isExpired() || !"PUBLISHED".equalsIgnoreCase(blog.getStatus())) {
            return "redirect:/blogs?error=expired";
        }

        blogService.recordView(blog);
        model.addAttribute("blog", blog);

        return "blog/detail";
    }
}

