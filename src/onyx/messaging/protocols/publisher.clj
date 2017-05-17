(ns onyx.messaging.protocols.publisher
  (:refer-clojure :exclude [key]))

(defprotocol Publisher
  (info [this])
  (equiv-meta [this pub-info])
  (start [this])
  (stop [this])
  (set-short-id! [this new-short-id])
  (set-replica-version! [this new-replica-version])
  (set-epoch! [this new-epoch])
  (set-endpoint-peers! [this new-peers])

  (short-id [this])
  (endpoint-status [this])
  (statuses [this])
  (alive? [this])
  (ready? [this])

  (poll-heartbeats! [this])
  (heartbeat! [this])
  (reset-segment-encoder! [this])
  (retry-encode-segment! [this])
  (encode-segment! [this bs])
  (offer-ready! [this])
  (offer-heartbeat! [this])
  (offer-segments! [this])
  (offer! [this buf length endpoint-epoch])
  (key [this]))
