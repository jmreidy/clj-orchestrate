(ns clj-orchestrate.core-test
  (:require [clojure.test :refer :all]
            [environ.core :refer [env]]
            [clj-orchestrate.core :as core]))


(deftest new-client
  (testing "Creating a new Orchestrate client"
    (let [client (core/new-client (:orch-key env))]
      (is (instance? io.orchestrate.client.OrchestrateClient client)))))

(deftest stop-client
  (testing "Stopping an Orchestrate client"
    (let [client (core/new-client (:orch-key env))]
      (core/stop-client client)
      (is :ok))))



