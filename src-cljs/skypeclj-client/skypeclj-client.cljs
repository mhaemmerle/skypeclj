(ns skypeclj-client.core)

(defn- on-open
  [event]
  (.log js/console "connection open"))

(defn- on-message
  [event]
  (.log js/console "received message" event))

(defn- on-error
  [event]
  (.log js/console "error" event))

(defn ^:export init
  [user-id]
  (doto (js/EventSource. (str "http://localhost:4000/562/events"))
    (.addEventListener "open" on-open)
    (.addEventListener "message" on-message)
    (.addEventListener "error" on-error)))
