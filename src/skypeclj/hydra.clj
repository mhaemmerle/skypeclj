(ns skypeclj.hydra
  (:require [clojure.tools.logging :as log])
  (:use [cheshire.core :only [generate-string]])
  (:import [org.mozilla.javascript Context Scriptable NativeObject NativeArray
            ImporterTopLevel])
  (:import (java.io InputStreamReader)))

(set! *warn-on-reflection* true)

(def file-name "resources/map.js")
(def url "http://www.reddit.com/r/aww.json")

(defn r
  [bot]
  (let [context ^Context (Context/enter)
        scope (.initStandardObjects context (ImporterTopLevel. context))
        thr (Thread/currentThread)
        ldr (.getContextClassLoader thr)
        stream (.getResourceAsStream ldr "map.js")
        is (InputStreamReader. stream)]
    (.setOptimizationLevel context -1)
    (.evaluateReader context scope is "map.js" 1 nil)
    (let [result (.evaluateString context scope "aww('http://www.reddit.com/r/aww.json');" "js" 1 nil)]
      result)))

(defn execute-script
  [file-name & args]
  (let [context (Context/enter)
        scope (.initStandardObjects context)
        script (slurp file-name)]
    (.setOptimizationLevel context 2)
    (let [function (.compileFunction context scope script "<compiled>" 1 nil)
          response (.call function context scope nil (object-array args))]
      (log/info "execute-script" response)
      (Context/exit))))
