package org.dbpedia.moss.filters;

import java.util.List;

import org.dbpedia.moss.utils.AdminAccess;
import org.dbpedia.moss.utils.HttpConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

@AdminOnly
public class AdminContainerFilter implements ContainerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(AdminContainerFilter.class);

    @Context
    private HttpServletRequest servletRequest;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) servletRequest.getAttribute(HttpConstants.OIDC.KEY_ROLES);
        String sub = (String) servletRequest.getAttribute(HttpConstants.OIDC.KEY_SUBJECT);
        String method = requestContext.getMethod();
        String path = requestContext.getUriInfo().getPath();

        if (sub == null) {
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity("{\"message\":\"Authorization required.\"}")
                            .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                            .build());
            return;
        }

        if (!AdminAccess.hasAdminRole(roles)) {
            logger.warn("Access denied for subject {} on {} /{}", sub, method, path);
            requestContext.abortWith(
                    Response.status(Response.Status.FORBIDDEN)
                            .entity("{\"message\":\"User is not an admin.\"}")
                            .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                            .build());
        }
    }
}
