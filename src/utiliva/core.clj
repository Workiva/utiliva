;; Copyright 2017-2018 Workiva Inc.
;;
;; Licensed under the Eclipse Public License 1.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://opensource.org/licenses/eclipse-1.0.php
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns utiliva.core
  (:import [clojure.lang MapEntry IDeref]
           [java.util PriorityQueue])
  (:refer-clojure :exclude [keep group-by]))

;; https://gist.github.com/galdre/e1851f73f0de9d6ebbf847c91d908f5d

(defn map-keys
  "Maps a function across the keys of a MapEntry collection. Returns
  a sequence. If you want a new map efficiently constructed, use
  (into {} (map-keys f) c)."
  ([f]
   (map (fn [entry-map]
          (MapEntry/create (-> entry-map key f)
                           (val entry-map)))))
  ([f k->v]
   (map (fn [entry-map]
          (MapEntry/create (-> entry-map key f)
                           (val entry-map)))
        k->v)))

(defn map-vals
  "Maps a function across the vals of a MapEntry collection. Returns
  a sequence. If you want a new map efficiently constructed, use
  (into {} (map-vals f) c)."
  ([f]
   (map (fn [entry-map]
          (MapEntry/create (key entry-map)
                           (-> entry-map val f)))))
  ([f k->v]
   (map (fn [entry-map]
          (MapEntry/create (key entry-map)
                           (-> entry-map val f)))
        k->v)))

(defn zip
  "Zips two functions across the input collection(s). Returns a MapEntry sequence.
  Returns a transducer if no collection(s) are provided. Similar to (map juxt)."
  ([f1 f2]
   (map (fn ([item]
             (MapEntry/create (f1 item) (f2 item)))
          ([item & items]
           (MapEntry/create (apply f1 item items) (apply f2 item items))))))
  ([f1 f2 c]
   (sequence (zip f1 f2) c))
  ([f1 f2 c & cs]
   (apply sequence (zip f1 f2) c cs)))

(defn zip-to
  "Stateful transducer. Create with collection c.
  Returns a MapEntry seq [input-1 c-1] [input-2 c-2] ... until c is empty or
  inputs are exhausted."
  [c]
  (fn [rf]
    (let [c (volatile! c)]
      (fn ([] (rf))
        ([result] (rf result))
        ([result input]
         (let [c-now @c]
           (if (some? c-now)
             (do (vswap! c next)
                 (rf result (MapEntry/create input (first c-now))))
             (rf result))))))))

(defn zip-from
  "Stateful transducer. Create with collection c.
  Returns a MapEntry seq [c-1 input-1] [c-2 input-2] ... until c is empty or
  inputs are exhausted."
  [c]
  (fn [rf]
    (let [c (volatile! c)]
      (fn ([] (rf))
        ([result] (rf result))
        ([result input]
         (let [c-now @c]
           (if (some? c-now)
             (do (vswap! c next)
                 (rf result (MapEntry/create (first c-now) input)))
             (rf result))))))))

(defn sorted-zipmap
  "Exactly like zipmap, except the resulting map is sorted. Optionally accepts a comparator.
  Motivation: faster than zipmapping then sorting."
  ([keys vals]
   (loop [map (sorted-map)
          ks (seq keys)
          vs (seq vals)]
     (if (and ks vs)
       (recur (assoc map (first ks) (first vs))
              (next ks)
              (next vs))
       map)))
  ([fn keys vals]
   (loop [map (sorted-map-by fn)
          ks (seq keys)
          vs (seq vals)]
     (if (and ks vs)
       (recur (assoc map (first ks) (first vs))
              (next ks)
              (next vs))
       map))))

(defn keep
  "Returns a lazy sequence of the non-nil results of (f item). Note that this
  means false return values will be included. f must be free of side-effects.
  Returns a transducer when no collection is provided.
  Differs from clojure.core/keep in that it can work with multiple collections
  the way map can."
  ([f]
   (fn [rf]
     (fn ([] (rf))
       ([result] (rf result))
       ([result input]
        (let [v (f input)]
          (if (nil? v)
            result
            (rf result v))))
       ([result input & inputs]
        (let [v (apply f input inputs)]
          (if (nil? v)
            result
            (rf result v)))))))
  ([f coll]
   (lazy-seq
    (when-let [s (seq coll)]
      (if (chunked-seq? s)
        (let [c (chunk-first s)
              size (count c)
              b (chunk-buffer size)]
          (dotimes [i size]
            (let [x (f (.nth c i))]
              (when-not (nil? x)
                (chunk-append b x))))
          (chunk-cons (chunk b) (keep f (chunk-rest s))))
        (let [x (f (first s))]
          (if (nil? x)
            (keep f (rest s))
            (cons x (keep f (rest s)))))))))
  ([f coll & colls]
   (let [step (fn step [cs]
                (lazy-seq
                 (let [ss (map seq cs)]
                   (when (every? identity ss)
                     (cons (map first ss) (step (map rest ss)))))))]
     (keep #(apply f %) (step (conj colls coll))))))

(defn keepcat
  "mapcat : map :: keepcat : keep"
  ([] (comp (keep identity) cat))
  ([f] (comp (keep f) cat))
  ([f & colls] (apply concat (apply keep f colls))))

(defn group-by
  "Behaves just like clojure.core/group-by, but optionally takes an xform
  as first argument to transform the inputs before grouping:
  (group-by (map inc) even? (range 10))"
  ([f coll]
   (persistent!
    (reduce
     (fn [ret x]
       (let [k (f x)]
         (assoc! ret k (conj (get ret k []) x))))
     (transient {}) coll)))
  ([xform f coll]
   (persistent!
    (transduce xform
               (fn
                 ([ret] ret)
                 ([ret x]
                  (let [k (f x)]
                    (assoc! ret k (conj (get ret k []) x)))))
               (transient {})
               coll))))

(defn merge-sorted-by
  "Given a number of collections sorted under the projection defined by f,
  this returns a vector containing all the items from those collections, all
  sorted under the projection defined by f."
  [f cs]
  (let [cnt (count cs)
        heap (PriorityQueue. cnt (fn [a b] (compare (-> a val f) (-> b val f))))]
    (doseq [item (sequence (comp (map first) (zip-from (range)) (filter #(some? (val %)))) cs)]
      (.add heap item))
    (loop [output (transient [])
           cs (transient (mapv next cs))]
      (if-let [nxt (.poll heap)]
        (let [idx (key nxt)
              item (val nxt)]
          (when-let [new-item (first (nth cs idx))]
            (.add heap (MapEntry/create idx new-item)))
          (recur (conj! output item)
                 (assoc! cs idx (next (nth cs idx)))))
        (persistent! output)))))

(defn merge-sorted
  "Given a number of sorted collections, this returns a vector containing all the items
  from those collections, sorted."
  [cs] (merge-sorted-by identity cs))

(defn piecewise-map
  "Declaratively defines and maps a piecewise function across a collection, with pieces
  split on the result of (pred x) for each x in coll. Usage:
  (piecewise-map even? {true inc, false dec} (range 10))
  => (1 0 3 2 5 4 7 6 9 8)
  If a function is not specified, defaults to the value of :default in fmap; if that
  is not defined, defaults to identity."
  ([pred fmap]
   (let [default (or (fmap :default) identity)]
     (map (fn [x] ((or (fmap (pred x)) default)
                   x)))))
  ([pred fmap coll]
   (let [default (or (fmap :default) identity)]
     (map (fn [x] ((or (fmap (pred x)) default)
                   x))
          coll)))
  ([pred fmap coll & colls]
   (let [default (or (fmap :default) (fn [x & _] x))]
     (apply map (fn [x & more] (apply (or (fmap (pred x)) default)
                                      x more))
            coll colls))))

(defn piecewise-pmap
  "Declaratively defines and maps a piecewise function across a collection, with pieces
  split on the result of (pred x) for each x in coll. Usage:
  (piecewise-map even? {true inc, false dec} (range 10))
  => (1 0 3 2 5 4 7 6 9 8)
  If a function is not specified, defaults to the value of :default in fmap; if that
  is not defined, defaults to identity."
  ([pred fmap coll]
   (let [default (or (fmap :default) identity)]
     (pmap (fn [x] ((or (fmap (pred x)) default)
                    x))
           coll)))
  ([pred fmap coll & colls]
   (let [default (or (fmap :default) (fn [x & _] x))]
     (apply pmap (fn [x & more] (apply (or (fmap (pred x)) default)
                                       x more))
            coll colls))))

(defn partition-map
  "Similar to piecewise-map. This partitions the collection by the result of (pred x) for
  each x in coll, then applies the functions in fmap directly to the partitions whole,
  rather than on individual elements. Even so, the element-wise ordering is preserved.
  Usage:
  (partition-map even? {true reverse false #(map - %)} (range 10))
  If a function is not specified, defaults to the value of :default in fmap; if that
  is not defined, defaults to identity.
  Supplied functions are never called on an empty partition."
  ([pred fmap coll]
   (if (empty? coll)
     coll
     (let [rmap (group-by (zip-from (range)) (comp pred val) coll)
           fdefault (get fmap :default identity)]
       (->> rmap
            (sequence (map (fn [[r kvs]]
                             (let [res ((if-let [f (fmap r)] f fdefault)
                                        (vals kvs))]
                               (sequence (zip-from (keys kvs)) res)))))
            (merge-sorted-by key)
            vals))))
  ([pred fmap coll & colls]
   (let [rmap (group-by (zip-from (range)) (comp pred first val) (apply map list coll colls))
         fdefault (get fmap :default (fn [id & _] id))]
     (->> rmap
          (sequence (map (fn [[r kvs]]
                           (let [res (apply (if-let [f (fmap r)] f fdefault)
                                            (apply map list (vals kvs)))]
                             (sequence (zip-from (keys kvs))
                                       res)))))
          (merge-sorted-by key)
          vals))))

(defn partition-pmap
  "Similar to piecewise-map. This partitions the collection by the result of (pred x) for
  each x in coll, then applies the functions in fmap directly to the partitions whole,
  rather than mapping across them.
  rather than on individual elements. Even so, the element-wise ordering is preserved.
  Usage:
  (partition-map even? {true reverse false #(map - %)} (range 10))
  If a function is not specified, defaults to the value of :default in fmap; if that
  is not defined, defaults to identity.
  Supplied functions are never called on an empty partition."
  ([pred fmap coll]
   (if (empty? coll)
     coll
     (let [rmap (group-by (zip-from (range))
                          (comp pred val)
                          coll)
           fdefault (get fmap :default identity)]
       (->> rmap
            (pmap (fn [[r kvs]]
                    (when (not-empty kvs)
                      (let [res ((if-let [f (fmap r)] f fdefault)
                                 (vals kvs))]
                        (sequence (zip-from (keys kvs))
                                  res)))))
            (sequence cat)
            (sort-by key)
            vals))))
  ([pred fmap coll & colls]
   (let [rmap (group-by (zip-from (range))
                        (comp pred first val)
                        (apply map list coll colls))
         fdefault (get fmap :default (fn [id & _] id))]
     (->> rmap
          (pmap (fn [[r kvs]]
                  (when (not-empty kvs)
                    (let [res (apply (if-let [f (fmap r)] f fdefault)
                                     (apply map list (vals kvs)))]
                      (sequence (zip-from (keys kvs))
                                res)))))
          (sequence cat)
          (sort-by key)
          vals))))

(defn reduce-indexed
  "Similar to map-indexed. The reducing function should take args [res idx val].
  More or less equivalent to (reduce-kv f (vec coll)), which would rely on the fact
  that vectors are indexed."
  ([f coll] (reduce-indexed f (first coll) (rest coll)))
  ([f val coll]
   (loop [idx 0
          res val
          [val & vals] coll]
     (if vals
       (recur (inc idx) (f res idx val) vals)
       (f res idx val)))))

(defn distinct-by ;; More or less copied straight from distinct in clojure core
  "Returns a lazy sequence of the elements of coll, removing any
  elements that return duplicate values when passed to a function f."
  ([f]
   (fn [rf]
     (let [seen (volatile! #{})]
       (fn
         ([] (rf))
         ([result] (rf result))
         ([result input]
          (let [o (f input)]
            (if (contains? @seen o)
              result
              (do (vswap! seen conj o)
                  (rf result input)))))))))
  ([f coll]
   (let [step (fn step [xs seen]
                (lazy-seq
                 ((fn [[x :as xs] seen]
                    (when-let [s (seq xs)]
                      (let [o (f x)]
                        (if (contains? seen o)
                          (recur (rest s) seen)
                          (cons x (step (rest s) (conj seen o)))))))
                  xs seen)))]
     (step coll #{}))))

(defn group-like
  "(group-like [:a :b :c :d :e] [[1 2] [3 4 5]])
  => [(:a :b) (:c :d :e)]"
  [flat grouped]
  (first (reduce (fn [[g f] t]
                   (let [[n f] (split-at (count t) f)]
                     [(conj g n) f]))
                 [[] flat]
                 grouped)))

(defn thread-local*
  "Non-macro version of thread-local - see documentation for same."
  [init]
  (let [generator (proxy [ThreadLocal] []
                    (initialValue [] (init)))]
    (reify IDeref
      (deref [this]
        (.get generator)))))

(defmacro thread-local
  "Takes a body of expressions, and returns a java.lang.ThreadLocal object.
   (see http://download.oracle.com/javase/6/docs/api/java/lang/ThreadLocal.html).
  To get the current value of the thread-local binding, you must deref (@) the
  thread-local object. The body of expressions will be executed once per thread
  and future derefs will be cached.
  Note that while nothing is preventing you from passing these objects around
  to other threads (once you deref the thread-local, the resulting object knows
  nothing about threads), you will of course lose some of the benefit of having
  thread-local objects."
  [& body]
  `(thread-local* (fn [] ~@body)))

(defmacro locking-vswap!
  "Version of vswap! that locks the volatile."
  [vol f & args]
  `(locking ~vol (vswap! ~vol ~f ~@args)))
