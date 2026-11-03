package com.smartsearch.controller;

import com.smartsearch.dto.UserProfileDto;
import com.smartsearch.model.Category;
import com.smartsearch.model.SavedResult;
import com.smartsearch.model.SearchHistory;
import com.smartsearch.model.User;
import com.smartsearch.service.UserService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping({"", "/", "/profile"})
    public String profile(Model model) {
        User user = userService.getCurrentUser().orElseThrow();
        UserProfileDto dto = new UserProfileDto();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setMobileNumber(user.getMobileNumber());
        dto.setWhatsappNumber(user.getWhatsappNumber());

        model.addAttribute("currentUser", user);
        model.addAttribute("profileDto", dto);
        return "user/profile";
    }

    @PostMapping("/profile")
    public String updateProfile(@Valid @ModelAttribute("profileDto") UserProfileDto dto,
                                BindingResult bindingResult,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        User user = userService.getCurrentUser().orElseThrow();
        if (bindingResult.hasErrors()) {
            model.addAttribute("currentUser", user);
            return "user/profile";
        }

        try {
            userService.updateProfile(user, dto);
            redirectAttributes.addFlashAttribute("successMessage", "Profile updated successfully!");
            return "redirect:/user/profile";
        } catch (IllegalArgumentException e) {
            model.addAttribute("currentUser", user);
            model.addAttribute("errorMessage", e.getMessage());
            return "user/profile";
        }
    }

    @GetMapping("/history")
    public String searchHistory(@RequestParam(value = "query", required = false) String query,
                                @RequestParam(value = "page", defaultValue = "1") int page,
                                Model model) {
        User user = userService.getCurrentUser().orElseThrow();
        int pageNumber = Math.max(page - 1, 0);
        Page<SearchHistory> historyPage = userService.getUserHistory(user, query, PageRequest.of(pageNumber, 15));

        model.addAttribute("currentUser", user);
        model.addAttribute("historyPage", historyPage);
        model.addAttribute("query", query != null ? query : "");
        model.addAttribute("currentPage", page);
        return "user/history";
    }

    @PostMapping("/history/delete")
    public String deleteHistoryItem(@RequestParam("id") Long id, RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUser().orElseThrow();
        userService.deleteHistoryItem(user, id);
        redirectAttributes.addFlashAttribute("successMessage", "History entry deleted.");
        return "redirect:/user/history";
    }

    @PostMapping("/history/clear")
    public String clearAllHistory(RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUser().orElseThrow();
        userService.clearAllHistory(user);
        redirectAttributes.addFlashAttribute("successMessage", "Your search history has been cleared.");
        return "redirect:/user/history";
    }

    @GetMapping("/saved")
    public String savedResults(@RequestParam(value = "category", required = false) String category,
                               @RequestParam(value = "page", defaultValue = "1") int page,
                               Model model) {
        User user = userService.getCurrentUser().orElseThrow();
        int pageNumber = Math.max(page - 1, 0);
        Page<SavedResult> savedPage = userService.getUserSavedResults(user, category, PageRequest.of(pageNumber, 12));

        model.addAttribute("currentUser", user);
        model.addAttribute("savedPage", savedPage);
        model.addAttribute("selectedCategory", category != null ? category : "ALL");
        model.addAttribute("categories", Category.values());
        model.addAttribute("currentPage", page);
        return "user/saved";
    }

    @PostMapping("/saved/delete")
    public String deleteSavedResult(@RequestParam("id") Long id,
                                    @RequestParam(value = "category", required = false) String category,
                                    RedirectAttributes redirectAttributes) {
        User user = userService.getCurrentUser().orElseThrow();
        userService.removeSavedResult(user, id);
        redirectAttributes.addFlashAttribute("successMessage", "Bookmark removed.");
        return "redirect:/user/saved" + (category != null ? "?category=" + category : "");
    }
}

