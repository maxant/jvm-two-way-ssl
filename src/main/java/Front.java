import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.net.URL;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Front {

    public static void main(String[] args) throws NoSuchAlgorithmException, IOException {

        System.setProperty("javax.net.ssl.keyStore", "certificates/front/front-keystore.jks");
        System.setProperty("javax.net.ssl.keyStorePassword", "12345_fks");
        System.setProperty("javax.net.ssl.keyStoreType", "JKS");
        System.setProperty("javax.net.ssl.trustStore", "certificates/front/front-truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "12345_fts");
        System.setProperty("javax.net.ssl.trustStoreType", "JKS");
        System.setProperty("javax.net.debug", "ssl");

        URL obj = new URL("https://localhost:10001/middle");
        HttpsURLConnection connection = (HttpsURLConnection) obj.openConnection();
        connection.setRequestMethod("GET");
        connection.setDoOutput(false);
        int responseCode = connection.getResponseCode();
        System.out.println(
                LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME) + " front - response from middle: " + responseCode);
    }
}