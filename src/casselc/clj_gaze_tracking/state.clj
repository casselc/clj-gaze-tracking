(ns
 ^{:clojure.tools.namespace.repl/load false}
 casselc.clj-gaze-tracking.state
  (:require
   [io.github.humbleui.window :as window]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def *window (atom nil))

(def *app (atom nil))

(def *captured (atom nil))

(def *state (atom {:capturing? true
                   :shutting-down? false}))

(defn redraw! [] (some-> *window deref window/request-frame))