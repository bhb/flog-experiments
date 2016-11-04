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

;; public ;;;;

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
        (recur (into visited-urls new-urls)
               (into remaining-urls new-urls)
               (distinct (into images new-imgs))))
      (sort images))))

(defn temporary-error? [x]
  (#{503} x))

(defn success? [x]
  (#{200 201 204} x))

(defn crawled-images-error-handling
  "Crawls URL and returns a list of images found within that domain."
  [domain url]
  (loop [visited-urls #{}
         [u & remaining-urls :as urls] [(full-url domain url)]
         images []
         errors {}]
    (if (or (nil? u)
            (< 3 (count (get errors u))))
      (sort images)
      (let [response (http/get u {:throw-exceptions false})
            status (:status response)]
        (if-not (success? status)
          (if (temporary-error? status)
            (do
              ;; wait
              (Thread/sleep 50)
              (recur visited-urls
                     urls
                     images
                     (update errors u conj status)))
            (throw (ex-info "Server returned unrecoverable response" {:response response})))
          (let [body (:body response)
                new-imgs (internal-images body)
                new-urls (remove visited-urls (internal-urls domain body))]
            (recur (into visited-urls new-urls)
                   (into remaining-urls new-urls)
                   (distinct (into images new-imgs))
                   errors)))))))

(comment
  (servers/with-server
    servers/errors
    #(crawled-images-error-handling "http://localhost:8080" "/index.html"))
  )




(comment
  

  )
