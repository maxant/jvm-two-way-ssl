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
