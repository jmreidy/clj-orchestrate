# clj-orchestrate

An (unofficial) idiomatic Clojure wrapper of the Orchestrate.io Java client. This client makes use
of core.async channels to provide non-blocking access to your Orchestrate collections.

**This is note yet published to Clojars; initial version will be published when Aggregate
search result handling is complete, which should be done shortly!**

###Author's Note
This is my first Clojure project, and as such, I'm fairly confidant that I'm doing
lots of things wrong. To a JavaScripter's eyes, the code looks lovely; I'm sure
it's ghastly to a Lisp-er. Along those lines, I'd be very happy to accept "constructive 
criticism PRs" from more experienced Clojurians!

Test coverage is on the way, but the wrapper has been built via REPL, so everything should
be working correctly. If you come across any problems, please open an issue.

Finally, I want to thank the team at Orchestrate for building such a fantastically
useful service!

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

###Channels
The underlying Orchestrate Java client makes every request in the form of a Future, and provides
a callback-based solution for processing results and errors. Instead of using callbacks
or relying on the derefing of futures, clj-orchestrate functions all take two channels - a success
channel and an error channel. In all cases, Orchestrate calls will be performed in a non-blocking
manner, with the result (or error) passed to the supplied channels.

###Handling results
Java result objects are passed back from the Java client to the success and error channels.
While it's possble that you may want to work with these Java objects directly, a number
of helper methods have been provided that allow for the user of the clj-orchestrate client
to work with pure Clojure data structures. There's two transforming functions of note, both
located in the `util` namespace:

* `get-results` will return a hashmap or lazy seq of hashmaps, depending on the query type

* `get-results-with-meta` will return a hashmap (or list of hashmaps) with two keys, `:data` 
and `:meta`. The `:data` value is the actual result value, which the `:meta` value is a map
of object metadata - the collection, key, and ref of that object.

Creation operations return metadata with no value, and delete operations return booleans without
metadata at all. In these cases, `get-results` will return the metadata map or a boolean (respectively),
while `get-results-with-meta` will return an object with an nil `:data` value (for metadata only)
or an empty `:meta` value (for booleans).

These helper functions can be especially helpful when supplied as a transducer when creating
a channel, as in the following:

```clojure
(def succ-chan (chan 1 (map get-results-with-meta))
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
Orchestrate data is intelligently indexed by the service automatically, and made available
for searching via 
[Lucene-style queries](http://lucene.apache.org/core/4_3_0/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#Overview).
Searching is exposed in the Clojure wrapper via the `search` function in the `kv` namespace.
In its simplest form, only a collection and query is needed.

```clojure
(kv/search client "test" "*" sc ec)
```

A more complicated form is also available, allowing the specification of:

* a `limit` (defaults to 10, capped at 100)
* `offset` (defauls to 0)
* `with-values?` to return hydrated search results (true, default) or metadata only (false)

This form uses an option map:

```clojure
(kv/search client "test" {:query "*"
                          :limit 20
                          :offset 1
                          :succ-chan sc
                          :err-chan ec})
``` 

####Aggregates
According to the Java client docs, "any query can be optionally accompanied by a 
collection of aggregate functions, each providing a summary of the data items 
matched by the query. There are four different kinds of aggregate functions: 
Statistical, Range, Distance, and TimeSeries." (See the [API Reference](https://orchestrate.io/docs/apiref#aggregates).)

Aggregates are supplied to the `search` function as the value of another options key: `aggregates`.
The value should simply be a vector of hash-maps. Each map should have a `:type` and `:field`
key. `:type` denotes the type of Aggregate function: 

* A type of `:stats` will configure a Statistical aggregate.
* A type of `:range` will configure a Range aggregate
* A type of `:distance` will configure a Distance aggregate
* A type of `:time` will configure a TimeSeries aggregate.

Each of these aggregate functions relies on the `:field` key to denote the fieldname to be run
against. Below, see a fully populated aggregate search query, with example configuration options
for each type:

```clojure
(kv/search client "test" {:query "*"
                          :succ-chan sc
                          :err-chan ec
                          :aggregates [{:type "range" :field "number" :ranges [[4,6]]}
                                       {:type "stats" :field "number"}
                                       {:type "distance" :field "location" :ranges [[0, 5]]}
                                       {:type "time" :field "time" :interval :day}]})
           
```

TimeSeries intervals allow for an `:interval` of `:hour`, `:day`, 
`:week`, `:month`, `:quarter`, `:year`. `Range`s, supplied to both Distance
and Range aggregates, are simply vectors of Doubles serving as lower and upper bounds. 
If you would like to ignore one of these bounds, replace the Double with either
`Double/NEGATIVE_INFINITY` or `Double/POSITIVE_INFINITY`.

####Handling Search Results
*Finishing handling of Aggregate results, the last item to be implemented before initial release.*


###Events

The Orchestrate Events functionality is accessed through the `clj-orchestrate.events` namespace. 
In the Orchestrate.io service, an event is a time ordered piece of data you want to store in the context of a key.

####Fetch Events (List)
To fetch events belonging to a `key` in a specific `collection` with a particular
event `type`, call the `events/list` function. This function will return ALL
events for the given object of the provided type.

```clojure
(events/list client "test" {:key "key" :type "event-type" :succ-chan sc :err-chan :ec})
```

A shorthand version of this function also exists:

```clojure
(events/list client "test" "key" "event-type" sc ec)
```

It's also possible to narrow the list of events by providing a start timestamp, an
end timestamp, or both (e.g. all events after a time, all events before a time, all
events between a start and end time). This operation can be performed by supplying
`start` and `end` Longs to the `list` call:

```clojure
(events/list client "test" {:key "key" 
                            :type "event-type" 
                            :start 1421704814680
                            :end 1421704814690
                            :succ-chan sc 
                            :err-chan :ec})
```

####Fetch Event (Single)
To get a *single* instance of an event of provided `type` belonging to an object
with `key` in `collection`, use the `fetch` function with a specific `timestamp`
and `ordinal`.

```clojure
(events/fetch client "test" {:key "key"
                            :type "event-type"
                            :timestamp 1421704814680
                            :ordinal "07b91c5c5b084000"
                            :succ-chan sc
                            :err-chan :ec})
```

Note that the event fetch calls (list or single) return objects with metadata
that specifies timestamp and ordinal values, in addition to the usual ref value.

####Create Event
Creating an event (and updating or patching an event) follows in the same style
as create an KV object, with the main difference being the addition of an event `type`
parameter.

```clojure
(events/create client "test" "key" "event-type" {:value "event value"} sc ec)
```

####Update an Event
To update a specific event, you'll need the timestamp and ordinal of that event,
just as you would need for fetching a single event.

```clojure
(events/update client "test" {:key "key"
                              :type "event-type"
                              :timestamp 1421704814680
                              :ordinal "07b91c5c5b084000"
                              :value {:value "updated value"}
                              :succ-chan sc
                              :err-chan ec})
```

An update can be made *conditionally* (e.g. requiring a match to a certain ref) just like
with KV operations, by providing a `match-ref` value:


```clojure
(events/update client "test" {:key "key"
                              :type "event-type"
                              :timestamp 1421704814680
                              :ordinal "07b91c5c5b084000"
                              :match-ref "700f69ffab6edbdf"
                              :value {:value "updated value"}
                              :succ-chan sc
                              :err-chan ec})
```

####Patch an Event
Patching an event follows in the same format as updating an event, but instead of a wholesale
replacement of the event value, a patch will be applied. As with the KV patch above,
the Orchestrate JSONPatch is created by supplying a vector of maps.


```clojure
(events/patch client "test" {:key "key"
                             :type "event-type"
                             :timestamp 1421704814680
                             :ordinal "07b91c5c5b084000"
                             :patch [{:op "replace" :patch "value" :value "updated value"}]
                             :succ-chan sc
                             :err-chan ec})
```

As with other operations, a *conditional* patch is supported via the `match-ref` key.

```clojure
(events/patch client "test" {:key "key"
                             :type "event-type"
                             :timestamp 1421704814680
                             :ordinal "07b91c5c5b084000"
                             :match-ref "700f69ffab6edbdf"
                             :patch [{:op "replace" :patch "value" :value "updated value"}]
                             :succ-chan sc
                             :err-chan ec})
```

####Delete an event
Deleting an event is supported by the `events/delete` function. It follows in the style of 
the KV delete operation, and like the other single-event functions requires both a `timestamp`
and `ordinal` key. *Unlike* the KV delete operation, *all* event delets are treated as a purge.

```clojure
(events/delete client "test" {:key "key"
                              :type "event-type"
                              :timestamp 1421704814680
                              :ordinal "07b91c5c5b084000"
                              :value {:value "updated value"}
                              :succ-chan sc
                              :err-chan ec})
```

Conditional deletes are specified via the `match-ref` key:

```clojure
(events/delete client "test" {:key "key"
                              :type "event-type"
                              :timestamp 1421704814680
                              :ordinal "07b91c5c5b084000"
                              :match-ref "700f69ffab6edbdf"
                              :value {:value "updated value"}
                              :succ-chan sc
                              :err-chan ec})
```

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
