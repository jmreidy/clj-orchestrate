(ns clj-orchestrate.core
  (:import (io.orchestrate.client.jsonpatch JsonPatchOp JsonPatch)
           (io.orchestrate.client KvObject KvList ResponseListener KvMetadata OrchestrateClient RelationList EventList Event EventMetadata))
  (:require [clojure.walk :refer [keywordize-keys stringify-keys]]
            [clojure.core.async :refer [put! chan]]))


(defn new-client
  "Instantiate a new Orchestrate client"
  [api-key]
  (OrchestrateClient. api-key))

(defn stop-client
  "Stop a running Orchestrate client"
  [client]
  (.close client))

(defn- kv->map
  "Convert a KV Object to a hashmap"
  [^KvObject obj]
  (keywordize-keys (into {} (.getValue obj))))

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
  (keywordize-keys (into {} (.getValue event))))

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
  
  

(defmulti get-results-with-meta class)

(defmethod get-results-with-meta KvList [kv-results]
  (map (fn [r] {:data (kv->map r) :meta (kv->meta r)})
       (.getResults kv-results)))

(defmethod get-results-with-meta KvObject [kv-result]
  {:data (kv->map kv-result) :meta (kv->meta kv-result)})

(defmethod get-results-with-meta KvMetadata [kv-meta]
  {:data nil :meta (kv->meta kv-meta)})

(defmethod get-results-with-meta RelationList [relations]
  (map (fn [r] {:data (kv->map r) :meta (kv->meta r)})
       (.getRelatedObjects relations)))

(defmethod get-results-with-meta Boolean [success?]
  {:data success? :meta nil})

(defmethod get-results-with-meta Event [^Event event]
  {:data (get-results event) :meta (ev->meta event)})

(defmethod get-results-with-meta EventList [^EventList events]
  (map (fn [e] {:data (get-results e) :meta (ev->meta e)})
       (.getEvents events)))

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
