import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

import static org.junit.Assert.assertEquals;

public class SunX509KeyManagerImplTest {

    @Test
    public void test() throws Exception {
        //given
        char[] password = "12345_mks".toCharArray();
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try(InputStream is = new FileInputStream("certificates/middle/middle-keystore.jks")){
            keyStore.load(is, password);
        }
        SunX509KeyManagerImpl sut = new SunX509KeyManagerImpl(keyStore, password);

        //when choose client alias
        String clientAlias = sut.chooseClientAlias(new String[]{"DSA"}, null, null);

        //then choose client alias
        assertEquals("middle-client", clientAlias); //THIS LINE FAILS WITH "Java HotSpot(TM) 64-Bit Server VM (build 25.112-b15, mixed mode)" on Fedora 25

        //when choose server alias
        String serverAlias = sut.chooseServerAlias("DSA", null, null);

        //then choose client alias
        assertEquals("middle-server", serverAlias);
    }
}