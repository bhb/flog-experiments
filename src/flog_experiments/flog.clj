(ns flog-experiments.flog
  (:require [clojure.spec :as s]
            [clojure.core.match :as m]))

(s/def :fx/id uuid?)
(s/def :fx/parent :fx/id)
(s/def :fx/name keyword?)
(s/def :fx/data (s/map-of keyword? any?))
(s/def :fx/ts pos-int?)

(defmulti entry-spec first)

(defmethod entry-spec :command [_]
  (s/cat
   :type #{:command}
   :opts (s/keys :req-un [:fx/name :fx/data]
                 :opt-un [:fx/id :fx/ts])))

(defmethod entry-spec :event [_]
  (s/cat
   :type #{:event}
   :opts (s/keys :req-un [:fx/name :fx/data]
                 :opt-un [:fx/id :fx/parent :fx/ts])))

(defmethod entry-spec :state [_]
  (s/cat
   :type #{:state}
   :state (s/map-of keyword? any?)))

(s/def :fx/entry (s/multi-spec entry-spec first))

(s/def :fx/log (s/coll-of :fx/entry :kind vector?))

(defn uuid []
  (java.util.UUID/randomUUID))

(defn subvec? [v1 v2]
  (= v1 (subvec v2 0 (count v1))))

(s/fdef execute
        :args (s/cat :commands-fn fn?
                     :execute-fn fn?
                     :params (s/* any?)))
(defn run [commands-fn events-fn & params]
  (loop [log []
         iterations 0]
    (if (< 100 iterations)
      (throw (ex-info "Too many iterations" {:log log}))
      (let [new-log (s/assert :fx/log (apply commands-fn log params))
            _ (assert (subvec? log new-log) "command-fn may not remove entries from log")
            _ (assert (not= log new-log) "command-fn must append new entries to log")
            last-entry (last new-log)]
        (m/match [last-entry]
                 [[:command {:name :fx/return
                             :data {:fx/value val}}]] val
                 :else (recur (s/assert :fx/log (events-fn new-log))
                              (inc iterations)))))))

(defn trial-run [log commands-fn & params]
  (fn [log]
    (let [new-log (apply commands-fn log params)]
      (assert (vector? log) "command-fn must return a vector")
      (assert (subvec? log new-log) "command-fn may not remove entries from log")
      (assert (not= log new-log) "command-fn must append new entries to log")
      (assert (s/valid? :fx/log new-log) (s/explain-str :fx/log new-log))
      (m/match [(last new-log)]
               [[:command {:name :fx/return
                           :data {:fx/value val}}]] val
               :else new-log))))

;; TODO - might clean things up to merge state from oldest to newest
;;        so implementations don't need to manually merge state
(defn state [log]
  (if-let [entry (->> log
                      (filter #(= (first %) :state))
                      last)]
    (second entry)
    {}))

(comment
  (s/explain
   :fx/log
   [[:command
     {:name :http/get, :data {:url "http://localhost:8080/index.html"}}]
    [:event {:name :http/get,
             :data {:url "http://localhost:8080/index.html",
                    :body
                    "<some html>",
                    :status 200}}]]
   [:command {:name :fx/return
              :data {:fx/value "<some html>"}}]))
