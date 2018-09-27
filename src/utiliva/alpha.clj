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

(ns utiliva.alpha)

(defn mreduce
  "(alpha) Coordinated reduction across multiple collections. At each step, (sel cs)
  is used to select the collection to be used in the next step of the reduction.
  Terminates when all collections have been consumed."
  [sel f init & cs]
  (assert (<= 0 (count cs)))
  (loop [x init
         cs (map seq cs)]
    (if (empty? cs)
      x
      (let [sel (sel cs)
            cs (filter identity (map #(if (= sel %) (next %) %) cs))]
        (recur (f x (first sel))
               cs)))))

(defn sreduce
  "(alpha) Selectively reduce. Just like reduce, but takes an additional argument,
  (fn sel ([coll]) ([val coll])), which is expected to select one value out of the collection,
  returning a vector of the form [selected-item all-other-items]. It reduces over the collection
  but in the order that items are selected. If no initial value is supplied, the selector fn
  is called with its single arity. At all other times, it is passed the current state of the
  reduction as the first argument and the remainder of the collection as the second argument." ;; TODO: explain gooder
  ([sel f coll]
   (let [[init coll] (sel coll)]
     (sreduce sel f init coll)))
  ([sel f init coll]
   (loop [[nxt coll] (sel init coll)
          v init]
     (let [res (f v nxt)]
       (if (reduced? res)
         @res
         (if (seq coll)
           (recur (sel res coll) res)
           res))))))

(defn fill-vec
  "(alpha) Examines each item in a sequence in turn, replacing nils with fill-val.
  Returns a vector."
  [fill-val s]
  (mapv (fnil identity fill-val) s))

(defn retry-until
  "(alpha) Calls function f with args, and until (done-pred retries result)
  returns true, will re-invoke function f after waiting wait-time ms.

  wait-time may be an integer value of ms to wait, or a function that
  accepts the current retry-count and returns the number of ms to wait."
  [done-pred wait-time f & args]
  (loop [retries 0]
    (let [result (try (apply f args)
                      (catch Throwable e e))]
      (if-not (done-pred retries result)
        (do (Thread/sleep (long (if (fn? wait-time) (wait-time retries) wait-time)))
            (recur (inc retries)))
        (if (instance? Throwable result)
          (throw result)
          result)))))
