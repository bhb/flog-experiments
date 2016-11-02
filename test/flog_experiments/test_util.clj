(ns flog-experiments.test-util
  (:require [flog-experiments.wiremock :as wm]))

(defn wiremock [f]
  (let [s (wm/server)]
    (wm/start s)
    (f)
    (wm/stop s)))
