FROM ubuntu:16.04

RUN apt-get -y update; \
    apt-get -y install openjdk-8-jdk-headless; \
    apt-get -y autoclean

ENV JAVA_OPTS "-Xmx512m -Xms512m"

RUN mkdir /benchcurl
WORKDIR /benchcurl

ADD target/uberjar/benchcurl-0.2.0-SNAPSHOT-standalone.jar /benchcurl/

ENTRYPOINT ["/usr/bin/java"]
CMD ["-jar", "benchcurl-0.2.0-SNAPSHOT-standalone.jar"]

