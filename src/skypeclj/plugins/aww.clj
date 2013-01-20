(ns skypeclj.plugins.aww
  (:use skypeclj.registry)
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]
            [skypeclj.hydra :as hydra]))

(defplugin
  (:cmd
   ""
   #{"aww"}
   (fn [{:keys [args] :as bot}]
     (hydra/r bot))))
