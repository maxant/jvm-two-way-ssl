# What is this?

See blog article: http://blog.maxant.co.uk/pebble/TODO

A repo for an investigation into how the JVM handles mutual TLS authentication (two-way SSL), particularly
how the JVM handles incoming and outgoing connections, when it has just a single key store.

Consider a client application named "Front" connecting to a server named "Middle", which in turn makes a further
connection to a downstream server named "Back".  Each connection should use mututal (client & server certificate) TLS authentication.

We noticed that the JVM running in the middle seems to be unable to create mutually authenticated TLS connections for both
incoming and outgoing connections, because it presents the wrong certificate during one of the handshakes.

The idea is to have two certificates in the keystore. One presented to callers, known as the server certificate
which is used to verify the hostname of the server.  The second is presented to downstream servers when they
request a client certificate during the SSL handshake.
Since the keystore is used for holding both certificates, the JVM needs a way
to differentiate them. It is assumed that the normal mechanism to do this is the "extended key usage" certificate
extension, see https://tools.ietf.org/html/rfc5280#section-4.2.1.12.
The two relevant values are "clientAuth" and "serverAuth" for the client and server certificates respectively.

Upon closer investigation, the problem seems to be related to the following code in the default key manager
(SunX509KeyManagerImpl), see http://hg.openjdk.java.net/jdk8u/jdk8u60/jdk/file/935758609767/src/share/classes/sun/security/ssl/SunX509KeyManagerImpl.java#l220

    /*
     * Choose an alias to authenticate the client side of a secure
     * socket given the public key type and the list of
     * certificate issuer authorities recognized by the peer (if any).
     */
    @Override
    public String chooseClientAlias(String[] keyTypes, Principal[] issuers,
            Socket socket) {

        ...

        for (int i = 0; i < keyTypes.length; i++) {
            String[] aliases = getClientAliases(keyTypes[i], issuers);
            if ((aliases != null) && (aliases.length > 0)) {
                return aliases[0];  <========== NEEDS TO BE MORE SELECTIVE AND USE ALIAS WITH SUITABLE EXTENDED KEY TYPE
            }
        }
        return null;
    }

Similar code is used for choosing the server alias.

It turns out that the selection is also based on the hashcode of the alias and certificate combination (which form a
map entry) because during
selection the entry set of a HashMap of alias to certificate is iterated over. See line 352 of
http://hg.openjdk.java.net/jdk8u/jdk8u60/jdk/file/935758609767/src/share/classes/sun/security/ssl/SunX509KeyManagerImpl.java#l352

Due to the code selecting the first alias and due to the order of the aliases being constant once the keystore is loaded
(hashcode calculation is deterministic), the JVM will never be capable of
being able to select the correct certificate for both client and server connections
and so will always fail to complete
the SSL handshake with one of the caller or the down stream server.

For this reason the author of this page believes this to be a bug rather than a change request.

A patched version of the SunX509KeyManagerImpl (see [PatchedSunX509KeyManagerImpl](./src/main/java/PatchedSunX509KeyManagerImpl.java)),
fixes the problem. It was necessary to add the same algorithm to both of the following methods:

- chooseServerAlias
- chooseClientAlias

The more selective algorithm uses the extended key usage as follows:

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


Logs including SSL debug logs (generated using the system property `-Djavax.net.debug=ssl`) from three
tests are available:

- [Unsuccessful, i.e. using just one keystore for both client and server certificates of the middle (Front => Middle => Back), where middle presents client certificate to front, instead of server certificate](./unsuccessful_client.md)
- [Unsuccessful, i.e. using just one keystore for both client and server certificates of the middle (Front => Middle => Back), where middle sends server certificate to back, instead of client certificate](./unsuccessful_server.md)
- [Successful, i.e. using patch (Front => MiddleWithPatchedKeyManager => Back) or using two keystores (Front => Middle2 => Back)](./successful.md)

# Unit Tests

[PatchedSunX509KeyManagerImplTest](./src/test/java/PatchedSunX509KeyManagerImplTest.java) tests the patched implementation and the test passes succesfully.
[SunX509KeyManagerImplTest](./src/test/java/SunX509KeyManagerImplTest.java) tests the original implementation but the test fails.

# Running the examples

The following classes can be found under `src/main/java`, and all have `main` methods for running them:

- Back: Represents a simple downstream SSL enabled server which requests a client certificate. Runs on port 10002 and uses key- and truststores from `certificates/back`.
- Front: Represents a simple SSL enabled client which makes an HTTPS request to middle. Uses key- and truststores from `certificates/front`.
- Middle: Represents a simple SSL enabled server which receives requests on port 10001 and requests a client certificate. It then makes it's own request to the downstream "back" server using SSL.

As such, the middle server is the interesting component as it requires mutual TLS authentication for incoming as well as outgoing connections.

Additionally, there are two further versions of the middle server:

- Middle2: This implementation uses two keystores instead of just one, each containing exactly one certificate, so that selection is always correct. This is probably the best way to fix the problem today.
- MiddleWithPatchedKeyManager: This implementation uses the single keystore with both client and server certificates, but uses a patched version of the [PatchedSunX509KeyManagerImpl](./src/main/java/PatchedSunX509KeyManagerImpl.java)  which is capable of reading extended key usage in the certificates in order to make the correct selection.

# Certificates, Key- and Truststores

See [certificates/README.md](./certificates/README.md).

# Bug Report

A bug was reported to Oracle on 20180225 with internal review ID 9052786.
