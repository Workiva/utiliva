;; Copyright 2017-2019 Workiva Inc.
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

(ns utiliva.comparator
  (:refer-clojure :exclude [< <= > >= min max min-by max-by]))

;; USEFUL COMPARATORS

(defn <
  "Exactly like clojure.core/<, but requires an explicit comparator (of the -1/0/1 variety)."
  ([cmp x] true)
  ([cmp x y] (clojure.core/< (cmp x y) 0))
  ([cmp x y & more]
   (if (< cmp x y)
     (if (next more)
       (recur cmp y (first more) (next more))
       (< cmp y (first more)))
     false)))

(defn <=
  "Exactly like clojure.core/<=, but requires an explicit comparator (of the -1/0/1 variety)."
  ([cmp x] true)
  ([cmp x y] (clojure.core/<= (cmp x y) 0))
  ([cmp x y & more]
   (if (<= cmp x y)
     (if (next more)
       (recur cmp y (first more) (next more))
       (<= cmp y (first more)))
     false)))

(defn >
  "Exactly like clojure.core/>, but requires an explicit comparator (of the -1/0/1 variety)."
  ([cmp x] true)
  ([cmp x y] (clojure.core/> (cmp x y) 0))
  ([cmp x y & more]
   (if (> cmp x y)
     (if (next more)
       (recur cmp y (first more) (next more))
       (> cmp y (first more)))
     false)))

(defn >=
  "Exactly like clojure.core/>=, but requires an explicit comparator (of the -1/0/1 variety)."
  ([cmp x] true)
  ([cmp x y] (clojure.core/>= (cmp x y) 0))
  ([cmp x y & more]
   (if (>= cmp x y)
     (if (next more)
       (recur cmp y (first more) (next more))
       (>= cmp y (first more)))
     false)))

(defn min
  "Exactly like clojure.core/min, but requires an explicit comparator of the -1/0/1 variety."
  ([cmp x] x)
  ([cmp x y] (if (clojure.core/> 0 (cmp x y)) x y))
  ([cmp x y & more] (reduce (partial min cmp) (min cmp x y) more)))

(defn max
  "Exactly like clojure.core/max, but requires an explicit comparator of the -1/0/1 variety."
  ([cmp x] x)
  ([cmp x y] (if (clojure.core/> 0 (cmp x y)) y x))
  ([cmp x y & more] (reduce (partial max cmp) (max cmp x y) more)))

(defn min-by
  "Exactly like clojure.core/min-key, but requires an explicit comparator of the -1/0/1 variety."
  ([cmp f x] x)
  ([cmp f x y] (if (< cmp (f x) (f y)) x y))
  ([cmp f x y & more] (reduce (partial min-by cmp f) (min-by cmp f x y) more)))

(defn max-by
  "Exactly like clojure.core/max-key, but requires an explicit comparator fof the -1/0/1 variety."
  ([cmp f x] x)
  ([cmp f x y] (if (> cmp (f x) (f y)) x y))
  ([cmp f x y & more] (reduce (partial max-by cmp f) (max-by cmp f x y) more)))

;; Constructing comparators via composition:

(defn- compare-comp*
  "Utility function for the macro compare-comp. Does the inlining. Pretty
  sure I saw a better implementation of this somewhere once."
  ^java.util.Comparator
  [syma symb fns]
  (if (next fns)
    `(let [r# (~(first fns) ~syma ~symb)]
       (if-not (zero? r#)
         r#
         ~(compare-comp* syma symb (next fns))))
    `(~(first fns) ~syma ~symb)))

(defmacro compare-comp
  "Composes the comparators. Inlines everything. There's a better implementation somewhere."
  [& fns]
  (let [syma (gensym)
        symb (gensym)]
    `(fn [~syma ~symb] ~(compare-comp* syma symb fns))))

;; Constructing comparators intended to operate on sequences:

(defn- seq-comparator*
  "Utility function for seq-comparator. Does the inlining."
  [x y i [f & fs]]
  (if (some? fs)
    `(let [r# (~f (nth ~x ~i) (nth ~y ~i))]
       (if-not (zero? r#)
         r#
         ~(seq-comparator* x y (inc i) fs)))
    `(~f (nth ~x ~i) (nth ~y ~i))))

(defmacro seq-comparator
  "Builds a discriminating comparator for two vectors given a sequence of
   element-wise first-to-last comparators.
   eg: >  ((seq-comparator compare compare) [1 2] [1 1])
       => 1"
  ([& fs]
   (let [n (count fs)
         x (gensym "x_")
         y (gensym "y_")
         fn-names (for [i (range (count fs))] (gensym))]
     `(let ~(vec (interleave fn-names fs))
        (fn [~x ~y]
          ~(seq-comparator* x y 0 fn-names))))))

;; Constructing comparators dependent upon projections:

(defn- proj-comparator*
  "Utility function for proj-comparator. Does the inlining."
  [a b [[p f] & pfs]]
  (if pfs
    `(let [r# (~f (~p ~a) (~p ~b))]
       (if-not (zero? r#)
         r#
         ~(proj-comparator* a b pfs)))
    `(~f (~p ~a) (~p ~b))))

(defmacro proj-comparator
  "Builds a discriminating comparator for two arbitrary objects given
   an alternating sequence of projection functions and comparators
   for said projections
   eg: > ((proj-comparator :a compare :b compare) {:a 1 :b 2} {:a 1 :b 1})
       => 1"
  ([& projs-and-cmps]
   (assert (even? (count projs-and-cmps)) "projected tuple comparator requires an even number of forms as: proj comp")
   (let [asym (gensym)
         bsym (gensym)
         cnt (/ (count projs-and-cmps) 2)
         projs (take-nth 2 projs-and-cmps)
         proj-names (for [i (range cnt)] (gensym))
         cmps (take-nth 2 (rest projs-and-cmps))
         cmp-names (for [i (range cnt)] (gensym))
         proj-cmp-names (interleave proj-names cmp-names)]
     `(let ~(vec (concat (interleave proj-names projs)
                         (interleave cmp-names cmps)))
        (fn [~asym ~bsym]
          ~(proj-comparator* asym bsym (partition 2 proj-cmp-names)))))))
