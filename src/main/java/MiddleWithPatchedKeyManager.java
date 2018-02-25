import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.KeyStore;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class MiddleWithPatchedKeyManager {

    public static void main(String[] args) throws Exception {

        System.setProperty("javax.net.ssl.keyStore", "certificates/middle/middle-keystore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "12345_mks");
        System.setProperty("javax.net.ssl.keyStoreType", "JKS");
        System.setProperty("javax.net.ssl.trustStore", "certificates/middle/middle-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "12345_mts");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        System.setProperty("javax.net.debug", "ssl");

        HttpsServer server = HttpsServer.create(new InetSocketAddress(10001), 0);

        // ////////////////////////////////////////////////////////////
        // START PATCH
        // ////////////////////////////////////////////////////////////
        SSLContext sslContext = SSLContext.getInstance("TLS");

        // ////////////// KEY STORE /////////////////
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try(InputStream is = new FileInputStream(System.getProperty("javax.net.ssl.keyStore"))){
            keyStore.load(is, System.getProperty("javax.net.ssl.keyStorePassword").toCharArray());
        }

        PatchedSunX509KeyManagerImpl keyManager = new PatchedSunX509KeyManagerImpl(keyStore, System.getProperty("javax.net.ssl.keyStorePassword").toCharArray());

        // ////////////// TRUST STORE /////////////////
        KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try(InputStream is = new FileInputStream(System.getProperty("javax.net.ssl.trustStore"))) {
            trustStore.load(is, System.getProperty("javax.net.ssl.trustStorePassword").toCharArray());
        }

        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);

        sslContext.init(new KeyManager[]{ keyManager }, trustManagerFactory.getTrustManagers(), null);

        // ////////////////////////////////////////////////////////////
        // END PATCH
        // ////////////////////////////////////////////////////////////

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
        server.createContext("/middle", httpExchange -> {
            URL obj = new URL("https://localhost:10002/back");
            HttpsURLConnection connection = (HttpsURLConnection) obj.openConnection();

            //ALSO IMPORTANT
            connection.setSSLSocketFactory(sslContext.getSocketFactory());

            connection.setRequestMethod("GET");
            connection.setDoOutput(false);
            int responseCode = connection.getResponseCode();
            System.out.println(
                    LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + " middle - response from back: " + responseCode);

            httpExchange.sendResponseHeaders(200, 0);
        });
        server.start();
    }

}