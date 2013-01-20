(ns skypeclj.registry
  (:use [aleph http formats]
        [lamina core]
        [useful.fn :only [fix to-fix !]])
  (:require [clojure.tools.logging :as log]
            [skypeclj.hydra :as hydra]))

;; taken from http://github.com/flatland/lazybot

(defn merge-with-conj [& args]
  (apply merge-with #(if (vector? %) (conj % %2) (conj [] % %2)) args))

(defn parse-fns
  [body]
  (apply merge-with-conj
         (for [[one & [two three four :as args]] body]
           {one
            (case one
              :cmd {:docs two
                    :triggers three
                    :fn four}
              :hook {two {:fn three}}
              :indexes (vec args)
              two)})))

(defn if-seq-error [fn-type possible-seq]
  (if (and (not (fn? possible-seq)) (seq possible-seq))
    (throw (Exception. (str "Only one " fn-type " function allowed.")))
    possible-seq))

(def make-vector (to-fix (! vector?) vector))

(defmacro defplugin [& body]
  (let [{:keys [cmd hook cleanup init routes]} (parse-fns body)
        scmd (if (map? cmd) [cmd] cmd)]
    `(let [pns# *ns*
           p-name# (keyword (last (.split (str pns#) "\\.")))]
       (defn ~'load-this-plugin [bot#]
         (when ~init ((if-seq-error "init" ~init) bot#))
         (dosync
          (swap! bot# assoc-in [:plugins p-name#]
                 {:commands ~scmd
                  :hooks (into {}
                               (for [[k# v#] (apply merge-with-conj
                                                    (make-vector ~hook))]
                                 [k# (make-vector v#)]))
                  :cleanup (if-seq-error "cleanup" ~cleanup)
                  :routes ~routes}))))))

(defn load-plugin
  [bot plugin]
  (let [ns (symbol (str "skypeclj.plugins." plugin))]
    (require ns :reload)
    ((resolve (symbol (str ns "/load-this-plugin"))) bot)))

(defn find-command
  [bot cmd]
  (some #(when (= cmd (:triggers %)) %) (mapcat :commands (vals (:plugins @bot)))))

(defn handle
  [bot cmd-fn args]
  (log/info "execute" cmd-fn)
  (try
    (cmd-fn (assoc @bot :args args))
    (catch Exception exception
      (.getMessage exception))))
