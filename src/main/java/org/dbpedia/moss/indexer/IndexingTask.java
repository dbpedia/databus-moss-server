package org.dbpedia.moss.indexer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Collectors;

public class IndexingTask implements Runnable {

    List<String> todos;
    String configPath;
    String indexerJarPath;
    String indexEndpoint;

    public IndexingTask(String configPath, String indexEndpoint, List<String> todos) {
        this.todos = todos;
        this.configPath = configPath;
        this.indexEndpoint = indexEndpoint;
    }

    @SuppressWarnings({ "deprecation" })
    @Override
    public void run() {
        System.out.println("Starting indexing runner on thread " + Thread.currentThread().getId());

        try {
            File configFile = new File(configPath);
            System.out.println("Building index with config " + configFile.getAbsolutePath());

            // Construct the URL for the index endpoint
            URL url = new URL(indexEndpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=boundary");
            connection.setDoOutput(true);
            
            try (OutputStream outputStream = connection.getOutputStream();
                PrintWriter writer = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8), true)) {
                // Write the boundary and content-disposition header for the file part
                String boundary = "boundary";
                writer.println("--" + boundary);
                writer.println("Content-Disposition: form-data; name=\"config\"; filename=\"" + configFile.getName() + "\"");
                writer.println("Content-Type: application/octet-stream");
                writer.println(); 

                // Write the contents of the config file
                Files.copy(configFile.toPath(), outputStream);
                outputStream.flush();

                // Write the todos as a comma-separated string
                String todosString = todos.stream().collect(Collectors.joining(","));
                writer.println("--" + boundary);
                writer.println("Content-Disposition: form-data; name=\"values\"");
                writer.println();
                writer.println(todosString);

                // Write closing boundary
                writer.println("--" + boundary + "--");
            }

            // Get the response code
            int responseCode = connection.getResponseCode();
            System.out.println("Response code: " + responseCode);

            // Print the response
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
            }

        } catch (IOException e) {
            System.out.println("indexing error");
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("indexing error");
            e.printStackTrace();
        }

        System.out.println("Done on thread " + Thread.currentThread().getId());
    }
}
