(ns ^{:clojure.tools.namespace.repl/load false}
 user
  (:require
   [clj-async-profiler.core :as prof]
   [casselc.clj-gaze-tracking :as main]
   [casselc.clj-gaze-tracking.state :as state]
   [io.github.humbleui.app :as app]
   [io.github.humbleui.debug :as debug]
   [io.github.humbleui.window :as window] 
   [clojure.tools.namespace.repl :as ns]))

(defn reset-window
  "Resets the window position and size back to some defaults."
  []
  (app/doui
   (when-some [window @state/*window]
     (window/set-window-position window 860 566)
     (window/set-content-size window 1422 800)
     #_(window/set-z-order window :floating))))

(defn reload
  "Reload all namespaces that have changed on disk and redraw the app."
  []
  (ns/refresh :after 'casselc.clj-gaze-tracking.state/redraw!))

(defn -main
  "Starts both the UI and the nREPL server."
  [& args]
  (println "starting shit")
  #_(prof/serve-ui "0.0.0.0" 8181)
  (ns/set-refresh-dirs "src")
  ;; start app
  #_(prof/profile {:event :alloc})
  (main/-main)

  ;; (reset! debug/*enabled? true)
  
  ;; start nREPL server (on another thread)
  #_(apply nrepl/-main args))

(comment
  (-main)

  ;; Anything we do to the app UI, we need to eval it wrapped in `doui` so that
  ;; it runs on the UI thread.
  (reload)
  (reset-window)

  ;; keep window on top even when not focused
  (app/doui
   (window/set-z-order @state/*window :floating))

  ;; set window to hide normally when not focused
  (app/doui
   (window/set-z-order @state/*window :normal))
  )