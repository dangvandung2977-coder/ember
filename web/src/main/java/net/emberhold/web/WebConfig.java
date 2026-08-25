package net.emberhold.web;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

/** Registers the staff bearer-token filter on all /api/* endpoints. */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<AdminTokenFilter> adminTokenFilter(
            @Value("${ember.web.token:change-me}") String token) {
        FilterRegistrationBean<AdminTokenFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new AdminTokenFilter(token));
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);
        return reg;
    }
}
