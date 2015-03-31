(ns clj-orchestrate.kv-test
  (:require [clojure.test :refer :all]
            [clojure.core.async :as async]
            [clj-orchestrate.test.helpers :as h]
            [clj-orchestrate.kv :as kv]
            [clj-orchestrate.util :as util :refer [get-results]]))


(def test-data (atom {}))

(defn tchan [& [time]] (async/timeout (or time 5000)))

(defn seed-data [vals]
  (let [post-chan (async/chan)
        res-chan (tchan)]
    (mapv #(kv/post h/client h/col % post-chan) vals)
    (async/go-loop [results []
                    res (async/<! post-chan)]
      (let [results (conj results res)]
        (if (= (count results) (count vals))
          (do
            (async/close! post-chan)
            (async/>! res-chan results))
          (recur results (async/<! post-chan)))))
    res-chan))

(defn kv-fixtures [f]
  (let [dc (tchan)]
    (h/delete-collection h/col dc)
    (async/<!! dc)
    (reset! test-data (async/<!! (seed-data (take 10 (repeat {:foo "bar"})))))
    (f)
    (reset! test-data {})))

(use-fixtures :each kv-fixtures)

(deftest kv-fetch
  (let [res-chan (tchan)
        err-chan (async/chan 1 (map println))
        key (-> (first @test-data) (.getKey))]
    (kv/fetch h/client h/col key res-chan err-chan)
    (let [result (async/<!! res-chan)]
      (is (instance? io.orchestrate.client.KvObject result)))))

(deftest kv-list
  (let [res-chan  (tchan)]
    (kv/list h/client h/col res-chan)
    (let [result (async/<!! res-chan)]
      (is (instance? io.orchestrate.client.KvList result)))))

(deftest kv-get-next-list
  (testing "if a list response has no more pages"
    (let [res-chan (tchan)
          next-chan (tchan)]
      (kv/list h/client h/col {:succ-chan res-chan :limit 50})
      (let [resp (kv/get-next-list (async/<!! res-chan) next-chan)]
        (is (= false resp) "get-next-list returns false"))))
  (testing "if a list response has a next page"
    (let [res-chan (tchan)
          next-chan (tchan)
          err-chan (async/chan 1 (map println))]
      (kv/list h/client h/col {:succ-chan res-chan :limit 5})
      (let [resp (kv/get-next-list (async/<!! res-chan) next-chan err-chan)
            result (async/<!! next-chan)]
        (is (= true resp) "get-next-list returns true")
        (is (instance? io.orchestrate.client.KvList result)
            "a KvList is written to the success chan")))))


(deftest kv-post
  (let [res-chan  (tchan)]
    (kv/post h/client h/col h/data res-chan)
    (let [result (async/<!! res-chan)]
      (is (instance? io.orchestrate.client.KvObject result)))))

