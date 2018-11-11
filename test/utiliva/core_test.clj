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

(ns utiliva.core_test
  (:require [utiliva.core :as c]
            [clojure.test :refer :all])
  (:import [clojure.lang MapEntry]))

(def alphabet
  "The modern English alphabet is a Latin alphabet consisting of 26 letters
  (each having an uppercase and a lowercase form) – exactly the same letters
  that are found in the ISO basic Latin alphabet"
  "abcdefghijklmnopqrstuvwxyz")

(def idx->letter
  (->> alphabet
       (zipmap (range))
       (sort-by key)))

(def letter->idx
  (zipmap (vals idx->letter)
          (keys idx->letter)))

(deftest unit:zips
  (is (= idx->letter
         (sequence (c/zip key val) idx->letter)
         (sequence (c/zip identity (into {} idx->letter)) (range 26))
         (sequence (c/zip letter->idx identity) alphabet)
         (sequence (c/zip-to alphabet) (range 52))
         (sequence (c/zip-to (cycle alphabet)) (range 26))
         (sequence (c/zip-from (range)) alphabet)
         (sequence (c/zip-from (range 26)) (take 50 (cycle alphabet)))
         (seq (c/sorted-zipmap (reverse (range 26)) (reverse alphabet)))
         (seq (c/sorted-zipmap (reverse (range -10 26)) (reverse alphabet)))
         (seq (c/sorted-zipmap (keys (into {} idx->letter)) (vals (into {} idx->letter))))
         (sort-by key (zipmap (range) alphabet))))
  (is (= ()
         (sequence (c/zip inc dec) ())
         (sequence (c/zip-to alphabet) ())
         (sequence (c/zip-from (range)) ())
         (sequence (c/sorted-zipmap (reverse (range 26)) ())))))

(def coll-0 (filter even? (range 100)))
(deftest unit:keep-fork
  (are [x] (= coll-0 x)
    (c/keep #(when (even? %) %) (range 100))
    (sequence (c/keep #(when (even? %) %))
              (range 100))
    (keep #(when (even? %) %) (range 100))
    (sequence (keep #(when (even? %) %))
              (range 100))
    (c/keep #(when (even? (+ % %2)) %) (range 100) (repeat 0))
    (sequence (c/keep #(when (even? (+ % %2)) %))
              (range 100)
              (repeat 0)))
  (is (= (c/keep #(when (even? %) %) ())
         (keep #(when (even? %) %) ())))
  (are [x] (= coll-0 x)
    (c/keepcat #(when (even? (first %)) %) (map list (range 100)))
    (sequence (c/keepcat #(when (even? (first %)) %))
              (map list (range 100)))
    (apply concat (keep #(when (even? (first %)) %) (map list (range 100))))
    (sequence (comp (keep #(when (even? (first %)) %))
                    cat)
              (map list (range 100)))
    (c/keepcat #(when (even? (+ (first %) %2)) %) (map list (range 100)) (repeat 0))
    (sequence (c/keepcat #(when (even? (+ (first %) %2)) %))
              (map list (range 100))
              (repeat 0)))
  (is (= (sequence (c/keepcat #(when (even? (first %)) %))
                   (map list ()))
         (sequence (comp (keep #(when (even? (first %)) %))
                         cat)
                   (map list ())))))

(def coll-1 (group-by even? (range 100)))
(def coll-2 (group-by even? ()))
(deftest unit:group-by-fork
  (are [x] (= coll-1 x)
    (c/group-by even? (range 100))
    (c/group-by (map identity) even? (range 100))
    (c/group-by (map inc) even? (range -1 99))
    (c/group-by (map val) even? (sort-by val (zipmap (range 300 400) (range 100)))))
  (are [x] (= coll-2 x)
    (c/group-by even? ())
    (c/group-by (map val) even? ())))

(def coll-3 (map #(if (even? %) (inc %) (dec %)) (range 1000)))
(def coll-4 (map #(if (even? %) (inc %) %) (range 1000)))
(def coll-5 (map #(if (zero? (mod % 3)) (inc %) (dec %)) (range 1000)))
(def coll-6 (map #(if (even? %) % nil) (range 100)))
(deftest unit:piecewise-partition
  (are [x] (= x coll-3)
    (c/piecewise-map even? {true inc false dec} (range 1000))
    (c/piecewise-pmap even? {true inc false dec} (range 1000))
    (c/partition-map even? {true #(map inc %) false #(map dec %)} (range 1000))
    (c/partition-pmap even? {true #(map inc %) false #(map dec %)} (range 1000)))
  ;; testing default defaults:
  (are [x] (= x coll-4)
    (c/piecewise-map even? {true inc} (range 1000))
    (c/piecewise-pmap even? {true inc} (range 1000))
    (c/partition-map even? {true #(map inc %)} (range 1000))
    (c/partition-pmap even? {true #(map inc %)} (range 1000)))
  ;; testing explicit defaults:
  (are [x] (= x coll-5)
    (c/piecewise-map #(mod % 3) {:default dec 0 inc} (range 1000))
    (c/piecewise-pmap #(mod % 3) {:default dec 0 inc} (range 1000))
    (c/partition-map #(mod % 3) {:default #(map dec %) 0 #(map inc %)} (range 1000))
    (c/partition-pmap #(mod % 3) {:default #(map dec %) 0 #(map inc %)} (range 1000)))
  (are [x] (= x ())
    (c/piecewise-map even? {true inc false dec} ())
    (c/piecewise-pmap even? {true inc false dec} ())
    (c/partition-map even? {true #(map inc %) false #(map dec %)} ())
    (c/partition-pmap even? {true #(map inc %) false #(map dec %)} ())    
    (c/piecewise-map even? {true inc} ())
    (c/piecewise-pmap even? {true inc} ())
    (c/partition-map even? {true #(map inc %)} ())
    (c/partition-pmap even? {true #(map inc %)} ())
    (c/piecewise-map #(mod % 3) {:default dec 0 inc} ())
    (c/piecewise-pmap #(mod % 3) {:default dec 0 inc} ())
    (c/partition-map #(mod % 3) {:default #(map dec %) 0 #(map inc %)} ())
    (c/partition-pmap #(mod % 3) {:default #(map dec %) 0 #(map inc %)} ()))
  (are [x] (= coll-6 x)
    (c/partition-map even? {false (constantly ())} (range 100))
    (c/partition-map even? {false (constantly nil)} (range 100))
    (c/partition-pmap even? {false (constantly ())} (range 100))
    (c/partition-pmap even? {false (constantly nil)} (range 100))))

(deftest unit:locking-vswap!
  (let [v1 (volatile! 0)
        v2 (volatile! 0)
        f (fn [x] (Thread/sleep (rand-int 20)) (inc x))
        finished-1 (->> (for [n (range 30)]
                          (future (vswap! v1 f)))
                        (map deref)
                        (doall))
        finished-2 (->> (for [n (range 30)]
                          (future (c/locking-vswap! v2 f)))
                        (map deref)
                        (doall))]
    (is (not= @v1 30) "clojure.core/vswap! was strangely consistent!")
    (is (= @v2 30) "utiliva.core/locking-vswap! was not consistent!")))
