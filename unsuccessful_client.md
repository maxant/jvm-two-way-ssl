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

