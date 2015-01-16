(ns clj-orchestrate.graph
  (:require [clj-orchestrate.core :as core :refer [make-listener]]))

(defn link 
  [client relation source target & chans]
  (-> client
      (.relation (:collection source) (:key source))
      (.to (:collection target) (:key target))
      (.put relation)
      (.on (make-listener (first chans) (next chans)))))

(defn get-links
  [client relations source & chans]
  (let [relations (if-not (vector? relations) (vector relations) relations)]
    (-> client
        (.relation (:collection source) (:key source))
        (.get java.util.HashMap (into-array String relations))
        (.on (make-listener (first chans) (next chans)))
        )))

(defn delete
  [client relation source target & chans]
  (-> client
      (.relation (:collection source) (:key source))
      (.to (:collection target) (:key target))
      (.purge relation)
      (.on (make-listener (first chans) (next chans)))))
