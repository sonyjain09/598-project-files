(ns jepsen.tests.couchbase
  (:require [jepsen [cli :as cli]
                    [control :as c]
                    [db :as db]
                    [generator :as gen]
                    [nemesis :as nemesis]
                    [tests :as tests]
                    [checker :as checker]
                    [client :as client]]
            [jepsen.os.debian :as debian]))

(defrecord CouchbaseClient []
  client/Client
  (setup! [_ test node]
    ; Setup Couchbase client connection
    )
  (invoke! [_ test op]
    ; Perform operations (read/write)
    (assoc op :type :ok))
  (teardown! [_ test]
    ; Cleanup after tests
    ))

(defn couchbase-test
  [opts]
  (merge tests/noop-test
         {:name "couchbase"
          :os debian/os
          :db (reify db/DB
                (setup! [_ test node]
                  (c/exec :echo "Setup Couchbase on node" node))
                (teardown! [_ test node]
                  (c/exec :echo "Teardown Couchbase on node" node)))
          :client (CouchbaseClient.)
          :nemesis (nemesis/partition-random-halves)
          :generator (gen/phases
                       (gen/clients
                         (gen/mix [
                           (gen/once {:type :write, :value 1})
                           (gen/once {:type :read})
                         ]))
                       (gen/time-limit (:time-limit opts)))
          :checker (checker/linearizable)}
         opts))

(defn -main
  [& args]
  (cli/run! (merge (cli/single-test-cmd {:test-fn couchbase-test})
                   {:concurrency 3
                    :time-limit 20})
            args))
