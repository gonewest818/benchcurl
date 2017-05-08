(ns benchcurl.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [ring.util.response :refer [content-type header response status]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.java.shell :refer [sh]]
            [defcon.core])
  (:import [org.apache.commons.io IOUtils]
           [org.apache.commons.io.input BoundedInputStream])
  (:gen-class))


;;;
;;; Configuration options
;;; 

(def config
  (defcon.core/init
    [
     {:name :benchcurl-port         :type :integer :default 8000}
     {:name :benchcurl-max-threads  :type :integer :default 8}
     {:name :benchcurl-ssl-port     :type :integer :default 443}
     {:name :benchcurl-keystore     :type :string  :default "benchcurl.jks"} ; path?
     {:name :benchcurl-key-password :type :string  :default nil}
     ]))


;;;
;;; Apache Bench load generation
;;; 

(defn ab-extract-value
  ([r v]
   (ab-extract-value r v ""))
  ([r v sep]
   (let [key (keyword (-> v
                          (clojure.string/replace " " "-")
                          (clojure.string/lower-case)))
         pattern (re-pattern (str "(?m)^\\s*" v sep "\\s+(\\S+)"))
         value (second (re-find pattern r))]
     {key value})))

(defn ab-extract-percentiles
  [r]
  (if (re-find #"Percentage of the requests" r)
    (into {}
          (map #(ab-extract-value r %)
               ["50%" "66%" "75%" "80%" "90%" "95%" "98%" "99%" "100%"]))))

(defn ab-parse-results
  [r]
  (let [ab-version (second (re-find #"ApacheBench, Version ([\d\.]+) " r))
        percentiles (ab-extract-percentiles r)]
    (into {:ab-version ab-version :percentiles percentiles}
          (map #(ab-extract-value r % ":")
               ["Server Software"
                "Server Hostname"
                "Server Port"
                "Document Path"
                "Document Length"
                "Concurrency Level"
                "Time taken for tests"
                "Complete requests"
                "Failed requests"
                "Non-2xx responses"
                "Total transferred"
                "HTML transferred"
                "Requests per second"
                "Time per request"
                "Transfer rate"]))))

(defn remote-benchmark
  "Benchmark uses Apache Bench in a subprocess to generate remote load
      # ab -c <threads> -n <count> <url>/file?size=<size>
  and returns the dictionary that clojure.java.shell/sh produces"
  [server port count size threads]
  (let [remote-url (format "http://%s:%s/file?size=%d" server port size)
        result (sh "ab"
                   "-c" (str threads)
                   "-n" (str count)
                   remote-url)]
    ;; if successful, parse output for readability in the json response
    (if (zero? (:exit result))
      (into result {:parsed (ab-parse-results (:out result))})
      result)))

;;;
;;; Route Management
;;;

(defroutes bc-routes
  "Benchcurl's supported routes are:
       GET /file?size=<#bytes>
       PUT /file
       GET /benchcurl?server=example.com&port=8000&count=100&size=100&threads=4
   ...anything else yields a 404"

  (GET "/file" [size]
    (let [size (or (and size (Integer/parseInt size)) 1024)
          stream (-> (io/input-stream "/dev/urandom")
                     (BoundedInputStream. size))]
      (log/info "generating random octets with size=" size)
      (log/debug "input-stream=" stream)
      (-> (response stream)
          (content-type "application/octet-stream")
          (header "x-benchcurl-meta" (str "size=" size)))))

  (POST "/file" {body :body}
    (let [size (count (IOUtils/toByteArray body))]
      (log/info (str "received file with size: " size))
      (-> (response (str "\"created\" file with size:" size))
          (status 201))))

  (GET "/benchcurl" [server port count size threads]
    (log/info (str "Benchmarking remote site " server ":" port
                   " with count: " count
                   " size: " size
                   " and threads: " threads))
    (let [server  (or server "localhost")
          port    (or port "8000")
          count   (or (and count (Integer/parseInt count)) 1)
          size    (or (and size (Integer/parseInt size)) 1024)
          threads (or (and threads (Integer/parseInt threads)) 1)
          results (remote-benchmark server port count size threads)]
      (-> (response (json/write-str results))
          (content-type "application/json")
          (header "x-benchcurl-meta"
                  (format "url=%s:%s:count=%d:size=%d:threads=%d"
                          server port count size threads)))))

  (route/not-found "Not found"))

(def app
  ^{:doc "Add ring middleware for some standard functionality"}
  (-> bc-routes
      (wrap-defaults api-defaults)
      (wrap-with-logger)))


;;;
;;; Server Configuration and Launch
;;; 

(def http
  ^{:doc "Handle to the Jetty server so it can be stopped later."}
  (atom nil))

(defn start-jetty!
  "Start the jetty server with/without blocking the calling thread"
  ([]
   (start-jetty! false))
  ([blocking]
   (let [jetty-config {:port         (:benchcurl-port config)
                       :ssl?         true
                       :ssl-port     (:benchcurl-ssl-port config)
                       :keystore     (:benchcurl-keystore config)
                       :key-password (:benchcurl-key-password config)
                       :max-threads  (:benchcurl-max-threads config)
                       :min-threads  4
                       :join?        blocking}]
     (log/info "jetty config = " (assoc jetty-config :key-password "<elided>"))
     (reset! http (jetty/run-jetty app jetty-config)))))

(defn stop-jetty!
  "Stop the running jetty server"
  []
  (.stop @http)
  (reset! http nil))

(defn -main
  "Launch the benchcurl services and blocks"
  [& args]
  (start-jetty! true))


