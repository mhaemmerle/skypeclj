(ns skypeclj.logger
  (:use camel-snake-kebab
        [lamina core executor]
        [aleph http formats]
        [cheshire.core :only [generate-string parse-string]]
        compojure.core)
  (:require [clojure.tools.logging :as log]
            [compojure.route :as route]
            [hiccup.page :refer [html5 include-js include-css]]
            [hiccup.element :refer [javascript-tag]]
            [clj-time.format :refer [formatters unparse]]
            [clj-time.coerce :refer [from-long]])
  (:import [com.skype.api Conversation Message]))

(set! *warn-on-reflection* true)

(def aleph-stop (atom nil))
(def conversations (atom {}))

(def stylesheet "http://thomasf.github.com/solarized-css/solarized-dark.min.css")
;; (def stylesheet "http://thomasf.github.com/solarized-css/solarized-light.min.css")

(def hour-minute-formatter (formatters :hour-minute))

(defn init-conversations
  [c]
  (doseq [^Conversation conversation c]
    (let [oid-keyword (keyword (str (.getOid conversation)))
          display-name (.getDisplayName conversation)
          identity (.getIdentity conversation)
          data {:display-name display-name :identity identity :messages []}]
      (swap! conversations assoc oid-keyword data))))

(defn log-message
  [^Conversation conversation ^Message message]
  (let [oid-keyword (keyword (str (.getOid conversation)))
        display-name (.getDisplayName conversation)
        identity (.getIdentity conversation)
        tagged-message {:author (.getAuthor message)
                        :author-display-name (.getAuthorDisplayName message)
                        :timestamp (* 1000 (.getTimestamp message))
                        :message-body (.getBodyXml message)}]
    (swap! conversations update-in [oid-keyword :messages] conj tagged-message))
  nil)

(defn wrap-bounce-favicon [handler]
  (fn [req]
    (if (= [:get "/favicon.ico"] [(:request-method req) (:uri req)])
      {:status 404
       :headers {}
       :body "Page not found"}
      (handler req))))

(defn index-handler
  ([request]
     (index-handler request nil))
  ([request c]
     {:status 200
      :headers {"Content-Type" "text/html; charset=utf-8"}
      :body (html5
             [:head
              [:title ""]
              (include-css stylesheet)]
             [:body
              (for [[id data] @conversations]
                [:div (:display-name data)
                 (for [{:keys [author author-display-name
                               timestamp message-body]} (:messages data)]
                   [:div {:class "info"}
                    (let [hour-min (unparse hour-minute-formatter (from-long timestamp))]
                      [:a {:href (str "#" timestamp) :name timestamp} hour-min])
                    author-display-name message-body
                    [:br]])])
              (javascript-tag "var CLOSURE_NO_DEPS = true;")
              (include-js "/js/main.js")
              (javascript-tag "skypeclj_client.core.init()")])}))

(defn- encode-event-data
  [data]
  (str (format "data: %s\n\n" (generate-string data))))

;; topics == oid's

(defn register-event-listener
  [event-channel topic]
  )

(defn deregister-event-listener
  [event-channel]
  )

(defn events-handler
  [response-channel request]
  (let [{{:keys [conversation-oid]} :route-params} request
        _ (log/info "events-handler" conversation-oid)
        event-channel (channel)]
    (register-event-listener event-channel nil)
    (enqueue response-channel
             {:status 200
              :headers {"Content-Type" "text/event-stream"}
              :body (map* encode-event-data event-channel)})))

(def handlers
  (routes
   (GET "/" [] index-handler)
   (GET "/:conversation" [conversation] (index-handler conversation))
   (GET "/:conversation/events" [conversation] (wrap-aleph-handler events-handler))
   (route/resources "/")
   (route/not-found "Page not found")))

(def app
  (-> handlers
      wrap-bounce-favicon))

(defn stop
  []
  (@aleph-stop))

(defn start
  [host port]
  (let [wrapped-handler (wrap-ring-handler app)
        stop-fn (start-http-server wrapped-handler {:port port})]
    (reset! aleph-stop stop-fn)
    stop-fn))
