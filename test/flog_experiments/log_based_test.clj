(ns flog-experiments.log-based-test
  (:require [clojure.test :refer :all]
            [flog-experiments.servers :as servers]
            [flog-experiments.flog :as fl]
            [flog-experiments.log-based :refer :all]))

(deftest crawled-images-test
  (is (= ["/img/clojure.jpg"
          "/img/logo.jpg"
          "/img/mainpage.jpg"
          "/img/me.jpg"
          "/img/rock-climbing.jpg"]
         (let [f (fl/trial-run [] crawled-images "http://localhost:8000" "/index.html")]
           (-> []
               f
               f
               (conj [:event {:name :http/get :data {:body servers/index-page
                                                     :status 200
                                                     :url "http://localhost:8000/index.html"}}])
               f
               (conj [:event {:name :http/get :data {:body servers/about-page
                                                     :status 200
                                                     :url "http://localhost:8000/about.html"}}])
               f
               (conj [:event {:name :http/get :data {:body servers/interests-page
                                                     :status 200
                                                     :url "http://localhost:8000/interests.html"}}])
               f
               f)))))

(deftest no-errors-server
  (is (= ["/img/clojure.jpg"
          "/img/logo.jpg"
          "/img/mainpage.jpg"
          "/img/me.jpg"
          "/img/rock-climbing.jpg"]
         (servers/with-server servers/no-errors
           #(crawled-images! "http://localhost:8080" "/index.html")))))

(deftest errors-server
  (is (= ["/img/logo.jpg"
          "/img/mainpage.jpg"
          "/img/me.jpg"]
         (servers/with-server servers/errors
           #(crawled-images-error-handling! "http://localhost:8080" "/index.html")))))

;; TODO - add timeouts








