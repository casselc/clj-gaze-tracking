(ns casselc.gaze-tracking
  (:require
   [membrane.java2d :as backend]
   [membrane.ui :as ui :refer [vertical-layout
                               horizontal-layout
                               rectangle
                               padding
                               image
                               checkbox
                               label]])
  (:import
   (java.awt.event WindowEvent)
   (java.util.concurrent Executors)
   (org.bytedeco.opencv opencv_java)
   (org.opencv.core Mat MatOfByte MatOfRect Rect)
   (org.opencv.imgproc Imgproc)
   (org.opencv.imgcodecs Imgcodecs)
   (org.opencv.objdetect CascadeClassifier)
   (org.opencv.videoio VideoCapture Videoio))
  (:gen-class))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(set-agent-send-executor! (Executors/newVirtualThreadPerTaskExecutor))
(set-agent-send-off-executor! (Executors/newVirtualThreadPerTaskExecutor))

(opencv_java.)

(def ^CascadeClassifier face-classifier (CascadeClassifier. "resources/haarcascade_frontalface_alt.xml"))
(def ^CascadeClassifier eye-classifier (CascadeClassifier. "resources/haarscascade_eye_tree_eyeglasses.xml"))

(declare capture)

(defn- process-frame
  [^Mat color-frame ^Mat gray-frame ^MatOfRect features]
  (Imgproc/cvtColor color-frame gray-frame Imgproc/COLOR_BGR2GRAY)
  (Imgproc/equalizeHist gray-frame gray-frame)
  (.detectMultiScale face-classifier gray-frame features)
  (let [faces (.toArray features)]
    (when-let [^Rect face-bounds (areduce faces i largest nil
                                          (let [^Rect f (aget faces i)]
                                            (if (and largest
                                                     (< (.area ^Rect f) (.area ^Rect largest)))
                                              largest
                                              f)))]
      (let [gray-face (.submat gray-frame face-bounds)
            color-face (.submat color-frame face-bounds)
            out-frame (MatOfByte.)
            out-face (MatOfByte.)
            out-left-eye (MatOfByte.)
            out-right-eye (MatOfByte.)]
        (try
          (.detectMultiScale eye-classifier gray-face features)
          (let [eyes (.toArray features)]
            (when (= 2 (alength eyes))
              (let [^Rect bounds-a (aget eyes 0)
                    ^Rect bounds-b (aget eyes 1)
                    [^Rect left-bounds
                     ^Rect right-bounds] (if (< (.x bounds-a) (.x bounds-b))
                                           [bounds-a bounds-b]
                                           [bounds-b bounds-a])
                    left-eye (.submat color-face left-bounds)
                    right-eye (.submat color-face right-bounds)]
                (try
                  (Imgcodecs/imencode ".jpeg" color-frame out-frame)
                  (Imgcodecs/imencode ".jpeg" color-face out-face)
                  (Imgcodecs/imencode ".jpeg" left-eye out-left-eye)
                  (Imgcodecs/imencode ".jpeg" right-eye out-right-eye)
                  {:frame (.toArray out-frame)
                   :face (.toArray out-face)
                   :left-eye (.toArray out-left-eye)
                   :right-eye (.toArray out-right-eye)}
                  (finally
                    (.release left-eye)
                    (.release right-eye))))))
          (finally
            (.release gray-face)
            (.release color-face)
            (.release out-face)
            (.release out-left-eye)
            (.release out-right-eye)))))))

(defn capture-frames!
  [{:keys [capturing? ^VideoCapture device buffers]
    :as current}]
  (if capturing?
    (do
      (println (System/currentTimeMillis) "Capturing frame")
      (send-off capture capture-frames!)
      (let [{:keys [color-frame gray-frame features]} buffers]
        (if (.read device color-frame)
          (merge current (process-frame color-frame gray-frame features))
          current)))
    current))

(defn start-capture!
  [[width height] interval-ms]
  (when-let [^VideoCapture device (cond
                                    (Videoio/hasBackend Videoio/CAP_DSHOW) (VideoCapture. 0 Videoio/CAP_DSHOW)
                                    (Videoio/hasBackend Videoio/CAP_AVFOUNDATION) (VideoCapture. 0 Videoio/CAP_AVFOUNDATION)
                                    (Videoio/hasBackend Videoio/CAP_V4L2) (VideoCapture. "/dev/video0" Videoio/CAP_V4L2)
                                    :else (VideoCapture. 0))]
    (.setExceptionMode device true)
    (.set device Videoio/CAP_PROP_FRAME_WIDTH width)
    (.set device Videoio/CAP_PROP_FRAME_HEIGHT height)
    (let [width (int (.get device Videoio/CAP_PROP_FRAME_WIDTH))
          height (int (.get device Videoio/CAP_PROP_FRAME_HEIGHT))
          buffers {:color-frame (Mat. height width 16)
                   :gray-frame (Mat. height width 0)
                   :features (MatOfRect.)}]
      (add-watch capture ::capture-frames
                 (fn [_ _ old new]
                   (when (and (:capturing? new)
                              (not (:capturing? old)))
                     (println "Starting frame capture")
                     (send-off capture capture-frames!))))
      (send capture assoc
            :device device
            :width width
            :height height
            :interval interval-ms
            :buffers buffers
            :capturing? false))))

(defn stop-capture!
  []
  (send capture (fn [{:keys [^VideoCapture device shut-down?]
                      {:keys [^Mat color-frame ^Mat gray-frame ^MatOfRect features]} :buffers}]
                  (when-not shut-down?
                    (println "Stopping capture...")
                    (some-> features .release)
                    (some-> gray-frame .release)
                    (some-> color-frame .release)
                    (some-> device .release)
                    (println "Capture resources released."))
                  #_(shutdown-agents)
                  {:shut-down? true})))

(def capture (agent {}
                    :validator (fn [{:keys [device shut-down?] :as val}]
                                 (complement
                                  (when-not (or (= {} val) shut-down? device)
                                    (println "Got a bad val:" val))))
                    :error-handler (fn [capture e]
                                     (println "Agent encountered error:" e)
                                     (let [{:keys [^VideoCapture device width height interval]} @capture]
                                       (some-> device .release)
                                       (future
                                         (restart-agent capture {})
                                         (when (and width height interval)
                                           (println "Attempting to restart capture device.")
                                           (start-capture! [width height] interval)))))))

(defn capture-view
  [{:keys [capturing? frame face left-eye right-eye]}]
  (horizontal-layout
   (if frame
     (image frame [640 480])
     (rectangle 640 480))
   (vertical-layout
    (if face
      (image face [256 256])
      (rectangle 256 256))
    (horizontal-layout (if left-eye
                         (image left-eye [128 128])
                         (rectangle 128 128))
                       (if right-eye
                         (image right-eye [128 128])
                         (rectangle 128 128)))
    (padding 5
             (horizontal-layout (label "Capture?")
                                (ui/on :mouse-down
                                       (fn [_]
                                         (send capture update :capturing? not)
                                         nil)
                                       (checkbox capturing?)))))))

(defn -main
  [& _]
  (let [window-info (backend/run #(capture-view @capture)
                                 {:window-title "Gaze Tracking"
                                  :window-start-width 914
                                  :window-start-height 519})
        ^javax.swing.JFrame frame (::backend/frame window-info)
        closing? (promise)]
    (when-let [repaint (::backend/repaint window-info)]
      (add-watch capture ::repaint-on-change (fn [_ _ _ _] (repaint))))
    (.addWindowListener frame
                        (reify java.awt.event.WindowListener
                          (^void windowActivated [_ ^WindowEvent _])
                          (^void windowClosed [_ ^WindowEvent _])
                          (^void windowClosing [_ ^WindowEvent _]
                            (println "Window closing...")
                            (deliver closing? true))
                          (^void windowDeactivated [_ ^WindowEvent _])
                          (^void windowDeiconified [_ ^WindowEvent _])
                          (^void windowIconified [_ ^WindowEvent _])
                          (^void windowOpened [_ ^WindowEvent _])))
    (start-capture! [640 480] 100)
    @closing?
    (stop-capture!)
    (.dispose frame)))

(comment
  (-main)
  @capture
  (stop-capture!))