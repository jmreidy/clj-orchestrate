(ns clj-orchestrate.core
  (:import (io.orchestrate.client OrchestrateClient KvObject KvList ResponseAdapter ResponseListener KvMetadata))
  (:require [clojure.walk :refer [keywordize-keys stringify-keys]]
            [clojure.core.async :refer [put! chan]]))


(defn- kv->map
  "Convert a KV Object to a hashmap"
  [^KvObject obj]
  (keywordize-keys (into {} (.getValue obj))))

(defn- kv->vec
  "Convert a list of KV Objects to a lazy-seq of hashmaps"
  [^KvList list]
  (map kv->map (.getResults list)))

(defn- kv->meta
  "Convert a kv metadata object to a map"
  [^KvMetadata meta]
  {:collection (.getCollection meta) :key (.getKey meta) :ref (.getRef meta)})

(defmulti get-results class)
(defmethod get-results KvList [kv-results] (kv->vec kv-results))
(defmethod get-results KvObject [kv-result] (kv->map kv-result))
(defmethod get-results KvMetadata [kv-meta] (kv->meta kv-meta))

(defn- make-listener
  ([succ-chan]
    (reify
      ResponseListener
      (onSuccess [this res] (put! succ-chan res))))
  ([succ-chan err-chan]
    (reify
      ResponseListener
      (onSuccess [this res] (put! succ-chan res))
      (onFailure [this err] (put! err-chan err)))))

(defn- kv-req
  [query succ-chan err-chan]
  (let [handler (make-listener succ-chan err-chan)]
    (-> query (.get java.util.HashMap) (.on handler))))


(defn new-client
  "Instantiate a new Orchestrate client"
  [api-key]
  (OrchestrateClient. api-key))

(defn stop-client
  "Stop a running Orchestrate client"
  [client]
  (.stop client))

(defn kv-fetch
  "Fetch a kv element using a collection name and key"
  ([client collection {:keys [key ref succ-chan err-chan]}]
    (let [handler (make-listener succ-chan err-chan)]
      (-> client
          (.kv collection key)
          (.get java.util.HashMap (if ref ref))
          (.on handler))))
  ([client collection key chan]
    (kv-fetch client collection {:key key :succ-chan chan}))
  ([client collection key succ-chan err-chan]
    (kv-fetch client collection {:key key :succ-chan succ-chan :err-chan err-chan})))

(defn kv-list
  "Fetch all kv elements for a given collection name"
  ([client collection options]
    (let [{:keys [limit values? succ-chan err-chan]
           :or {limit 10 values? true}} options]
      (kv-req (-> client
                  (.listCollection collection)
                  (.limit limit)
                  (.withValues values?))
              succ-chan err-chan))))

(defn kv-put
  "Put or update data in a collection for a provided key"
  [client collection {:keys [key value succ-chan err-chan]}]
  (let [handler (make-listener succ-chan err-chan)
        value (stringify-keys value)]
    (-> client (.kv collection key) (.put value) (.on handler))))

(defn kv-delete
  "Delete an item from a collection at the provided key"
  ([client collection key & chans]
    (let [succ-chan (first chans)
          err-chan (next chans)
          handler (make-listener succ-chan err-chan)]
      (-> client (.kv collection key) (.delete)))))