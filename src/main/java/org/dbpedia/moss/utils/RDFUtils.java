package org.dbpedia.moss.utils;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;


public final class RDFUtils {

  

    public static String getPropertyValue(Model model, Resource resource, String propertyURI) {
        // Get the statement corresponding to the property URI from the resource
        Statement statement = resource.getRequiredProperty(model.createProperty(propertyURI));
        
        if(statement == null) {
            return null;

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
        return null;
    }


}