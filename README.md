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

Benchcurl is configured using "defcon" which means options can be supplied
either with environment variables or in a Java Properties file, whichever
suits your needs. Any missing variables are filled in with defaults. Defcon
tries to minimze surprises, and therefore any missing variables having no
default value will cause an exception to be thrown at startup time.

The configurables are:

| Name                   | Type    | Default         | Description |
| ---------------------- | ------- | --------------- | ----------- |
| BENCHCURL_PORT         | integer | 8000            | for unencrypted web traffic |
| BENCHCURL_MAX_THREADS  | integer | 8               | this config goes directly to Jetty |
| BENCHCURL_SSL_PORT     | integer | 443             | for encrypted web traffic |
| BENCHCURL_KEYSTORE     | string  | "benchcurl.jks" | local path to the keystore |
| BENCHCURL_KEY_PASSWORD | string  | null            | no default provided, must configure |


## TLS encryption

In your environment you may already be handlng TLS in nginx, but
Jetty supports TLS encryption with self-signed certs and we want to keep
the deployment of benchcurl as simple as we can, so we're doing
TLS directly in the app.

You're going to need the Java 'keytool' for this part. Here's the command
line to generate a self-signed cert valid for 90 days: 

    $ keytool -genkey -keyalg RSA -alias selfsigned -keystore benchcurl.jks -validity 90 -keysize 2048
    Enter keystore password:  
    Re-enter new password: 
    What is your first and last name?
      [Unknown]:  Administrator
    What is the name of your organizational unit?
      [Unknown]:  Site Operations
    What is the name of your organization?
      [Unknown]:  DWA Nova, LLC
    What is the name of your City or Locality?
      [Unknown]:  Burbank
    What is the name of your State or Province?
      [Unknown]:  CA
    What is the two-letter country code for this unit?
      [Unknown]:  US
    Is CN=Administrator, OU=Site Operations, O="DWA Nova, LLC", L=Burbank, ST=CA, C=US correct?
      [no]:  yes

    Enter key password for <selfsigned>
        (RETURN if same as keystore password):  


Here we've used "benchcurl.jks" as the keystore name but you can call it what you
like as long as you supply the BENCHCURL_KEYSTORE as described in the options
section above. Likewise, keep a record of the password you supply to keytool,
because you're required to pass that in as the BENCHCURL_KEY_PASSWORD as well.


## Examples

Retrieve a file of a specified size.  The body you get back will be purely random,
and the content type header will always be application/octet-stream. In typical
usage you discard the body (because it doesn't matter) but read the timing in
the output from curl.

    $ curl -v https://example.com/file?size=1000000 -o /dev/null


Send a file. The body you send can be anything you like, and you should set
the header to be "application/octet-stream."  On the server side the body is
discarded so again it doesn't really matter what bytes you send.

    $ curl -X POST --data-binary @source-file \
           -H "Content-Type: application/octet-stream" \
           https://example.com/file

Benchmark another site.  In this case the *remote* server invokes ApacheBench
and points it at the server and port you specify.

    $ curl https://example.com/benchcurl?size=1024&count=10&threads=5&server=remotesite.com&port=8000


