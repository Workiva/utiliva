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

(ns utiliva.sorted-cache
  (:require [clojure.core.cache :as c]))

(c/defcache SortedLRUCache [^clojure.lang.Sorted scache lru tick limit]
  ;; copying & extending LRU impl from clojure.core.cache
  c/CacheProtocol
  (lookup [_ item]
          (get scache item))
  (lookup [_ item not-found]
          (get scache item not-found))
  (has? [_ item]
        (contains? scache item))
  (hit [_ item]
       (let [tick+ (inc tick)]
         (SortedLRUCache. scache
                          (if (contains? scache item)
                            (assoc lru item tick+)
                            lru)
                          tick+
                          limit)))
  (miss [_ item result]
        (let [tick+ (inc tick)]
          (if (>= (count lru) limit)
            (let [k (if (contains? lru item)
                      item
                      (first (peek lru))) ;; minimum-key, maybe evict case
                  c (-> scache (dissoc k) (assoc item result))
                  l (-> lru (dissoc k) (assoc item tick+))]
              (SortedLRUCache. c l tick+ limit))
            (SortedLRUCache. (assoc scache item result)  ;; no change case
                             (assoc lru item tick+)
                             tick+
                             limit))))
  (evict [this key]
         (let [v (get scache key ::miss)]
           (if (= v ::miss)
             this
             (SortedLRUCache. (dissoc scache key)
                              (dissoc lru key)
                              (inc tick)
                              limit))))
  (seed [_ base]
        (let [init-cache (into (clojure.data.priority-map/priority-map)
                               (for [[k _] base] [k 0]))]
          (SortedLRUCache. base init-cache 0 limit)))

  Object
  (toString [_] (str scache \, \space lru \, \space tick \, \space limit))

  ;; the three lines of interesting extension
  clojure.lang.Sorted
  (comparator [this]           (.comparator scache))
  (entryKey [this k]           (.entryKey scache k))
  (seqFrom [this k ascending?] (.seqFrom scache k ascending?)))

(defn sorted-lru-cache-factory
  "Same as clojure.core.cache/lru-cache-factory but the returned object
   implements clojure.lang.Sorted."
  [base & {threshold :threshold :or {threshold 32}}]
  {:pre [(number? threshold) (< 0 threshold)
         (map? base)]}
  (let [base (if-not (instance? clojure.lang.Sorted base)
               (into (sorted-map) base) base)]
    (clojure.core.cache/seed
     (SortedLRUCache. (sorted-map)
                      (clojure.data.priority-map/priority-map)
                      0
                      threshold)
     base)))
