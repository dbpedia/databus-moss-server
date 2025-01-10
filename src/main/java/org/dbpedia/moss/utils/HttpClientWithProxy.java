package org.dbpedia.moss.utils;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;

public class HttpClientWithProxy {


    public static Proxy getProxy(String scheme) {
        String proxyEnv = getProxyUrl(scheme);

        if (proxyEnv != null) {
            URI proxyUri = URI.create(proxyEnv);
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort()));
        }
        
        return Proxy.NO_PROXY; 
    }

    private static String getProxyUrl(String scheme) {

        return null;

        /*
        boolean isHttps = scheme.equalsIgnoreCase("https");
        String proxyEnv = null;

        if(isHttps) {
            proxyEnv = System.getenv("HTTPS_PROXY");
            
            if(proxyEnv == null) {
                proxyEnv = System.getenv("https_proxy");
            }
        }
        else {
            proxyEnv = System.getenv("HTTP_PROXY");
            
            if(proxyEnv == null) {
                proxyEnv = System.getenv("http_proxy");
            }
        }

        return proxyEnv; */
    }

    private static HttpHost getProxyHost(String scheme) {

        String proxyEnv = getProxyUrl(scheme);
   
        if (proxyEnv != null) {
            URI proxyUri = URI.create(proxyEnv);
            return new HttpHost(proxyUri.getHost(), proxyUri.getPort(), proxyUri.getScheme());
        }

        return null;
    }

    public static CloseableHttpClient create() {
        HttpHost httpProxy = getProxyHost("http");
        HttpHost httpsProxy = getProxyHost("https");

        if (httpProxy != null || httpsProxy != null) {
            return HttpClients.custom()
                    .setProxy(httpsProxy != null ? httpsProxy : httpProxy)
                    .build();
        } else {
            return HttpClients.createDefault();
        }
    }
}
