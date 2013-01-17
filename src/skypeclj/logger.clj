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
  (:import [com.skype.api Conversation Message]
           it.sauronsoftware.cron4j.Scheduler))

(set! *warn-on-reflection* true)

(defonce aleph-stop (atom nil))
(defonce conversations (atom {}))

(def skype-logs-dir (clojure.java.io/file "skype-logs"))

(defn rotate-logs
  []
  (log/info "rotate-logs"))

(defn start-logrotate-scheduler
  []
  (log/info "start-logrotate-scheduler")
  (doto (Scheduler.)
    (.schedule "*/1 * * * *" rotate-logs)
    (.start)))

;; (start-logrotate-scheduler)

;; TODO implement
(defn ^:private write-to-disk
  [conversation date filename]
  (log/info "write-to-disk" conversation date filename))

;; TODO implement
(defn ^:private load-from-disk
  [conversation date filename]
  (log/info "load-from-disk" conversation date filename))

(defn timestamp-to-date
  [timestamp]
  (unparse (formatters :date) (from-long (* timestamp 1000))))

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

(defn list-logs
  [dir]
  (doseq [^java.io.File f (filter #(not (.isDirectory ^java.io.File %)) (file-seq dir))]
    (log/info "list-logs" (.getName f))))

;; (list-logs skype-logs-dir)

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
                (for [{:keys [author author-display-name
                              timestamp message-body]} (:messages data)]
                  (let [date-time (from-long (* 1000 timestamp))
                        hour-min (unparse (formatters :hour-minute) date-time)]
                    [:div
                     (time-link timestamp hour-min)
                     (escape-html author-display-name)
                     message-body])))

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
        ;; _ (log/info "events-handler" conversation-oid)
        event-channel (channel)]
    (register-event-listener event-channel nil)
    (enqueue response-channel
             {:status 200
              :headers {"Content-Type" "text/event-stream"}
              :body (map* encode-event-data event-channel)})))

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
  (let [wrapped-handler (wrap-ring-handler app)
        stop-fn (start-http-server wrapped-handler {:port port})]
    (reset! aleph-stop stop-fn)
    stop-fn))
