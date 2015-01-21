(ns clj-orchestrate.events
  (:import (io.orchestrate.client.jsonpatch JsonPatchOp JsonPatch))
  (:require [clj-orchestrate.util :as util :refer [make-listener maps->patch]]
            [clojure.walk :refer [stringify-keys]]
            [cheshire.core :as cheshire]))


(defn fetch
  "Fetch an individual event"
  ([client collection {:keys [key type timestamp ordinal succ-chan err-chan]}]
    (let [handler (make-listener succ-chan err-chan)]
      (-> client
          (.event collection key)
          (.type type)
          (.timestamp timestamp)
          (.ordinal ordinal)
          (.get java.util.HashMap)
          (.on handler)))))

(defn list
  "Fetch events belonging to a key in a specific collection of a specific type"
  ([client collection {:keys [key type start end succ-chan err-chan]}]
    (let [handler (make-listener succ-chan err-chan)]
      (-> client
          (.event collection key)
          (.type type)
          (#(if-not (nil? start) (.start % start) %))
          (#(if-not (nil? end) (.end % end) %))
          (.get java.util.HashMap)
          (.on handler))))
  
  ([client collection key type & chans]
    (list client collection {:key key :type type :succ-chan (first chans) :err-chan (second chans)})))

(defn create
  "Store an event to a key in a collection with a specific type"
  ([client collection key type payload & chans]
    (let [payload (stringify-keys payload)]
      (-> client
          (.event collection key)
          (.type type)
          (.create payload)
          (.on (make-listener (first chans) (second chans)))))))

(defn update
  "Update an event to a new version"
  ([client collection {:keys [key type timestamp ordinal value match-ref succ-chan err-chan]}]
    (-> client
        (.event collection key)
        (.type type)
        (.timestamp timestamp)
        (.ordinal ordinal)
        (#(if-not (nil? match-ref) (.ifMatch % match-ref) %))
        (.update (stringify-keys value))
        (.on (make-listener succ-chan err-chan)))))

(defn patch
  "Update an event to a new version"
  ([client collection {:keys [key type timestamp ordinal patch match-ref succ-chan err-chan]}]
    (let [patch (maps->patch patch)]
      (println patch)
      (-> client
          (.event collection key)
          (.type type)
          (.timestamp timestamp)
          (.ordinal ordinal)
          (#(if-not (nil? match-ref) (.ifMatch % match-ref) %))
          (.patch patch)
          (.on (make-listener succ-chan err-chan))))))

(defn delete
  "Delete a single event instance"
  ([client collection {:keys [key type timestamp ordinal match-ref succ-chan err-chan]}]
    (-> client
        (.event collection key)
        (.type type)
        (.timestamp timestamp)
        (.ordinal ordinal)
        (#(if-not (nil? match-ref) (.ifMatch % match-ref) %))
        (.purge)
        (.on (make-listener succ-chan err-chan)))))
