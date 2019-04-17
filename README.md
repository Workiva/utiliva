# utiliva [![Clojars Project](https://img.shields.io/clojars/v/com.workiva/utiliva.svg)](https://clojars.org/com.workiva/utiliva) [![CircleCI](https://circleci.com/gh/Workiva/utiliva/tree/master.svg?style=svg)](https://circleci.com/gh/Workiva/utiliva/tree/master)

> *utiliva, compound Latin substantive adjective* - Things for doing useful stuff

<!-- toc -->

- [utiliva.core](#utilivacore)
  * [Working with maps](#working-with-maps)
    + [`map-keys`](#map-keys)
    + [`map-vals`](#map-vals)
    + [`zip-to`](#zip-to)
    + [`zip-from`](#zip-from)
  * [Forked/extended Clojure.core functions](#forkedextended-clojurecore-functions)
    + [`sorted-zipmap`](#sorted-zipmap)
    + [`keep`](#keep)
    + [`keepcat`](#keepcat)
    + [`group-by`](#group-by)
    + [`group-like`](#group-like)
    + [`reduce-indexed`](#reduce-indexed)
    + [`distinct-by`](#distinct-by)
  * [Merging sorted lists](#merging-sorted-lists)
    + [`merge-sorted`](#merge-sorted)
    + [`merge-sorted-by`](#merge-sorted-by)
  * [Defining maps across collection subsets](#defining-maps-across-collection-subsets)
    + [`piecewise-map`](#piecewise-map)
    + [`piecewise-pmap`](#piecewise-pmap)
    + [`partition-map`](#partition-map)
    + [`partition-pmap`](#partition-pmap)
  * [ThreadLocal](#threadlocal)
    + [`thread-local`](#thread-local)
    + [`thread-local*`](#thread-local)
- [utiliva.alpha](#utilivaalpha)
- [utiliva.comparator](#utilivacomparator)
  * [Forked/extended from Clojure.core](#forkedextended-from-clojurecore)
    + [`<`](#)
    + [`<=`](#)
    + [`>`](#)
    + [`>=`](#)
    + [`min`](#min)
    + [`max`](#max)
    + [`min-by`](#min-by)
    + [`max-by`](#max-by)
  * [Composing comparators](#composing-comparators)
    + [`compare-comp`](#compare-comp)
    + [`seq-comparator`](#seq-comparator)
    + [`proj-comparator`](#proj-comparator)
- [utiliva.control](#utilivacontrol)
    + [`?->`](#-)
    + [`?->>`](#-)
    + [`->?->>`](#--)
    + [`->>?->`](#--)
- [utiliva.macros](#utilivamacros)
    + [`when-class`](#when-class)
    + [`if-class`](#if-class)
- [utiliva.recursion](#utilivarecursion)
    + [`*on-expansion*`](#on-expansion)
    + [`recursive-expansion`](#recursive-expansion)
- [utiliva.sorted-cache](#utilivasorted-cache)
- [utiliva.uuid](#utilivauuid)
- [Maintainers and Contributors](#maintainers-and-contributors)
  * [Active Maintainers](#active-maintainers)
  * [Previous Contributors](#previous-contributors)

<!-- tocstop -->

<!-- topstop -->

## Overview

**Utiliva** is a collection of generalized doodads we've found useful in Workiva projects. The library is split into several namespaces: `alpha`, `comparator`, `control`, `core`, `macros`, `recursion`, `sorted-cache`, and `uuid`.

## API Documentation

[Clojure API documentation can be found here.](/documentation/clojure/index.html)

## utiliva.core

### Working with maps

The [Plumbing library](https://github.com/plumatic/plumbing) is full of interesting and marvellous tools, but over time we found ourselves importing the library only for the utility functions `map-keys`, `map-vals`, `map-from-keys`, and `map-from-vals`. And these, while useful, are very inefficient when chained in a row: there is no sense in repeatedly tearing down and constructing multiple transitory maps. We have replaced our use of these functions with the following in `utiliva.core`:

#### `map-keys`

```
([f] [f k->v])
Maps a function across the keys of a MapEntry collection. Returns a
sequence of MapEntries. If you want a new map efficiently constructed,
use the transducer form: (into {} (map-keys f) c).
```

#### `map-vals`

```
([f] [f k->v])
Maps a function across the vals of a MapEntry collection. Returns a
sequence of MapEntries. If you want a new map efficiently constructed,
use the transducer form: (into {} (map-vals f) c).
```

#### `zip-to`

```
[c]
Stateful transducer. Create with collection c. Returns a MapEntry seq
[input-1 c-1] [input-2 c-2] ... until c is empty or the transducer
is completed.
```

#### `zip-from`

```
[c]
Stateful transducer. Create with collection c. Returns a MapEntry seq
[c-1 input-1] [c-2 input-2] ... until c is empty or the transducer
is completed.
```

### Forked/extended Clojure.core functions

#### `sorted-zipmap`

```
([keys vals] [fn keys vals])
Exactly like zipmap, except the resulting map is sorted. Optionally
accepts a comparator. Motivation: faster than sorting after zipmap.
```

#### `keep`

```
([f] [f coll] [f coll & colls])
Returns a lazy sequence of the non-nil results of (f item). Note that
this means false return values will be included. f must be free of
side-effects. Returns a transducer when no collections are provided.
Differs from clojure.core/keep in that it can work with multiple
collections the way `map` can.
```

#### `keepcat`

```
([] [f] [f & colls])
mapcat : map :: keepcat : keep
```

#### `group-by`

```
([f coll] [f cs])
Behaves just like group-by, but optionally takes an xform as first
argument:
(group-by (map inc) even? (range 10))
```

#### `group-like`

```
[flat grouped]
Groups a flat collection in the manner of a nested collection.
(group-like [:a :b :c :d :e] [[1 2] [3 4 5]])
;=[(:a :b) (:c :d :e)]
```

#### `reduce-indexed`

```
([f coll] [f init coll])
Similar to map-indexed. The reducing function should take args:
[res idx val]
More or less equivalent to (reduce-kv f (vec coll)), which relies
on the fact that vectors are indexed.
```

#### `distinct-by`

```
([f] [f coll])
Returns a lazy sequence of the elements of coll, removing any values
that are identical under the projection defined by `f`.
```

### Merging sorted lists

The following experimental functions use priority queues to run "efficiently." For your use case, verify that they are actually faster than alternative approaches. YMMV.

#### `merge-sorted`

```
[cs]
Given any number of sorted collections, this returns a vector
containing all the items from those collections, still sorted.
```

#### `merge-sorted-by`

```
[f cs]
Given a number of collections sorted under the projection defined by f,
this returns a vector containing all the items from those collections,
still sorted under the projection defined by f.
```

### Defining maps across collection subsets

#### `piecewise-map`

```
([pred fmap] [pred fmap coll] [pred fmap coll & colls])
Declaratively defines and maps a piecewise function across a coll,
with pieces split on the result of (pred x) for each x in coll.
Example:
(piecewise-map even? {true inc, false dec} (range 10))
;=(1 0 3 2 5 4 7 6 9 8)
If a function is not specified, defaults to the value of :default in
fmap; if that is not defined, defaults to identity.
```

#### `piecewise-pmap`

```
([pred fmap coll] [pred fmap coll & colls])
piecewise-pmap : piecewise-map :: pmap : map
```

#### `partition-map`

```
([pred fmap coll] [pred fmap coll & colls])
Similar to piecewise-map. This partitions the collection by the result
of (pred x) for each x in coll, then applies the functions in fmap
to the entire partitions, rather than on individual elements. Even so,
element-wise ordering is preserved in the output, assuming that the
for each transformation function, the arity of the input matches
the arity of the result.
Example:
(partition-map even? {true reverse, false #(map - %)} (range 10))
;=(8 -1 6 -3 4 -5 2 -7 0 -9)
If a transformation is not specified, defaults to the value of :default
in fmap; if that is not defined, defaults to identity.
Supplied functions are never called on an empty partition.
```

#### `partition-pmap`

```
([pred fmap coll] [pred fmap coll & colls])
partition-pmap : partition-map :: pmap : map
```

### ThreadLocal

#### `thread-local`

```
[& body] Macro.
Takes a body of expressions, and returns a java.lang.ThreadLocal object.
(see http://download.oracle.com/javase/6/docs/api/java/lang/ThreadLocal.html).
To get the current value of the thread-local binding, you must deref (@) the
thread-local object. The body of expressions will be executed once per thread
and future derefs will be cached.
Note that while nothing is preventing you from passing these objects around
to other threads (once you deref the thread-local, the resulting object knows
nothing about threads), you will of course lose some of the benefit of having
thread-local objects.
```

#### `thread-local*`

```
[generator-fn]
Non-macro version of `thread-local`.
```

## utiliva.alpha

A [few utilities](src/utiliva/alpha.clj) that have been useful but probably have much room for improvement.

## utiliva.comparator

All things comparator.

### Forked/extended from Clojure.core

#### `<`

```
([cmp x] [cmp x y] [cmp x y & more])
Exactly like clojure.core/<, but requires an explicit comparator
(of the -1/0/1 variety)
```

#### `<=`

```
([cmp x] [cmp x y] [cmp x y & more])
Exactly like clojure.core/<=, but requires an explicit comparator
(of the -1/0/1 variety)
```

#### `>`

```
([cmp x] [cmp x y] [cmp x y & more])
Exactly like clojure.core/>, but requires an explicit comparator
(of the -1/0/1 variety)
```

#### `>=`

```
([cmp x] [cmp x y] [cmp x y & more])
Exactly like clojure.core/>=, but requires an explicit comparator
(of the -1/0/1 variety)
```

#### `min`

```
([cmp x] [cmp x y] [cmp x y & more])
Exactly like clojure.core/min, but requires an explicit comparator
(of the -1/0/1 variety)
```

#### `max`

```
([cmp x] [cmp x y] [cmp x y & more])
Exactly like clojure.core/max, but requires an explicit comparator
(of the -1/0/1 variety)
```

#### `min-by`

```
([cmp f x] [cmp f x y] [cmp f x y & more])
Exactly like clojure.core/min-key, but requires an explicit comparator
(of the -1/0/1 variety)
```

#### `max-by`

```
([cmp f x] [cmp f x y] [cmp f x y & more])
Exactly like clojure.core/max-key, but requires an explicit comparator
(of the -1/0/1 variety)
```

### Composing comparators

#### `compare-comp`

```
[& fns] Macro.
Composes the comparators. Generates an inlined implementation.
```

#### `seq-comparator`

```
[& fs] Macro.
Builds a comparator for two vectors given a sequence of element-wise
first-to-last comparators.
Example:
((seq-comparator compare compare) [1 2] [1 1])
;=1
```

#### `proj-comparator`

```
[& projs-and-cmps]
Builds a discriminating comparator for two arbitrary objects given
an alternating sequence of projection functions and comparators
for said projections.
Example:
((proj-comparator :a compare :b compare) {:a 1 :b 2} {:a 1 :b 1})
;=1
```

## utiliva.control

Control forms, at the moment consisting in just a few threading macros.

#### `?->`

```
[expr & clauses] Macro.
Like cond->, but threads the argument through the conditions as
well as the expressions.
Example:
(?-4 even? inc odd? inc neg? inc)
;=6
```

#### `?->>`

```
[expr & clauses] Macro.
Like cond->>, but threads the argument through the conditions as
well as the expressions.
Example:
(?->4 even? (assoc {} :four) map? keys)
;=(:four)
```

#### `->?->>`

```
[expr & clauses]
Like ?->>, but threads the argument through the conditions as
though with ->, and through the expressions as though with ->>.
```

#### `->>?->`

```
[expr & clauses]
Like ?->, but threads the argument through the conditions as
though with ->>, and through the expressions as though with ->.
```

## utiliva.macros

A handful of utilities we've used when writing macros. The only truly notable ones are these two gems (i.e., complete hacks):

#### `when-class`

```
[class & body]
When the class can be found in Java's loading path, this expands
to the body; otherwise the body is suppressed.
```

#### `if-class`

```
[class then else]
When the class can be found in Java's loading path, this expands
to the form in the `then` clause; otherwise, it expands to the
form in the `else` clause.
```

## utiliva.recursion

A simple implementation of recursive expansion. Rebinding `utiliva.recursion/*on-expansion*` will let you perform side-effects at each iteration of the expansion.

#### `*on-expansion*`

```
[expr result]
Called whenever operation expr is expanded. Passed the source
and result exprs. Default implementation is no-op.
Rebind (see `binding`) to a side-effecting function to do
things like trace the expansion process.
```

#### `recursive-expansion`

```
([expander input] [max-iter expander input])
expander should be a function that takes the input and produces
the desired expansion.
```

## utiliva.sorted-cache

`clojure.core.cache` produces caches with map semantics. This namespace provides an implementation of an lru-cache that has sorted-map semantics.

## utiliva.uuid

[Squuids](src/utiliva/uuid.clj).

## Maintainers and Contributors

### Active Maintainers

  -

### Previous Contributors

  - Timothy Dean <timothy.dean@workiva.com>
  - Houston King <houston.king@workiva.com>
