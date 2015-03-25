(ns clj-orchestrate.test.helpers
  (:require [environ.core :refer [env]]
            [clj-orchestrate.core :as core]
            [clojure.core.async :as async]
            [clj-orchestrate.util :as util]))


(def client (core/new-client (:orch-key env)))
(def col "test")

(defn in?
  "true if seq contains elm"
  [seq elm]
  (some #(= elm %) (vec seq)))

(defn delete-collection [col sc]
  (let [ec (async/chan 1 (map #(throw %)))]
    (-> client
      (.deleteCollection col)
      (.on (util/make-listener sc ec))
      (.get))))

(def data {:foo "bar" :baz [1 2]})
