(ns skypeclj.logger
  (:use camel-snake-kebab
        [lamina core executor]
        [aleph http formats]
        [cheshire.core :only [generate-string parse-string]]
        compojure.core
        [ring.middleware params keyword-params nested-params session])
  (:require [clojure.tools.logging :as log]
            [compojure.route :as route]
            [hiccup.page :refer [html5 include-js include-css]]
            [hiccup.element :refer [javascript-tag]]
            [hiccup.util :refer [escape-html]]
            [ring.util.response :as resp]
            [clj-time.format :refer [formatters unparse]]
            [clj-time.coerce :refer [from-long]]
            [cemerick.friend :as friend]
            [cemerick.friend.openid :as openid])
  (:import [com.skype.api Conversation Message]))

(set! *warn-on-reflection* true)

(def aleph-stop (atom nil))
(def conversations (atom {}))

(def stylesheet "http://thomasf.github.com/solarized-css/solarized-dark.min.css")
;; (def stylesheet "http://thomasf.github.com/solarized-css/solarized-light.min.css")

(def hour-minute-formatter (formatters :hour-minute))

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

(defn init-conversations
  [c]
  (doseq [^Conversation conversation c]
    (let [oid-keyword (keyword (str (.getOid conversation)))
          display-name (.getDisplayName conversation)
          identity (.getIdentity conversation)
          data {:display-name display-name :identity identity :messages []}]
      (swap! conversations assoc oid-keyword data)

      (log/info "getUnconsumedNormalMessages" (.getUnconsumedNormalMessages conversation) "for" oid-keyword)
      (let [last-messages (.getLastMessages conversation 1357767070)
            context-messages (.contextMessages last-messages)
            unconsumed-messages (.unconsumedMessages last-messages)]
        (log/info "context-messages" (count context-messages) "unconsumed-messages" (count unconsumed-messages))
        (doseq [message context-messages]
          ;; should mark message as read
          (log-message conversation message))
        (doseq [message unconsumed-messages]
          ;; should mark message as read
          (log-message conversation message)))

      )))

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
  ([request conversation-oid]
     (log/info "index-handler" conversation-oid)
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
                    (escape-html (str "<" author-display-name ">")) message-body
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

(def app-routes-with-auth
  (-> app-routes
      (friend/authenticate
       {:workflows [(openid/workflow :openid-uri "/openid"
                                     :realm "http://localhost:4000"
                                     :credential-fn identity)]})))

(def app
  (-> app-routes-with-auth
      wrap-bounce-favicon
      wrap-keyword-params
      wrap-nested-params
      wrap-params
      wrap-session))

(defn stop
  []
  (@aleph-stop))

(defn start
  [host port]
  (let [wrapped-handler (wrap-ring-handler app)
        stop-fn (start-http-server wrapped-handler {:port port})]
    (reset! aleph-stop stop-fn)
    stop-fn))
