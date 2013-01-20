(ns skypeclj.plugins.echo
  (:use skypeclj.registry)
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]))

(defplugin
  (:cmd
   "Echoes everything back to you. Even silence."
   #{"echo"}
   (fn [{:keys [args] :as bot}]
     (if (seq args)
       (str (format "You said: \"%s\"" (string/join " " args)))
       "You decided to remain silent."))))
