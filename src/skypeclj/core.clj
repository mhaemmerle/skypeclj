(ns skypeclj.core
  (:gen-class)
  (:use clojure.tools.cli)
  (:require [clojure.tools.logging :as log]
            [skypeclj.config :as config]
            [skypeclj.bot :as bot]
            [skypeclj.logger :as logger]))

(defn at-exit
  [runnable]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^Runnable runnable)))

(defn stop
  []
  (logger/stop)
  (bot/stop))

(defn start
  []
  (let [logger-config (:logger config/config)
        runtime-config (:runtime config/config)
        {:keys [username password key-filename]} config/config]
    (logger/start (:host logger-config) (:port logger-config))
    (bot/start (:host runtime-config) (:port runtime-config) username password key-filename)
    (at-exit stop)))

(defn -main [& args]
  (start))
