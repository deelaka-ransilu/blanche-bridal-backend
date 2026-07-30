package com.blanchebridal.backend.auth.security;

import com.blanchebridal.backend.user.entity.User;
import com.blanchebridal.backend.user.repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        boolean valid;
        boolean expired = false;
        try {
            valid = jwtUtil.validateToken(token);
        } catch (ExpiredJwtException e) {
            valid = false;
            expired = true;
        } catch (Exception e) {
            valid = false;
        }

        if (!valid) {
            if (expired) {
                log.info("[JwtFilter] Expired token — returning 401 for client-side refresh");
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"success\":false,\"message\":\"Token expired\",\"error\":\"TOKEN_EXPIRED\"}");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        String email = jwtUtil.extractEmail(token);
        String role = jwtUtil.extractRole(token);

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            java.util.Optional<User> userOpt = userRepository.findByEmail(email);

            if (userOpt.isEmpty()) {
                log.warn("[JwtFilter] User not found: {}", email);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"User not found\"}");
                return;
            }

            User user = userOpt.get();

            if (!user.isActive()) {
                log.warn("[JwtFilter] Rejected token for inactive user: {}", email);
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false,\"message\":\"Account is inactive. Please contact an administrator.\"}");
                return;
            }

            log.info("[JwtFilter] Authenticated user: {} with role: {}", email, role);

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            user,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
            authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}