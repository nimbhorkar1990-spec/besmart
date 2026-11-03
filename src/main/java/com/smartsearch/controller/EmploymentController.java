package com.smartsearch.controller;

import com.smartsearch.dto.JobListingDto;
import com.smartsearch.model.User;
import com.smartsearch.service.EmploymentService;
import com.smartsearch.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Controller
public class EmploymentController {

    private final EmploymentService employmentService;
    private final UserService userService;

    public EmploymentController(EmploymentService employmentService, UserService userService) {
        this.employmentService = employmentService;
        this.userService = userService;
    }

    @GetMapping({"/employment", "/services/employment"})
    public String employmentPortal(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "category", required = false, defaultValue = "ALL") String category,
            @RequestParam(value = "type", required = false, defaultValue = "ALL") String type,
            HttpServletRequest request,
            Model model) {

        // Eagerly establish session and resolve CSRF token before template rendering starts
        request.getSession(true);
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // Trigger token generation before response stream commits
            model.addAttribute("_csrf", csrfToken);
        }

        Optional<User> currentUser = userService.getCurrentUser();
        model.addAttribute("currentUser", currentUser.orElse(null));

        boolean isEmployer = currentUser.isPresent() && (
                "EMPLOYER".equalsIgnoreCase(currentUser.get().getAccountType()) ||
                currentUser.get().getRole() == com.smartsearch.model.Role.ROLE_ADMIN
        );
        model.addAttribute("isEmployer", isEmployer);

        List<JobListingDto> jobs = employmentService.getJobListings(search, category, type);
        model.addAttribute("jobs", jobs);
        model.addAttribute("stats", employmentService.getEmploymentStats());
        model.addAttribute("search", search != null ? search : "");
        model.addAttribute("selectedCategory", category);
        model.addAttribute("selectedType", type);
        model.addAttribute("totalJobCount", jobs.size());

        return "employment";
    }

    @PostMapping("/employment/apply")
    public String applyForJob(
            @RequestParam("jobId") Long jobId,
            @RequestParam("fullName") String fullName,
            @RequestParam("email") String email,
            @RequestParam(value = "phone", required = false) String phone,
            @RequestParam(value = "experience", required = false) String experience,
            @RequestParam(value = "notes", required = false) String notes,
            RedirectAttributes redirectAttributes) {

        boolean applied = employmentService.applyToJob(jobId, fullName, email, phone, experience, notes);
        if (applied) {
            redirectAttributes.addFlashAttribute("successMessage",
                    "Application submitted successfully! Our recruitment team and the hiring employer have received your profile.");
        } else {
            redirectAttributes.addFlashAttribute("errorMessage", "Job posting could not be located or has expired.");
        }
        return "redirect:/employment";
    }

    @PostMapping("/employment/post-job")
    public String postJob(
            @RequestParam("title") String title,
            @RequestParam("company") String company,
            @RequestParam("location") String location,
            @RequestParam(value = "jobType", defaultValue = "Full-time") String jobType,
            @RequestParam(value = "category", defaultValue = "ENGINEERING") String category,
            @RequestParam(value = "salaryRange", defaultValue = "Best in Industry") String salaryRange,
            @RequestParam(value = "experienceLevel", defaultValue = "2 - 5 Years") String experienceLevel,
            @RequestParam("description") String description,
            @RequestParam(value = "skills", defaultValue = "Java, Spring Boot") String skills,
            @RequestParam(value = "shopActCert", required = false) String shopActCert,
            @RequestParam(value = "gstCert", required = false) String gstCert,
            @RequestParam(value = "reraCert", required = false) String reraCert,
            @RequestParam(value = "msmeCert", required = false) String msmeCert,
            RedirectAttributes redirectAttributes) {

        Optional<User> currentUserOpt = userService.getCurrentUser();
        if (currentUserOpt.isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Authentication Required: Please sign in with an Employer account to post job advertisements.");
            return "redirect:/login";
        }

        User currentUser = currentUserOpt.get();
        boolean isEmployer = "EMPLOYER".equalsIgnoreCase(currentUser.getAccountType()) ||
                currentUser.getRole() == com.smartsearch.model.Role.ROLE_ADMIN;

        if (!isEmployer) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Access Denied: Only authenticated Employer accounts can post job advertisements. Your account is registered as a Job Seeker.");
            return "redirect:/employment";
        }

        List<String> tags = Arrays.stream(skills.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        JobListingDto job = new JobListingDto();
        job.setTitle(title.trim());
        job.setCompany(company != null && !company.isBlank() ? company.trim() : (currentUser.getCompanyName() != null ? currentUser.getCompanyName() : currentUser.getFullName()));
        job.setLocation(location.trim());
        job.setJobType(jobType);
        job.setCategory(category);
        job.setSalaryRange(salaryRange.trim());
        job.setExperienceLevel(experienceLevel.trim());
        job.setDescription(description.trim());
        job.setTags(tags);

        // Populate certificate fields; fallback to employer's registered verified certificates if left blank
        String finalShopAct = (shopActCert != null && !shopActCert.isBlank()) ? shopActCert.trim() : currentUser.getShopActCert();
        String finalGst = (gstCert != null && !gstCert.isBlank()) ? gstCert.trim() : currentUser.getGstCert();
        String finalRera = (reraCert != null && !reraCert.isBlank()) ? reraCert.trim() : currentUser.getReraCert();
        String finalMsme = (msmeCert != null && !msmeCert.isBlank()) ? msmeCert.trim() : currentUser.getMsmeCert();

        job.setShopActCert(finalShopAct);
        job.setGstCert(finalGst);
        job.setReraCert(finalRera);
        job.setMsmeCert(finalMsme);
        job.setFeatured(true);

        try {
            employmentService.postJob(job);
            redirectAttributes.addFlashAttribute("successMessage",
                    "New job opening '" + title + "' for '" + job.getCompany() + "' posted successfully to the Employment Portal with verified business credentials!");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/employment";
    }
}
