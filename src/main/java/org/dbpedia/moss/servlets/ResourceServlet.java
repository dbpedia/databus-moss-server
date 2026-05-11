package org.dbpedia.moss.servlets;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ResourceServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private static final String CONTEXT_FILE = "res/context.jsonld";
    private static final String FACETS_FILE = "res/facets.json";


    public ResourceServlet() {
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getPathInfo(); // path after /res

        String filePath;
        if (null == path) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found");
            return;
        } else switch (path) {
            case "/context.jsonld" -> filePath = CONTEXT_FILE;
            case "/facets.json" -> filePath = FACETS_FILE;
            default -> {
                resp.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found");
                return;
            }
        }

        File file = new File(filePath);
        if (!file.exists() || !file.isFile()) {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND, "File not found on disk");
            return;
        }

        resp.setContentType("application/json");
        resp.setContentLengthLong(file.length());

        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = resp.getOutputStream()) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                os.write(buffer, 0, bytesRead);
            }
        }
    }
}
