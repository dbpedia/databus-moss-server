package org.dbpedia.moss.servlets.modules;

import java.io.IOException;
import java.util.Optional;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.dbpedia.moss.config.MossConfiguration;
import org.dbpedia.moss.config.MossModule;
import org.dbpedia.moss.utils.MossUtils;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ModuleResourceServlet extends HttpServlet {

    private final ModuleStore moduleStore;

    public ModuleResourceServlet() {
        moduleStore = new ModuleStore(MossConfiguration.get().getModuleDirectory().toPath());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String pathInfo = req.getPathInfo(); // e.g. "/moduleId"
        if (pathInfo == null || pathInfo.length() <= 1) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, "Missing module id in path");
            return;
        }

        // Extract moduleId from path
        String moduleId = pathInfo.substring(1); // remove leading '/'

        Optional<MossModule> optModule = moduleStore.loadModule(moduleId);
        if (optModule.isEmpty()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Module not found: " + moduleId);
            return;
        }

        MossModule module = optModule.get();

        // Determine RDF language from Accept header
        String acceptHeader = req.getHeader("Accept");
        Lang acceptLang = RDFLanguages.contentTypeToLang(acceptHeader);

        if (acceptLang == null) {
            acceptLang = Lang.JSONLD;
        }

        // Convert module to RDF model
        Model moduleModel = module.toModel();

        // Send RDF to client
        MossUtils.sendPrettyRDF(resp, acceptLang, moduleModel);
    }
}
