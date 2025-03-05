package org.dbpedia.moss.utils;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;

public class RequestMethodFilterWrapper implements Filter {
    private final Filter targetFilter;
    private final String[] allowedMethods;

    public RequestMethodFilterWrapper(Filter targetFilter, String[] allowedMethods) {
        this.targetFilter = targetFilter;
        this.allowedMethods = allowedMethods;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        targetFilter.init(filterConfig);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest) {
            HttpServletRequest httpRequest = (HttpServletRequest) request;

            for(int i = 0; i < allowedMethods.length; i++) {
                if (allowedMethods[i].equalsIgnoreCase(httpRequest.getMethod())) {
                    chain.doFilter(request, response);
                    return;
                }
            }
        }
        
        targetFilter.doFilter(request, response, chain);
    }

    @Override
    public void destroy() {
        targetFilter.destroy();
    }
}