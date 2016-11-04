(ns flog-experiments.wiremock-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as http]
            [flog-experiments.wiremock :as wm]
            [flog-experiments.test-util :as test-util]))

(use-fixtures :each test-util/wiremock)

(deftest mock-content
  (wm/stub {:request { :method "GET" :url "/hello"}
            :response { :status 200 :body "Hello World"}})
  (is (= 200 (:status (http/get "http://localhost:8080/hello"))))
  (is (= "Hello World" (:body (http/get "http://localhost:8080/hello")))))

(deftest mock-timeout
  (wm/stub {:request { :method "GET" :url "/hello"}
            :response { :status 200 :body "Hello World"
                       :fixedDelayMilliseconds 500}})
  (is (<= 500 (->> (http/get "http://localhost:8080/hello")
                  time
                  with-out-str
                  (re-find #"(\d+).")
                  last
                  Integer.))))
