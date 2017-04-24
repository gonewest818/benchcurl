(ns benchcurl.core
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.middleware.multipart-params :refer [wrap-multipart-params]]
            [ring.middleware.multipart-params.byte-array :refer [byte-array-store]]
            [ring.middleware.logger :refer [wrap-with-logger]]
            [ring.util.io :refer [piped-input-stream]]
            [ring.util.response :refer [content-type header response status]]
            [clojure.tools.logging :as log]
            [clojure.data.json :as json]
            [clojure.java.shell :refer [sh]])
  (:gen-class))


;;;
;;; Stream generation
;;; 

(defn rand-input-stream
  "Return a buffered input stream that produces exactly n bytes
  read from /dev/random, i.e. a fixed-length random stream"
  [n]
  (let [buffer-size 4096]
   (piped-input-stream
    (fn [ostream]
      (with-open [devrandom (clojure.java.io/input-stream "/dev/random")] 
        (if (> n 0)
          (let [rba (byte-array buffer-size)]
            (loop [remainder n]
              (.read devrandom rba)
              (if (<= remainder buffer-size)
                ;; flush after last chunk
                (do (.write ostream rba 0 remainder)
                    (.flush ostream))
                ;; else keep looping
                (do
                  (.write ostream rba)
                  (recur (- remainder buffer-size))))))))))))

;;;
;;; Load generation
;;; 

(defn remote-benchmark
  "Benchmark uses Apache Bench in a subprocess to generate remote load,
  i.e. literally
  
      # ab -c <threads> -n <count> <url>/file?size=<size>

  and returns the dictionary that clojure.java.shell/sh produces"
  [url count size threads]
  (let [remote-url (format "%s/file?size=%d" url size)]
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

       GET /benchcurl?url=example.com&count=100&size=100&threads=4

   ...anything else yields a 404"

  (GET "/file" [size]
    (log/info "generating random octets with size=" size)
    (-> (response (rand-input-stream size))
        (content-type "application/octet-stream")
        (header "x-benchcurl-meta" (str "size=" size))))

  (PUT "/file" [name :as req]
    (response (str "PUT requested with name: " name)))

  (GET "/benchcurl" [url count size threads :as req]
    (log/info (str "Benchmarking remote site " url
                   " with count: " count
                   " size: " size
                   " and threads: " threads))
    (let [results (remote-benchmark url count size threads)]
      (-> (response (json/write-str results))
          (content-type "application/json")
          (header "x-benchcurl-meta"
                  (format "url=%s:count=%d:size=%d:threads=%d"
                          url count size threads)))))
  (route/not-found "Not found"))

(defn app
  "Wrap the Compojure routes with middleware"
  []
  (-> bc-routes
      (wrap-defaults api-defaults)
      (wrap-multipart-params {:store (byte-array-store)})
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
     (reset! http (jetty/run-jetty (app) jetty-config)))))

(defn stop-jetty!
  "Stop the running jetty server"
  []
  (.stop @http)
  (reset! http nil))

(defn -main
  "Launch the benchcurl services and blocks"
  [& args]
  (start-jetty! true))


