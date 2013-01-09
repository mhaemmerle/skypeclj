(ns skypeclj.bot
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [skypeclj.skype :as skype]
            [skypeclj.config :as config]
            [skypeclj.logger :as logger])
  (:import [com.skype.api Skype AccountListener Account$Status Account$Property
            Conversation Message]))

;; get missing messages
;; (let [conversation (skype/open-conversation skype "johann-bot")])

(def ^:dynamic *skype* nil)

(def bot-prefix "@helga")
(def bot-name (:username config/config))
(def commands (atom {}))

(defn account-on-property-change
  [account property value string-value]
  (log/info "account-on-property-change" property value)
  (when (and (= Account$Property/P_STATUS property)
             (= (.getId Account$Status/LOGGED_IN) value))
    (log/info "We're logged in!")
    (logger/init-conversations (skype/get-conversation-list *skype*))))

(defn register-command!
  [cmd fun]
  (swap! commands assoc cmd fun))

(defn deregister-command!
  [cmd]
  (swap! commands dissoc cmd))

(defn ^:private command?
  [cmd]
  (if (or (nil? cmd)
          (nil? (cmd @commands)))
    false true))

(defn execute-command
  [conversation cmd args]
  (log/info "execute-command" cmd args)
  (skype/post-text conversation (try
                                  (apply (cmd @commands) args)
                                  (catch Exception exception
                                    (.getMessage exception)))))

(defn ^:private parse-message
  [^Conversation conversation ^Message message]
  (let [author (.getAuthor message)
        message-body (.getBodyXml message)
        maybe-prefix-and-rest (clojure.string/split message-body #" " 2)]
    (log/info "author" author "maybe-prefix-and-rest" maybe-prefix-and-rest)
    (when (and (= bot-prefix (first maybe-prefix-and-rest))
               (not= bot-name author))
      (let [the-rest (second maybe-prefix-and-rest)
            without-bot-prefix (clojure.string/split the-rest #" ")
            cmd-string (first without-bot-prefix)
            cmd (keyword cmd-string)
            args (rest without-bot-prefix)]
        (when (command? cmd)
          (execute-command conversation cmd args))))))

;; register plugins as commands?

(register-command! :echo (fn [& args] (str (format "'%s'" (clojure.string/join " " args)))))
(register-command! :crash (fn [& args] (throw (Exception. "don't provoke me!"))))

(defn skype-on-message
  [skype message changes-inbox-timestamp supersedes-history-message conversation]
  (log/info "skype-on-message" message (.getTimestamp message))
  (logger/log-message conversation message)
  (parse-message conversation message))

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
