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

(ns utiliva.recursion)

(defn ^:dynamic *on-expansion*
  "Called whenever operation expr is expanded.
  Passed the source and result exprs.
  Default implementation is no-op.
  Re-bind (see 'binding) to a side effecting function
  to do things like trace the expansion process."
  [expr result])

(defn recursive-expansion
  ([expander input]
   (recursive-expansion 1000 expander input))
  ([max-iter expander input]
   (when (< max-iter 0) (throw
                         (ex-info "Maximum recursive expansion iterations exceeded: terminating expansion."
                                  {:error :recursive-expansion/iterations
                                   :input input
                                   :expander expander})))
   (let [output (expander input)]
     (if (= output input)
       input
       (do (*on-expansion* input output)
           (recur (dec max-iter) expander output))))))

