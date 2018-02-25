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

