(defproject benchcurl "0.3.0-SNAPSHOT"
  :description "Simple RESTful test endpoint and load generator"
  :url "http://github.anim.dreamworks.com/nokamoto/benchcurl"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :dependencies [[org.clojure/clojure "1.8.0"]                 
                 [org.clojars.gonewest818/defcon "0.5.0-SNAPSHOT"]
                 [org.clojure/data.json "0.2.6"]
                 [compojure "1.6.0"]
                 [ring "1.6.0"]
                 [ring/ring-defaults "0.3.0"]
                 [ring.middleware.logger "0.5.0"]
                 [commons-io/commons-io "2.5"]
                 [log4j/log4j "1.2.17"
                   :exclusions [javax.main/mail
                                javax.jms/jms
                                com.sun.jdmk/jmxtools
                                com.sun.jmx/jmxri]]
                 [org.clojure/tools.logging "0.3.1"]]

  :main ^:skip-aot benchcurl.core

  :target-path "target/%s"

  :profiles {:uberjar {:aot :all}})
