#/bin/sh -x

# build jar first
lein do clean, uberjar

# self-signed cert for TLS
keytool -genkey \
        -keyalg RSA \
        -dname "CN=Administrator, OU=Site Operations, O=DWA\ Nova\,\ LLC, L=Burbank, ST=CA, C=US" \
        -alias selfsigned \
        -keystore benchcurl.jks \
        -validity 90 \
        -keysize 2048

# build docker image and tag
docker build -t gonewest818/benchcurl:0.3.0-SNAPSHOT .

