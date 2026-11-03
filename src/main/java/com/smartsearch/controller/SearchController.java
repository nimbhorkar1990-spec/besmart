package com.smartsearch.controller;

import com.smartsearch.dto.SearchRequestDto;
import com.smartsearch.dto.SearchResponseDto;
import com.smartsearch.model.Category;
import com.smartsearch.model.User;
import com.smartsearch.service.SearchService;
import com.smartsearch.service.SystemSettingService;
import com.smartsearch.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;

@Controller
public class SearchController {

    private final SearchService searchService;
    private final UserService userService;
    private final SystemSettingService settingService;

    public SearchController(SearchService searchService,
                            UserService userService,
                            SystemSettingService settingService) {
        this.searchService = searchService;
        this.userService = userService;
        this.settingService = settingService;
    }

    @GetMapping("/search")
    public String search(@ModelAttribute SearchRequestDto requestDto,
                         HttpServletRequest httpRequest,
                         Model model) {
        // Normalize request
        if ((requestDto.getQuery() == null || requestDto.getQuery().isBlank()) && requestDto.getQ() != null) {
            requestDto.setQuery(requestDto.getQ().trim());
        }
        if (requestDto.getQuery() == null) {
            requestDto.setQuery("");
        }
        if (requestDto.getCategory() == null || requestDto.getCategory().isBlank()) {
            requestDto.setCategory("ALL");
        }

        String ipAddress = httpRequest.getRemoteAddr();
        SearchResponseDto response = searchService.performSearch(requestDto, ipAddress);

        Optional<User> currentUser = userService.getCurrentUser();
        model.addAttribute("currentUser", currentUser.orElse(null));
        model.addAttribute("req", requestDto);
        model.addAttribute("res", response);
        model.addAttribute("categories", Category.values());
        model.addAttribute("voiceSearchEnabled", settingService.getBooleanSetting("VOICE_SEARCH_ENABLED", true));

        return "search";
    }
}

