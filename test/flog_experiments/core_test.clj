(ns flog-experiments.core-test
  (:require [clojure.test :refer :all]
            [flog-experiments.core :refer :all]
            [clj-http.client :as http]
            [flog-experiments.wiremock :as wm]
            ))

(defn wiremock [f]
  (let [s (wm/server)]
    (wm/start s)
    (f)
    (wm/stop s)))

(use-fixtures :each wiremock)

(deftest mock-content
  (wm/stub { :request { :method "GET" :url "/hello"}
            :response { :status 200 :body "Hello World"}})
  (is (= 200 (:status (http/get "http://localhost:8080/hello"))))
  (is (= "Hello World" (:body (http/get "http://localhost:8080/hello")))))
