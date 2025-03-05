package org.dbpedia.moss.indexer;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

public class IndexingTask implements Runnable {

    private final static Logger logger = LoggerFactory.getLogger(IndexingTask.class);

    private final static String REQ_KEY_CONFIG = "config";
    
    private final static String REQ_KEY_VALUES = "values";

    private List<String> todos;
    private File configFile;
    private String indexEndpoint;

    public IndexingTask(File configPath, String indexEndpoint, List<String> todos) {
        this.todos = todos;
        this.configFile = configPath;
        this.indexEndpoint = indexEndpoint;
    }

    @Override
    public void run() {

        long tid = Thread.currentThread().threadId();
        logger.info("[Thread {}] Indexing with {}...", tid, configFile.getAbsolutePath());

        if(todos != null) {
            String todosString = todos.stream().collect(Collectors.joining(","));
            logger.info("[Thread {}] VALUES: {}...", tid, todosString);
        }

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(indexEndpoint);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            // Add the file part
            builder.addBinaryBody(REQ_KEY_CONFIG, configFile, ContentType.APPLICATION_OCTET_STREAM, configFile.getName());

            // Add the todos part if not null
            if (todos != null) {
                String todosString = todos.stream().collect(Collectors.joining(","));
                builder.addTextBody(REQ_KEY_VALUES, todosString, ContentType.TEXT_PLAIN);
            }

            // Set the multipart entity to the request
            uploadFile.setEntity(builder.build());

            // Execute the request
            try (CloseableHttpResponse response = httpClient.execute(uploadFile)) {
                int responseCode = response.getCode();

                // Print the response
                String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
                
                logger.info("[Thread {}] Indexing request response: {} - {}", tid, responseCode, responseString);

                // Print the response content if the request was successful
                if (responseCode == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            logger.info("[Thread {}] {}", tid, line);
                        }
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            logger.error("[Thread {}] Indexing error: {}", tid,  e.getMessage());
            e.printStackTrace();
        }

        logger.info("[Thread {}] Indexing task completed.", tid);
    }
}
