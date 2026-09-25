package org.dbpedia.moss;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class JsonLdReserializeTest {

    @Test
    void jenaReserializedJsonLdDoesNotReferenceExternalContext() throws Exception {
        Path template = Path.of("config/modules/oemeta/template.jsonld");
        Model model = RDFDataMgr.loadModel(template.toString());

        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, model, Lang.JSONLD);
        String reserialized = writer.toString();

        assertFalse(reserialized.contains("localhost:8080/modules/oemeta/context.jsonld"),
                "Reserialized JSON-LD should not depend on localhost context URL");

        Path out = Path.of("target/reserialized-template.jsonld");
        Files.writeString(out, reserialized);
    }
}
