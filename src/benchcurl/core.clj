(ns benchcurl.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.logger :refer [wrap-with-logger]]
;            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
;            [ring.middleware.multipart-params.byte-array :refer [byte-array-store]]
            [ring.util.response :refer [content-type header response status]]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.java.shell :refer [sh]])
  (:import [org.apache.commons.io.input BoundedInputStream])
  (:gen-class))


;;;
;;; Load generation
;;; 

(defn remote-benchmark
  "Benchmark uses Apache Bench in a subprocess to generate remote load
      # ab -c <threads> -n <count> <url>/file?size=<size>
  and returns the dictionary that clojure.java.shell/sh produces"
  [server port count size threads]
  (let [remote-url (format "http://%s:%s/file?size=%d" server port size)]
    (sh "ab"
        "-c" (str threads)
        "-n" (str count)
        remote-url)))

;;;
;;; Route Management
;;;

(defroutes bc-routes
  "Benchcurl's supported routes are:
       GET /file?size=<#bytes>
       PUT /file?name=<string>
       GET /benchcurl?server=example.com&port=8000&count=100&size=100&threads=4
   ...anything else yields a 404"

  (GET "/file" [size]
    (let [size (Integer/parseInt size)
          stream (-> (io/input-stream "/dev/random")
                     (BoundedInputStream. size))]
      (log/info "generating random octets with size=" size)
      (log/debug "input-stream=" stream)
      (-> (response stream)
          (content-type "application/octet-stream")
          (header "x-benchcurl-meta" (str "size=" size)))))

  (PUT "/file" [name :as req]
    (response (str "PUT requested with name: " name)))

  (GET "/benchcurl" [server port count size threads]
    (log/info (str "Benchmarking remote site " server ":" port
                   " with count: " count
                   " size: " size
                   " and threads: " threads))
    (let [count (Integer/parseInt count)
          size (Integer/parseInt size)
          threads (Integer/parseInt threads)
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
      ;; (wrap-multipart-params {:store (byte-array-store)})
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
   (let [jetty-config {:port 8000
                       :max-threads 8
                       :min-threads 4
                       :join? blocking}]
     (log/info "jetty config = " jetty-config)
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


