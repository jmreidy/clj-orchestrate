(ns clj-orchestrate.kv
  (:import (io.orchestrate.client.jsonpatch JsonPatchOp JsonPatch))
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

(defmulti get-results-with-meta class)

(defmethod get-results-with-meta KvList [kv-results]
  (map (fn [r] {:data (kv->map r) :meta (kv->meta r)})
       (.getResults kv-results)))

(defmethod get-results-with-meta KvObject [kv-result]
  {:data (kv->map kv-result) :meta (kv->meta kv-result)})

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


(defn fetch
  "Fetch a kv element using a collection name and key"
  ([client collection {:keys [key ref succ-chan err-chan]}]
    (let [handler (make-listener succ-chan err-chan)]
      (-> client
          (.kv collection key)
          (.get java.util.HashMap ref)
          (.on handler))))
  ([client collection key chan]
    (fetch client collection {:key key :succ-chan chan}))
  ([client collection key succ-chan err-chan]
    (fetch client collection {:key key :succ-chan succ-chan :err-chan err-chan})))

(defn list
  "Fetch all kv elements for a given collection name"
  ([client collection options]
    (if-not (map? options)
      (list client collection {:succ-chan options})
      (let [{:keys [limit values? succ-chan err-chan]
             :or {limit 10 values? true}} options
            handler (make-listener succ-chan err-chan)]
        (-> client
            (.listCollection collection)
            (.limit limit)
            (.withValues values?)
            (.get java.util.HashMap)
            (.on handler)))))
  ([client collection succ-chan err-chan]
    (list client collection {:succ-chan succ-chan :err-chan err-chan})))


(defn put
  "Put or update data in a collection for a provided key"
  [client collection {:keys [key value match-ref only-if-absent? succ-chan err-chan]}]
  (let [handler (make-listener succ-chan err-chan)
        value (stringify-keys value)]
    (-> client
        (.kv collection key)
        (#(if match-ref (.ifMatch % match-ref) %))
        (#(if only-if-absent? (.ifAbsent %) %))
        (.put value)
        (.on handler))))

(defn patch
  "Partially update data in a collection for a provided key"
  [client collection key patch {:keys [match-ref succ-chan err-chan]}]
  (let [handler (make-listener succ-chan err-chan)
        patch (.build (reduce
                        #(.op % (JsonPatchOp. (:op %2) (:path %2) (:value %2)))
                        (. JsonPatch builder)
                        patch))]
    (-> client
        (.kv collection key)
        (#(if match-ref (.ifMatch % match-ref) %))
        (.patch patch)
        (.on handler))))

(defn delete
  "Delete an item from a collection at the provided key"
  ([client collection key & chans]
    (let [handler (make-listener (first chans) (next chans))]
      (-> client
          (.kv collection key)
          (.delete)
          (.on handler)))))
