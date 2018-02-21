# What is this?

A repo for an investigation into how the JVM handles two-way SSL, particularly
how a single JVM handles incoming and outgoing connections, when it has just a single key store.


# TODO

- use signed certs, rather than installing the actual cert. if the actual cert is installed, we have less problems.