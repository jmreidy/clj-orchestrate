(ns clj-orchestrate.kv-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [clj-orchestrate.test.helpers :as h]
            [clj-orchestrate.kv :as kv]
            [clj-orchestrate.util :as util :refer [get-results]]))


(def test-data (atom {}))

(defn kv-fixtures [f]
  (let [pc (async/chan 1 (map #(.getKey %)))
        post-data (fn [_] (kv/post h/client h/col h/data pc))
        dc (async/chan 1 (map post-data))]
    (h/delete-collection h/col dc)
    (swap! test-data assoc :ref (async/<!! pc))
    (f)
    (reset! test-data {})))

(use-fixtures :each kv-fixtures)

(deftest kv-fetch
  (let [res-chan (async/chan)]
    (kv/fetch h/client h/col (:ref @test-data) res-chan)
    (let [result (async/<!! res-chan)]
      (is (instance? io.orchestrate.client.KvObject result))
      (is (= h/data (get-results result))))))

(deftest kv-list
  (let [res-chan  (async/chan)]
    (kv/list h/client h/col res-chan)
    (let [result (async/<!! res-chan)]
      (is (instance? io.orchestrate.client.KvList result))
      (is (h/in? (get-results result) h/data)))))

(deftest kv-post
  (let [res-chan  (async/chan)]
    (kv/post h/client h/col h/data res-chan)
    (let [result (async/<!! res-chan)]
      (is (instance? io.orchestrate.client.KvObject result)))))

