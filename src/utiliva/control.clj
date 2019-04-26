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

(ns utiliva.control
  "Control forms.")

(defmacro ?->>
  "Like cond->>, but threads the argument through the conditions as well.
  (?->> 4 even? (assoc {} :four))"
  [expr & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        pstep (fn [[test step]] `(if (->> ~g ~test) (->> ~g ~step) ~g))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (map pstep (partition 2 clauses)))]
       ~g)))

(defmacro ?->
  "Like cond->, but threads the argument through the conditions as well.
  (?-> 4 even? inc odd? inc neg? inc)"
  [expr & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        pstep (fn [[test step]] `(if (-> ~g ~test) (-> ~g ~step) ~g))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (map pstep (partition 2 clauses)))]
       ~g)))

(defmacro ->>?->
  "Like ?->, but the conditions are threaded with ->> and the results are
  threaded with ->.
  (->>?-> 4 (> 2) inc (> 6) dec)"
  [expr & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        pstep (fn [[test step]] `(if (->> ~g ~test) (-> ~g ~step) ~g))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (map pstep (partition 2 clauses)))]
       ~g)))

(defmacro ->?->>
  "Like cond->, but threads the argument through the conditions as well.
  (->?->> 4 (> 2) inc (> 6) dec)"
  [expr & clauses]
  (assert (even? (count clauses)))
  (let [g (gensym)
        pstep (fn [[test step]] `(if (-> ~g ~test) (->> ~g ~step) ~g))]
    `(let [~g ~expr
           ~@(interleave (repeat g) (map pstep (partition 2 clauses)))]
       ~g)))

(defmacro locking-when
  "Like locking, but only locks when lock? evaluates as true."
  [lock? x & body]
  `(let [body-fn# (fn [] ~@body)]
     (if ~lock?
       (locking ~x (body-fn#))
       (body-fn#))))

(defmacro guarded-let
  "Binds symbols as in let. If an exception is thrown within the
  block of bindings, all symbols already successfully bound will
  have guard-fn called on them, in reverse order, for side-effects."
  [guard-fn bindings & body]
  (let [stack (gensym)
        transformation (fn [[sym init]]
                         (conj (if (= sym '_)
                                 ()
                                 (list '_ `(vswap! ~stack conj ~sym)))
                               init sym))
        bindings (sequence (comp (partition-all 2)
                                 (mapcat transformation))
                           bindings)
        body (cons `(vswap! ~stack empty) body)]
    `(let [~stack (volatile! ())
           guard-fn# ~guard-fn]
       (try
         (let ~(vec bindings)
           ~@body)
         (finally
           (doseq [item# @~stack]
             (guard-fn# item#)))))))
