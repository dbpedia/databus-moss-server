package org.dbpedia.moss.jwt;

import org.eclipse.jetty.security.UserAuthentication;
import org.eclipse.jetty.server.Authentication;

import jakarta.servlet.ServletRequest;

/**
 * JWT-specific user authentication.
 *
 * @author Justin Merz
 */
public class JwtAuthentication extends UserAuthentication {

    /** JWT authenticator that produced this authentication. */
    private final JwtAuthenticator authenticator;

    /** JWT ticket that was successfully validated to permit authentication. */
    private final String jwtToken;


    /**
     * Creates a new instance.
     *
     * @param authenticator The authenticator that produced this authentication.
     * @param jwtTokwn The passed JWT token
     * @param principle The user principle extracted from the JWT token.
     */
    public JwtAuthentication(final JwtAuthenticator authenticator, final String jwtToken, final JwtPrincipal principle) {
        super(authenticator.getAuthMethod(), new JwtUserIdentity(principle));
        this.authenticator = authenticator;
        this.jwtToken = jwtToken;
    }

    /** @return The JWT Token that was successfully validated to permit authentication. */
    public String getTicket() {
        return jwtToken;
    }

    @Override
    public Authentication logout(ServletRequest request) {
        this.authenticator.clearCachedAuthentication(jwtToken);
        return super.logout(request);
    }
}