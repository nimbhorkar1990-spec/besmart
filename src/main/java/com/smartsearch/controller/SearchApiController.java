package com.smartsearch.controller;

import com.smartsearch.dto.SearchRequestDto;
import com.smartsearch.dto.SearchResponseDto;
import com.smartsearch.model.User;
import com.smartsearch.service.AnalyticsService;
import com.smartsearch.service.SearchService;
import com.smartsearch.service.SystemSettingService;
import com.smartsearch.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class SearchApiController {

    private final SearchService searchService;
    private final AnalyticsService analyticsService;
    private final UserService userService;
    private final SystemSettingService settingService;

    public SearchApiController(SearchService searchService,
                               AnalyticsService analyticsService,
                               UserService userService,
                               SystemSettingService settingService) {
        this.searchService = searchService;
        this.analyticsService = analyticsService;
        this.userService = userService;
        this.settingService = settingService;
    }

    @GetMapping("/suggest")
    public ResponseEntity<List<String>> getSuggestions(@RequestParam("q") String prefix) {
        if (!settingService.getBooleanSetting("SUGGEST_ENABLED", true)) {
            return ResponseEntity.ok(List.of());
        }
        List<String> suggestions = analyticsService.getSuggestions(prefix, 8);
        return ResponseEntity.ok(suggestions);
    }

    @GetMapping("/search")
    public ResponseEntity<SearchResponseDto> searchApi(@ModelAttribute SearchRequestDto request, HttpServletRequest httpRequest) {
        if ((request.getQuery() == null || request.getQuery().isBlank()) && request.getQ() != null) {
            request.setQuery(request.getQ().trim());
        }
        String ipAddress = httpRequest.getRemoteAddr();
        SearchResponseDto response = searchService.performSearch(request, ipAddress);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/user/saved")
    public ResponseEntity<Map<String, Object>> toggleSavedResult(@RequestBody Map<String, String> payload) {
        Map<String, Object> resp = new HashMap<>();
        Optional<User> currentUser = userService.getCurrentUser();

        if (currentUser.isEmpty()) {
            resp.put("success", false);
            resp.put("message", "Please log in to save bookmarks.");
            return ResponseEntity.status(401).body(resp);
        }

        User user = currentUser.get();
        String url = payload.get("url");
        String title = payload.getOrDefault("title", "Untitled");
        String snippet = payload.getOrDefault("snippet", "");
        String category = payload.getOrDefault("category", "ALL");
        String imageUrl = payload.get("imageUrl");

        if (url == null || url.isBlank()) {
            resp.put("success", false);
            resp.put("message", "URL is required.");
            return ResponseEntity.badRequest().body(resp);
        }

        if (userService.isResultSaved(user, url)) {
            userService.removeSavedResultByUrl(user, url);
            resp.put("success", true);
            resp.put("saved", false);
            resp.put("message", "Bookmark removed.");
        } else {
            userService.saveResult(user, title, url, snippet, category, imageUrl);
            resp.put("success", true);
            resp.put("saved", true);
            resp.put("message", "Bookmark saved to your account!");
        }

        return ResponseEntity.ok(resp);
    }
}

