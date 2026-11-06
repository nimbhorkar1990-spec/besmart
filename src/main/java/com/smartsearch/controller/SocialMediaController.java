package com.smartsearch.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SocialMediaController {

    @GetMapping({"/social-media", "/services/social-media"})
    public String socialMedia(Model model) {
        return "social-media";
    }
}
