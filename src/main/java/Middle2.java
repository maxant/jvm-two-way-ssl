import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Middle2 {

    public static void main(String[] args) throws NoSuchAlgorithmException, IOException, InterruptedException {

        System.setProperty("javax.net.ssl.keyStore", "certificates/middle/middle-keystore-just-server-cert.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "12345_mks");
        System.setProperty("javax.net.ssl.keyStoreType", "JKS");
        System.setProperty("javax.net.ssl.trustStore", "certificates/middle/middle-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "12345_mts");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        System.setProperty("javax.net.debug", "ssl");

        HttpsServer server = HttpsServer.create(new InetSocketAddress(10001), 0);
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
        server.createContext("/middle", httpExchange -> {

            try{
                // ////////////// KEY STORE /////////////////
                KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
                try(InputStream is = new FileInputStream("certificates/middle/middle-keystore-just-client-cert.jks")){
                    keyStore.load(is, "12345_mks".toCharArray());
                }

                KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
                keyManagerFactory.init(keyStore, "12345_mks".toCharArray());

                // ////////////// TRUST STORE /////////////////
                KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
                try(InputStream is = new FileInputStream(System.getProperty("javax.net.ssl.trustStore"))) {
                    trustStore.load(is, System.getProperty("javax.net.ssl.trustStorePassword").toCharArray());
                }

                TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                trustManagerFactory.init(trustStore);

                SSLContext c = SSLContext.getInstance("TLS");
                c.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);

                URL obj = new URL("https://localhost:10002/back");
                HttpsURLConnection connection = (HttpsURLConnection) obj.openConnection();
                connection.setSSLSocketFactory(c.getSocketFactory());
                connection.setRequestMethod("GET");
                connection.setDoOutput(false);
                int responseCode = connection.getResponseCode();
                System.out.println(
                        LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + " middle - response from back: " + responseCode);

                httpExchange.sendResponseHeaders(200, 0);
            }catch(Exception e){
                e.printStackTrace();
            }
        });
        server.start();
    }
}