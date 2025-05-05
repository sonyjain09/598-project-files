;; src/jepsen/tests/foundationdb.clj
(ns jepsen.tests.foundationdb
  (:require
    [jepsen.control           :as c]
    [jepsen.core              :as j]
    [jepsen.db                :as db]
    [jepsen.generator         :as gen]
    [jepsen.checker           :as checker]
    [jepsen.report            :as report]
    [jepsen.nemesis           :as nemesis]
    [clojure.string           :as str]))

(defrecord FDBDB []
  db/DB
  (setup!   [_ test node]
    ;; initialize fdbcli cluster if needed
    nil)
  (teardown![_ test node] nil))

(defn fdb-db [] (->FDBDB))

(defrecord FDBClient []
  db/Client
  (open!    [_ test node] nil)
  (invoke!  [_ test op]
    (case (:f op)
      :write (assoc op :type :ok)
      :read  (assoc op :type :ok :value nil)))
  (teardown![_ test] nil))

(defn fdb-client [] (->FDBClient))

(defn mixed-workload []
  (->> (gen/phases
         (gen/clients
           (gen/each
             (concat
               (repeat 20 {:f :write})
               (repeat 20 {:f :read}))
             {:key   (gen/random-key)
              :value (fn [_ op] (+ (* 1000 (:process op)) (:process op)))})
           15)
         (gen/nemesis
           (gen/seq
             [(nemesis/partition-random-halves {:duration 30000})
              (nemesis/kill-random-node    {:duration 10000})
              (nemesis/partition-random-latency {:latency 200 :duration 30000})])))
       gen/stop))

(defn foundationdb-test []
  (j/test
    {:name        "foundationdb"
     :db          (fdb-db)
     :client      (fdb-client)
     :generator   (mixed-workload)
     :checker     (checker/linearizable)
     :reporters   [report/html report/ci]
     :nemesis     {:partition nemesis/partition-random-halves
                   :kill      nemesis/kill-random-node
                   :latency   nemesis/partition-random-latency}
     :times       10
     :time-limit 300000}))

(defn -main [& args]
  (j/run! (foundationdb-test)))
