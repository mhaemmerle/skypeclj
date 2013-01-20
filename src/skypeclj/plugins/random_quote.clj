(ns skypeclj.plugins.random-quote
  (:use skypeclj.registry
        [aleph http formats])
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string])
  (:import org.apache.commons.lang.StringEscapeUtils))

(def url "http://www.iheartquotes.com/api/v1/random?source=joel_on_software+paul_graham+prog_style")

(defplugin
  (:cmd
   "Gets a random quote from http://iheartquotes.com"
   #{"quote"}
   (fn [_]
     (log/info "quote")
     (let [response (sync-http-request {:method :get :url url})
           lines (string/split-lines (bytes->string (:body response)))]
       (StringEscapeUtils/unescapeHtml (string/join "\n" (drop-last 2 lines)))))))
