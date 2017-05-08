FROM alpine:3.5

RUN apk --no-cache add openjdk8 apache2-utils

ENV JAVA_OPTS "-Xmx512m -Xms512m"

RUN mkdir /benchcurl
WORKDIR /benchcurl

ADD target/uberjar/benchcurl-0.3.0-SNAPSHOT-standalone.jar /benchcurl/
ADD benchcurl.jks /benchcurl/

ENTRYPOINT ["/usr/bin/java"]
CMD ["-jar", "benchcurl-0.3.0-SNAPSHOT-standalone.jar"]

