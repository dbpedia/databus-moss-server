package org.dbpedia.moss.indexer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

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
        System.out.println("Ich bims der runner auf thread " + Thread.currentThread().getId());

        try {
            File configFile = new File(configPath);
            System.out.println("Building index with config " + configFile.getAbsolutePath());

            byte[] configFileBytes = readBytesFromFile(configFile);

            // Construct the URL for the index endpoint
            URL url = new URL(indexEndpoint);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=boundary");
            connection.setDoOutput(true);

            // Write the config file bytes to the connection output stream
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(configFileBytes);
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
            System.out.println("gefahr");
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("gefahr");
            e.printStackTrace();
        }

        System.out.println("Fertig auf " + Thread.currentThread().getId());
    }

    private byte[] readBytesFromFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            int c;
            while ((c = reader.read()) != -1) {
                bos.write(c);
            }
            return bos.toByteArray();
        }
    }
}
