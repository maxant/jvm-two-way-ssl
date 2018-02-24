import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Middle {

    public static void main(String[] args) throws NoSuchAlgorithmException, IOException, InterruptedException {

        //FAILS WHEN FRONT CALLS BECAUSE MIDDLE PRESENTS THE client-middle
        //CERT INSTEAD OF server-middle.
        //client sees:
        //    Exception in thread "main" javax.net.ssl.SSLHandshakeException: sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
        //middle sees:
        //    Thread-2, RECV TLSv1.2 ALERT:  fatal, certificate_unknown

        System.setProperty("javax.net.ssl.keyStore", "certificates/middle/middle-keystore.jks");
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
            URL obj = new URL("https://localhost:10002/back");
            HttpsURLConnection connection = (HttpsURLConnection) obj.openConnection();
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