(ns skypeclj.bot
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [skypeclj.skype :as skype]
            [skypeclj.config :as config]
            [skypeclj.logger :as logger]
            [skypeclj.registry :as registry])
  (:import [com.skype.api Skype AccountListener Account$Status Account$Property
            Conversation Message]))

(defonce bot (atom {:skype nil}))

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
            cmd (first without-bot-prefix)
            args (rest without-bot-prefix)]
        (log/info "cmd" cmd "args" args)
        (let [reply (if-let [cmd-fn (:fn (registry/find-command bot #{cmd}))]
                      (registry/handle bot cmd-fn args)
                      (str "I am very sorry, but I don't understand that request."))]
          (log/info "reply" reply)
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
  (log/info "skype-on-message - thread name:" (.getName (Thread/currentThread)))
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
  [runtime-host runtime-port username password key]
  (register-default-listeners)

  (registry/load-plugin bot "pastebin")
  (registry/load-plugin bot "random-quote")
  (registry/load-plugin bot "aww")
  (registry/load-plugin bot "echo")

  (let [skype (skype/start key runtime-host runtime-port)
        account (skype/login skype username password)]
    (swap! bot assoc :skype skype)
    (log/info "account" account (.getSkypeName account))
    nil))

;; (defn list-all-commands
;;   [bot]
;;   (let [r (reduce (fn [b [k v]]
;;                     (.append b (str (name k) "\n")))
;;                   (StringBuffer.)
;;                   (:commands @bot))]
;;     (.toString r)))

;; (register bot :crash (fn [& args] (throw (Exception. "don't provoke me!"))))
;; (register bot :lq (fn [& args] (list-all-commands bot)))
