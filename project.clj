(defproject skypeclj "0.1.0-SNAPSHOT"
  :description "A simple Clojure wrapper for the Skype Java API."
  :url "http://github.com/mhaemmerle/skypeclj"
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.skype/skypekit "1.0"]
                 [org.slf4j/slf4j-api "1.6.6"]
                 [org.slf4j/slf4j-log4j12 "1.6.6"]
                 [org.clojure/tools.logging "0.2.3"]
                 [camel-snake-kebab "0.1.0-SNAPSHOT"]]
  :plugins [[lein-marginalia "0.7.1"]])
