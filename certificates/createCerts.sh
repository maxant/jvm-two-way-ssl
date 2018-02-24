#!/bin/bash

#https://docs.oracle.com/javase/8/docs/technotes/tools/unix/keytool.html
#https://stackoverflow.com/questions/30634658/how-to-create-a-certificate-chain-using-keytool

echo
echo
echo
echo ////////////////////////////////////////////////////
echo ////////////////////////////////////////////////////
echo //
echo // Generating certificates and stores...
echo //
echo ////////////////////////////////////////////////////
echo ////////////////////////////////////////////////////
echo
echo cleaning up
echo
rm -f ca/ca.pem
rm -f ca/ca.jks
rm -f ca/cachain.pem
rm -f ca/root.jks
rm -f ca/root.pem
rm -f middle/middle-server.pem
rm -f middle/middle-client.pem
rm -f middle/middle-keystore.jks
rm -f front/front-client.pem
rm -f front/front-keystore.jks
rm -f back/back-server.pem
rm -f back/back-keystore.jks

export rootpass=123451
export capass=123452
export middlekeystorepass=123453
export frontkeystorepass=123454
export backkeystorepass=123455

# generating pairs: note that store password and key password are always the same, otherwise java throws a bizarre error when using the keystores

# ####################################################################################
echo
echo /////////////////////////////////////////
echo // Root and CA
echo /////////////////////////////////////////
echo generating root pair
keytool -genkeypair -keystore ca/root.jks -storepass $rootpass -keypass $rootpass -alias root -ext bc:c -dname "cn=maxant root, ou=IT, o=maxant, c=CH"
echo generating ca pair
keytool -genkeypair -keystore ca/ca.jks -storepass $capass -keypass $capass -alias ca -ext bc:c -dname "cn=maxant ca, ou=IT, o=maxant, c=CH"

echo exporting root cert as ca/root.pem
keytool -keystore ca/root.jks -alias root -storepass $rootpass -exportcert -rfc > ca/root.pem
echo creating CSR for CA and signing with root and storing as ca/ca.pem
keytool -storepass $capass -keystore ca/ca.jks -certreq -alias ca | keytool -storepass $rootpass -keystore ca/root.jks -gencert -alias root -ext BC=0 -rfc > ca/ca.pem

echo concatenating root and ca into ca/cachain.pem
cat ca/root.pem ca/ca.pem > ca/cachain.pem
echo importing chain into ca/ca.jks
#use noprompt, because we trust the certificate reply that was just generated but we havent imported the root or ca into the jvm cacerts file
keytool -keystore ca/ca.jks -storepass $capass -importcert -alias ca -noprompt -file ca/cachain.pem

# ####################################################################################
echo
echo /////////////////////////////////////////
echo // middle application
echo /////////////////////////////////////////
echo // server certificate
echo generating middle server pair
keytool -genkeypair -keystore middle/middle-keystore.jks -storepass $middlekeystorepass -keypass $middlekeystorepass -alias middle-server -ext eku:usage=serverAuth -dname "cn=localhost, ou=IT, o=maxant, c=CH"

echo creating CSR for middle-server and signing with ca and storing as middle/middle-server.pem
keytool -storepass $middlekeystorepass -keystore middle/middle-keystore.jks -certreq -alias middle-server | keytool -storepass $capass -keystore ca/ca.jks -gencert -alias ca -ext ku:c=dig,keyEncipherment -ext eku:usage=serverAuth -rfc > middle/middle-server.pem

echo /////////////////////////////////////////
echo // client certificate
echo generating middle client pair
keytool -genkeypair -keystore middle/middle-keystore.jks -storepass $middlekeystorepass -keypass $middlekeystorepass -alias middle-client -ext eku:usage=clientAuth -dname "cn=tech-user-middle, ou=IT, o=maxant, c=CH"

echo creating CSR for middle-client and signing with ca and storing as middle/middle-client.pem
keytool -storepass $middlekeystorepass -keystore middle/middle-keystore.jks -certreq -alias middle-client | keytool -storepass $capass -keystore ca/ca.jks -gencert -alias ca -ext ku:c=dig,keyEncipherment -ext eku:usage=clientAuth -rfc > middle/middle-client.pem

# ####################################################################################
echo
echo /////////////////////////////////////////
echo // front application
echo /////////////////////////////////////////
echo // client certificate
echo generating front client pair
keytool -genkeypair -keystore front/front-keystore.jks -storepass $frontkeystorepass -keypass $frontkeystorepass -alias front-client -ext eku:usage=clientAuth -dname "cn=tech-user-front, ou=IT, o=maxant, c=CH"

echo creating CSR for front-client and signing with ca and storing as front/front-client.pem
keytool -storepass $frontkeystorepass -keystore front/front-keystore.jks -certreq -alias front-client | keytool -storepass $capass -keystore ca/ca.jks -gencert -alias ca -ext ku:c=dig,keyEncipherment -ext eku:usage=clientAuth -rfc > front/front-client.pem

# ####################################################################################
echo
echo /////////////////////////////////////////
echo // back application
echo /////////////////////////////////////////
echo // server certificate
echo generating back server pair
keytool -genkeypair -keystore back/back-keystore.jks -storepass $backkeystorepass -keypass $backkeystorepass -alias back-server -ext eku:usage=serverAuth -dname "cn=localhost, ou=IT, o=maxant, c=CH"

echo creating CSR for back-server and signing with ca and storing as back/back-server.pem
keytool -storepass $backkeystorepass -keystore back/back-keystore.jks -certreq -alias back-server | keytool -storepass $capass -keystore ca/ca.jks -gencert -alias ca -ext ku:c=dig,keyEncipherment -ext eku:usage=serverAuth -rfc > back/back-server.pem

# ####################################################################################
echo
echo /////////////////////////////////////////
echo // import signed cert into keystore
echo /////////////////////////////////////////
echo // server certificate
keytool -storepass $middlekeystorepass -keystore middle/middle-keystore.jks -alias middle-client -importcert -file middle/middle-client.pem -noprompt


#TODO
# - do we need to import the ca response into the keystores? the keys in the keystores are NOT signed...
# maybe it would be better to create a single keystore for all keys and then build key/truststores for each application

