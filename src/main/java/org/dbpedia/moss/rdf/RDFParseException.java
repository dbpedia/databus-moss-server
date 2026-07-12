package org.dbpedia.moss.rdf;

public class RDFParseException extends RuntimeException {
    public RDFParseException(String message) {
        super(message);
    }

    public RDFParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
