package org.dbpedia.moss.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.json.JsonReader;

public class MossContext {

    private static Document context;

    public static void initialize() throws FileNotFoundException, JsonLdError {

        String contextPath = "./config/context.jsonld";
        JsonReader jsonReader = Json.createReader(new FileInputStream(contextPath));
        JsonObject contextJson = jsonReader.readObject();
        jsonReader.close();
        
        // Modify the @context by adding a new field dynamically
        JsonObjectBuilder modifiedContextBuilder = Json.createObjectBuilder(contextJson.getJsonObject(RDFUris.JSONLD_CONTEXT));
        modifiedContextBuilder.add("indexer", ENV.MOSS_BASE_URL + "/indexer/");
        modifiedContextBuilder.add("layer", ENV.MOSS_BASE_URL + "/layer/");
        
        // Rebuild the full JSON-LD context
        JsonObjectBuilder fullContextBuilder = Json.createObjectBuilder();
        fullContextBuilder.add(RDFUris.JSONLD_CONTEXT, modifiedContextBuilder.build());
        
        JsonObject updatedContext = fullContextBuilder.build();
        context = JsonDocument.of(updatedContext);
    }

    public static Document get() {
        return context;
    }

}
