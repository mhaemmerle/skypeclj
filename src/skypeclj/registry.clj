(ns skypeclj.registry
  (:use [aleph http formats])
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [skypeclj.hydra :as hydra])
  (:import org.apache.commons.lang.StringEscapeUtils))

;; need a description for the commands/plugins

(defn register
  [bot cmd fun]
  (swap! bot assoc-in [:commands cmd] fun))

(defn deregister
  [bot cmd]
  (swap! bot update-in [:commands] dissoc cmd))

(defn registered?
  [bot cmd]
  (if (or (nil? cmd)
          (nil? (cmd (:commands @bot))))
    false true))

(defn random-quote
  [bot]
  (let [url "http://www.iheartquotes.com/api/v1/random?source=joel_on_software+paul_graham+prog_style"
        response (sync-http-request {:method :get :url url})
        lines (string/split-lines (bytes->string (:body response)))]
    (StringEscapeUtils/unescapeHtml (string/join "\n" (drop-last 2 lines)))))

(defn list-all-commands
  [bot]
  (let [r (reduce (fn [b [k v]]
                    (.append b (str (name k) "\n")))
                  (StringBuffer.)
                  (:commands @bot))]
    (.toString r)))

(defn handle
  [bot cmd args]
  (log/info "execute" cmd args)
  (try
    (apply (cmd (:commands @bot)) args)
    (catch Exception exception
      (.getMessage exception))))

(defn register-default-commands
  [bot]
  (register bot :echo (fn [& args] (string/join " " args)))
  (register bot :crash (fn [& args] (throw (Exception. "don't provoke me!"))))
  (register bot :awww (fn [& args] (hydra/r bot)))
  (register bot :rq (fn [& args] (random-quote bot)))
  (register bot :lq (fn [& args] (list-all-commands bot))))
