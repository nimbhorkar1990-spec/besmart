package com.smartsearch.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/");
        registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/");
        registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/");
        registry.addResourceHandler("/favicon.ico").addResourceLocations("classpath:/static/images/logo-icon.svg");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/dashboard", "/admin/dashboard");
        registry.addRedirectViewController("/indexer", "/admin/indexer");
        registry.addRedirectViewController("/crawler", "/admin/indexer");
        registry.addRedirectViewController("/profile", "/user/profile");
        registry.addRedirectViewController("/history", "/user/history");
        registry.addRedirectViewController("/saved", "/user/saved");
        registry.addRedirectViewController("/user", "/user/profile");
        registry.addRedirectViewController("/user/", "/user/profile");
    }
}
