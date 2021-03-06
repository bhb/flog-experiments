(ns flog-experiments.servers
    (:require [flog-experiments.wiremock :as wm]
              [hiccup.page :as h.page]
              [hiccup.element :as h.el]))

(def index-page
  (h.page/html5
   [:body
    [:h1
     "My page"
     ]
    (h.el/image "/img/logo.jpg")
    (h.el/image "/img/mainpage.jpg")
    (h.el/unordered-list
     [(h.el/link-to "/index.html" "Home")
      (h.el/link-to "/about.html" "About")
      (h.el/link-to "/interests.html" "Interests")])]))

(def about-page
  (h.page/html5
   [:body
    [:h1
     "About"]
        (h.el/image "/img/logo.jpg")
    (h.el/image "/img/me.jpg")
    (h.el/unordered-list
     [(h.el/link-to "/index.html" "Home")
      (h.el/link-to "/interests.html" "Interests")])]))

(def interests-page
  (h.page/html5
   [:body
    [:h1
     "Interests"]
    (h.el/image "/img/logo.jpg")
    (h.el/image "/img/clojure.jpg")
    (h.el/image "/img/rock-climbing.jpg")
    (h.el/unordered-list
     [(h.el/link-to "/index.html" "Home")
      (h.el/link-to "/about.html" "About")])]))

(defn no-errors []
  (doseq [sc [{:request {:method "GET" :url "/index.html"}
               :response {:status 200 :body index-page}}
              {:request {:method "GET" :url "/about.html"}
               :response {:status 200 :body about-page}}
              {:request {:method "GET" :url "/interests.html"}
               :response {:status 200 :body interests-page}}
              ]]
    (wm/stub sc)))

(defn errors []
  (doseq [sc [{:scenarioName "Server overloaded"
                :requiredScenarioState "Started"
                :newScenarioState "Some overloaded"
                :request {:method "GET" :url "/index.html"}
               :response {:status 503}}
              {:scenarioName "Server overloaded"
                :requiredScenarioState "Started"
                :request {:method "GET" :url "/about.html"}
               :response {:status 503}}
              {:scenarioName "Server overloaded"
                :requiredScenarioState "Started"
                :request {:method "GET" :url "/interests.html"}
               :response {:status 503}}
              ;;;;;;;;;;;;;;;;;;;
              {:scenarioName "Server overloaded"
               :requiredScenarioState "Some overloaded"
               :newScenarioState "All OK"
               :request {:method "GET" :url "/index.html"}
               :response {:status 200 :body index-page}}
              {:scenarioName "Server overloaded"
               :requiredScenarioState "Some overloaded"
               :newScenarioState "All OK"
               :request {:method "GET" :url "/about.html"}
               :response {:status 503}}
              {:scenarioName "Server overloaded"
               :requiredScenarioState "Some overloaded"
               :newScenarioState "All OK"
               :request {:method "GET" :url "/interests.html"}
               :response {:status 503}}
              ;;;;;;;;;;;;;;;;;;;
              {:scenarioName "Server overloaded"
               :requiredScenarioState "All OK"
               :request {:method "GET" :url "/index.html"}
               :response {:status 200 :body index-page}}
              {:scenarioName "Server overloaded"
               :requiredScenarioState "All OK"
               :request {:method "GET" :url "/about.html"}
               :response {:status 200 :body about-page}}
              {:scenarioName "Server overloaded"
               :requiredScenarioState "All OK"
               :request {:method "GET" :url "/interests.html"}
               :response {:status 404}}]]
    (wm/stub sc)))

(defn timeouts []
  (doseq [sc [{:request {:method "GET" :url "/index.html"}
               :response {:status 200
                          :body index-page
                          :fixedDelayMilliseconds 20}}
              {:request {:method "GET" :url "/about.html"}
               :response {:status 200
                          :body about-page
                          :fixedDelayMilliseconds 200}}
              {:request {:method "GET" :url "/interests.html"}
               :response {:status 200
                          :body interests-page
                          :fixedDelayMilliseconds 600}}]]
    (wm/stub sc)))

(defn with-server [setup f]
  (let [s (wm/server)]
    (wm/stop s)
    (wm/start s)
    (try
      (setup)
      (f)
      (finally
        (wm/stop s)))))
