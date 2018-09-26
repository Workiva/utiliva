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

(ns utiliva.uuid-test
  (:require [clojure.test :refer :all]
            [utiliva.uuid :refer :all]))

(deftest squuid-construction-and-time-extraction
  (let [msec (System/currentTimeMillis)
        uuid (squuid msec)
        expected-ms (* 1000 (quot msec 1000))]
    (is (= expected-ms (squuid-time-millis uuid)))

    (let [s1 (squuid)
          _ (Thread/sleep 1000)
          s2 (squuid)]

      (is (= -1 (compare s1 s2)))
      (is (= 1 (compare s2 s1))))))
