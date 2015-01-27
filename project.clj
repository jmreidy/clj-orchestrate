(defproject jmreidy/clj-orchestrate "0.1.0"
  :author "Justin Reidy <http://rzrsharp.net>"
  :description "Clojure Orchestrate.io client"
  :url "https://github.com/jmreidy/clj-orchestrate"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0-alpha5"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [cheshire "5.4.0"]
                 [environ "1.0.0"]
                 [io.orchestrate/orchestrate-client "0.7.0"]]
  :plugins [[lein-environ "1.0.0"]])

