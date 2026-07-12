package org.dbpedia.moss.auth.filters;

import java.lang.reflect.Method;

import org.dbpedia.moss.http.HttpConstants;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

@RequiresPermission("")
public class PermissionContainerFilter implements ContainerRequestFilter {

    @Context
    private HttpServletRequest servletRequest;

    @Context
    private ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        RequiresPermission annotation = findRequiresPermission();
        if (annotation == null) {
            return;
        }

        String required = annotation.value();
        if (required == null || required.isBlank()) {
            return;
        }

        String sub = (String) servletRequest.getAttribute(HttpConstants.OIDC.KEY_SUBJECT);

        @SuppressWarnings("unchecked")
        java.util.Set<String> permissions = (java.util.Set<String>) servletRequest.getAttribute(
                HttpConstants.OIDC.KEY_PERMISSIONS);
        if (permissions != null && permissions.contains(required)) {
            return;
        }

        if (sub == null) {
            abort(requestContext, Response.Status.UNAUTHORIZED, "Authorization required.");
            return;
        }

        abort(requestContext, Response.Status.FORBIDDEN, "Missing required permission: " + required);
    }

    private RequiresPermission findRequiresPermission() {
        if (resourceInfo.getResourceMethod() == null) {
            return null;
        }
        Method method = resourceInfo.getResourceMethod();
        RequiresPermission annotation = method.getAnnotation(RequiresPermission.class);
        if (annotation != null) {
            return annotation;
        }
        for (Class<?> iface : resourceInfo.getResourceClass().getInterfaces()) {
            try {
                Method ifaceMethod = iface.getMethod(method.getName(), method.getParameterTypes());
                annotation = ifaceMethod.getAnnotation(RequiresPermission.class);
                if (annotation != null) {
                    return annotation;
                }
            } catch (NoSuchMethodException ignored) {
                // continue
            }
        }
        return null;
    }

    private void abort(ContainerRequestContext requestContext, Response.Status status, String message) {
        requestContext.abortWith(
                Response.status(status)
                        .entity("{\"message\":\"" + message + "\"}")
                        .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                        .build());
    }
}
