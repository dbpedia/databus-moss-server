package org.dbpedia.moss.auth;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.dbpedia.moss.app.ENV;
import org.dbpedia.moss.http.HttpClientWithProxy;
import org.dbpedia.moss.http.HttpConstants;

public class OIDCDiscoveryService {

    private static final Logger logger = LoggerFactory.getLogger(OIDCDiscoveryService.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final long REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(15);

    private static OIDCDiscoveryService instance;
    private volatile ServiceState currentState;

    private static class ServiceState {
        final OIDCDiscoveryDocument document;
        final JwkProvider jwkProvider;
        final long lastRefresh;

        ServiceState(OIDCDiscoveryDocument document, JwkProvider jwkProvider) {
            this.document = document;
            this.jwkProvider = jwkProvider;
            this.lastRefresh = System.currentTimeMillis();
        }
    }

    private OIDCDiscoveryService() {
        refresh();
    }

    public static synchronized OIDCDiscoveryService getInstance() {
        if (instance == null) {
            instance = new OIDCDiscoveryService();
        }
        return instance;
    }

    public OIDCDiscoveryDocument getDiscovery() {
        ensureFreshness();
        return currentState.document;
    }

    public JwkProvider getJwkProvider() {
        ensureFreshness();
        return currentState.jwkProvider;
    }

    private void ensureFreshness() {
        if (currentState == null || (System.currentTimeMillis() - currentState.lastRefresh) > REFRESH_INTERVAL) {
            synchronized (this) {
                if (currentState == null || (System.currentTimeMillis() - currentState.lastRefresh) > REFRESH_INTERVAL) {
                    refresh();
                }
            }
        }
    }

    private void refresh() {
        try {
            JsonNode json = fetchDiscoveryJson();
            if (json != null) {
                OIDCDiscoveryDocument doc = new OIDCDiscoveryDocument(json);
                JwkProvider provider = new UrlJwkProvider(new URI(doc.getJwksUri()).toURL());
                this.currentState = new ServiceState(doc, provider);
                logger.info("OIDC discovery updated for issuer: {}", doc.getIssuer());
            }
        } catch (URISyntaxException | IOException e) {
            logger.error("Failed to refresh OIDC discovery", e);
            if (currentState == null) {
                throw new RuntimeException("Initial OIDC discovery failed. Application cannot verify tokens.");
            }
        }
    }

    private JsonNode fetchDiscoveryJson() throws IOException, URISyntaxException {
        String discoveryUrl = ENV.AUTH_OIDC_DISCOVERY_URL;
        if (discoveryUrl == null || discoveryUrl.isBlank()) {
            discoveryUrl = ENV.AUTH_OIDC_ISSUER + HttpConstants.OIDC.DISCOVERY_DOCUMENT;
        }

        URI discoveryUri = new URI(discoveryUrl);
        try (CloseableHttpClient client = HttpClientWithProxy.create(discoveryUri.getScheme(), discoveryUri.getHost())) {
            HttpGet get = new HttpGet(discoveryUrl);
            try (CloseableHttpResponse response = client.execute(get)) {
                return mapper.readTree(EntityUtils.toString(response.getEntity()));
            }
        }
    }
}
