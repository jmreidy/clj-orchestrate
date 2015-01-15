# clj-orchestrate

An idiomatic Clojure wrapper of the Orchestrate.io Java client.

## Usage

###Add a dependency
Add the necessary dependency to your Leiningen `project.clj`, and require as normal.

```clojure
[com.rzrsharp/jmreidy "0.1.0"] ;project.clj
(ns my-app.core (:require [rzrsharp.clj-orchestrate :as orch])) ;app file
```

###Construct a client
A new instance of the Java library's `OrchestrateClient` is easily
created by supplying your API key to the `new-client` function.

```clojure
(def key "API KEY HERE")
(def client (orch/new-client key))
```

###Choose a data center

Not implemented yet

###Stopping a client

```clojure
(orch/stop-client client)
```

###Fetch data

There's multiple ways to query for Key-Value data within a given Orchestrate
collection. The most straightforward way to query is to find the latest
object for a given key. This query should be supplied with a channel to
handle the success response, and should usually also receive an error response
channel.

```clojure
(orch/kv-fetch client "collection" "key" succ-chan)
(orch/kv-fetch client "collection" "key" succ-chan err-chan)
```

Pro tip: Create a partially applied query for future convenience.

```clojure
(def collection-fetch (partial orch/kv-fetch client "collection")
(collection-fetch "key" succ-chan)
```

If you'd like to query for a specific `ref` of a KV Object, or if it's
just your style preference, use the more verbose
named parameter version.

```clojure
(orch/kv-fetch client "collection" {:key "key" :ref "ref" :succ-chan chan :err-chan echan})
```

###List Data

It's easy to list all data from a collection.

```clojure
(orch/kv-list client "collection" {:limit 10 :values? true :succ-chan sc :err-chan ec })
 ```

 The query function defaults to a limit of 10, and supports paging up to 100 objects. `values?`
 corresponds to the underlying Java `withValues` option, which specifies whether the list
 of KV objects should be populated with the Object values. It defaults to true.

 The error channel is not required but is strongly recommended.

 ###Storing Data

 Adding or updating KV Objects are both accomplished with the same operation.

 ```clojure
 (orch/kv-put client "collection" {:key "key" :value {:foo "bar"} :succ-chan sc :err-chan ec})
```

Hashes with `:keyword` keys are intelligently converted to string keys.
The error channel is not required but is strongly recommended.

####Conditional Store
For conditional updates, you can check to make sure that you're updating a specific ref with `match-ref`.
The following code will only update the value at "key" if the object has the matching "ref".

 ```clojure
 (orch/kv-put client "collection" {:key "key" :value {:foo "bar"} :match-ref "ref" :succ-chan sc :err-chan ec})
```

Likewise, it's possible to limit a put operation to creation rather than update with 
`only-if-absent?`


 ```clojure
 (orch/kv-put client "collection" {:key "key" :value {:foo "bar"} :only-if-absent? true :succ-chan sc :err-chan ec})
```



####Store with Server-generated Keys
Not yet implemented

###Patch/Partial Updates
In addition to `put`-ing full JSON values, it's also possible to apply partial updates
 by using a JSON patch format. Unlike the Java client, which uses a builder to programtically
 construct the patch, the Clojure client expects the user to supply the patch as a hashmap. Please
 see [the Orchestrate reference](https://orchestrate.io/docs/apiref#keyvalue-patch) for details
 on what keys can be supplied.

###Deleting Data
A KV object can be deleted from a collection by providing its keys. Note
that deletes by default will not permanently delete the resource (see purge below).

```clojure
(orch/kv-delete client "collection" "key" succ-chan err-chan)
```

The error channel is not required but is strongly recommended.

####Conditonal Delete
Not yet implemented

####Purge Delete
Not yet implemented


## License

Copyright © 2015 Justin Reidy

Distributed under the Eclipse Public License either version 1.0 or any later version.
