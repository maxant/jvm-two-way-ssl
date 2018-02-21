import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

public class Front4 {

    public static void main(String[] args) throws NoSuchAlgorithmException, IOException {

        //THIS WORKS, because the truststore ALSO trusts the client-middle
        //even though the front should be served the server-middle

        System.setProperty("javax.net.ssl.keyStore", "front/front-keystore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "123456");
        System.setProperty("javax.net.ssl.keyStoreType", "JKS");

        //USE truststore WITH client-middle!!
        System.setProperty("javax.net.ssl.trustStore", "front/front-truststore-with-client-middle.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        System.setProperty("javax.net.debug", "ssl");

        URL obj = new URL("https://localhost:10001/middle");
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
                LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + " front - response from middle: " + responseCode);
    }
}