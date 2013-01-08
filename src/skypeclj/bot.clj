(ns skypeclj.bot
  (:require [clojure.tools.logging :as log]
            [skypeclj.skype :as skype]
            [skypeclj.config :as config])
  (:import [com.skype.api Skype AccountListener Account$Status Account$Property]))

(def ^:dynamic *skype* nil)

(def bot-name (:username config/config))

(defn account-on-property-change
  [account property value string-value]
  (log/info "account-on-property-change" property value)
  (when (and (= Account$Property/P_STATUS property)
             (= (.getId Account$Status/LOGGED_IN) value))
    (log/info "We're logged in!")
    ;; (let [conversation (skype/open-conversation skype "johann-bot")])
    (skype/get-conversation-list *skype*)))

(defn skype-on-message
  [skype message changes-inbox-timestamp supersedes-history-message conversation]
  (log/info "skype-on-message" message))

(defn stop
  []
  (skype/stop *skype*))

(defn start
  [runtime-host runtime-port username password key]
  (skype/add-listener! :account-listener :on-property-change account-on-property-change)
  (skype/add-listener! :skype-listener :on-message skype-on-message)
  (let [skype (skype/start key runtime-host runtime-port)
        account (skype/login skype username password)]
    (alter-var-root #'*skype* (constantly skype))
    (log/info "account" account (.getSkypeName account))
    nil))
