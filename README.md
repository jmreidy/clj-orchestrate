# clj-orchestrate

An idiomatic Clojure wrapper of the Orchestrate.io Java client. This client makes use
of core.async channels to provide non-blocking access to your Orchestrate collections.

## Usage

###Add a dependency

Add the necessary dependency to your Leiningen `project.clj`, and require as normal.

```clojure
[jmreidy/clj-orchestrate "0.1.0"] ;project.clj
(ns my-app.core (:require [jmreidy.clj-orchestrate :as orch])) ;app file
```

Further, KeyValue operations are located in the `clj-orchestrate.kv` namespace.

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
(kv/fetch client "collection" "key" succ-chan)
(kv/fetch client "collection" "key" succ-chan err-chan)
```

Pro tip: Create a partially applied query for future convenience.

```clojure
(def collection-fetch (partial kv/fetch client "collection")
(collection-fetch "key" succ-chan)
```

If you'd like to query for a specific `ref` of a KV Object, or if it's
just your style preference, use the more verbose
named parameter version.

```clojure
(kv/fetch client "collection" {:key "key" :ref "ref" :succ-chan chan :err-chan echan})
```

###List Data

It's easy to list all data from a collection.

```clojure
(kv/list client "collection" {:limit 10 :values? true :succ-chan sc :err-chan ec })
 ```

 The query function defaults to a limit of 10, and supports paging up to 100 objects. `values?`
 corresponds to the underlying Java `withValues` option, which specifies whether the list
 of KV objects should be populated with the Object values. It defaults to true.

 The error channel is not required but is strongly recommended.


###Storing Data 

 Adding or updating KV Objects are both accomplished with the same operation.

 ```clojure
 (kv/put client "collection" {:key "key" :value {:foo "bar"} :succ-chan sc :err-chan ec})
```

Hashes with `:keyword` keys are intelligently converted to string keys.
The error channel is not required but is strongly recommended.

####Conditional Store

For conditional updates, you can check to make sure that you're updating a specific ref with `match-ref`.
The following code will only update the value at "key" if the object has the matching "ref".

 ```clojure
 (kv/put client "collection" {:key "key" :value {:foo "bar"} :match-ref "ref" :succ-chan sc :err-chan ec})
```

Likewise, it's possible to limit a put operation to creation rather than update with 
`only-if-absent?`


 ```clojure
 (kv/put client "collection" {:key "key" :value {:foo "bar"} :only-if-absent? true :succ-chan sc :err-chan ec})
```

####Store with Server-generated Keys

When creating a new KV record, you will frequently want to use a server-generated key rather than your 
own (e.g. surrogate versus natural keys). There's a seperate updating function that allows for
this action:

```clojure
(kv/post client "test" {:value "test"} success-chan error-chan)
```

The keys of the value object are stringified, as with the other data update operations.
The key (and ref) of the newly created object are available as a result of the call.


###Patch/Partial Updates

In addition to `put`-ing full JSON values, it's also possible to apply partial updates
by using a JSON patch format. Unlike the Java client, which uses a builder to programtically
construct the patch, the Clojure client expects the user to supply the patch as a vector of hashmaps. Please
see [the Orchestrate reference](https://orchestrate.io/docs/apiref#keyvalue-patch) for details
on what keys can be supplied.
 
```clojure
(kv/patch orch
          "test"
          "key"
          [{:op "replace" :path "val" :value "v01"}]
          {:succ-chan sc})
 ```
 
####Conditional Partial Update
 
As with the conditional `put` above, conditional partial updates can be specified with the 
`match-ref` key in the options hash.
 
```clojure
(kv/patch orch
          "test"
          "key"
          [{:op "replace" :path "val" :value "v01"}]
          {:succ-chan sc :match-ref "ref"})
```
 
####Test Patch
 
A "test" patch is a special JSONPatch op that allows for a type of commit/rollback functionality,
based on the results of a value test. The `test` op is specified in a patch like any other op.
 
```clojure
(kv/patch orch
          "test"
          "key"
          [{:op "replace" :path "val" :value "v02"}
           {:op "test" :path "val" :value "v03"]
          {:succ-chan sc})
 ```
 
###Merge Update
 
*Note: This appears to currently be broken in the Java client.*
 
The final type of KV Update operation is a "merge" update, which relies on the format of
a [JsonMergePatch](https://tools.ietf.org/html/rfc7386). 
 
 ```clojure
(kv/merge client "collection" "key" {:v "val05"} {:succ-chan sc :err-chan ec})) 
```
 
###Deleting Data
A KV object can be deleted from a collection by providing its keys. Note
that deletes by default will not permanently delete the resource (see purge below).

```clojure
(orch/kv-delete client "collection" "key" succ-chan err-chan)
```

The error channel is not required but is strongly recommended.

####Conditonal Delete
As with other KV operations, a conditional delete can be performed by checking
against a ref using the `:match-ref` option.

```clojure
(kv/delete client "test" {:key "key" :match-ref "ref" :succ-chan sc :err-chan ec})
```

####Purge Delete
To delete an element entirely from the KV store, pass a `:purge? true` option to the delete call.

```clojure
(kv/delete client "test" {:key "key" :purge? true :succ-chan sc :err-chan ec})
```

###Search

###Events

###Relations/Graph

The Orchestrate Graph (Relations) functionality is accessed through the `clj-orchestrate.graph` 
namespace.

####List relations

A graph search is performed with the `get-links` function, which is supplied with

* the client connection
* the type of relation to search against
* the collection and key of the "source" element
* a success channel and an error channel

```clojure
;Get Joe's parents
(graph/get-links client "hasParent" {:collection "family" :key "Joe"} sc ec)
```

Multiple "degrees of separation" can be searched by supplying a vector of 
relation types, instead of a single string.

```clojure
;Get Joe's aunts and uncles
(graph/get-links client ["hasParent" "hasSibling"] {:collection "family" :key "Joe"} sc ec)
```

####Add relation

Adding a relation between entities in Orchestrate is as simple as supplying the relation type,
a source element (by collection and key), and a target element.

```clojure
(graph/link client 
            "hasParent" 
            {:collection "family" :key "Joe"} 
            {:collection "family" :key "Joe's dad"} 
            sc ec)
```

Keep in mind that relations are uni-directional. In the above example, a separate / inverse
`hasChild` relationship may need to be created as well.

####Delete relation
Deleting relations follows the same format as adding them - define a relation type,
supply a source by collection and key, and define a target by collection and key.

```clojure
(graph/delete client 
              "hasParent"
              {:collection "family" :key "Joe"} 
              {:collection "family" :key "Joe's Dad"} 
              sc ec)
```



## License

Copyright © 2015 Justin Reidy

Distributed under the Eclipse Public License either version 1.0 or any later version.
