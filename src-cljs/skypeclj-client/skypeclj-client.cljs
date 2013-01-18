(ns skypeclj-client.core
  (:require [crate.core :as crate]
            [goog.dom :as dom]
            [snout.core :as snout])
  (:use [jayq.core :only [$ append delegate data document-ready xhr]])
  (:use-macros [crate.def-macros :only [defpartial defelem]]
               [snout.macros :only [defroute]]))

;; (defroute "/:id/:date"
;;   [id date & args]
;;   (.log js/console "id" id "date" date "args" args))
;; (snout/set-token! "/123/2013-01-18")
;; (snout/get-token)

(defelem time-link
  [timestamp hour-min]
  [:a {:href (str "#" timestamp) :name timestamp} hour-min])

(defpartial formatted-message
  [{:keys [guid author timestamp hour-min message]}]
  [:div
   (time-link timestamp hour-min)
   author
   message])

(defn- on-open
  [event]
  (.log js/console "connection open"))

(defn- on-message
  [event]
  (let [event-data (js->clj (JSON/parse (.-data event)) :keywordize-keys true)]
    (.prepend ($ :#conv-container) (formatted-message event-data))))

(defn- on-error
  [event]
  (.log js/console "error" event))

(defn ^:export init
  [id]
  (doto (js/EventSource. (str "http://localhost:4000/" id "/events"))
    (.addEventListener "open" on-open)
    (.addEventListener "message" on-message)
    (.addEventListener "error" on-error)))
