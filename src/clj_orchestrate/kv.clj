(ns clj-orchestrate.kv
  (:import (io.orchestrate.client.jsonpatch JsonPatchOp JsonPatch))
  (:require [clj-orchestrate.core :as core :refer [make-listener]]
            [clojure.walk :refer [stringify-keys]]
            [cheshire.core :as cheshire]))



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


(defn post
  "Create a new item in a collection with an auto-generated key"
  [client collection value & chans]
  (let [handler (make-listener (first chans) (next chans))
        value (stringify-keys value)]
    (-> client
        (.postValue collection value)
        (.on handler))))

(defn put
  "Put or update data in a collection for a provided key"
  ([client collection key value succ-chan] 
    (put client collection {:key key :value value :succ-chan succ-chan}))
  ([client collection {:keys [key value match-ref only-if-absent? succ-chan err-chan]}]
    (let [handler (make-listener succ-chan err-chan)
          value (stringify-keys value)]
      (-> client
          (.kv collection key)
          (#(if match-ref (.ifMatch % match-ref) %))
          (#(if only-if-absent? (.ifAbsent %) %))
          (.put value)
          (.on handler)))))

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

(defn merge
  "Merge data in a collection for a provided key"
  [client collection key value {:keys [match-ref succ-chan err-chan]}]
  (let [handler (make-listener succ-chan err-chan)
        value (cheshire/generate-string value)]
    (println value)
    (-> client
        (.kv collection key)
        (#(if match-ref (.ifMatch % match-ref) %))
        (.merge value)
        (.on handler))))

(defn delete
  "Delete an item from a collection at the provided key"
  ([client collection {:keys [key succ-chan err-chan purge? match-ref] :or {purge? false}}]
    (let [handler (make-listener succ-chan err-chan)]
      (-> client
          (.kv collection key)
          (#(if match-ref (.ifMatch % match-ref) %))
          (.delete purge?)
          (.on handler))))
  ([client collection key & chans]
    (delete client
            collection
            {:key key :succ-chan (first chans) :err-chan (next chans)})))
