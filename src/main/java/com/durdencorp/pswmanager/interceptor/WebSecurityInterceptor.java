package com.durdencorp.pswmanager.interceptor;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

@Component
public class WebSecurityInterceptor implements HandlerInterceptor {
    private static final String CSRF_SESSION_ATTRIBUTE = "csrfToken";
    private static final String CSRF_HEADER = "X-CSRF-TOKEN";
    private final SecureRandom random = new SecureRandom();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        addSecurityHeaders(response);
        HttpSession session = request.getSession(true);
        String expected = (String) session.getAttribute(CSRF_SESSION_ATTRIBUTE);
        if (expected == null) {
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            expected = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            session.setAttribute(CSRF_SESSION_ATTRIBUTE, expected);
        }
        request.setAttribute("csrfToken", expected);

        if (isUnsafe(request) && !isCsrfExempt(request)) {
            String supplied = request.getHeader(CSRF_HEADER);
            if (supplied == null) supplied = request.getParameter("_csrf");
            if (supplied == null || !java.security.MessageDigest.isEqual(
                    expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    supplied.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Token CSRF non valido");
                return false;
            }
        }
        return true;
    }

    private boolean isUnsafe(HttpServletRequest request) {
        return !java.util.Set.of("GET", "HEAD", "OPTIONS", "TRACE").contains(request.getMethod());
    }

    private boolean isCsrfExempt(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/api/security/check-password") || path.startsWith("/api/password-tools/");
    }

    private void addSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; "
                + "style-src 'self' 'unsafe-inline' https://cdnjs.cloudflare.com https://cdn.jsdelivr.net; "
                + "font-src 'self' https://cdnjs.cloudflare.com; img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'");
    }
}
