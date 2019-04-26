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

(ns utiliva.macros
  (:require [backtick :refer [resolve-symbol]]))

(defn- class-sym*
  [sym]
  (try
    (-> sym resolve-symbol name Class/forName)
    (catch Throwable e)))

(defn class-sym?
  [sym]
  (boolean (class-sym* sym)))

(defn sym->class
  [sym]
  (if-let [class (class-sym* sym)]
    class
    (throw (IllegalArgumentException. (format "Unable to resolve class-name: %s" (pr-str sym))))))

(defmacro when-class
  "When the class can be found, this expands to the body; when not, it suppresses it."
  [class & body]
  (when (class-sym? class)
    `(do ~@body)))

(defmacro if-class
  "When the class can be found, this expands to the form in the `then` clause;
  when not, it expands to the form in the `else` clause."
  [class then else]
  (if (class-sym? class) then else))
