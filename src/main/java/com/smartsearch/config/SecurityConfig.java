package com.smartsearch.config;

import com.smartsearch.security.CustomUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;

    public SecurityConfig(CustomUserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                // Static resources
                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
                // Error handling
                .requestMatchers("/error", "/error/**").permitAll()
                // Public search and homepage aliases
                .requestMatchers("/", "/index", "/index.html", "/home", "/employment", "/employment/**", "/services/**", "/blogs", "/blogs/**").permitAll()
                .requestMatchers("/search", "/api/search", "/api/search/**", "/api/suggest", "/api/voice/**").permitAll()
                .requestMatchers("/login", "/register", "/forgot-password").permitAll()
                // Admin dashboard and management
                .requestMatchers("/admin", "/admin/**", "/dashboard", "/indexer", "/crawler", "/api/admin/**").hasAuthority("ROLE_ADMIN")
                // User portal
                .requestMatchers("/user", "/user/**", "/profile", "/history", "/saved", "/api/user/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .loginProcessingUrl("/login")
                .defaultSuccessUrl("/", false)
                .failureUrl("/login?error=true")
                .permitAll()
            )
            .logout(logout -> logout
                .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                .logoutSuccessUrl("/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll()
            )
            .rememberMe(remember -> remember
                .key("smartSearchSecureRememberMeKey123!*")
                .tokenValiditySeconds(86400 * 14) // 14 days
                .userDetailsService(userDetailsService)
            )
            .csrf(csrf -> csrf
                // Disable CSRF for REST API calls from client JS if desired or allow headers
                .ignoringRequestMatchers("/api/**")
            );

        return http.build();
    }
}
