# benchcurl

A simplistic REST wrapper around Apache Bench to test responsiveness
of REST transactions over distance.

Benchcurl can respond to GET and PUT requests with dummy payloads,
and it can generate a continuous stream of GET requests via Apache Bench. 


## Usage

As a single jar

    $ git clone ...
    $ lein uberjar
    $ java -jar target/uberjar/benchcurl-0.2.0-standalone.jar


As a docker image

    $ ./docker_build.sh
    $ docker run -it --rm gonewest818/benchcurl:0.2.0


## Options

For now there are no externally configurable settings, but there is a hashmap
in the code that configures the server, for instance the port and the number of
threads Jetty runs.


## Examples

Retrieve a file of a specified size.  The body you get back will be purely random,
and the content type header will always be application/octet-stream. In typical
usage you discard the body (because it doesn't matter) but read the timing in
the output from curl.

    $ curl -v http://example.com/file?size=1000000 -o /dev/null


Send a file. The body you send can be anything you like, and you should set
the header to be "application/octet-stream."  On the server side the body is
discarded so again it doesn't really matter what bytes you send.

    $ curl -X POST --data-binary @source-file \
           -H "Content-Type: application/octet-stream" \
           http://example.com/file

Benchmark another site.  In this case the *remote* server invokes ApacheBench
and points it at the server and port you specify.

    $ curl http://example.com/benchcurl?size=1024&count=10&threads=5&server=remotesite.com&port=8000


