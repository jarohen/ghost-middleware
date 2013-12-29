(ns ghost.middleware
  (:require [clojure.java.shell :refer [sh]]
            [clojure.java.io :as io]
            [ring.util.response :refer [response]]
            [clojure.string :as s])
  (:import [java.io File]))

(defn copy-to-temp-file [url]
  (doto (File/createTempFile "phantom-script" ".js")
    (->> (io/copy (slurp url)))
    (.deleteOnExit)))

(defonce phantom-script
  (let [script (io/resource "phantom-script.js")]
    (if (= "file" (.getProtocol script))
      (io/as-file script)
      (copy-to-temp-file script))))

(defn rebuild-query-params [query-params]
  (let [remaining-query-params (dissoc query-params
                                       "_escaped_fragment")]
    (when (seq remaining-query-params)
      (->> (for [[k v] remaining-query-params]
             (str k "=" v))
           (s/join "&")
           (str "?")))))

(defn rebuild-url [{:keys [scheme server-name server-port uri query-params]}]
  (format "%s://%s:%d%s%s#!%s"
          (name scheme)
          server-name
          server-port
          uri
          (or (rebuild-query-params query-params) "")
          (get query-params "_escaped_fragment")))

(defn phantom-req [req]
  (-> (sh "phantomjs" (.getPath phantom-script) (rebuild-url req))
      :out
      response))

(defn wrap-shebang [handler]
  (fn [req]
    (if-let [fragment (get-in req [:query-params "_escaped_fragment"])]
      (phantom-req req)
      (handler req))))
