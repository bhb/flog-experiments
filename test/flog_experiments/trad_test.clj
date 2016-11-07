(ns flog-experiments.trad-test
  (:require [clojure.test :refer :all]
            [flog-experiments.servers :as servers]
            [flog-experiments.trad :as trad]))

(deftest no-errors-server
  (is (= ["/img/clojure.jpg"
          "/img/logo.jpg"
          "/img/mainpage.jpg"
          "/img/me.jpg"
          "/img/rock-climbing.jpg"]
         (servers/with-server servers/no-errors
           #(trad/crawled-images "http://localhost:8080" "/index.html")))))

(deftest errors-server
  (is (= ["/img/logo.jpg"
          "/img/mainpage.jpg"
          "/img/me.jpg"]
         (servers/with-server servers/errors
           #(trad/crawled-images-error-handling "http://localhost:8080" "/index.html")))))
