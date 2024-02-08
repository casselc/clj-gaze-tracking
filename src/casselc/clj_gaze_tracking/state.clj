(ns
 ^{:clojure.tools.namespace.repl/load false}
 casselc.clj-gaze-tracking.state
  (:require
   [io.github.humbleui.window :as window]))

(def *window (atom nil))

(def *app (atom nil))

(def *captured (atom nil))

(def *state (atom {:capturing? false
                   :shutting-down? false}))

(defn redraw! [] (some-> *window deref window/request-frame))