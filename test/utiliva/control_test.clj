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

(ns utiliva.control-test
  (:require [utiliva.control :refer :all]
            [clojure.test :refer :all]))

(deftest unit:?->>
  (is (= -2
         (?->> 0
               (even?) (inc) ;; handles parens
               odd? inc ;; and no parens
               odd? (+ 10) ;; ignores failing conditions
               (odd?) (+ 10) ;; ignores failing conditions
               (< 1) inc ;; thread last in condition
               odd? (- 1))))) ;; thread last in expression

(deftest unit:?->
  (is (= 2
         (?->> 0
               (even?) (inc) ;; handles parens
               odd? inc ;; and no parens
               odd? (+ 10) ;; ignores failing conditions
               (odd?) (+ 10) ;; ignores failing conditions
               (> 1) inc ;; thread first in condition
               odd? (- 1))))) ;;thread first in expression

(deftest unit:->>?->
  (is (= 2
         (->>?-> 0
                 (even?) (inc) ;; handles parens
                 odd? inc ;; and no parens
                 odd? (+ 10) ;; ignores failing conditions
                 (odd?) (+ 10) ;; ignores failing conditions
                 (< 1) inc ;; thread last in condition
                 odd? (- 1))))) ;; thread first in expression

(deftest unit:->?->>
  (is (= -2
         (->?->> 0
                 (even?) (inc) ;; handles parens
                 odd? inc ;; and no parens
                 odd? (+ 10) ;; ignores failing conditions
                 (odd?) (+ 10) ;; ignores failing conditions
                 (> 1) inc ;; thread first in condition
                 odd? (- 1))))) ;; thread last in expression

(deftest unit:locking-when
  (testing "locks lock"
    (let [some-val 2
          lock (Object.)
          p (promise)]
      (future
        (locking-when (even? some-val) lock
                      (Thread/sleep 1000)
                      3))
      (Thread/sleep 10)
      (future
        (locking-when (= 2 some-val) lock
                      (deliver p 3)))
      (is (= ::timed-out
             (deref p 10 ::timed-out)))))
  (testing "locks don't lock"
    (let [some-val 3
          lock (Object.)
          p (promise)]
      (future
        (locking-when (even? some-val) lock
                      (Thread/sleep 1000)
                      3))
      (Thread/sleep 10)
      (future
        (locking-when (= 2 some-val) lock
                      (deliver p 3)))
      (is (= 3
             (deref p 10 ::timed-out))))))

(deftest unit:guarded-let
  (let [order-of-resolution (atom [])
        guard-fn (fn [x] (swap! order-of-resolution conj x))]
    (is (thrown-with-msg?
         Exception
         #"Oops!"
         (guarded-let guard-fn
                      [x 0
                       y (+ 1 x)
                       z (- y x 3)
                       ohno (throw (Exception. "Oops!"))]
                      :some-value)))
    (is (= [-2 1 0] @order-of-resolution))))
