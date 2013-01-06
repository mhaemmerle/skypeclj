(ns skypeclj.logger
  (:use camel-snake-kebab
        [lamina core executor]
        [aleph http formats]
        [cheshire.core :only [generate-string parse-string]]
        compojure.core)
  (:require [clojure.tools.logging :as log]
            [compojure.route :as route]
            [skypeclj.skype :as skype]))

;; register listeners with skype namespace

(defn start
  [host port]
  (log/info "start-web-server"))

(defn stop
  []
  (log/info "stop-web-server"))

;; /c/backend
;; /c/johann-bot