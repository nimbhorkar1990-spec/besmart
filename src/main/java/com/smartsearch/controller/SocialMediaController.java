package com.smartsearch.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SocialMediaController {

    @GetMapping({"/social-media", "/services/social-media", "/services/social-media-platform"})
    public String socialMedia() {
        return "redirect:http://localhost:8000/";
    }
}
