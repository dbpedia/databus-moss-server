package org.dbpedia.moss.indexer;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.stream.Collectors;

public class IndexingTask implements Runnable {

    List<String> todos;
    String configPath;
    String indexEndpoint;

    public IndexingTask(String configPath, String indexEndpoint, List<String> todos) {
        this.todos = todos;
        this.configPath = configPath;
        this.indexEndpoint = indexEndpoint;
    }

    @Override
    public void run() {
        System.out.println("Starting indexing runner on thread " + Thread.currentThread().threadId());

        File configFile = new File(configPath);
        System.out.println("Building index with config " + configFile.getAbsolutePath());

        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpPost uploadFile = new HttpPost(indexEndpoint);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();

            // Add the file part
            builder.addBinaryBody("config", configFile, ContentType.APPLICATION_OCTET_STREAM, configFile.getName());

            // Add the todos part if not null
            if (todos != null) {
                String todosString = todos.stream().collect(Collectors.joining(","));
                builder.addTextBody("values", todosString, ContentType.TEXT_PLAIN);
            }

            // Set the multipart entity to the request
            uploadFile.setEntity(builder.build());

            // Execute the request
            try (CloseableHttpResponse response = httpClient.execute(uploadFile)) {
                int responseCode = response.getCode();
                System.out.println("Response code: " + responseCode);

                // Print the response
                String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
                System.out.println(responseString);

                // Print the response content if the request was successful
                if (responseCode == 200) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);
                        }
                    }
                }
            } catch (ParseException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            System.out.println("indexing error");
            e.printStackTrace();
        }

        System.out.println("Done on thread " + Thread.currentThread().threadId());
    }
}
