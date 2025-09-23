package org.dbpedia.moss.servlets.modules;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;


    interface SubResourceHandler {
        void get(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException;

        void update(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException;

        void delete(HttpServletRequest req, HttpServletResponse resp, String moduleId) throws IOException;
    }