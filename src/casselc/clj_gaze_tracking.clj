(ns casselc.clj-gaze-tracking
  (:require
   [io.github.humbleui.ui :as ui]
   [casselc.clj-gaze-tracking.capture :as capture]
   [casselc.clj-gaze-tracking.state :as state]))

(def app
  "Main app definition."
  (ui/default-theme ; we must wrap our app in a theme
   {}
   ;; just some random stuff
   (ui/padding 5
               (ui/column
                (ui/button
                 #(swap! state/*state update :capturing? not)
                 (ui/dynamic _ [capturing? (:capturing? @state/*state)]
                             (ui/label (str (if capturing? "Stop" "Start") " Capture"))))
                (ui/gap 0 10)
                (ui/dynamic _ [captured-frame @state/*captured]
                            (ui/image (or captured-frame "test.png")))))))

;; reset current app state on eval of this ns
(reset! state/*app app)

(defn -main
  "Run once on app start, starting the humble app."
  [& args]
  (add-watch state/*captured ::redraw-on-capture
             (fn [_ _ old new]
               (when (not= old new)
                 (state/redraw!))))

  (ui/start-app!
   (reset! state/*window
           (ui/window
            {:title    "Capture Viewer"
             :bg-color 0xFFFFFFFF}
            state/*app)))
  (state/redraw!) 
  @(capture/capture-thread 50))
