(ns flog-experiments.trad
  (:require [flog-experiments.servers :as servers]
            [clj-http.client :as http]
            [hickory.core :as h]
            [clojure.string :as string]
            [hickory.select :as h.select]))

(defn full-url [domain path]
  (if (string/starts-with? path "/")
    (str domain path)
    path))

(defn internal-urls [domain body]
  (->> body
       h/parse
       h/as-hickory
       (h.select/select (h.select/tag :a))
       (map (comp (partial full-url domain) :href :attrs))))

;; Note you can already test whether only 
;; internal domains are selected here.
(defn internal-images [body]
  (->> body
       h/parse
       h/as-hickory
       (h.select/select (h.select/tag :img))
       (map (comp :src :attrs))))

(defn temporary-error? [x]
  (#{503 :timeout} x))

(defn non-fatal-error? [x]
  (#{404} x))

(defn success? [x]
  (#{200 201 204} x))

(defn safe-get [url]
  (try
    (http/get url {:throw-exceptions false
                   :socket-timeout 500
                   :conn-timeout 500})
    (catch java.net.SocketTimeoutException e
      {:status :timeout})))


;; public ;;;;

(defn crawled-images-error-handling
  "Crawls URL and returns a list of images found within that domain.
  
  Handles the following errors:
  * if response is 404, skips page
  * if response is 503, sleeps and tries again (3 times and then skips)
  * if response times out, sleeps and tries again (3 times and then skips)"
  [domain url]
  (loop [visited-urls #{}
         [u & remaining-urls :as urls] [(full-url domain url)]
         images []
         errors {}]
    (if (nil? u)
      (sort images)
      (let [response (safe-get u)
            {:keys [body status]} response]
        (cond
          (success? status)
          (let [new-imgs (internal-images body)
                new-urls (remove visited-urls (internal-urls domain body))]
            (recur (into visited-urls [u])
                   (into remaining-urls new-urls)
                   (distinct (into images new-imgs))
                   errors))
          
          (non-fatal-error? status)
          (recur (into visited-urls [u])
                 remaining-urls
                 images
                 errors)

          (temporary-error? status)
          (if (< (count (get errors u)) 2)
            (do
              ;; wait
              (Thread/sleep 50)
              (recur visited-urls
                     urls
                     images
                     (update errors u conj status)))
            (recur (into visited-urls [u])
                   remaining-urls
                   images
                   errors))
          
          :else
          (throw (ex-info "Server returned unrecoverable response" {:response response})))))))

(defn crawled-images
  "Crawls URL and returns a list of images found within that domain."
  [domain url]
  (loop [visited-urls #{}
         [u & remaining-urls] [(full-url domain url)]
         images []]
    (if u
      (let [body (:body (http/get u))
            new-imgs (internal-images body)
            new-urls (remove visited-urls (internal-urls domain body))]
        (recur (into visited-urls [u])
               (into remaining-urls new-urls)
               (distinct (into images new-imgs))))
      (sort images))))

(comment
  (servers/with-server
    servers/errors
    #(crawled-images-error-handling "http://localhost:8080" "/index.html")))
