package com.osporo.engine.shared.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.osporo.engine.auth.JwtService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    
    private final JwtService jwtService;
    private final TenantContextHolder tenantContextHolder;

    public JwtAuthenticationFilter(
        JwtService jwtService,
        TenantContextHolder tenantContextHolder
    ) {
        this.jwtService           = jwtService;
        this.tenantContextHolder  = tenantContextHolder;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            String authHeader = request.getHeader("Authorization");
            // log.debug("Authorization header: {}", authHeader);

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                // String tenantHeader = request.getHeader("X-Tenant-ID");
                // log.debug("No Bearer token found. X-Tenant-ID header: {}", tenantHeader);
                resolveTenantFromHeader(request);
                filterChain.doFilter(request, response);
                return;
            }

            String token = authHeader.substring(7);
            // log.debug("Token: {}", token);

            try {
                Claims claims = jwtService.validateAndExtract(token);
                // log.debug("Claims: {}", claims);

                UUID tenantId = UUID.fromString(claims.get("tenant_id", String.class));
                UUID userId   = UUID.fromString(claims.getSubject());

                List<String> permissions = claims.get("permissions", List.class);

                List<GrantedAuthority> authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

                    OsporoPrincipal principal = new OsporoPrincipal(userId, tenantId, permissions);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(principal, null, authorities);

                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
                );

                SecurityContextHolder.getContext().setAuthentication(authentication);
                tenantContextHolder.setTenantId(tenantId);

            } catch (JwtException | IllegalArgumentException e) {
                // log.error(e.getMessage());
                SecurityContextHolder.clearContext();
            }

            filterChain.doFilter(request, response);

        } finally {
            tenantContextHolder.clear();
        }
    }

    private void resolveTenantFromHeader(HttpServletRequest request) {
        String tenantHeader = request.getHeader("X-Tenant-ID");
        // log.debug("Attempting to resolve tenant from header. Value: {}", tenantHeader);

        if (tenantHeader != null && !tenantHeader.isBlank()) {
            try {
                UUID tenantId = UUID.fromString(tenantHeader);
                tenantContextHolder.setTenantId(tenantId);
                // log.debug("Tenant context set from header: {}", tenantId);
            } catch (IllegalArgumentException ignored) {
                // Malformed tenant ID — service layer handles missing context
                // log.warn("Malformed X-Tenant-ID header value: {}", tenantHeader);
            }
        } /* else {
            log.debug("X-Tenant-ID header is missing or blank");
        } */
    }
}
