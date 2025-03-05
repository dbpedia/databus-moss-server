package org.dbpedia.moss.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;

import jakarta.json.JsonObject;

public final class RDFUtils {

  

    public static String getPropertyValue(Model model, Resource resource, Property property, String defaultValue) {
        // Get the statement corresponding to the property URI from the resource
        Statement statement = resource.getProperty(property);
        
        if(statement == null) {
            return defaultValue;

        }
        // Check if the statement exists and if the object is a resource (not a literal)
        if (statement.getObject().isResource()) {
            // Return the URI of the resource object
            return statement.getObject().asResource().getURI();
        }

        if(statement.getObject().isLiteral()) {
            return statement.getObject().asLiteral().getString();
        }

        
    
        // Return null if the property is not found or the object is not a resource
        return defaultValue;
    }

    private static Document modelToDocument(Model model) throws JsonLdError {
        // Convert the Jena Model to a JSON-LD format
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        model.write(out, Lang.JSONLD.getName());
        InputStream jsonLdInputStream = new ByteArrayInputStream(out.toByteArray());
        return JsonDocument.of(jsonLdInputStream);
    }

    public static JsonObject compact(Model listModel) {
        try {
            // Convert the Jena Model to a JSON-LD string
            Document document = modelToDocument(listModel);
            return JsonLd.compact(document, MossContext.get()).get();
        } catch (JsonLdError e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        } 
    }


}