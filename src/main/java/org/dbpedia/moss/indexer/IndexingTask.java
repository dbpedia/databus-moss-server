package org.dbpedia.moss.indexer;

import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.dbpedia.moss.utils.ENV;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class IndexingTask implements Runnable {

    private static final String PARAM_CONFIG = "config";

    private static final String PARAM_VALUES = "values";

    private static final String ROUTE_INDEX = "/api/index/run";

    private static final String ROUTE_DELETE = "/api/index/delete";

    private static final Logger logger = LoggerFactory.getLogger(IndexingTask.class);
    
    private String resourceURI;

    private IndexGroup indexGroup;

  
    public IndexingTask(String resourceURI, IndexGroup indexGroup) {
        this.resourceURI = resourceURI;
        this.indexGroup = indexGroup;
    }

    @Override
    public void run() {
        

        try(CloseableHttpClient httpClient = HttpClients.createDefault()) {

            // FIRST: Delete document from index

            if(resourceURI != null) {
                HttpPost deleteRequest = createRequest(ENV.LOOKUP_BASE_URL + ROUTE_DELETE, null);
            
                try (CloseableHttpResponse deleteResponse = httpClient.execute(deleteRequest)) {
                    int responseCode = deleteResponse.getCode();
                    logger.info("Resource <{}> deleted from index with code {} " , resourceURI, responseCode);
                } catch (IOException e) {
                    logger.error("Failed to delete resource from index <{}>: {}", resourceURI, e.getMessage());;
                }
            }

            if(indexGroup == null) {
                return;
            }

            // SECOND: Sequentially run all indexers of the specified group for the resource
            String indexEndpointURL = ENV.LOOKUP_BASE_URL + ROUTE_INDEX;
            logger.info("Reindexing <{}> ({})" , resourceURI, indexGroup.getName());

            for(File indexConfiguration : indexGroup.getIndexConfigurations()) {
                
                String indexerName = indexConfiguration.getName();
                HttpPost indexRequest = createRequest(indexEndpointURL, indexConfiguration);
        
                try (CloseableHttpResponse response = httpClient.execute(indexRequest)) {
                    int responseCode = response.getCode();

                    if(responseCode < 400) {
                        logger.info("Indexer {} completed with {}" , indexerName, responseCode);
                    } else {
                        logger.error("Indexer {} failed with {}: check the Lookup logs for more information", 
                            indexerName, responseCode);
                    }

                    
                } catch (IOException e) {
                    logger.error("Indexer {} failed: {}", indexerName, e.getMessage());;
                }
            }

        } catch (IOException e) {
            logger.error("Indexing task failed: {}", e.getMessage());
        }   
    }

    private HttpPost createRequest(String indexEndpointURL, File indexConfiguration) {
        HttpPost postRequest = new HttpPost(indexEndpointURL);
        MultipartEntityBuilder builder = MultipartEntityBuilder.create();

        if(indexConfiguration != null) {
            builder.addBinaryBody(PARAM_CONFIG, indexConfiguration, 
                ContentType.APPLICATION_OCTET_STREAM, indexConfiguration.getName());
        }

        if(resourceURI != null) {
            builder.addTextBody(PARAM_VALUES, resourceURI, ContentType.TEXT_PLAIN);
        }
   
        postRequest.setEntity(builder.build());
        return postRequest;
    }

    public IndexGroup getIndexGroup() {
        return indexGroup;
    }

    public String getResourceURI() {
        return resourceURI;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        IndexingTask that = (IndexingTask) obj;
        return Objects.equals(resourceURI, that.resourceURI) &&
                Objects.equals(indexGroup, that.indexGroup);
    }

    @Override
    public int hashCode() {
        return Objects.hash(resourceURI, indexGroup);
    }

}

/*
 * // Print the response content if the request was successful
                    if (responseCode == 200) {
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getEntity().getContent()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                System.out.println(line);
                            }
                        }
                    }
                         // Print the response
                    // String responseString = EntityUtils.toString(response.getEntity(), "UTF-8");
                    // System.out.println(responseString);

                    
 */