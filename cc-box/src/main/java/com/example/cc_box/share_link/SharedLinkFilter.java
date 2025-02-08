// SharedLinkFilter.java
package com.example.cc_box.share_link;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.IOException;
import java.util.*;
import java.net.URL;

public class SharedLinkFilter extends OncePerRequestFilter {
    private static final Logger logger = LoggerFactory.getLogger(SharedLinkFilter.class);

    private final SharedLinkService sharedLinkService;

    public SharedLinkFilter(SharedLinkService sharedLinkService) {
        this.sharedLinkService = sharedLinkService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String requestUri = request.getRequestURI();

        if (requestUri.startsWith("/share/")) {
            String token = extractToken(requestUri);

            if (token != null) {
                try {
                    SharedLink sharedLink = sharedLinkService.getResourceByToken(token);
                    if (sharedLink != null) {
                        if (sharedLink.getExpiryDate() != null &&
                                sharedLink.getExpiryDate().before(new Date())) {
                            response.sendError(HttpServletResponse.SC_GONE, "Link has expired");
                            return;
                        }

                        String authToken = sharedLink.getAuthToken();
                        URL originalUrl = new URL(sharedLink.getOriginalUrl());
                        String path = originalUrl.getPath();

                        // Create a wrapped request with complete header handling
                        HttpServletRequest wrappedRequest = new HttpServletRequestWrapper(request) {
                            private final Map<String, String> customHeaders = new HashMap<>();

                            {
                                customHeaders.put("Authorization",  authToken);
                            }

                            @Override
                            public String getHeader(String name) {
                                String headerValue = customHeaders.get(name);
                                return headerValue != null ? headerValue : super.getHeader(name);
                            }

                            @Override
                            public Enumeration<String> getHeaders(String name) {
                                if (customHeaders.containsKey(name)) {
                                    return Collections.enumeration(Collections.singletonList(customHeaders.get(name)));
                                }
                                return super.getHeaders(name);
                            }

                            @Override
                            public Enumeration<String> getHeaderNames() {
                                Set<String> headerNames = new HashSet<>();
                                Enumeration<String> originalHeaders = super.getHeaderNames();
                                while (originalHeaders.hasMoreElements()) {
                                    headerNames.add(originalHeaders.nextElement());
                                }
                                headerNames.addAll(customHeaders.keySet());
                                return Collections.enumeration(headerNames);
                            }
                        };

                        logger.info("Forwarding to path: {}", path);
                        request.getRequestDispatcher(path).forward(wrappedRequest, response);
                        return;
                    } else {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found");
                    }
                } catch (Exception e) {
                    logger.error("Error processing the request", e);
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error processing request");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(String requestUri) {
        String[] parts = requestUri.split("/");
        return parts.length > 2 ? parts[parts.length - 1] : null;
    }
}
