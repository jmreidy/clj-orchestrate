(ns clj-orchestrate.core-test
  (:require [clojure.test :refer :all]
            [environ.core :refer [env]]
            [clj-orchestrate.core :as core])
  (:import (io.orchestrate.client OrchestrateClient)))


(deftest orchestrate-core
  (testing "Creating a new Orchestrate client"
    (let [client (core/new-client (:orch-key env))]
      (is (= (class client) OrchestrateClient)))))


