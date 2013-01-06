(ns skypeclj.core
  (:gen-class)
  (:use clojure.tools.cli)
  (:require [clojure.tools.logging :as log]
            [skypeclj.config :as config]
            [skypeclj.skype :as skype]
            [skypeclj.logger :as logger]))

(defn at-exit
  [runnable]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable runnable)))

(defn -main [& args]
  (let [logger-config (:logger config/config)
        runtime-config (:runtime config/config)
        {:keys [username password key-filename]} config/config]
    (logger/start (:host logger-config) (:port logger-config))
    (let [skype (skype/start key-filename (:host runtime-config) (:port runtime-config))
          account (skype/login skype username password)]
      (log/info "account" account (.getSkypeName account))
      (let [conversation (skype/open-conversation skype "johann-bot")
            converstation-list (skype/get-conversation-list skype)]
        (log/info "conversation" conversation))
      (at-exit (partial skype/stop skype)))))
