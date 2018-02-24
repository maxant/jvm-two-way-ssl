import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Back {

    public static void main(String[] args) throws NoSuchAlgorithmException, IOException {
        System.setProperty("javax.net.ssl.keyStore", "certificates/back/back-keystore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "12345_bks");
        System.setProperty("javax.net.ssl.keyStoreType", "JKS");
        System.setProperty("javax.net.ssl.trustStore", "certificates/back/back-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "12345_bts");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        System.setProperty("javax.net.debug", "ssl");

        HttpsServer server = HttpsServer.create(new InetSocketAddress(10002), 0);
        SSLContext sslContext = SSLContext.getDefault();
        server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters params) {
                try {
                    SSLEngine engine = sslContext.createSSLEngine();
                    params.setNeedClientAuth(true);
                    params.setCipherSuites(engine.getEnabledCipherSuites());
                    params.setProtocols(engine.getEnabledProtocols());
                    SSLParameters defaultSSLParameters = sslContext.getDefaultSSLParameters();
                    defaultSSLParameters.setNeedClientAuth(true);
                    params.setSSLParameters(defaultSSLParameters);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    System.out.println("Failed to create HTTPS server");
                }
            }
        });
        server.createContext("/back", httpExchange -> {
            System.out.println(LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + " back");
            httpExchange.sendResponseHeaders(200, 0);
        });

        server.start();
    }
}