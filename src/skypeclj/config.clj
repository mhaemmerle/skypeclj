(ns skypeclj.config
  (:require [clojure.java.io :as io])
  (:import [java.io PushbackReader]))

(def config (binding [*read-eval* false]
              (with-open [r (io/reader "resources/config.clj")]
                (read (PushbackReader. r)))))
