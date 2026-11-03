package com.smartsearch.controller;

import com.smartsearch.model.User;
import com.smartsearch.repository.SearchHistoryRepository;
import com.smartsearch.service.AnalyticsService;
import com.smartsearch.service.SystemSettingService;
import com.smartsearch.service.UserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Optional;

@Controller
public class HomeController {

    private final UserService userService;
    private final SearchHistoryRepository historyRepository;
    private final AnalyticsService analyticsService;
    private final SystemSettingService settingService;

    public HomeController(UserService userService,
                          SearchHistoryRepository historyRepository,
                          AnalyticsService analyticsService,
                          SystemSettingService settingService) {
        this.userService = userService;
        this.historyRepository = historyRepository;
        this.analyticsService = analyticsService;
        this.settingService = settingService;
    }

    @GetMapping({"/", "/index", "/index.html", "/home"})
    public String index(Model model) {
        Optional<User> currentUser = userService.getCurrentUser();
        model.addAttribute("currentUser", currentUser.orElse(null));

        // Recent searches for current user (or popular queries if anonymous)
        if (currentUser.isPresent()) {
            List<String> recentQueries = historyRepository.findDistinctRecentQueriesByUser(currentUser.get(), PageRequest.of(0, 5));
            model.addAttribute("recentSearches", recentQueries);
        } else {
            List<String> topQueries = analyticsService.getTopQueries(5)
                    .stream()
                    .map(com.smartsearch.model.SearchAnalytics::getQuery)
                    .toList();
            model.addAttribute("recentSearches", topQueries);
        }

        model.addAttribute("voiceSearchEnabled", settingService.getBooleanSetting("VOICE_SEARCH_ENABLED", true));
        return "index";
    }
}

