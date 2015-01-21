(ns clj-orchestrate.graph
  (:require [clj-orchestrate.util :as util :refer [make-listener]]))

(defn link 
  [client relation source target & chans]
  (-> client
      (.relation (:collection source) (:key source))
      (.to (:collection target) (:key target))
      (.put relation)
      (.on (make-listener (first chans) (second chans)))))

(defn get-links
  [client relations source & chans]
  (let [relations (if-not (vector? relations) (vector relations) relations)]
    (-> client
        (.relation (:collection source) (:key source))
        (.get java.util.HashMap (into-array String relations))
        (.on (make-listener (first chans) (second chans)))
        )))

(defn delete
  [client relation source target & chans]
  (-> client
      (.relation (:collection source) (:key source))
      (.to (:collection target) (:key target))
      (.purge relation)
      (.on (make-listener (first chans) (second chans)))))
