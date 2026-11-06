package com.smartsearch.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class OrkutController {

    @GetMapping({"/services/orkut", "/orkut"})
    public String orkut() {
        return "redirect:http://localhost:8001/";
    }
}
