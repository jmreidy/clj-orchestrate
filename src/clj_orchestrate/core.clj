(ns clj-orchestrate.core
  (:import (io.orchestrate.client OrchestrateClient KvObject KvList ResponseAdapter ResponseListener KvMetadata)))


(defn new-client
  "Instantiate a new Orchestrate client"
  [api-key]
  (OrchestrateClient. api-key))

(defn stop-client
  "Stop a running Orchestrate client"
  [client]
  (.close client))

