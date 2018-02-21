import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

public class Front3 {

    public static void main(String[] args) throws NoSuchAlgorithmException, IOException {

        //proves that SSL works
        //because connection fails, since front-truststore.jks doesnt contain
        //server-back certificate

        //PROVIDE client-front cert
        System.setProperty("javax.net.ssl.keyStore", "front/front-keystore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "123456");
        System.setProperty("javax.net.ssl.keyStoreType", "JKS");

        //DO NOT TRUST THE BACK!!!
        System.setProperty("javax.net.ssl.trustStore", "front/front-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "123456");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        System.setProperty("javax.net.debug", "ssl");

        //CALL BACK DIRECTLY TO SEE IF IT WORKS
        //back has this error:
        //    RECV TLSv1.2 ALERT:  fatal, certificate_unknown
        //front has this error:
        //    Caused by: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested
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
                LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + " front - response from middle: " + responseCode);
    }
}