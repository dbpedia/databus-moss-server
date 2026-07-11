package org.dbpedia.moss.filters;

import org.dbpedia.moss.utils.HttpConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class LoggingExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger logger = LoggerFactory.getLogger(LoggingExceptionMapper.class);

    @Override
    public Response toResponse(Throwable exception) {
        if (exception instanceof WebApplicationException webEx) {
            Response response = webEx.getResponse();
            int status = response.getStatus();
            if (status >= 500) {
                logger.error("Request failed with {}: {}", status, exception.getMessage(), exception);
            }
            return response;
        }

        logger.error("Unhandled exception", exception);
        return Response.serverError()
                .type(HttpConstants.MediaTypes.APPLICATION_JSON)
                .entity("{\"error\":\"Internal Server Error\",\"message\":\"An unexpected error occurred\"}")
                .build();
    }
}
