(ns skypeclj.bot
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [skypeclj.skype :as skype]
            [skypeclj.config :as config]
            [skypeclj.logger :as logger]
            [skypeclj.registry :as registry])
  (:import [com.skype.api Skype AccountListener Account$Status Account$Property
            Conversation Message]))

(defonce bot (atom {:skype nil :commands nil}))

(defn ^:private parse-message
  [^Conversation conversation ^Message message]
  (let [author (.getAuthor message)
        message-body (.getBodyXml message)
        maybe-prefix-and-rest (string/split message-body #" " 2)
        bot-prefix (:prefix config/config)
        bot-name (:username config/config)
        lc (string/lower-case (first maybe-prefix-and-rest))]
    (log/info "author" author "maybe-prefix-and-rest" maybe-prefix-and-rest
              "prefix" bot-prefix "bot-name" bot-name)
    (when (and (= bot-prefix lc)
               (not= bot-name author))
      (log/info "shit")
      (let [the-rest (second maybe-prefix-and-rest)
            without-bot-prefix (string/split the-rest #" ")
            cmd-string (first without-bot-prefix)
            cmd (keyword cmd-string)
            args (rest without-bot-prefix)]
        (log/info "cmd" cmd "registered?" (registry/registered? bot cmd))
        (let [reply (if (registry/registered? bot cmd)
                      (registry/handle bot cmd args)
                      (str "I am very sorry, but I don't understand that request."))]
              (skype/post-text conversation reply))))))

(defn account-on-property-change
  [account property value string-value]
  (log/info "account-on-property-change" property value)
  (when (and (= Account$Property/P_STATUS property)
             (= (.getId Account$Status/LOGGED_IN) value))
    (log/info "We're logged in!")
    (logger/init-conversations (skype/get-conversation-list (:skype @bot)))))

(defn message-on-property-change
  [message property value string-value]
  (log/info "message-on-property-change" property value))

(defn skype-on-message
  [skype message changes-inbox-timestamp supersedes-history-message conversation]
  (log/info "skype-on-message" message (.getTimestamp message) changes-inbox-timestamp
            supersedes-history-message)
  (logger/log-message conversation message)
  (parse-message conversation message))

(defn register-default-listeners
  []
  (skype/add-listener! :account-listener :on-property-change account-on-property-change)
  (skype/add-listener! :skype-listener :on-message skype-on-message)
  (skype/add-listener! :message-listener :on-property-change message-on-property-change))

(defn stop
  []
  (skype/stop (:skype @bot)))

(defn start
  "Entry point that initializes the bot and makes sure the connection to SkypeKit is up and running"
  [runtime-host runtime-port username password key]
  (register-default-listeners)
  (registry/register-default-commands bot)
  (let [skype (skype/start key runtime-host runtime-port)
        account (skype/login skype username password)]
    (swap! bot assoc :skype skype)
    (log/info "account" account (.getSkypeName account))
    nil))
