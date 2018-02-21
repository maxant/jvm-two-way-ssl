This folder contains the root CA key and the self-signed certificate.

See https://datacenteroverlords.com/2012/03/01/creating-your-own-ssl-certificate-authority/

Password: 123456

Generate the key:

    openssl genrsa -des3 -out rootCA.key 2048

Create a self-signed certificate:

    openssl req -x509 -new -nodes -key rootCA.key -sha256 -days 1024 -out rootCA.pem

    Country Name (2 letter code) [XX]:CH
    State or Province Name (full name) []:Bern
    Locality Name (eg, city) [Default City]:Bern
    Organization Name (eg, company) [Default Company Ltd]:maxant
    Organizational Unit Name (eg, section) []:IT
    Common Name (eg, your name or your server's hostname) []:Ant Kutschera
    Email Address []:caenquiries@maxant.co.uk

