#/bin/sh -x

# build jar first
lein do clean, uberjar

# build docker image and tag
docker build -t gonewest818/benchcurl:0.2.0 .

