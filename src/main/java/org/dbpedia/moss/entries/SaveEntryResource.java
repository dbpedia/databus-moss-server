package org.dbpedia.moss.entries;

import org.dbpedia.moss.generated.api.ApiApi;
import org.dbpedia.moss.http.HttpConstants;
import org.dbpedia.moss.http.MutableServletRequest;
import org.dbpedia.moss.users.UserDatabaseManager;

import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;

@Deprecated
public class SaveEntryResource implements ApiApi {

    @Context
    private HttpServletRequest request;

    private final EntriesResource entriesResource;

    @Inject
    public SaveEntryResource(UserDatabaseManager userDatabaseManager) {
        this.entriesResource = new EntriesResource(userDatabaseManager);
    }

    @Override
    public Response saveEntry(String module, String resource, String body) {
        HttpServletRequest req = new MutableServletRequest(request, body, HttpConstants.MediaTypes.TEXT_TURTLE)
                .withParameter("module", module)
                .withParameter("resource", resource);
        return entriesResource.saveEntryRequest(req);
    }
}
