(ns onyx.peer.operation
  (:require [onyx.extensions :as extensions]
            [taoensso.timbre :refer [info]]))

(defn apply-fn [f params segment]
  ((reduce #(partial %1 %2) f params) segment))

(defn resolve-fn [task-map]
  (try
    (let [user-ns (symbol (name (namespace (:onyx/fn task-map))))
          user-fn (symbol (name (:onyx/fn task-map)))]
      (or (ns-resolve user-ns user-fn) (throw (Exception.))))
    (catch Exception e
      (throw (ex-info "Could not resolve function in catalog" {:fn (:onyx/fn task-map)})))))

(defn sentinel-node-name [task-id input-name]
  (format "%s-%s" task-id input-name))

(defn vote-for-sentinel-leader [log task-id input-name uuid]
  (let [node (sentinel-node-name task-id input-name)]
    (extensions/write-chunk log :sentinel {:leader uuid} node)))

(defn filter-sentinels [decompressed]
  (remove (partial = :done) decompressed))

(defn learned-all-sentinels? [event state]
  (= (into #{} (keys (:learned-sentinel state)))
     (into #{} (keys (:onyx.core/ingress-queues event)))))

(defn drained-all-inputs? [event state]
  (let [drained-inputs (keys (into {} (filter second (:drained-inputs state))))]
    (= (into #{} drained-inputs)
       (into #{} (keys (:onyx.core/ingress-queues event))))))

(defn on-last-batch
  [event f]
  {})

(defn start-lifecycle?
  [{:keys [onyx.core/queue onyx.core/ingress-queues onyx.core/task-map]}]
  true)

(def site-fns
  {:send extensions/send-peer-site
   :acker extensions/acker-peer-site
   :completion extensions/completion-peer-site})

(defn peer-link
  [{:keys [onyx.core/messenger onyx.core/state] :as event} peer-id link-type]
  (try
    (if-let [link (get-in @state [peer-id link-type])]
      link
      (let [site ((get site-fns link-type) messenger event)
            link (extensions/connect-to-peer messenger event site)]
        (swap! state assoc-in [peer-id link-type] link)
        link))
    (catch Exception e
      (.printStackTrace e))))

