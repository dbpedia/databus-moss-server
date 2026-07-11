package org.dbpedia.moss.filters;

import org.dbpedia.moss.utils.HttpConstants;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

@RequiresAuthentication
public class AuthenticationContainerFilter implements ContainerRequestFilter {

    @Context
    private HttpServletRequest servletRequest;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String sub = (String) servletRequest.getAttribute(HttpConstants.OIDC.KEY_SUBJECT);
        if (sub == null) {
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .entity("{\"message\":\"Authorization required.\"}")
                            .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                            .build());
        }
    }
}
