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

(ns utiliva.plasmodesma
  "As the plasmodesma provides a mechanism for communication and nutrients to
  pass through cell walls in plants, so this namespace provides mechanisms for
  specific dynamic vars to pass across thread boundaries. It only works if
  permeating vars are declared with def-dyn, and if the clojure.core fns/macros
  bound-fn, bound-fn*, pmap, and future are replaced by the equivalents defined
  here."
  (:require [tesserae.core :as tess])
  (:refer-clojure :exclude [bound-fn bound-fn* pmap future]))

(def registry (atom #{}))

(defmacro def-dyn
  "This is just a def that also happens to register the new var in
  utiliva.plasmodesma/registry."
  [var val]
  (assert (:dynamic (meta var)) "def-dyn only works for dynamic vars.")
  `(->> (def ~var ~val)
        (swap! registry conj)))

(defn bound-fn*
  "This mimics clojure.core/bound-fn*, but only passes along local bindings for
  vars present in eva.concurrent.plasmodesma/registry."
  [f]
  (let [env (select-keys (get-thread-bindings) @registry)]
    (fn [& args]
      (apply with-bindings* env f args))))

(defmacro bound-fn
  "This mimics clojure.core/bound-fn, but only passes along local bindings for
  vars present in utiliva.plasmodesma/registry."
  [& fntail]
  `(bound-fn* (fn ~@fntail)))

(defn pmap
  "This wraps f in utiliva.plasmodesma/bound-fn*, then delegates to pmap."
  ([f coll]
   (clojure.core/pmap (bound-fn* f) coll))
  ([f coll & colls]
   (apply clojure.core/pmap (bound-fn* f) coll colls)))

(defmacro future
  "This mimics clojure.core/future, but uses utiliva.plasmodesma/bound-fn
  and returns a tessera."
  [& body]
  `(tess/future-call (bound-fn [] ~@body)))
