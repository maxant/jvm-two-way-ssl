# What is this?

A repo for an investigation into how the JVM handles two-way SSL, particularly
how a single JVM handles incoming and outgoing connections, when it has just a single key store.

Problem seems to be related to:

http://hg.openjdk.java.net/jdk8u/jdk8u60/jdk/file/935758609767/src/share/classes/sun/security/ssl/SunX509KeyManagerImpl.java#l220


    /*
     * Choose an alias to authenticate the client side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    @Override
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers,
            Socket socket) {
        /*
         * We currently don't do anything with socket, but
         * someday we might.  It might be a useful hint for
         * selecting one of the aliases we get back from
         * getClientAliases().
         */

        if (keyTypes == null) {
            return null;
        }

        for (int i = 0; i < keyTypes.length; i++) {
            String[] aliases = getClientAliases(keyTypes[i], issuers);
            if ((aliases != null) && (aliases.length > 0)) {
                return aliases[0];  <========== NEEDS TO BE MORE SELECTIVE AND USE ALIAS WITH SUITABLE EXTENDED KEY TYPE
            }
        }
        return null;
    }

It turns out the selection is also pretty much random (based on hash code of alias)

http://hg.openjdk.java.net/jdk8u/jdk8u60/jdk/file/935758609767/src/share/classes/sun/security/ssl/SunX509KeyManagerImpl.java#l352

    for (Map.Entry<String,X509Credentials> entry : credentialsMap.entrySet()) {

A patched version of the SunX509KeyManagerImpl (see ./src/main/java/PatchedSunX509KeyManagerImpl), fixes the problem.
It was necessary to add roughly the same algorithm to both of the following methods:

- chooseServerAlias
- chooseClientAlias

    ...
    for(String alias : aliases){
        try {
            //assume cert in index 0 is the lowest one in the chain, and check its EKU
            X509Certificate certificate = this.credentialsMap.get(alias).certificates[0];
            List<String> ekus = certificate.getExtendedKeyUsage();
            for (String eku : ekus) {
                if(eku.equals("1.3.6.1.5.5.7.3.1")){  //TODO replace with constant. .1 for server, .2 for client
                    return alias;
                }
            }
        }catch(CertificateParsingException e){
            //TODO handle properly
            e.printStackTrace();
        }
    }

    //default as implemented in openjdk
    return aliases[0];

Logs from three tests are shown below:

- Successful, i.e. using two patch (Front => MiddleWithPatchedKeyManager => Back)
- Successful, i.e. using two keystores (Front => Middle2 => Back)
- Unsuccessful, i.e. using just one keystore for both client and server certificates of the middle server (Front => Middle => Back)

# Successful, i.e. using two patch (Front => MiddleWithPatchedKeyManager => Back)

## Front SSL logs

    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=tech-user-middle, OU=IT, O=maxant, C=CH

Middle just presented the wrong certificate! It should have been the server one!

Certificate Extensions: 4
[1]: ObjectId: 2.5.29.35 Criticality=false
AuthorityKeyIdentifier [
KeyIdentifier [
0000: 0C 91 17 EE CB 17 EE ED   AC D0 7B 1E A5 82 FC 0F  ................
0010: 9F 3A FF F6                                        .:..
]
]

[2]: ObjectId: 2.5.29.37 Criticality=false
ExtendedKeyUsages [
  clientAuth
]

[3]: ObjectId: 2.5.29.15 Criticality=true
KeyUsage [
  DigitalSignature
  Key_Encipherment
]

[4]: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: 8A C9 66 E6 27 38 9E DD   28 F1 44 B7 47 6F 48 73  ..f.'8..(.D.GoHs
0010: 96 80 24 22                                        ..$"
]
]

]
  Algorithm: [SHA1withDSA]
  Signature:
0000: 30 2C 02 14 3F F8 3D 89   9F 61 0F 25 92 C2 0F 18  0,..?.=..a.%....
0010: F5 AE F9 0B 95 29 C7 51   02 14 5E 19 95 68 E8 3D  .....).Q..^..h.=
0020: 51 0B 7F A5 60 6F AD 5E   B0 54 01 BB 15 67        Q...`o.^.T...g

]
chain [1] = [
[
  Version: V3
  Subject: CN=maxant ca, OU=IT, O=maxant, C=CH
  Signature Algorithm: SHA1withDSA, OID = 1.2.840.10040.4.3

  Key:  Sun DSA Public Key
    Parameters:DSA
	p:     fd7f5381 1d751229 52df4a9c 2eece4e7 f611b752 3cef4400 c31e3f80 b6512669
    455d4022 51fb593d 8d58fabf c5f5ba30 f6cb9b55 6cd7813b 801d346f f26660b7
    6b9950a5 a49f9fe8 047b1022 c24fbba9 d7feb7c6 1bf83b57 e7c6a8a6 150f04fb
    83f6d3c5 1ec30235 54135a16 9132f675 f3ae2b61 d72aeff2 2203199d d14801c7
	q:     9760508f 15230bcc b292b982 a2eb840b f0581cf5
	g:     f7e1a085 d69b3dde cbbcab5c 36b857b9 7994afbb fa3aea82 f9574c0b 3d078267
    5159578e bad4594f e6710710 8180b449 167123e8 4c281613 b7cf0932 8cc8a6e1
    3c167a8b 547c8d28 e0a3ae1e 2bb3a675 916ea37f 0bfa2135 62f1fb62 7a01243b
    cca4f1be a8519089 a883dfe1 5ae59f06 928b665e 807b5525 64014c3b fecf492a

  y:
    c4ca2fd5 b201cf58 5b0c9381 31dd3a55 be945c34 0b86d6ab 3aa68026 cc1cebdf
    eaf1964c 08e0062b f2269795 e9e83fa2 464fb9cf 9fde043b 245f97b9 86393863
    f0974f34 0c938716 90bee46b 7cd226dd 958a4bee 345d434d cbbff883 c664fe33
    dbbf27e4 2e8cb40f 7ec5cf09 bb11bad2 1ac6e004 77c15d99 a4f7fdcb 41e6fab2

  Validity: [From: Sat Feb 24 20:01:17 CET 2018,
               To: Fri May 25 21:01:17 CEST 2018]
  Issuer: CN=maxant root, OU=IT, O=maxant, C=CH
  SerialNumber: [    784005e2]

Certificate Extensions: 3
[1]: ObjectId: 2.5.29.35 Criticality=false
AuthorityKeyIdentifier [
KeyIdentifier [
0000: 28 C9 B2 87 03 4A B7 DB   F7 FD 44 9A 5B 5D 61 12  (....J....D.[]a.
0010: A9 8F 7D 3B                                        ...;
]
]

[2]: ObjectId: 2.5.29.19 Criticality=false
BasicConstraints:[
  CA:true
  PathLen:0
]

[3]: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: 0C 91 17 EE CB 17 EE ED   AC D0 7B 1E A5 82 FC 0F  ................
0010: 9F 3A FF F6                                        .:..
]
]

]
  Algorithm: [SHA1withDSA]
  Signature:
0000: 30 2C 02 14 24 27 CD 32   9D 61 88 97 54 35 7C 2F  0,..$'.2.a..T5./
0010: 2C 3B 43 A4 06 58 F0 C8   02 14 03 53 79 1B 9D C4  ,;C..X.....Sy...
0020: AD FC A2 51 08 51 B6 F3   53 12 3D 60 5A C9        ...Q.Q..S.=`Z.

]
***
Exception in thread "main" javax.net.ssl.SSLHandshakeException: java.security.cert.CertificateException: No name matching localhost found
%% Invalidated:  [Session-1, TLS_DHE_DSS_WITH_AES_128_CBC_SHA256]
main, SEND TLSv1.2 ALERT:  fatal, description = certificate_unknown
main, WRITE: TLSv1.2 Alert, length = 2
main, called closeSocket()
main, handling exception: javax.net.ssl.SSLHandshakeException: java.security.cert.CertificateException: No name matching localhost found
	at sun.security.ssl.Alerts.getSSLException(Alerts.java:192)
	at sun.security.ssl.SSLSocketImpl.fatal(SSLSocketImpl.java:1949)
	at sun.security.ssl.Handshaker.fatalSE(Handshaker.java:302)
	at sun.security.ssl.Handshaker.fatalSE(Handshaker.java:296)
	at sun.security.ssl.ClientHandshaker.serverCertificate(ClientHandshaker.java:1506)
	at sun.security.ssl.ClientHandshaker.processMessage(ClientHandshaker.java:216)
	at sun.security.ssl.Handshaker.processLoop(Handshaker.java:979)
	at sun.security.ssl.Handshaker.process_record(Handshaker.java:914)
	at sun.security.ssl.SSLSocketImpl.readRecord(SSLSocketImpl.java:1062)
	at sun.security.ssl.SSLSocketImpl.performInitialHandshake(SSLSocketImpl.java:1375)
	at sun.security.ssl.SSLSocketImpl.startHandshake(SSLSocketImpl.java:1403)
	at sun.security.ssl.SSLSocketImpl.startHandshake(SSLSocketImpl.java:1387)
	at sun.net.www.protocol.https.HttpsClient.afterConnect(HttpsClient.java:559)
	at sun.net.www.protocol.https.AbstractDelegateHttpsURLConnection.connect(AbstractDelegateHttpsURLConnection.java:185)
	at sun.net.www.protocol.http.HttpURLConnection.getInputStream0(HttpURLConnection.java:1512)
	at sun.net.www.protocol.http.HttpURLConnection.getInputStream(HttpURLConnection.java:1440)
	at java.net.HttpURLConnection.getResponseCode(HttpURLConnection.java:480)
	at sun.net.www.protocol.https.HttpsURLConnectionImpl.getResponseCode(HttpsURLConnectionImpl.java:338)
	at Front.main(Front.java:24)
Caused by: java.security.cert.CertificateException: No name matching localhost found
	at sun.security.util.HostnameChecker.matchDNS(HostnameChecker.java:221)
	at sun.security.util.HostnameChecker.match(HostnameChecker.java:95)
	at sun.security.ssl.X509TrustManagerImpl.checkIdentity(X509TrustManagerImpl.java:455)
	at sun.security.ssl.X509TrustManagerImpl.checkIdentity(X509TrustManagerImpl.java:436)
	at sun.security.ssl.X509TrustManagerImpl.checkTrusted(X509TrustManagerImpl.java:200)
	at sun.security.ssl.X509TrustManagerImpl.checkServerTrusted(X509TrustManagerImpl.java:124)
	at sun.security.ssl.ClientHandshaker.serverCertificate(ClientHandshaker.java:1488)
	... 14 more

Process finished with exit code 1


## Middle SSL logs

Using SSLEngineImpl.
Ignoring unavailable cipher suite: TLS_DHE_DSS_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_RSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_RSA_WITH_AES_256_CBC_SHA256
Ignoring unavailable cipher suite: TLS_DHE_DSS_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384
Ignoring unavailable cipher suite: TLS_RSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384
Ignoring unavailable cipher suite: TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384
Ignoring unavailable cipher suite: TLS_ECDH_RSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384
Ignoring unavailable cipher suite: TLS_DHE_RSA_WITH_AES_256_CBC_SHA256
Ignoring unavailable cipher suite: TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_DHE_DSS_WITH_AES_256_CBC_SHA256
Ignoring unavailable cipher suite: TLS_DHE_RSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
Using SSLEngineImpl.
Ignoring unavailable cipher suite: TLS_DHE_DSS_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_RSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_RSA_WITH_AES_256_CBC_SHA256
Ignoring unavailable cipher suite: TLS_DHE_DSS_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384
Ignoring unavailable cipher suite: TLS_RSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384
Ignoring unavailable cipher suite: TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384
Ignoring unavailable cipher suite: TLS_ECDH_RSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384
Ignoring unavailable cipher suite: TLS_DHE_RSA_WITH_AES_256_CBC_SHA256
Ignoring unavailable cipher suite: TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_DHE_DSS_WITH_AES_256_CBC_SHA256
Ignoring unavailable cipher suite: TLS_DHE_RSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_DHE_DSS_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_RSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_DHE_RSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDHE_ECDSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_RSA_WITH_AES_256_CBC_SHA256
Ignoring unavailable cipher suite: TLS_DHE_DSS_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384
Ignoring unavailable cipher suite: TLS_RSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384
Ignoring unavailable cipher suite: TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384
Ignoring unavailable cipher suite: TLS_ECDH_RSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384
Ignoring unavailable cipher suite: TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384
Ignoring unavailable cipher suite: TLS_DHE_RSA_WITH_AES_256_CBC_SHA256
Ignoring unavailable cipher suite: TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_DHE_DSS_WITH_AES_256_CBC_SHA256
Ignoring unavailable cipher suite: TLS_DHE_RSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA
Ignoring unavailable cipher suite: TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384
Allow unsafe renegotiation: false
Allow legacy hello messages: true
Is initial handshake: true
Is secure renegotiation: false
Ignoring unsupported cipher suite: TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256 for TLSv1
Ignoring unsupported cipher suite: TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256 for TLSv1
Ignoring unsupported cipher suite: TLS_RSA_WITH_AES_128_CBC_SHA256 for TLSv1
Ignoring unsupported cipher suite: TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256 for TLSv1
Ignoring unsupported cipher suite: TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256 for TLSv1
Ignoring unsupported cipher suite: TLS_DHE_RSA_WITH_AES_128_CBC_SHA256 for TLSv1
Ignoring unsupported cipher suite: TLS_DHE_DSS_WITH_AES_128_CBC_SHA256 for TLSv1
Ignoring unsupported cipher suite: TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256 for TLSv1.1
Ignoring unsupported cipher suite: TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256 for TLSv1.1
Ignoring unsupported cipher suite: TLS_RSA_WITH_AES_128_CBC_SHA256 for TLSv1.1
Ignoring unsupported cipher suite: TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256 for TLSv1.1
Ignoring unsupported cipher suite: TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256 for TLSv1.1
Ignoring unsupported cipher suite: TLS_DHE_RSA_WITH_AES_128_CBC_SHA256 for TLSv1.1
Ignoring unsupported cipher suite: TLS_DHE_DSS_WITH_AES_128_CBC_SHA256 for TLSv1.1
Thread-2, READ: TLSv1.2 Handshake, length = 195
*** ClientHello, TLSv1.2
RandomCookie:  GMT: 1502741305 bytes = { 231, 184, 208, 99, 105, 169, 189, 97, 111, 216, 222, 52, 103, 69, 54, 163, 160, 147, 27, 66, 74, 107, 65, 111, 74, 124, 29, 40 }
Session ID:  {}
Cipher Suites: [TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256, TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256, TLS_RSA_WITH_AES_128_CBC_SHA256, TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256, TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256, TLS_DHE_RSA_WITH_AES_128_CBC_SHA256, TLS_DHE_DSS_WITH_AES_128_CBC_SHA256, TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA, TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA, TLS_RSA_WITH_AES_128_CBC_SHA, TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA, TLS_ECDH_RSA_WITH_AES_128_CBC_SHA, TLS_DHE_RSA_WITH_AES_128_CBC_SHA, TLS_DHE_DSS_WITH_AES_128_CBC_SHA, TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256, TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256, TLS_RSA_WITH_AES_128_GCM_SHA256, TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256, TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256, TLS_DHE_RSA_WITH_AES_128_GCM_SHA256, TLS_DHE_DSS_WITH_AES_128_GCM_SHA256, TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA, TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA, SSL_RSA_WITH_3DES_EDE_CBC_SHA, TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA, TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA, SSL_DHE_RSA_WITH_3DES_EDE_CBC_SHA, SSL_DHE_DSS_WITH_3DES_EDE_CBC_SHA, TLS_EMPTY_RENEGOTIATION_INFO_SCSV]
Compression Methods:  { 0 }
Extension elliptic_curves, curve names: {secp256r1, sect163k1, sect163r2, secp192r1, secp224r1, sect233k1, sect233r1, sect283k1, sect283r1, secp384r1, sect409k1, sect409r1, secp521r1, sect571k1, sect571r1, secp160k1, secp160r1, secp160r2, sect163r1, secp192k1, sect193r1, sect193r2, secp224k1, sect239k1, secp256k1}
Extension ec_point_formats, formats: [uncompressed]
Extension signature_algorithms, signature_algorithms: SHA512withECDSA, SHA512withRSA, SHA384withECDSA, SHA384withRSA, SHA256withECDSA, SHA256withRSA, SHA224withECDSA, SHA224withRSA, SHA1withECDSA, SHA1withRSA, SHA1withDSA, MD5withRSA
***
%% Initialized:  [Session-1, SSL_NULL_WITH_NULL_NULL]
matching alias: middle-server
matching alias: middle-client
%% Negotiating:  [Session-1, TLS_DHE_DSS_WITH_AES_128_CBC_SHA256]
*** ServerHello, TLSv1.2
RandomCookie:  GMT: 1502741306 bytes = { 40, 132, 144, 209, 47, 10, 103, 126, 194, 50, 79, 198, 92, 162, 98, 53, 186, 189, 94, 201, 156, 73, 84, 174, 222, 114, 193, 81 }
Session ID:  {90, 146, 3, 58, 54, 221, 155, 253, 252, 48, 53, 230, 150, 51, 21, 223, 70, 219, 142, 155, 37, 33, 172, 96, 189, 151, 10, 154, 54, 177, 137, 167}
Cipher Suite: TLS_DHE_DSS_WITH_AES_128_CBC_SHA256
Compression Method: 0
Extension renegotiation_info, renegotiated_connection: <empty>
***
Cipher suite:  TLS_DHE_DSS_WITH_AES_128_CBC_SHA256
*** Certificate chain
chain [0] = [
[
  Version: V3
  Subject: CN=tech-user-middle, OU=IT, O=maxant, C=CH
  Signature Algorithm: SHA1withDSA, OID = 1.2.840.10040.4.3

  Key:  Sun DSA Public Key
    Parameters:DSA
	p:     fd7f5381 1d751229 52df4a9c 2eece4e7 f611b752 3cef4400 c31e3f80 b6512669
    455d4022 51fb593d 8d58fabf c5f5ba30 f6cb9b55 6cd7813b 801d346f f26660b7
    6b9950a5 a49f9fe8 047b1022 c24fbba9 d7feb7c6 1bf83b57 e7c6a8a6 150f04fb
    83f6d3c5 1ec30235 54135a16 9132f675 f3ae2b61 d72aeff2 2203199d d14801c7
	q:     9760508f 15230bcc b292b982 a2eb840b f0581cf5
	g:     f7e1a085 d69b3dde cbbcab5c 36b857b9 7994afbb fa3aea82 f9574c0b 3d078267
    5159578e bad4594f e6710710 8180b449 167123e8 4c281613 b7cf0932 8cc8a6e1
    3c167a8b 547c8d28 e0a3ae1e 2bb3a675 916ea37f 0bfa2135 62f1fb62 7a01243b
    cca4f1be a8519089 a883dfe1 5ae59f06 928b665e 807b5525 64014c3b fecf492a

  y:
    d0c54457 ad56bdaf e0f700bd c381107a c67dc96d ccaf0185 3cd36acf 4a1d5aae
    b102aab2 ecafa511 97b6a0b0 03859dae 96d235d1 27a219c7 0e126382 da875aba
    25656ab4 97f30218 b4db497d e585b97b b86f0dc7 b5a6994a 6ed000f0 a2026db8
    6f8b557e d97c49e4 e8e5d1d6 df2be903 a5945710 557508bf 34d9486e 8b61bee8

  Validity: [From: Sat Feb 24 20:01:19 CET 2018,
               To: Fri May 25 21:01:19 CEST 2018]
  Issuer: CN=maxant ca, OU=IT, O=maxant, C=CH
  SerialNumber: [    01b14081]

Certificate Extensions: 4
[1]: ObjectId: 2.5.29.35 Criticality=false
AuthorityKeyIdentifier [
KeyIdentifier [
0000: 0C 91 17 EE CB 17 EE ED   AC D0 7B 1E A5 82 FC 0F  ................
0010: 9F 3A FF F6                                        .:..
]
]

[2]: ObjectId: 2.5.29.37 Criticality=false
ExtendedKeyUsages [
  clientAuth
]

[3]: ObjectId: 2.5.29.15 Criticality=true
KeyUsage [
  DigitalSignature
  Key_Encipherment
]

[4]: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: 8A C9 66 E6 27 38 9E DD   28 F1 44 B7 47 6F 48 73  ..f.'8..(.D.GoHs
0010: 96 80 24 22                                        ..$"
]
]

]
  Algorithm: [SHA1withDSA]
  Signature:
0000: 30 2C 02 14 3F F8 3D 89   9F 61 0F 25 92 C2 0F 18  0,..?.=..a.%....
0010: F5 AE F9 0B 95 29 C7 51   02 14 5E 19 95 68 E8 3D  .....).Q..^..h.=
0020: 51 0B 7F A5 60 6F AD 5E   B0 54 01 BB 15 67        Q...`o.^.T...g

]
chain [1] = [
[
  Version: V3
  Subject: CN=maxant ca, OU=IT, O=maxant, C=CH
  Signature Algorithm: SHA1withDSA, OID = 1.2.840.10040.4.3

  Key:  Sun DSA Public Key
    Parameters:DSA
	p:     fd7f5381 1d751229 52df4a9c 2eece4e7 f611b752 3cef4400 c31e3f80 b6512669
    455d4022 51fb593d 8d58fabf c5f5ba30 f6cb9b55 6cd7813b 801d346f f26660b7
    6b9950a5 a49f9fe8 047b1022 c24fbba9 d7feb7c6 1bf83b57 e7c6a8a6 150f04fb
    83f6d3c5 1ec30235 54135a16 9132f675 f3ae2b61 d72aeff2 2203199d d14801c7
	q:     9760508f 15230bcc b292b982 a2eb840b f0581cf5
	g:     f7e1a085 d69b3dde cbbcab5c 36b857b9 7994afbb fa3aea82 f9574c0b 3d078267
    5159578e bad4594f e6710710 8180b449 167123e8 4c281613 b7cf0932 8cc8a6e1
    3c167a8b 547c8d28 e0a3ae1e 2bb3a675 916ea37f 0bfa2135 62f1fb62 7a01243b
    cca4f1be a8519089 a883dfe1 5ae59f06 928b665e 807b5525 64014c3b fecf492a

  y:
    c4ca2fd5 b201cf58 5b0c9381 31dd3a55 be945c34 0b86d6ab 3aa68026 cc1cebdf
    eaf1964c 08e0062b f2269795 e9e83fa2 464fb9cf 9fde043b 245f97b9 86393863
    f0974f34 0c938716 90bee46b 7cd226dd 958a4bee 345d434d cbbff883 c664fe33
    dbbf27e4 2e8cb40f 7ec5cf09 bb11bad2 1ac6e004 77c15d99 a4f7fdcb 41e6fab2

  Validity: [From: Sat Feb 24 20:01:17 CET 2018,
               To: Fri May 25 21:01:17 CEST 2018]
  Issuer: CN=maxant root, OU=IT, O=maxant, C=CH
  SerialNumber: [    784005e2]

Certificate Extensions: 3
[1]: ObjectId: 2.5.29.35 Criticality=false
AuthorityKeyIdentifier [
KeyIdentifier [
0000: 28 C9 B2 87 03 4A B7 DB   F7 FD 44 9A 5B 5D 61 12  (....J....D.[]a.
0010: A9 8F 7D 3B                                        ...;
]
]

[2]: ObjectId: 2.5.29.19 Criticality=false
BasicConstraints:[
  CA:true
  PathLen:0
]

[3]: ObjectId: 2.5.29.14 Criticality=false
SubjectKeyIdentifier [
KeyIdentifier [
0000: 0C 91 17 EE CB 17 EE ED   AC D0 7B 1E A5 82 FC 0F  ................
0010: 9F 3A FF F6                                        .:..
]
]

]
  Algorithm: [SHA1withDSA]
  Signature:
0000: 30 2C 02 14 24 27 CD 32   9D 61 88 97 54 35 7C 2F  0,..$'.2.a..T5./
0010: 2C 3B 43 A4 06 58 F0 C8   02 14 03 53 79 1B 9D C4  ,;C..X.....Sy...
0020: AD FC A2 51 08 51 B6 F3   53 12 3D 60 5A C9        ...Q.Q..S.=`Z.

]
***
*** Diffie-Hellman ServerKeyExchange
DH Modulus:  { 253, 127, 83, 129, 29, 117, 18, 41, 82, 223, 74, 156, 46, 236, 228, 231, 246, 17, 183, 82, 60, 239, 68, 0, 195, 30, 63, 128, 182, 81, 38, 105, 69, 93, 64, 34, 81, 251, 89, 61, 141, 88, 250, 191, 197, 245, 186, 48, 246, 203, 155, 85, 108, 215, 129, 59, 128, 29, 52, 111, 242, 102, 96, 183, 107, 153, 80, 165, 164, 159, 159, 232, 4, 123, 16, 34, 194, 79, 187, 169, 215, 254, 183, 198, 27, 248, 59, 87, 231, 198, 168, 166, 21, 15, 4, 251, 131, 246, 211, 197, 30, 195, 2, 53, 84, 19, 90, 22, 145, 50, 246, 117, 243, 174, 43, 97, 215, 42, 239, 242, 34, 3, 25, 157, 209, 72, 1, 199 }
DH Base:  { 247, 225, 160, 133, 214, 155, 61, 222, 203, 188, 171, 92, 54, 184, 87, 185, 121, 148, 175, 187, 250, 58, 234, 130, 249, 87, 76, 11, 61, 7, 130, 103, 81, 89, 87, 142, 186, 212, 89, 79, 230, 113, 7, 16, 129, 128, 180, 73, 22, 113, 35, 232, 76, 40, 22, 19, 183, 207, 9, 50, 140, 200, 166, 225, 60, 22, 122, 139, 84, 124, 141, 40, 224, 163, 174, 30, 43, 179, 166, 117, 145, 110, 163, 127, 11, 250, 33, 53, 98, 241, 251, 98, 122, 1, 36, 59, 204, 164, 241, 190, 168, 81, 144, 137, 168, 131, 223, 225, 90, 229, 159, 6, 146, 139, 102, 94, 128, 123, 85, 37, 100, 1, 76, 59, 254, 207, 73, 42 }
Server DH Public Key:  { 166, 218, 186, 176, 204, 199, 115, 124, 183, 18, 237, 114, 164, 29, 193, 164, 169, 78, 77, 58, 0, 186, 4, 211, 212, 75, 115, 122, 210, 231, 126, 62, 89, 36, 111, 231, 188, 110, 63, 241, 40, 205, 241, 60, 138, 240, 205, 11, 183, 194, 14, 91, 131, 9, 35, 9, 100, 51, 157, 151, 121, 115, 212, 217, 17, 242, 35, 252, 156, 71, 222, 165, 221, 191, 83, 27, 46, 25, 196, 18, 18, 220, 89, 24, 62, 48, 172, 232, 109, 176, 161, 214, 59, 241, 67, 129, 137, 125, 47, 177, 21, 154, 255, 92, 88, 136, 225, 238, 157, 97, 161, 211, 92, 86, 147, 44, 248, 150, 41, 39, 198, 185, 141, 52, 167, 38, 127, 95 }
Signature Algorithm SHA1withDSA
Signed with a DSA or RSA public key
*** CertificateRequest
Cert Types: RSA, DSS, ECDSA
Supported Signature Algorithms: SHA512withECDSA, SHA512withRSA, SHA384withECDSA, SHA384withRSA, SHA256withECDSA, SHA256withRSA, SHA224withECDSA, SHA224withRSA, SHA1withECDSA, SHA1withRSA, SHA1withDSA, MD5withRSA
Cert Authorities:
<CN=maxant ca, OU=IT, O=maxant, C=CH>
*** ServerHelloDone
Thread-2, WRITE: TLSv1.2 Handshake, length = 2244
Thread-2, READ: TLSv1.2 Alert, length = 2
Thread-2, RECV TLSv1.2 ALERT:  fatal, certificate_unknown
Thread-2, fatal: engine already closed.  Rethrowing javax.net.ssl.SSLException: Received fatal alert: certificate_unknown
Thread-2, fatal: engine already closed.  Rethrowing javax.net.ssl.SSLException: Received fatal alert: certificate_unknown
Thread-2, called closeInbound()
Thread-2, fatal: engine already closed.  Rethrowing javax.net.ssl.SSLException: Inbound closed before receiving peer's close_notify: possible truncation attack?
Thread-2, called closeOutbound()
Thread-2, closeOutboundInternal()
Thread-2, SEND TLSv1.2 ALERT:  warning, description = close_notify
Thread-2, WRITE: TLSv1.2 Alert, length = 2


## Back SSL logs

No logs, since connection cannot be estabilished between front and middle, so middle never tries to call back.

# Successful, i.e. using two keystores (Front => Middle2 => Back)

## Front SSL logs

    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]

That is the serial number of the certificate with alias "middle-server", which can be used for server authorisation:

    ExtendedKeyUsages [
      serverAuth
    ]
    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH

The certificate is trusted because the CA certificate is in the truststore.

    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>

The server (middle) just requested a client certificate and is saying it has to be signed by the "maxant ca" certificate authority, because that is the only CA certificate in its truststore.

    *** ServerHelloDone
    matching alias: front-client

The client found a suitable certificate

    *** Certificate chain
      Subject: CN=tech-user-front, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]
    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    *** Finished

SSL handshake all done, and the front application received an HTTP OK 200 from the middle server:

    2018-02-24T21:42:26.647 front - response from middle: 200

## Middle SSL logs

    *** ClientHello, TLSv1.2

A client has connected, and the JVM for the middle server has found a suitable server certificate with the following alias:

    matching alias: middle-server
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]

That is the serial number of the server certificate from the middle server's keystore.

    ExtendedKeyUsages [
      serverAuth
    ]
    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>

The server (middle) just sent the client certificate request and is saying it has to be signed by the "maxant ca" certificate authority, because that is the only CA certificate in its truststore.

    *** ServerHelloDone
    *** Certificate chain
      Subject: CN=tech-user-front, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]

That is the client certificate which the client (front) sent to the middle server.

    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH

And it is trusted, because it was signed by the CA which is in the middle server's trust store.

    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    *** Finished

Now the middle server is creating an SSL connection to the back server.
The program loads the key- and trust-stores, and the log shows what is found:

    found key for : middle-client
      Subject: CN=tech-user-middle, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]
    ***
    adding as trusted cert:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH

Now the SSL handshake with the back server starts:

    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    5e98cdef]

The middle server just received the back server's server certificate. The serial number is that of the certificate with the alias  "back-server".

    ExtendedKeyUsages [
      serverAuth
    ]
    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH

The middle server is saying it trusts the back server's certificate because it was signed by the maxant CA.
Note that the ordering is different from other logs. That can happen.

    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>

The back server just sent a client certificate request to the middle server, requiring a certificate that is signed by the maxant CA, again because that is the
only certificate in the back server's trust store.

    *** ServerHelloDone
    matching alias: middle-client

The middle server just found a suitable client certificate.

    *** Certificate chain
      Subject: CN=tech-user-middle, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]
    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    *** Finished

SSL handshake done, and middle server gets an HTTP OK 200 response:

    2018-02-24T21:42:26.582 middle - response from back: 200

## Back SSL logs

    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    5e98cdef]
    ExtendedKeyUsages [
      serverAuth
    ]

The back server got a connection and sent the certificate with the serial number matching the certificate with the alias "back-server".

    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>

The back server requested a client certificate signed by the maxant CA.

    *** ServerHelloDone
    *** Certificate chain
      Subject: CN=tech-user-middle, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]

The middle server sent a suitable client certificate.

    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH

The back server trusts the client certificate from the middle server because it was signed by the maxant CA.

    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    *** Finished

SSL handshake done, and the back server logs that it was called:

    2018-02-24T21:42:26.521 back



# Unsuccessful, i.e. using just one keystore for both client and server certificates of the middle server (Front => Middle => Back)

## Front SSL logs

    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]
    ExtendedKeyUsages [
      serverAuth
    ]
    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH
    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>
    *** ServerHelloDone
    matching alias: front-client
    *** Certificate chain
      Subject: CN=tech-user-front, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]
    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    *** Finished

    main, received EOFException: ignored
    main, called closeInternal(false)
    main, SEND TLSv1.2 ALERT:  warning, description = close_notify
    main, WRITE: TLSv1.2 Alert, length = 64
    main, called closeSocket(false)
    main, called close()
    main, called closeInternal(true)


    %% Client cached [Session-1, TLS_DHE_DSS_WITH_AES_128_CBC_SHA256]
    %% Try resuming [Session-1, TLS_DHE_DSS_WITH_AES_128_CBC_SHA256] from port 45076
    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]
    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH
    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>
    *** ServerHelloDone
    matching alias: front-client
    *** Certificate chain
      Subject: CN=tech-user-front, OU=IT, O=maxant, C=CH
    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    *** Finished

    main, WRITE: TLSv1.2 Application Data, length = 224
    main, received EOFException: ignored
    main, called closeInternal(false)
    main, SEND TLSv1.2 ALERT:  warning, description = close_notify
    main, WRITE: TLSv1.2 Alert, length = 64
    main, called closeSocket(false)
    main, called close()
    main, called closeInternal(true)
    main, called close()
    main, called closeInternal(true)

    Exception in thread "main" java.net.SocketException: Unexpected end of file from server
        at sun.net.www.http.HttpClient.parseHTTPHeader(HttpClient.java:792)
        at sun.net.www.http.HttpClient.parseHTTP(HttpClient.java:647)
        at sun.net.www.http.HttpClient.parseHTTPHeader(HttpClient.java:789)
        at sun.net.www.http.HttpClient.parseHTTP(HttpClient.java:647)
        at sun.net.www.protocol.http.HttpURLConnection.getInputStream0(HttpURLConnection.java:1535)
        at sun.net.www.protocol.http.HttpURLConnection.getInputStream(HttpURLConnection.java:1440)
        at java.net.HttpURLConnection.getResponseCode(HttpURLConnection.java:480)
        at sun.net.www.protocol.https.HttpsURLConnectionImpl.getResponseCode(HttpsURLConnectionImpl.java:338)
        at Front.main(Front.java:26)

    Process finished with exit code 1

## Middle SSL logs

    *** ClientHello, TLSv1.2
    matching alias: middle-server
    matching alias: middle-client
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]
    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>
    *** ServerHelloDone
    *** Certificate chain
      Subject: CN=tech-user-front, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]
    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH
    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    *** Finished


    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    5e98cdef]
    ExtendedKeyUsages [
      serverAuth
    ]
    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH
    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>
    *** ServerHelloDone
    matching alias: middle-server
    matching alias: middle-client
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]
    ExtendedKeyUsages [
      serverAuth
    ]
    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    Thread-2, fatal error: 80: Inbound closed before receiving peer's close_notify: possible truncation attack?
    javax.net.ssl.SSLException: Inbound closed before receiving peer's close_notify: possible truncation attack?
    Thread-2, SEND TLSv1.2 ALERT:  fatal, description = internal_error
    Thread-2, called closeOutbound()
    Thread-2, closeOutboundInternal()



    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]
    ExtendedKeyUsages [
      serverAuth
    ]
    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>
    *** ServerHelloDone
    *** Certificate chain
      Subject: CN=tech-user-front, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]
    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH
    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    *** Finished

    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    5e98cdef]
    ExtendedKeyUsages [
      serverAuth
    ]
    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH
    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>
    *** ServerHelloDone
    matching alias: middle-server
    matching alias: middle-client
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]
    ExtendedKeyUsages [
      serverAuth
    ]
    *** ClientKeyExchange, DH
    *** CertificateVerify
    Thread-2, handling exception: java.net.SocketException: Broken pipe
    Thread-2, SEND TLSv1.2 ALERT:  fatal, description = unexpected_message
    Thread-2, Exception sending alert: java.net.SocketException: Broken pipe
    Thread-2, called closeSocket()
    Thread-2, called closeInbound()
    Thread-2, fatal error: 80: Inbound closed before receiving peer's close_notify: possible truncation attack?
    javax.net.ssl.SSLException: Inbound closed before receiving peer's close_notify: possible truncation attack?
    Thread-2, SEND TLSv1.2 ALERT:  fatal, description = internal_error
    Thread-2, called closeOutbound()
    Thread-2, closeOutboundInternal()



## Back SSL logs

    *** ClientHello, TLSv1.2
    matching alias: back-server
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    5e98cdef]
    ExtendedKeyUsages [
      serverAuth
    ]
    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>
    *** ServerHelloDone
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]
    ExtendedKeyUsages [
      serverAuth
    ]
    ***
    Thread-2, fatal error: 46: General SSLEngine problem
    sun.security.validator.ValidatorException: Extended key usage does not permit use for TLS client authentication
    Thread-2, SEND TLSv1.2 ALERT:  fatal, description = certificate_unknown
    Thread-2, WRITE: TLSv1.2 Alert, length = 2
    Thread-2, fatal: engine already closed.  Rethrowing javax.net.ssl.SSLHandshakeException: General SSLEngine problem
    Thread-2, called closeInbound()
    Thread-2, fatal: engine already closed.  Rethrowing javax.net.ssl.SSLException: Inbound closed before receiving peer's close_notify: possible truncation attack?
    Thread-2, called closeOutbound()
    Thread-2, closeOutboundInternal()


    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    5e98cdef]
    ExtendedKeyUsages [
      serverAuth
    ]
    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>
    *** ServerHelloDone
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]
    ExtendedKeyUsages [
      serverAuth
    ]
    ***
    Thread-2, fatal error: 46: General SSLEngine problem
    sun.security.validator.ValidatorException: Extended key usage does not permit use for TLS client authentication
    Thread-2, SEND TLSv1.2 ALERT:  fatal, description = certificate_unknown
    Thread-2, WRITE: TLSv1.2 Alert, length = 2
    Thread-2, fatal: engine already closed.  Rethrowing javax.net.ssl.SSLHandshakeException: General SSLEngine problem
    Thread-2, called closeInbound()
    Thread-2, fatal: engine already closed.  Rethrowing javax.net.ssl.SSLException: Inbound closed before receiving peer's close_notify: possible truncation attack?
    Thread-2, called closeOutbound()
    Thread-2, closeOutboundInternal()

