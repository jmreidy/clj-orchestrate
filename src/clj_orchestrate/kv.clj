(ns clj-orchestrate.kv
  (:require [clj-orchestrate.util :as util :refer [make-listener]]
            [clojure.walk :refer [stringify-keys]]
            [cheshire.core :as cheshire])
  (:import (io.orchestrate.client Aggregate Range TimeInterval KvList)
           (io.orchestrate.client.jsonpatch JsonPatch JsonPatchOp)))



(defn fetch
  "Fetch a kv element using a collection name and key"
  ([client collection {:keys [key ref succ-chan err-chan]}]
    (let [handler (make-listener succ-chan err-chan)]
      (-> client
          (.kv collection key)
          (.get java.util.HashMap ref)
          (.on handler))))
  ([client collection key & chans]
    (fetch client collection {:key key :succ-chan (first chans) :err-chan (second chans)})))

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

(defn get-next-list
  "Get the next page in a list"
  ([list & chans]
   (let [next? (.hasNext list)]
     (if next?
       (-> (.getNext list)
           (.on (make-listener (first chans) (second chans)))
           (.get)))
     next?)))



(defn post
  "Create a new item in a collection with an auto-generated key"
  [client collection value & chans]
  (let [handler (make-listener (first chans) (second chans))
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
            {:key key :succ-chan (first chans) :err-chan (second chans)})))


(defn- ->interval [int]
  (condp = int
    :hour TimeInterval/HOUR
    :day TimeInterval/DAY
    :week TimeInterval/WEEK
    :month TimeInterval/MONTH
    :quarter TimeInterval/QUARTER
    :year TimeInterval/YEAR))

(defn- parse-aggs [aggregates]
  (let [builder (Aggregate/builder)
        agg-filter (fn [type] (filter #(= (:type %) type) aggregates))
        range-mapper (partial map (fn [range]
                            {:field (:field range)
                             :ranges (map (fn [bounds]
                                            (Range/between (first bounds) (second bounds)))
                                          (:ranges range))}))
        stats (map (fn [stat] {:field (:field stat)})
                   (agg-filter "stats"))
        ranges (range-mapper (agg-filter "range"))
        distances (range-mapper (agg-filter "distance"))
        times (map (fn [time] {:field (:field time) :interval (->interval (:interval time))})
                   (agg-filter "time"))]
    
    (-> builder
        (#(if-not (empty? stats) 
           (reduce (fn [agg stat] (.stats agg (:field stat))) % stats)
           %))
        
        (#(if-not (empty? ranges) 
           (reduce 
             (fn [agg range] 
               (.range agg 
                       (:field range) 
                       (first (:ranges range))
                       (into-array Range (next (:ranges range)))))
             % ranges) 
           %))
        
        (#(if-not (empty? distances)
           (reduce
             (fn [agg dist]
               (.distance agg
                       (:field dist)
                       (first (:ranges dist))
                       (into-array Range (next (:ranges dist)))))
             % distances)
           %))
        
        (#(if-not (empty? times)
           (reduce
             (fn [agg time] (.timeSeries agg (:field time) (:interval time)))
             % times)
           %))

        (.build))))


(defn search
  "Execute a Lucene query in a collection"
  ([client collection {:keys [query limit offset sort with-values? aggregates succ-chan err-chan] 
                       :or {limit 10 offset 0 with-values? true}}]
    (let [aggregates (parse-aggs aggregates)]
      (-> client
          (.searchCollection collection)
          (#(if-not (empty? aggregates) (.aggregate % aggregates) %))
          (.limit limit)
          (.offset offset)
          (#(if-not (nil? sort) (.sort % sort) %))
          (.withValues with-values?)
          (.get java.util.HashMap query)
          (.on (make-listener succ-chan err-chan)))))
  
  ([client collection query & chans] 
    (search client collection {:query query
                               :succ-chan (first chans) 
                               :err-chan (second chans)})))
