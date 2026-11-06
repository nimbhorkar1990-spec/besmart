package com.smartsearch.controller;

import com.smartsearch.model.User;
import com.smartsearch.service.UserService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalModelAttributesAdvice {

    private final UserService userService;

    public GlobalModelAttributesAdvice(UserService userService) {
        this.userService = userService;
    }

    @ModelAttribute("currentUser")
    public User populateCurrentUser() {
        return userService.getCurrentUser().orElse(null);
    }
}

