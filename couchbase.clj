;; src/jepsen/tests/couchbase.clj
(ns jepsen.tests.couchbase
  (:require
    [jepsen.control           :as c]
    [jepsen.core              :as j]
    [jepsen.db                :as db]
    [jepsen.generator         :as gen]
    [jepsen.checker           :as checker]
    [jepsen.report            :as report]
    [jepsen.nemesis           :as nemesis]
    [clojure.string           :as str]))

;; -- 1) DB setup / teardown -----------------------------------------------
(defrecord CouchbaseDB []
  db/DB
  (setup!   [_ test node]   ;; no‐op, cluster already up
    nil)
  (teardown![_ test node]
    nil))

(defn couchbase-db [] (->CouchbaseDB))

;; -- 2) Client ------------------------------------------------------------
(defrecord CouchbaseClient []
  db/Client
  (open!    [_ test node]
    ;; use couchbase-cli / SDK to initialize per‐client state if needed
    nil)
  (invoke!  [_ test op]
    (case (:f op)
      :write
      (do
        ;; replace with cb SDK write to key (:key op), value (:value op)
        (assoc op :type :ok))

      :read
      (let [v ;; perform read from Couchbase
            nil]
        (assoc op :type :ok :value v)))

  (teardown![_ test]
    nil))

(defn couchbase-client [] (->CouchbaseClient))

;; -- 3) Workload generator + nemeses --------------------------------------
(defn mixed-workload []
  (->> (gen/phases
         ;; 15 clients cycling through 20 writes then 20 reads
         (gen/clients
           (gen/each
             (concat
               (repeat 20 {:f :write})
               (repeat 20 {:f :read}))
             {:key   (gen/random-key)
              :value (fn [_ op] (+ (* 1000 (:process op)) (:process op)))})
           15)
         ;; inject faults concurrently
         (gen/nemesis
           (gen/seq
             [(nemesis/partition-random-halves {:duration 30000})
              (nemesis/kill-random-node    {:duration 10000})
              (nemesis/partition-random-latency {:latency 200 :duration 30000})])))
       gen/stop))

;; -- 4) The test ----------------------------------------------------------
(defn couchbase-test
  []
  (j/test
    {:name        "couchbase"
     :db          (couchbase-db)
     :client      (couchbase-client)
     :generator   (mixed-workload)
     :checker     (checker/linearizable)
     :reporters   [report/html   report/ci]
     :nemesis     {:partition  nemesis/partition-random-halves
                   :kill       nemesis/kill-random-node
                   :latency    nemesis/partition-random-latency}
     :times       10    ;; run 10 iterations
     :time-limit  300000  ;; 5 minutes total (ms)
     }))

;; entry point
(defn -main [& args]
  (j/run! (couchbase-test)))
