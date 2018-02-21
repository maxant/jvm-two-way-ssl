Files in this folder:

- client_middle.cer: the public key of the middle system, with usage "client auth"
- client_middle.p12: the key pair of the middle system with password 123456, with usage "client auth"
- middle-keystore.jks: a keystore with password 123456 containing client-middle and server-middle pairs. this file is the main interest in this project!
- middle-truststore.jks: a truststore with password 123456 containing client-front and server-back pairs, since the middle app must trust the front as a client and the back as a server.

TODO continue here...

TODO how to sign and create a chain?

## signing:

export private key from KSE as pkcs8 file.
then create CSR:

    openssl req -new -key server_middle.pkcs8 -out server_middle.csr

sign it:

    openssl x509 -req -in server_middle.csr -CA ../ca/rootCA.pem -CAkey ../ca/rootCA.key -CAcreateserial -out server_middle_signed.crt -days 500 -sha256

right click in KSE on server cert and import CA response.

PROBLEM: extended key usage is gone!

