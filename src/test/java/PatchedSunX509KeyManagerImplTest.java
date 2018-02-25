import org.junit.Test;

import java.io.FileInputStream;
import java.io.InputStream;
import java.security.KeyStore;

import static org.junit.Assert.assertEquals;

public class PatchedSunX509KeyManagerImplTest {

    @Test
    public void test() throws Exception {
        //given
        char[] password = "12345_mks".toCharArray();
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        try(InputStream is = new FileInputStream("certificates/middle/middle-keystore.jks")){
            keyStore.load(is, password);
        }
        PatchedSunX509KeyManagerImpl sut = new PatchedSunX509KeyManagerImpl(keyStore, password);

        //when choose client alias
        String clientAlias = sut.chooseClientAlias(new String[]{"DSA"}, null, null);

        //then choose client alias
        assertEquals("middle-client", clientAlias);

        //when choose server alias
        String serverAlias = sut.chooseServerAlias("DSA", null, null);

        //then choose client alias
        assertEquals("middle-server", serverAlias);
    }
}