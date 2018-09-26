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

(ns utiliva.uuid
  (:import (java.util UUID)))

(def ^:const +ub32-mask+ 0x00000000ffffffff)

(defn ^UUID squuid
  ([] (squuid (System/currentTimeMillis)))
  ([msec]
   (let [uuid (UUID/randomUUID)
         sec (quot msec 1000)
         lsb (.getLeastSignificantBits uuid)
         msb (.getMostSignificantBits uuid)
         timed-msb (bit-or (bit-shift-left sec 32)
                           (bit-and +ub32-mask+ msb))]
     (UUID. timed-msb lsb))))

(defn squuid-time-millis [^UUID uuid]
  (let [msb (.getMostSignificantBits uuid)
        sec (bit-shift-right msb 32)]
    (* (long sec) 1000)))
