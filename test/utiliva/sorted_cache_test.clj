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

(ns utiliva.sorted-cache-test
  "testing the sorted cache impl"
  (:require [utiliva.sorted-cache :refer :all]
            [clojure.core.cache :refer [hit]]
            [clojure.test :refer :all])
  (:import [utiliva.sorted_cache SortedLRUCache]))

(defn do-dot-lookup-tests [c]
  (are [expect actual] (= expect actual)
    1   (.lookup ^SortedLRUCache c :a)
    2   (.lookup ^SortedLRUCache c :b)
    42  (.lookup ^SortedLRUCache c :c 42)
    nil (.lookup ^SortedLRUCache c :c)))

(defn do-ilookup-tests [c]
  (are [expect actual] (= expect actual)
    1   (:a c)
    2   (:b c)
    42  (:X c 42)
    nil (:X c)))

(defn do-assoc [c]
  (are [expect actual] (= expect actual)
    1   (:a (assoc c :a 1))
    nil (:a (assoc c :b 1))))

(defn do-dissoc [c]
  (are [expect actual] (= expect actual)
    2   (:b (dissoc c :a))
    nil (:a (dissoc c :a))
    nil (:b (-> c (dissoc :a) (dissoc :b)))
    0   (count (-> c (dissoc :a) (dissoc :b)))))

(defn do-getting [c]
  (are [actual expect] (= expect actual)
    (get c :a) 1
    (get c :e) nil
    (get c :e 0) 0
    (get c :b 0) 2
    (get c :f 0) nil

    (get-in c [:c :e]) 4
    (get-in c '(:c :e)) 4
    (get-in c [:c :x]) nil
    (get-in c [:f]) nil
    (get-in c [:g]) false
    (get-in c [:h]) nil
    (get-in c []) c
    (get-in c nil) c

    (get-in c [:c :e] 0) 4
    (get-in c '(:c :e) 0) 4
    (get-in c [:c :x] 0) 0
    (get-in c [:b] 0) 2
    (get-in c [:f] 0) nil
    (get-in c [:g] 0) false
    (get-in c [:h] 0) 0
    (get-in c [:x :y] {:y 1}) {:y 1}
    (get-in c [] 0) c
    (get-in c nil 0) c))

(defn do-finding [c]
  (are [expect actual] (= expect actual)
    (find c :a) [:a 1]
    (find c :b) [:b 2]
    (find c :c) nil
    (find c nil) nil))

(defn do-contains [c]
  (are [expect actual] (= expect actual)
    (contains? c :a) true
    (contains? c :b) true
    (contains? c :c) false
    (contains? c nil) false))

(defn do-sorted [c]
  (are [expect actual] (= expect actual)
    (subseq c > :a) [[:b 2] [:c {:d 3, :e 4}] [:f nil] [:g false]]
    (subseq c > :f) [[:g false]]))

(def big-map (into (sorted-map) {:a 1, :b 2, :c {:d 3, :e 4}, :f nil, :g false, nil {:h 5}}))
(def small-map (into (sorted-map) {:a 1 :b 2}))

(deftest test-sorted-lru-cache-ilookup
  (testing "that the SortedLRUCache can lookup via keywords"
    (do-ilookup-tests (SortedLRUCache. small-map {} 0 2)))
  (testing "that the SortedLRUCache can lookup via keywords"
    (do-dot-lookup-tests (SortedLRUCache. small-map {} 0 2)))
  (testing "assoc and dissoc for SortedLRUCache"
    (do-assoc (SortedLRUCache. {} {} 0 2))
    (do-dissoc (SortedLRUCache. {:a 1 :b 2} {} 0 2)))
  (testing "that get and cascading gets work for SortedLRUCache"
    (do-getting (SortedLRUCache. big-map {} 0 2)))
  (testing "that finding works for SortedLRUCache"
    (do-finding (SortedLRUCache. small-map {} 0 2)))
  (testing "that contains? works for SortedLRUCache"
    (do-contains (SortedLRUCache. small-map {} 0 2)))
  (testing "that SortedLRUCache supports subseq"
    (do-sorted (SortedLRUCache. big-map {} 0 2))))

(deftest test-lru-cache
  (testing "LRU-ness with empty cache and threshold 2"
    (let [C (sorted-lru-cache-factory (sorted-map) :threshold 2)]
      (are [x y] (= x y)
        {:a 1, :b 2} (-> C (assoc :a 1) (assoc :b 2) .scache)
        {:b 2, :c 3} (-> C (assoc :a 1) (assoc :b 2) (assoc :c 3) .scache)
        {:a 1, :c 3} (-> C (assoc :a 1) (assoc :b 2) (.hit :a) (assoc :c 3) .scache))))
  (testing "LRU-ness with seeded cache and threshold 4"
    (let [C (sorted-lru-cache-factory {:a 1, :b 2} :threshold 4)]
      (are [x y] (= x y)
        {:a 1, :b 2, :c 3, :d 4} (-> C (assoc :c 3) (assoc :d 4) .scache)
        {:a 1, :c 3, :d 4, :e 5} (-> C (assoc :c 3) (assoc :d 4) (.hit :c) (.hit :a) (assoc :e 5) .scache))))
  (testing "regressions against LRU eviction before threshold met"
    (is (= {:b 3 :a 4}
           (-> (sorted-lru-cache-factory {} :threshold 2)
               (assoc :a 1)
               (assoc :b 2)
               (assoc :b 3)
               (assoc :a 4)
               .scache)))

    (is (= {:e 6, :d 5, :c 4}
           (-> (sorted-lru-cache-factory {} :threshold 3)
               (assoc :a 1)
               (assoc :b 2)
               (assoc :b 3)
               (assoc :c 4)
               (assoc :d 5)
               (assoc :e 6)
               .scache)))

    (is (= {:a 1 :b 3}
           (-> (sorted-lru-cache-factory {} :threshold 2)
               (assoc :a 1)
               (assoc :b 2)
               (assoc :b 3)
               .scache))))

  (is (= {:d 4 :e 5}
         (-> (sorted-lru-cache-factory {} :threshold 2)
             (hit :x)
             (hit :y)
             (hit :z)
             (assoc :a 1)
             (assoc :b 2)
             (assoc :c 3)
             (assoc :d 4)
             (assoc :e 5)
             .scache))))
