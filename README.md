# What is this?

A repo for an investigation into how the JVM handles two-way SSL, particularly
how a single JVM handles incoming and outgoing connections, when it has just a single key store.


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

