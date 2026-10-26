package com.durdencorp.pswmanager.config;

import com.durdencorp.pswmanager.interceptor.RateLimitInterceptor;
import com.durdencorp.pswmanager.interceptor.AuthenticationInterceptor;
import com.durdencorp.pswmanager.interceptor.WebSecurityInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    @Autowired
    private AuthenticationInterceptor authenticationInterceptor;

    @Autowired
    private WebSecurityInterceptor webSecurityInterceptor;
    
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(webSecurityInterceptor).addPathPatterns("/**");
		registry.addInterceptor(authenticationInterceptor)
				.addPathPatterns("/**")
				.excludePathPatterns("/login", "/error", "/css/**", "/js/**", "/images/**", "/favicon.ico");
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/login")
                .excludePathPatterns(
                    "/css/**",
                    "/js/**", 
                    "/images/**",
                    "/favicon.ico",
                    "/error",
                    "/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                );
    }
}
