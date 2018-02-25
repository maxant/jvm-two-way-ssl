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

- Unsuccessful, i.e. using just one keystore for both client and server certificates of the middle (Front => Middle => Back), where middle presents client certificate to front, instead of server certificate
- Successful, i.e. using patch (Front => MiddleWithPatchedKeyManager => Back) or using two keystores (Front => Middle2 => Back)
- Unsuccessful, i.e. using just one keystore for both client and server certificates of the middle (Front => Middle => Back), where middle sends server certificate to back, instead of client certificate




# Unsuccessful, i.e. using just one keystore for both client and server certificates of the middle (Front => Middle => Back), where middle presents client certificate to front, instead of server certificate

## Front SSL logs

    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=tech-user-middle, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]

Middle just presented the wrong certificate! It should have been the server one!

    Exception in thread "main" javax.net.ssl.SSLHandshakeException: java.security.cert.CertificateException: No name matching localhost found
    main, SEND TLSv1.2 ALERT:  fatal, description = certificate_unknown
    main, called closeSocket()
    main, handling exception: javax.net.ssl.SSLHandshakeException: java.security.cert.CertificateException: No name matching localhost found
    ...
    Caused by: java.security.cert.CertificateException: No name matching localhost found
	... 14 more

## Middle SSL logs

    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=tech-user-middle, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]

Middle just chose the wrong certificate! It should have chose the one with extended key usage "serverAuth"!

The middle continues trying to complete the handshake until the client registers the error.

    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>
    *** ServerHelloDone

The front registers the error, and the middle closes the connection.

    Thread-2, RECV TLSv1.2 ALERT:  fatal, certificate_unknown
    Thread-2, fatal: engine already closed.  Rethrowing javax.net.ssl.SSLException: Received fatal alert: certificate_unknown
    Thread-2, called closeInbound()
    Thread-2, fatal: engine already closed.  Rethrowing javax.net.ssl.SSLException: Inbound closed before receiving peer's close_notify: possible truncation attack?
    Thread-2, called closeOutbound()
    Thread-2, closeOutboundInternal()
    Thread-2, SEND TLSv1.2 ALERT:  warning, description = close_notify


## Back SSL logs

No logs, since connection cannot be estabilished between front and middle, so middle never tries to call back.

# Successful, i.e. using patch (Front => MiddleWithPatchedKeyManager => Back) or using two keystores (Front => Middle2 => Back)

## Front SSL logs

    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]

That is the serial number of the certificate with alias "middle-server", which can be used for server authentication:

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

The middle just requested a client certificate and is saying it has to be signed by the "maxant ca" certificate authority, because that is the only CA certificate in its truststore.

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

SSL handshake all done, and the front application received an HTTP OK 200 from the middle:

    2018-02-24T21:42:26.647 front - response from middle: 200

## Middle SSL logs

    *** ClientHello, TLSv1.2

A client has connected, and the JVM for the middle has found a suitable server certificate with the following alias:

    matching alias: middle-server
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]

That is the serial number of the server certificate from the middle's keystore.

    ExtendedKeyUsages [
      serverAuth
    ]
    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>

The middle just sent the client certificate request and is saying it has to be signed by the "maxant ca" certificate authority, because that is the only CA certificate in its truststore.

    *** ServerHelloDone
    *** Certificate chain
      Subject: CN=tech-user-front, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]

That is the client certificate which the client (front) sent to the middle.

    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH

And it is trusted, because it was signed by the CA which is in the middle's trust store.

    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    *** Finished

Now the middle is creating an SSL connection to the back.
The program loads the key- and trust-stores, and the log shows what is found:

    found key for : middle-client
      Subject: CN=tech-user-middle, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]
    ***
    adding as trusted cert:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH

Now the SSL handshake with the back starts:

    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    5e98cdef]

The middle just received the back's server certificate. The serial number is that of the certificate with the alias  "back-server".

    ExtendedKeyUsages [
      serverAuth
    ]
    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH

The middle is saying it trusts the back's certificate because it was signed by the maxant CA.
Note that the ordering is different from other logs. That can happen.

    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>

The back just sent a client certificate request to the middle, requiring a certificate that is signed by the maxant CA, again because that is the
only certificate in the back's trust store.

    *** ServerHelloDone
    matching alias: middle-client

The middle just found a suitable client certificate.

    *** Certificate chain
      Subject: CN=tech-user-middle, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]
    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    *** Finished

SSL handshake done, and middle gets an HTTP OK 200 response:

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

The back got a connection and sent the certificate with the serial number matching the certificate with the alias "back-server".

    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>

The back requested a client certificate signed by the maxant CA.

    *** ServerHelloDone
    *** Certificate chain
      Subject: CN=tech-user-middle, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]

The middle sent a suitable client certificate.

    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH

The back trusts the client certificate from the middle because it was signed by the maxant CA.

    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    *** Finished

SSL handshake done, and the back logs that it was called:

    2018-02-24T21:42:26.521 back



# Unsuccessful, i.e. using just one keystore for both client and server certificates of the middle (Front => Middle => Back), where middle sends server certificate to back, instead of client certificate

## Front SSL logs

    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]

That is the serial number of the server certificate from the middle's keystore. That is correct.

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

The handshake is complete, but the middle closes it, because of an error downstream.

    main, received EOFException: ignored
    main, called closeInternal(false)
    main, SEND TLSv1.2 ALERT:  warning, description = close_notify
    main, WRITE: TLSv1.2 Alert, length = 64
    main, called closeSocket(false)
    main, called close()
    main, called closeInternal(true)

The front application tries to reestablish the connection, unsuccessfully. And finally, an error is logged:

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

## Middle SSL logs

    *** ClientHello, TLSv1.2
    matching alias: middle-server
    matching alias: middle-client

The middle just said that it found two suitable certificates, and that is wrong!
In this case, it chooses the one with the alias "middle-server", which just happens to be right.

    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]

That is the serial number of the server certificate from the middle's keystore. That is correct.

    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>

Middle requests a client certificate signed by the maxant CA.

    *** ServerHelloDone
    *** Certificate chain
      Subject: CN=tech-user-front, OU=IT, O=maxant, C=CH
    ExtendedKeyUsages [
      clientAuth
    ]

The front application delivered a suitable client certificate above.

    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH
    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    *** Finished

And the middle was happy with it. Now it starts the do the SSL handshake downstream with the back:

    *** ClientHello, TLSv1.2
    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    5e98cdef]
    ExtendedKeyUsages [
      serverAuth
    ]

The middle just received the certificate from the back, with the serial number matching the certificate with the alias "back-server".

    ***
    Found trusted certificate:
      Subject: CN=maxant ca, OU=IT, O=maxant, C=CH

The middle just trusted the back's server certificate.

    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>

The back just made a request for a client certificate from the mdidle.

    *** ServerHelloDone
    matching alias: middle-server
    matching alias: middle-client

The middle logged that it found two suitable certificates that it could send as client certificates to back.

    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]
    ExtendedKeyUsages [
      serverAuth
    ]

And the middle sent the server certificate instead of the client certificate. This was wrong!

    *** ClientKeyExchange, DH
    *** CertificateVerify
    *** Finished
    Thread-2, fatal error: 80: Inbound closed before receiving peer's close_notify: possible truncation attack?
    javax.net.ssl.SSLException: Inbound closed before receiving peer's close_notify: possible truncation attack?
    Thread-2, SEND TLSv1.2 ALERT:  fatal, description = internal_error
    Thread-2, called closeOutbound()
    Thread-2, closeOutboundInternal()

The back sent an error because it didn't accept the middle's server certificate as a client certificate, which is correct.

The middle re-attempts the connection, but fails again, this time with a broken pipe:

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

The back received a connection request and sent its server certificate with the alias "back-server".

    *** ServerHello, TLSv1.2
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    5e98cdef]

The middle just received the certificate from the back, with the serial number matching the certificate with the alias "back-server".

    ExtendedKeyUsages [
      serverAuth
    ]

The certificate sent from the back to the middle was correct, as it was one used for server authentication.

    *** Diffie-Hellman ServerKeyExchange
    *** CertificateRequest
    Cert Authorities:
    <CN=maxant ca, OU=IT, O=maxant, C=CH>

The back sent a client certificate request, for a certificate signed by the maxant CA.

    *** ServerHelloDone
    *** Certificate chain
      Subject: CN=localhost, OU=IT, O=maxant, C=CH
      SerialNumber: [    305e202e]
    ExtendedKeyUsages [
      serverAuth
    ]

The middle sent the wrong certificate! The back refuses to accept it:

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

The connection is reattempted by the middle, but fails again.
