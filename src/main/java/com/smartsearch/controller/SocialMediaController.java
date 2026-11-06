package com.smartsearch.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SocialMediaController {

    @GetMapping({"/services/social-media-platform", "/social-media-platform", "/services/social-media", "/social-media"})
    public String socialMediaPlatform(Model model) {
        model.addAttribute("title", "Social Media Platform");
        return "social-media-platform";
    }
}
