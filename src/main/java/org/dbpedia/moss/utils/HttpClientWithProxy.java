package org.dbpedia.moss.utils;

import org.apache.http.HttpHost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class HttpClientWithProxy {

    public static Proxy getProxy(String scheme, String targetHost) {
        if (isInNoProxy(targetHost)) {
            
            return Proxy.NO_PROXY;
        }

        String proxyEnv = getProxyUrl(scheme);

        if (proxyEnv != null) {
            URI proxyUri = URI.create(proxyEnv);
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyUri.getHost(), proxyUri.getPort()));
        }

        return Proxy.NO_PROXY;
    }

    private static String getProxyUrl(String scheme) {
        boolean isHttps = scheme.equalsIgnoreCase("https");
        String proxyEnv = null;

        if (isHttps) {
            proxyEnv = Optional.ofNullable(System.getenv("HTTPS_PROXY"))
                    .orElse(System.getenv("https_proxy"));
        } else {
            proxyEnv = Optional.ofNullable(System.getenv("HTTP_PROXY"))
                    .orElse(System.getenv("http_proxy"));
        }

        return proxyEnv;
    }

    private static boolean isInNoProxy(String host) {
        String noProxy = Optional.ofNullable(System.getenv("NO_PROXY"))
                .orElse(System.getenv("no_proxy"));

        if (noProxy == null || host == null) {
            return false;
        }

        List<String> noProxyList = Arrays.stream(noProxy.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .collect(Collectors.toList());

        for (String entry : noProxyList) {
            if (entry.startsWith(".")) {
                // Domain suffix match (e.g., .example.com matches foo.example.com)
                if (host.endsWith(entry)) {
                    return true;
                }
            } else if (host.equals(entry)) {
                // Exact match (e.g., localhost or 127.0.0.1)
                return true;
            } else if (host.endsWith("." + entry)) {
                // Allow match like host == foo.example.com, entry == example.com
                return true;
            }
        }

        return false;
    }

    private static HttpHost getProxyHost(String scheme, String targetHost) {
        if (isInNoProxy(targetHost)) {
            return null;
        }

        String proxyEnv = getProxyUrl(scheme);

        if (proxyEnv != null) {
            URI proxyUri = URI.create(proxyEnv);
            return new HttpHost(proxyUri.getHost(), proxyUri.getPort(), proxyUri.getScheme());
        }

        return null;
    }

    public static CloseableHttpClient create(String scheme, String targetHost) {
        HttpHost proxy = getProxyHost(scheme, targetHost);

        if (proxy != null) {
            return HttpClients.custom()
                    .setProxy(proxy)
                    .build();
        } else {
            return HttpClients.createDefault();
        }
    }
}
