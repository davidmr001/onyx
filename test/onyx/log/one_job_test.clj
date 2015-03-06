(ns onyx.log.one-job-test
  (:require [clojure.core.async :refer [chan >!! <!! close!]]
            [com.stuartsierra.component :as component]
            [onyx.log.entry :refer [create-log-entry]]
            [onyx.extensions :as extensions]
            [onyx.log.util :as util]
            [onyx.api :as api]
            [midje.sweet :refer :all]
            [zookeeper :as zk]))

(def onyx-id (java.util.UUID/randomUUID))

(def config (read-string (slurp (clojure.java.io/resource "test-config.edn"))))

(def scheduler :onyx.job-scheduler/greedy)

(def env-config
  {:hornetq/mode :udp
   :hornetq/server? true
   :hornetq.server/type :embedded
   :hornetq.udp/cluster-name (:cluster-name (:hornetq config))
   :hornetq.udp/group-address (:group-address (:hornetq config))
   :hornetq.udp/group-port (:group-port (:hornetq config))
   :hornetq.udp/refresh-timeout (:refresh-timeout (:hornetq config))
   :hornetq.udp/discovery-timeout (:discovery-timeout (:hornetq config))
   :hornetq.embedded/config (:configs (:hornetq config))
   :zookeeper/address (:address (:zookeeper config))
   :zookeeper/server? true
   :zookeeper.server/port (:spawn-port (:zookeeper config))
   :onyx.peer/job-scheduler scheduler
   :onyx/id onyx-id})

(def peer-config
  {:hornetq/mode :udp
   :hornetq.udp/cluster-name (:cluster-name (:hornetq config))
   :hornetq.udp/group-address (:group-address (:hornetq config))
   :hornetq.udp/group-port (:group-port (:hornetq config))
   :hornetq.udp/refresh-timeout (:refresh-timeout (:hornetq config))
   :hornetq.udp/discovery-timeout (:discovery-timeout (:hornetq config))
   :zookeeper/address (:address (:zookeeper config))
   :onyx/id onyx-id
   :onyx.peer/inbox-capacity (:inbox-capacity (:peer config))
   :onyx.peer/outbox-capacity (:outbox-capacity (:peer config))
   :onyx.peer/job-scheduler scheduler
   :onyx.peer/state {:task-lifecycle-fn util/stub-task-lifecycle}})

(def env (onyx.api/start-env env-config))

(def n-peers 5)

(def v-peers (onyx.api/start-peers n-peers peer-config))

(def catalog
  [{:onyx/name :a
    :onyx/ident :hornetq/read-segments
    :onyx/type :input
    :onyx/medium :hornetq
    :onyx/batch-size 20}

   {:onyx/name :b
    :onyx/fn :onyx.peer.single-peer-test/my-inc
    :onyx/type :function
    :onyx/batch-size 20}

   {:onyx/name :c
    :onyx/ident :hornetq/write-segments
    :onyx/type :output
    :onyx/medium :hornetq
    :onyx/batch-size 20}])

(onyx.api/submit-job
 peer-config
 {:workflow [[:a :b] [:b :c]]
  :catalog catalog
  :task-scheduler :onyx.task-scheduler/round-robin})

(def ch (chan n-peers))

(def subscription (onyx.api/subscribe-to-log peer-config ch))

(def log (:log (:env subscription)))

(def replica
  (loop [replica (:replica subscription)]
    (let [position (<!! ch)
          entry (extensions/read-log-entry log position)
          new-replica (extensions/apply-log-entry entry replica)
          counts (map count (mapcat vals (vals (:allocations new-replica))))]
      (when-not (= (into #{} counts) #{1 2})
        (recur new-replica)))))

(onyx.api/shutdown-env (:env subscription))

(fact "peers balanced on 1 jobs" true => true)

(doseq [v-peer v-peers]
  (onyx.api/shutdown-peer v-peer))

(onyx.api/shutdown-env env)

