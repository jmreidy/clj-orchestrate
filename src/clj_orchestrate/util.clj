(ns clj-orchestrate.util
  (:import (io.orchestrate.client.jsonpatch JsonPatchOp JsonPatch)
           (io.orchestrate.client KvObject KvList ResponseListener
                                  KvMetadata RelationList
                                  EventList Event EventMetadata
                                  SearchResults Result))
  (:require [clojure.walk :refer [stringify-keys]]
            [clojure.core.async :refer [put! chan]]))


(defn- ->clj [obj]
  (let [c (class obj)
        match-class? #(isa? c %)]
    (cond
      (match-class? java.util.Map) (let [entries (.entrySet obj)]
                                     (reduce (fn [m [^String k v]]
                                               (assoc m (keyword k) (->clj v)))
                                             {} entries))

      (match-class? java.util.List) (vec (map ->clj obj))

      :else obj)))

(defn- kv->map
  "Convert a KV Object to a hashmap"
  [^KvObject obj]
  (->clj (.getValue obj)))

(defn- kv->meta
  "Convert a kv metadata object to a map"
  [^KvMetadata meta]
  {:collection (.getCollection meta) :key (.getKey meta) :ref (.getRef meta)})

(defn- ev->meta
  "Collect an event's metadata into a map"
  [^EventMetadata meta]
  {:collection (.getCollection meta)
   :key (.getKey meta)
   :ref (.getRef meta)
   :timestamp (.getTimestamp meta)
   :ordinal (.getOrdinal meta)})

(defn- ev->map
  "Convert an Event object to a map"
  [^Event event]
  (->clj (.getValue event)))


;Parse Results
(defmulti get-results class)

(defmethod get-results KvList [kv-results]
  (map kv->map (.getResults kv-results)))

(defmethod get-results KvObject [kv-result] (kv->map kv-result))

(defmethod get-results KvMetadata [kv-meta] (kv->meta kv-meta))

(defmethod get-results RelationList [^RelationList relations]
  (map kv->map (.getRelatedObjects relations)))

(defmethod get-results Event [^Event event]
  (ev->map event))

(defmethod get-results EventList [^EventList events]
  (map ev->map (.getEvents events)))

(defmethod get-results Result [^Result result]
  (kv->map (.getKvObject result)))

;Parse Metadata
(defmulti get-meta class)

(defmethod get-meta Event [^Event event]
  (ev->meta event))

(defmethod get-meta Result [^Result result]
  {:score (.getScore result)})

(defmethod get-meta KvObject [^KvObject kv]
  (kv->meta kv))



;Parse Results WITH Metadata
(defmulti get-results-with-meta class)

(defn- result-mapper [el] {:data (get-results el) :meta (get-meta el)})

(defmethod get-results-with-meta KvList [kv-results]
  (map result-mapper (.getResults kv-results)))

(defmethod get-results-with-meta KvObject [kv-result]
  (result-mapper kv-result))

(defmethod get-results-with-meta KvMetadata [kv-meta]
  {:data nil :meta (kv->meta kv-meta)})

(defmethod get-results-with-meta RelationList [relations]
  (map result-mapper (.getRelatedObjects relations)))

(defmethod get-results-with-meta Boolean [success?]
  {:data success? :meta nil})

(defmethod get-results-with-meta Event [^Event event]
  (result-mapper event))

(defmethod get-results-with-meta EventList [^EventList events]
  (map result-mapper (.getEvents events)))

(defmethod get-results-with-meta SearchResults [^SearchResults results]
  (hash-map :results (map result-mapper (.getResults results))))

(defn maps->patch
  [patch]
  (.build (reduce
            #(.op % (JsonPatchOp. (:op %2) (:path %2) (:value %2)))
            (. JsonPatch builder)
            patch)))

(defn make-listener
  ([succ-chan err-chan]
    (reify
      ResponseListener
      (onSuccess [this res] (if-not (nil? succ-chan) (put! succ-chan res)))
      (onFailure [this err] (if-not (nil? err-chan) (put! err-chan err))))))
