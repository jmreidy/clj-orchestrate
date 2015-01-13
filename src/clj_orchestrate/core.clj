(ns clj-orchestrate.core
  (:import (io.orchestrate.client OrchestrateClient KvObject KvList ResponseAdapter ResponseListener KvMetadata))
  (:require [clojure.walk :refer [keywordize-keys]]
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
  [chan]
  (reify
    ResponseListener
    (onSuccess [this res] (put! chan (get-results res)))
    (onFailure [this err] (put! chan err))
    ))

(defn- kv-req
  [query chan]
  (let [handler (make-listener chan)]
    (-> query (.get java.util.HashMap) (.on handler))
    chan))


(defn client
  "Instantiate a new Orchestrate client"
  [api-key]
  (OrchestrateClient. api-key))

(defn kv-fetch
  "Fetch a kv element using a collection name and key"
  ([client collection key] (kv-fetch client collection key (chan)))
  ([client collection key chan] (kv-req (-> client (.kv collection key)) chan)))

(defn kv-list
  "Fetch all kv elements for a given collection name"
  ([client collection] (kv-list client collection (chan)))
  ([client collection chan] (kv-req (-> client (.listCollection collection)) chan)))

(defn kv-put
  "Put or update data in a collection for a provided key"
  [client collection key value]
  (let [chan (chan)
        handler (make-listener chan)]
    (-> client (.kv collection key) (.put value) (.on handler))
    chan))

(defn kv-delete
  "Delete an item from a collection at the provided key"
  [client collection key]
  (let [chan (chan)
        handler (make-listener chan)]
    (-> client (.kv collection key) (.delete))))