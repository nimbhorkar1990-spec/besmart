package com.smartsearch.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class CustomErrorController implements ErrorController {

    private static final Logger logger = LoggerFactory.getLogger(CustomErrorController.class);

    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        Object originalUri = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        Object message = request.getAttribute(RequestDispatcher.ERROR_MESSAGE);

        int statusCode = 500;
        if (status != null) {
            try {
                statusCode = Integer.parseInt(status.toString());
            } catch (NumberFormatException ignored) {
            }
        }

        String path = (originalUri != null) ? originalUri.toString() : request.getRequestURI();
        logger.warn("HTTP Error handled - Status: {}, Path: {}, Message: {}", statusCode, path, message);

        model.addAttribute("statusCode", statusCode);
        model.addAttribute("path", path != null ? path : "");

        switch (statusCode) {
            case 404 -> {
                model.addAttribute("errorTitle", "Page Not Found");
                model.addAttribute("errorMessage", "The page or resource you requested could not be found: " + path);
                model.addAttribute("errorHint", "Check the URL for typos or return to the search homepage to find what you need.");
            }
            case 403 -> {
                model.addAttribute("errorTitle", "Access Denied");
                model.addAttribute("errorMessage", "You do not have permission to access " + path);
                model.addAttribute("errorHint", "If this is an administrative area, please sign in with an administrator account.");
            }
            case 400 -> {
                model.addAttribute("errorTitle", "Bad Request");
                model.addAttribute("errorMessage", "The request could not be processed by the server.");
                model.addAttribute("errorHint", "Please check your query parameters and try again.");
            }
            case 500 -> {
                model.addAttribute("errorTitle", "Server Error");
                model.addAttribute("errorMessage", "An unexpected error occurred while processing your request.");
                model.addAttribute("errorHint", "Our team has been notified. Please try again in a few moments.");
            }
            default -> {
                model.addAttribute("errorTitle", "Error " + statusCode);
                model.addAttribute("errorMessage", (message != null && !message.toString().isBlank()) ? message.toString() : "An unexpected issue occurred.");
                model.addAttribute("errorHint", "Return to the search homepage or try again.");
            }
        }

        return "error";
    }
}

