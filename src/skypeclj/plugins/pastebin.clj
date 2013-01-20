(ns skypeclj.plugins.pastebin
  (:use skypeclj.registry
        [aleph http formats]
        [lamina core executor])
  (:require [clojure.tools.logging :as log]
            [clojure.string :as string]))

(def api-key "")
(def url "http://pastebin.com/api/api_post.php")
(def expire-duration "10M")

(defn do-paste
  [format code]
  (let [form-params {"api_paste_expire_date" expire-duration
                     "api_dev_key" api-key
                     "api_option" "paste"
                     "api_paste_code" code
                     "api_paste_format" format
                     "api_paste_private" 0}
        request {:method :post :url url :form-params form-params}
        response @(http-request (merge request {:auto-transform true}))]
    (:body response)))

(defplugin
  (:cmd
   "Paste code to http://pastebin.com; example langs are: actionscript3 clojure ruby erlang"
   #{"pastebin"}
   (fn [{:keys [args] :as bot}]
     (do-paste (first args) (string/join " " (rest args))))))
