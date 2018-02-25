# What is this?

See blog article: http://blog.maxant.co.uk/pebble/TODO

A repo for an investigation into how the JVM handles two-way SSL, particularly
how a single JVM handles incoming and outgoing connections, when it has just a single key store.

We noticed that the JVM seems to be unable to create two-way SSL connections for both incoming and
outgoing connections, because it presents the wrong certificate on one of the connections.

The idea is to have two certificates in the keystore. One presented to callers, known as the server certificate
which is used to verify the hostname of the server.  The second is presented to downstream servers when they
request a client certificate.  Since the keystore is used for holding both certificates, the JVM needs a way
to differentiate them. The "normal" mechanism are the extended key usage flags which can be set in the certificates,
see https://tools.ietf.org/html/rfc5280#section-4.2.1.12. The two relevant flags are "clientAuth" and "serverAuth"
for the client and server certificates respectively.

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

It turns out the selection is also based on the hashcode of the alias and certificate combination (which form a
map entry) because during
selection the entry set of a HashMap of alias to certificate is iterated over. See
http://hg.openjdk.java.net/jdk8u/jdk8u60/jdk/file/935758609767/src/share/classes/sun/security/ssl/SunX509KeyManagerImpl.java#l352

Due to the hashcode calculation being
deterministic, i.e. causing the enumeration to have the same order, the JVM will always be incapable of
being able to select both client and server certificates
because it always returns the first one found (order based on hashcode) and so will always fail to complete
the SSL handshake with either the caller or the down stream server.

For this reason the author of this page believes this to be a bug rather than a change request.

A patched version of the SunX509KeyManagerImpl (see ./src/main/java/PatchedSunX509KeyManagerImpl), fixes the problem.
It was necessary to add the same algorithm to both of the following methods:

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

Logs including SSL debug logs from three tests are available:

- [./unsuccessful_client.md](Unsuccessful, i.e. using just one keystore for both client and server certificates of the middle (Front => Middle => Back), where middle presents client certificate to front, instead of server certificate)
- Successful, i.e. using patch (Front => MiddleWithPatchedKeyManager => Back) or using two keystores (Front => Middle2 => Back)
- Unsuccessful, i.e. using just one keystore for both client and server certificates of the middle (Front => Middle => Back), where middle sends server certificate to back, instead of client certificate

