# What is this folder?

This folder contains the key- and truststores used by the code.

Application certificates are signed by the CA certificate which is issued by the root certificate, based on
the example at the bottom of this page: https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html

- ca/root.jks: The root keystore containing the root certificate pair; used only by `createCerts.sh` during store generation
- ca/ca.jks: The CA keystore containing the CA certificate used for signing front/middle/back certificates; used only by `createCerts.sh` during store generation
- front/front-keystore.jks: Contains the client certificate of the front application; used only by the `Front` class
- front/front-truststore.jks: Contains the ca certificate chain, so that the front can trust the middle; used only by the `Front` class
- middle/middle-keystore.jks: Contains the client and server certificates of the middle application; used by the `Middle` and `MiddleWithPatchedKeyManager` classes
- middle/middle-keystore-just-client-cert.jks: Contains just the client certificate of the middle application; only used by the `Middle2` class
- middle/middle-keystore-just-server-cert.jks: Contains just the server certificate of the middle application; only used by the `Middle2` class
- middle/middle-truststore.jks: Contains the ca certificate chain, so that the middle can trust the front and the back; used by all middle classes
- back/back-keystore.jks: Contains the server certificate of the back application; used only by the `Back` class
- back/back-truststore.jks: Contains the ca certificate chain, so that the back can trust the middle; used only by the `Back` class

N.b. Apart from passwords, all truststores are identical.

# Creating certificates.

Run `createCerts.sh`.


