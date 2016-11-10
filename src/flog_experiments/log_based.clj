(ns flog-experiments.log-based
  (:require
    [clj-http.client :as http]
    [clojure.core.match :as m]
    [clojure.string :as string]
    [flog-experiments.flog :as fl]
    [hickory.core :as h]
    [hickory.select :as h.select]
    [flog-experiments.servers :as servers]))

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
      {:status :timeout :body ""})))

(defn do-http [log]
  (->>
   (m/match [(last log)]
            [[:command {:name :http/get
                        :data {:url url}}]]
            [[:event {:name :http/get
                      :data (merge {:url url
                                    }
                                   (http/get url))}]]

            :else [])
   (into log)))

(defn crawled-images [log domain first-url]
  (let [{:keys [seen-urls remaining-urls images]} (fl/state log)
        [u & urls] remaining-urls]
    (->>
     (m/match [(last log)]
              [nil]
              [[:state {:seen-urls #{}
                        :remaining-urls [(full-url domain first-url)]
                        :images []}]]

              [[:event {:name :http/get
                        :data {:url url :body body}}]]
              [[:state {:seen-urls (conj seen-urls url)
                        :remaining-urls (vec (remove (conj seen-urls url) (into remaining-urls (internal-urls domain body))))
                        :images (distinct (into images (internal-images body)))}]]

              [[:state {}]]
              (if u
                [[:command {:name :http/get
                            :data {:url u}}]]
                [[:command {:name :fx/return
                            :data {:fx/value (sort images)}}]]))
     (into log))))

(defn crawled-images!
  "Crawls URL and returns a list of images found within that domain."
  [domain url]
  (fl/run crawled-images do-http domain url))

(defn do-http-error-handling [log]
  (let [old-state (fl/state log)]
    (->>
     (m/match [(last log)]
              [[:command {:name :http/get
                          :data {:url url}}]]
              (loop [tries 10
                     log-entries []]
                (let [url->errs (get (fl/state log-entries) :url->errs {})
                      errs (get url->errs url [])
                      {:keys [body status] :as response} (select-keys (safe-get url) [:body :status])]
                  (if (success? status)
                    (into log-entries [[:event {:name :http/get
                                                :data (merge {:url url} response)}]])
                    (if (and (temporary-error? status)
                             (< (count errs) 3)
                             (pos? tries))
                      (recur
                       (dec tries)
                       (into log-entries [[:state (merge old-state {:url->errs (assoc url->errs url (conj errs status))})]
                                          [:event {:name :http/get
                                                   :data (merge {:url url} response)}]]))
                      (into log-entries [[:event {:name :http/get
                                                  :data (merge {:url url} response)}]])
                      ))))
              :else [])
     (into log))))

(defn crawled-images-error-handling [log domain first-url]
  (let [st (fl/state log)
        {:keys [seen-urls remaining-urls images]
         :or {seen-urls #{}}} st
        [u & urls] remaining-urls]
    (->>
     (m/match [(last log)]
              [nil]
              [[:state (merge st
                              {:seen-urls #{}
                               :remaining-urls [(full-url domain first-url)]
                               :images []})]]

              ;; new stuff ;;
              [[:event {:name :http/get
                        :data {:url url :status status :body body}}]]
              (cond
                (success? status)
                [[:state (merge st
                                {:seen-urls (conj seen-urls url)
                                 :remaining-urls (vec (remove (conj seen-urls url)
                                                              (into remaining-urls
                                                                    (internal-urls domain body))))
                                 :images (distinct (into images (internal-images body)))})]]

                (or (temporary-error? status) (non-fatal-error? status))
                [[:state (merge st
                                {:seen-urls (conj seen-urls url)
                                 :remaining-urls (vec (remove (conj seen-urls url)
                                                              remaining-urls))
                                 :images images})]]

                :else
                [[:command {:name :fx/return
                            :data {:fx/value (sort images)}}]])
              
              ;;; end new stuff

              [[:state {}]]
              (if u
                [[:command {:name :http/get
                            :data {:url u}}]]
                [[:command {:name :fx/return
                            :data {:fx/value (sort images)}}]]))
     (into log))))

(defn crawled-images-error-handling!
  [domain url]
  (fl/run crawled-images-error-handling do-http-error-handling domain url))
