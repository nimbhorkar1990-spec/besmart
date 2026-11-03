package com.smartsearch.service;

import com.smartsearch.dto.UserProfileDto;
import com.smartsearch.dto.UserRegistrationDto;
import com.smartsearch.model.Role;
import com.smartsearch.model.SavedResult;
import com.smartsearch.model.SearchHistory;
import com.smartsearch.model.User;
import com.smartsearch.repository.SavedResultRepository;
import com.smartsearch.repository.SearchHistoryRepository;
import com.smartsearch.repository.UserRepository;
import com.smartsearch.security.CustomUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final SearchHistoryRepository historyRepository;
    private final SavedResultRepository savedResultRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository,
                       SearchHistoryRepository historyRepository,
                       SavedResultRepository savedResultRepository,
                       PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.historyRepository = historyRepository;
        this.savedResultRepository = savedResultRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Optional<User> getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return Optional.empty();
        }
        if (auth.getPrincipal() instanceof CustomUserDetails userDetails) {
            return userRepository.findById(userDetails.getId());
        }
        return userRepository.findByUsername(auth.getName());
    }

    @Transactional
    public User registerUser(UserRegistrationDto dto) {
        if (userRepository.existsByUsername(dto.getUsername())) {
            throw new IllegalArgumentException("Username '" + dto.getUsername() + "' is already taken.");
        }
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email '" + dto.getEmail() + "' is already registered.");
        }
        if (!dto.getPassword().equals(dto.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        if (dto.getMobileNumber() == null || dto.getMobileNumber().isBlank()) {
            throw new IllegalArgumentException("Mobile number is mandatory.");
        }

        User user = new User(
                dto.getUsername().trim(),
                dto.getEmail().trim().toLowerCase(),
                passwordEncoder.encode(dto.getPassword()),
                dto.getFullName().trim(),
                Role.ROLE_USER
        );
        user.setAvatarUrl("https://ui-avatars.com/api/?name=" + dto.getFullName().replace(' ', '+') + "&background=1a73e8&color=fff");
        user.setMobileNumber(dto.getMobileNumber().trim());
        user.setWhatsappNumber(dto.getWhatsappNumber() != null && !dto.getWhatsappNumber().isBlank() ? dto.getWhatsappNumber().trim() : null);

        if ("EMPLOYER".equalsIgnoreCase(dto.getAccountType())) {
            if (!dto.hasMinimumCertificates()) {
                throw new IllegalArgumentException(
                        "Employer Verification Error: Minimum any two certificates (Shop Act, GST Certificate, RERA Certificate, or MSME Certificate) are strictly required to sign up as an employer.");
            }
            user.setAccountType("EMPLOYER");
            user.setCompanyName(dto.getCompanyName() != null && !dto.getCompanyName().isBlank() ? dto.getCompanyName().trim() : dto.getFullName());
            user.setShopActCert(dto.getShopActCert() != null ? dto.getShopActCert().trim() : null);
            user.setGstCert(dto.getGstCert() != null ? dto.getGstCert().trim() : null);
            user.setReraCert(dto.getReraCert() != null ? dto.getReraCert().trim() : null);
            user.setMsmeCert(dto.getMsmeCert() != null ? dto.getMsmeCert().trim() : null);
            user.setBusinessVerified(true);
        } else {
            user.setAccountType("JOB_SEEKER");
        }

        return userRepository.save(user);
    }

    @Transactional
    public User updateProfile(User user, UserProfileDto dto) {
        if (!user.getEmail().equalsIgnoreCase(dto.getEmail()) && userRepository.existsByEmail(dto.getEmail())) {
            throw new IllegalArgumentException("Email '" + dto.getEmail() + "' is already taken.");
        }

        user.setFullName(dto.getFullName().trim());
        user.setEmail(dto.getEmail().trim().toLowerCase());

        if (dto.getMobileNumber() != null && !dto.getMobileNumber().isBlank()) {
            user.setMobileNumber(dto.getMobileNumber().trim());
        }
        if (dto.getWhatsappNumber() != null) {
            user.setWhatsappNumber(dto.getWhatsappNumber().isBlank() ? null : dto.getWhatsappNumber().trim());
        }

        if (dto.getAvatarUrl() != null && !dto.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(dto.getAvatarUrl().trim());
        }

        // Change password if provided
        if (dto.getNewPassword() != null && !dto.getNewPassword().isBlank()) {
            if (dto.getCurrentPassword() == null || !passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
                throw new IllegalArgumentException("Current password is incorrect.");
            }
            if (dto.getNewPassword().length() < 6) {
                throw new IllegalArgumentException("New password must be at least 6 characters.");
            }
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }

        return userRepository.save(user);
    }

    // --- Saved Results Management ---

    @Transactional
    public SavedResult saveResult(User user, String title, String url, String snippet, String category, String imageUrl) {
        return savedResultRepository.findByUserAndUrl(user, url)
                .orElseGet(() -> savedResultRepository.save(new SavedResult(user, title, url, snippet, category, imageUrl)));
    }

    @Transactional
    public void removeSavedResult(User user, Long savedResultId) {
        savedResultRepository.deleteByUserAndId(user, savedResultId);
    }

    @Transactional
    public void removeSavedResultByUrl(User user, String url) {
        savedResultRepository.findByUserAndUrl(user, url).ifPresent(savedResultRepository::delete);
    }

    public boolean isResultSaved(User user, String url) {
        if (user == null || url == null) return false;
        return savedResultRepository.existsByUserAndUrl(user, url);
    }

    public Page<SavedResult> getUserSavedResults(User user, String category, Pageable pageable) {
        if (category != null && !category.equalsIgnoreCase("ALL") && !category.isBlank()) {
            return savedResultRepository.findByUserAndCategoryOrderBySavedAtDesc(user, category.toUpperCase(), pageable);
        }
        return savedResultRepository.findByUserOrderBySavedAtDesc(user, pageable);
    }

    // --- Search History Management ---

    public Page<SearchHistory> getUserHistory(User user, String query, Pageable pageable) {
        if (query != null && !query.isBlank()) {
            return historyRepository.findByUserAndQueryContainingIgnoreCaseOrderBySearchedAtDesc(user, query.trim(), pageable);
        }
        return historyRepository.findByUserOrderBySearchedAtDesc(user, pageable);
    }

    @Transactional
    public void deleteHistoryItem(User user, Long historyId) {
        historyRepository.findById(historyId).ifPresent(h -> {
            if (h.getUser() != null && h.getUser().getId().equals(user.getId())) {
                historyRepository.delete(h);
            }
        });
    }

    @Transactional
    public void clearAllHistory(User user) {
        historyRepository.deleteByUser(user);
    }

    // --- Admin User Management ---

    public Page<User> getAllUsers(String search, Pageable pageable) {
        if (search != null && !search.isBlank()) {
            return userRepository.findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(search.trim(), search.trim(), pageable);
        }
        return userRepository.findAll(pageable);
    }

    @Transactional
    public void toggleUserStatus(Long userId) {
        userRepository.findById(userId).ifPresent(u -> {
            u.setEnabled(!u.isEnabled());
            userRepository.save(u);
        });
    }

    @Transactional
    public void deleteUser(Long userId) {
        userRepository.deleteById(userId);
    }
}

