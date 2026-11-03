package com.smartsearch.controller;

import com.smartsearch.dto.UserRegistrationDto;
import com.smartsearch.service.UserService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/login")
    public String loginPage(@RequestParam(value = "error", required = false) String error,
                            @RequestParam(value = "logout", required = false) String logout,
                            Model model) {
        if (userService.getCurrentUser().isPresent()) {
            return "redirect:/";
        }
        if (error != null) {
            model.addAttribute("errorMessage", "Invalid username/email or password.");
        }
        if (logout != null) {
            model.addAttribute("successMessage", "You have been logged out successfully.");
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage(@RequestParam(value = "role", required = false) String role, Model model) {
        if (userService.getCurrentUser().isPresent()) {
            return "redirect:/";
        }
        UserRegistrationDto dto = new UserRegistrationDto();
        if ("employer".equalsIgnoreCase(role)) {
            dto.setAccountType("EMPLOYER");
        }
        model.addAttribute("registrationDto", dto);
        return "auth/register";
    }

    @PostMapping("/register")
    public String processRegistration(@Valid @ModelAttribute("registrationDto") UserRegistrationDto dto,
                                      BindingResult bindingResult,
                                      RedirectAttributes redirectAttributes,
                                      Model model) {
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }

        try {
            userService.registerUser(dto);
            redirectAttributes.addFlashAttribute("successMessage", "Registration successful! You can now log in.");
            return "redirect:/login";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "auth/register";
        }
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage() {
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String handleForgotPassword(@RequestParam("identifier") String identifier,
                                       RedirectAttributes redirectAttributes) {
        // Safe UX response (preventing user enumeration)
        redirectAttributes.addFlashAttribute("successMessage", 
                "If an account exists for '" + identifier + "', password reset instructions have been dispatched.");
        return "redirect:/forgot-password";
    }
}

