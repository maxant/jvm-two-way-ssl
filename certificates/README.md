# Creating certificates.

Run `createCerts.sh`.




## Back

Has this error:

    Thread-2, fatal error: 46: General SSLEngine problem
    sun.security.validator.ValidatorException: Extended key usage does not permit use for TLS client authentication
    %% Invalidated:  [Session-1, TLS_DHE_DSS_WITH_AES_128_CBC_SHA256]
    Thread-2, SEND TLSv1.2 ALERT:  fatal, description = certificate_unknown
    Thread-2, WRITE: TLSv1.2 Alert, length = 2
    Thread-2, fatal: engine already closed.  Rethrowing javax.net.ssl.SSLHandshakeException: General SSLEngine problem
    Thread-2, called closeInbound()
    Thread-2, fatal: engine already closed.  Rethrowing javax.net.ssl.SSLException: Inbound closed before receiving peer's close_notify: possible truncation attack?
    Thread-2, called closeOutbound()
    Thread-2, closeOutboundInternal()
