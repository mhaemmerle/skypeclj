(ns skypeclj.logger
  (:use camel-snake-kebab
        [lamina core executor]
        [aleph http formats]
        [cheshire.core :only [generate-string parse-string]]
        compojure.core
        [ring.middleware params keyword-params nested-params session reload])
  (:require [clojure.tools.logging :as log]
            [compojure.route :as route]
            [hiccup.page :refer [html5 include-js include-css]]
            [hiccup.element :refer [javascript-tag]]
            [hiccup.util :refer [escape-html]]
            [hiccup.def :refer [defelem]]
            [ring.util.response :as resp]
            [clj-time.format :refer [formatters unparse]]
            [clj-time.coerce :refer [from-long to-long]]
            [clj-time.core :refer [now]]
            [cemerick.friend :as friend]
            [cemerick.friend.openid :as openid])
  (:import [com.skype.api Conversation Message]))

(set! *warn-on-reflection* true)

(defonce aleph-stop (atom nil))
(defonce message-channel (permanent-channel))

(defonce conversations (atom {}))

(def skype-logs-dir (clojure.java.io/file "skype-logs"))

(defn timestamp-to-date
  [timestamp]
  (unparse (formatters :date) (from-long (* timestamp 1000))))

(defn create-distributor
  []
  (distributor :conv-id
               (fn [facet facet-channel]
                 (log/info "distributor" facet facet-channel (keyword? facet))
                 (let [ch-name facet
                       conv-channel (named-channel ch-name (fn [_]))]
                   (log/info "distributor" ch-name conv-channel)
                   (close-on-idle 5000 facet-channel)
                   (close-on-idle 5000 conv-channel)
                   (ground conv-channel)
                   (siphon facet-channel conv-channel)))))

(defn register-conv-listener
  [conv-id]
  (log/info "register-conv-listener" (keyword? conv-id))
  (let [ch-name (keyword (str conv-id))
        conv-channel (named-channel ch-name (fn [_]))]
    (log/info "register-conv-listener" conv-id ch-name conv-channel)
    (map* :msg (tap conv-channel))))

;; should ignore messages, that are not from today
;; and restore them later on
(defn log-message
  [^Conversation conversation ^Message message]
  (let [oid-keyword (keyword (str (.getOid conversation)))
        display-name (.getDisplayName conversation)
        identity (.getIdentity conversation)
        timestamp (.getTimestamp message)
        ;; date-key (keyword (timestamp-to-date timestamp))
        tagged-message {:author (.getAuthor message)
                        :author-display-name (.getAuthorDisplayName message)
                        :timestamp timestamp
                        :message-body (.getBodyXml message)}]

    ;; TODO move somewhere else
    ;; parent fun should be generic logger-handler and then just to dispatch
    (let [date-time (from-long (* 1000 timestamp))
          hour-min (unparse (formatters :hour-minute) date-time)
          m {:guid nil
             :author (escape-html (.getAuthorDisplayName message))
             :timestamp timestamp
             :hour-min hour-min
             :message (.getBodyXml message)}]
      (enqueue message-channel {:conv-id oid-keyword :msg m}))

    (swap! conversations update-in [oid-keyword :messages] conj tagged-message))
  nil)

(defn ^:private log-unconsumed-messages
  [^Conversation conversation]
  (log/info "log-unconsumed-messages")
  (let [timestamp 1357767070
        last-messages (.getLastMessages conversation timestamp)
        context-messages (.contextMessages last-messages)
        unconsumed-messages (.unconsumedMessages last-messages)]
    (log/info "context-messages" (count context-messages)
              "unconsumed-messages" (count unconsumed-messages))
    (doseq [message unconsumed-messages]
      (log-message conversation message))))

;; TODO check local conversation files
;; does directory exist?
;; get files in directory
(defn init-conversations
  [c]
  (doseq [^Conversation conversation c]
    (let [oid (str (.getOid conversation))
          oid-keyword (keyword oid)
          display-name (.getDisplayName conversation)
          identity (.getIdentity conversation)
          data {:display-name display-name :identity identity :messages [] :oid oid}
          unconsumed-msg-count (.getUnconsumedNormalMessages conversation)
          now-in-seconds (/ (to-long (now)) 1000)]
      (log/info "conversation" data)
      (swap! conversations assoc oid-keyword data)
      (log/info "unconsumed-message-count" unconsumed-msg-count "for" oid-keyword)
      (log/info "consumption-horizon" (.getConsumptionHorizon conversation))
      ;; set horizon to zero to mark all messages as unconsumed
      (.setConsumedHorizon conversation 0 true)
      (when (> unconsumed-msg-count 0)
        (log-unconsumed-messages conversation)
        ;; (.setConsumedHorizon conversation now-in-seconds false)
        ))))

(defn wrap-bounce-favicon [handler]
  (fn [req]
    (if (= [:get "/favicon.ico"] [(:request-method req) (:uri req)])
      {:status 404
       :headers {}
       :body "Page not found"}
      (handler req))))

(defelem time-link
  [timestamp hour-min]
  [:a {:href (str "#" timestamp) :name timestamp} hour-min])

(defn index-handler
  ([request]
     (index-handler request nil))
  ([request conversation-oid]
     {:status 200
      :headers {"Content-Type" "text/html; charset=utf-8"}
      :body (html5
             [:head
              [:title ""]]
             [:body

              (for [[id data] @conversations]
                [:div
                 [:a {:href (str "/" (:oid data))} (:display-name data)]])

              [:br]

              ;; need date component
              (when-let [data (get @conversations (keyword conversation-oid))]
                [:div {:id "conv-container"}
                 (for [{:keys [author author-display-name
                               timestamp message-body]} (:messages data)]
                   (let [date-time (from-long (* 1000 timestamp))
                         hour-min (unparse (formatters :hour-minute) date-time)]
                     [:div
                      (time-link timestamp hour-min)
                      (escape-html author-display-name)
                      message-body]))])

              (javascript-tag "var CLOSURE_NO_DEPS = true;")
              (include-js "//ajax.googleapis.com/ajax/libs/jquery/1.8.3/jquery.min.js")
              (include-js "/js/main.js")
              (javascript-tag (str "skypeclj_client.core.init(" conversation-oid ")"))])}))

(defn- encode-event-data
  [data]
  (str (format "data: %s\n\n" (generate-string data))))

;; conversation id -> topic
(defn events-handler
  [response-channel request]
  (let [{:keys [route-params]} request]
    (log/info "events-handler" route-params)
    (when-let [id (:conversation route-params )]
      (let [event-channel (register-conv-listener id)]
        (on-closed event-channel #(log/info "user has left" id))
        (enqueue response-channel
                 {:status 200
                  :headers {"Content-Type" "text/event-stream"}
                  :body (map* encode-event-data event-channel)})))))

(defroutes app-routes
  (GET "/" [] (friend/authenticated index-handler))
  (GET "/login" request (if (friend/identity request)
                          (resp/redirect "/")
                          (resp/file-response "landing.html" {:root "resources/public"})))
  (friend/logout (ANY "/logout" request (resp/redirect "/")))
  (GET "/:conversation" [conversation] (friend/authenticated
                                        #(index-handler % conversation)))
  (GET "/:conversation/events" [conversation] (friend/authenticated
                                               (wrap-aleph-handler events-handler)))
  (route/files "/" {:root "resources/public"})
  (route/not-found "Not Found"))

(defonce app-routes-with-auth
  (-> app-routes
      (friend/authenticate
       {:workflows [(openid/workflow :openid-uri "/openid"
                                     :realm "http://localhost:4000"
                                     :credential-fn identity)]})))

(defonce app
  (-> app-routes-with-auth
      wrap-bounce-favicon
      wrap-keyword-params
      wrap-nested-params
      wrap-params
      wrap-session
      wrap-reload))

(defn stop
  []
  (@aleph-stop))

(defn start
  [host port]
  ;; routes incoming skype messages to listening web clients
  (siphon message-channel (create-distributor))
  (let [wrapped-handler (wrap-ring-handler app)
        stop-fn (start-http-server wrapped-handler {:port port})]
    (reset! aleph-stop stop-fn)
    stop-fn))
