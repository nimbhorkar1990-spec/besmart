package com.smartsearch.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OrkutController {

    @GetMapping({"/services/orkut", "/orkut"})
    public String orkut(Model model) {
        model.addAttribute("title", "Orkut");
        return "orkut";
    }
}

