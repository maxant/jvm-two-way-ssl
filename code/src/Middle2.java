import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

public class Middle2 {

    public static void main(String[] args) throws NoSuchAlgorithmException, IOException, InterruptedException {

        //WORKS FINE. KEY STORE CONTAINS server-middle and client-middle
        //BACK ASKS FOR A client cert and JVM delivers client-middle
        //AND SSL WORKS FINE

        System.setProperty("javax.net.ssl.keyStore", "middle/middle-keystore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "123456");
        System.setProperty("javax.net.ssl.keyStoreType", "JKS");
        System.setProperty("javax.net.ssl.trustStore", "middle/middle-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        System.setProperty("javax.net.debug", "ssl");

        System.out.println("starting...");

        URL obj = new URL("https://localhost:10002/back");
        HttpsURLConnection connection = (HttpsURLConnection) obj.openConnection();
        connection.setRequestMethod("GET");
        connection.setHostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String s, SSLSession sslSession) {
                return true;
            }
        });
        connection.setDoOutput(false);
        int responseCode = connection.getResponseCode();
        System.out.println(
                LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + " middle - response from back: " + responseCode);
    }
}