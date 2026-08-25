package net.emberhold.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Lightweight staff gate: requests to /api/* must carry {@code X-Admin-Token} matching the
 * configured {@code ember.web.token} (set via {@code EMBER_WEB_TOKEN}). Discord OAuth is a
 * documented follow-up; a shared secret is the honest MVP.
 */
public final class AdminTokenFilter extends OncePerRequestFilter {

    private final String token;

    public AdminTokenFilter(String token) {
        this.token = token;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String provided = req.getHeader("X-Admin-Token");
        if (token != null && !token.isBlank() && token.equals(provided)) {
            chain.doFilter(req, res);
            return;
        }
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"unauthorized\"}");
    }
}
