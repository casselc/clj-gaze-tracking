(ns casselc.clj-gaze-tracking
  (:require [io.github.humbleui.ui :as ui]
            [casselc.clj-gaze-tracking.state :as state])
  (:import
   (org.bytedeco.opencv opencv_java)
   (org.opencv.core Mat MatOfByte MatOfRect Point Rect Scalar Size)
   (org.opencv.face Face)
   (org.opencv.imgproc Imgproc)
   (org.opencv.imgcodecs Imgcodecs)
   (org.opencv.objdetect CascadeClassifier FaceDetectorYN)
   (org.opencv.videoio VideoCapture Videoio))
  (:gen-class))

(opencv_java.)



(def app
  "Main app definition."
  (ui/default-theme ; we must wrap our app in a theme
   {}
   ;; just some random stuff
   (ui/center
    (ui/height 480
               (ui/width 640
                         (ui/stack
                          (ui/dynamic _ [captured-frame @state/*captured]
                                      (ui/image (or captured-frame "test.png")))
                          (ui/center
                           (ui/button
                            #(swap! state/*state update :capturing? not)
                            (ui/dynamic _ [capturing? (:capturing? @state/*state)]
                                        (ui/label (str (if capturing? "Stop" "Start") " Capture")))))))))))

;; reset current app state on eval of this ns
(reset! state/*app app)

(def face-classifier (CascadeClassifier. "resources/haarcascade_frontalface_alt.xml"))

(defn capture-thread
  [sleep-ms]
  (future
    (println "Capture thread started")
    (println "DirectShow available?" (Videoio/hasBackend Videoio/CAP_DSHOW))
    (println "MSMF available?" (Videoio/hasBackend Videoio/CAP_MSMF))
    (println "FFMPEG available?" (Videoio/hasBackend Videoio/CAP_FFMPEG))
    (let [camera (VideoCapture. 0 (Videoio/CAP_DSHOW))
          facemark (Face/createFacemarkLBF)
          buff-frame (Mat. 480 640 16)
          gray-frame (Mat. 480 640 0)
          faces (MatOfRect.)
          landmarks (java.util.ArrayList.)]
      (try
        (println "Got capture device:" (.getBackendName camera) "open:" (.isOpened camera) "exceptions:" (.getExceptionMode camera))
        (.loadModel facemark "resources/lbfmodel.yaml")
        (loop [{:keys [shutting-down? capturing?]} @state/*state]
          (when-not shutting-down?
            (when capturing?
              (when (.read camera buff-frame) 
                (let [img (MatOfByte.)]
                  (try
                    (Imgproc/cvtColor buff-frame gray-frame Imgproc/COLOR_BGR2GRAY)
                    (Imgproc/equalizeHist gray-frame gray-frame)
                    (.detectMultiScale face-classifier gray-frame faces)
                    (doseq [face (.toList faces)]
                      (Imgproc/rectangle buff-frame face (Scalar. 255 0 255)))
                    
                    (when (.fit facemark gray-frame faces landmarks)
                      (let [l (nth landmarks 0)]
                        (Face/drawFacemarks buff-frame l (Scalar. 0 0 255)))
                      (doseq [l landmarks]
                        (.release l))
                      (.clear landmarks))
                    (when (Imgcodecs/imencode ".jpeg" buff-frame img)
                      (reset! state/*captured (.toArray img)))
                    (finally
                      (.release img)
                      (.release faces))))))
            (Thread/sleep sleep-ms)
            (recur @state/*state)))
        (finally
          (.release faces)
          (.release gray-frame)
          (.release buff-frame)
          (when camera
            (.release camera)))))))

(defn -main
  "Run once on app start, starting the humble app."
  [& args]
  (println "Launched with args:" args)

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
  (println "after start-app!")
  @(capture-thread 25))
#_(def eye-classifier (CascadeClassifier. "resources/haarscascade_eye_tree_eyeglasses.xml"))

#_(defn detect-faces
    [camera]
    (let [frame (Mat.)
          gray (Mat.)
          faces (MatOfRect.)
          eyes (MatOfRect.)]
      (when (.read camera frame)
        (Imgproc/cvtColor frame gray Imgproc/COLOR_BGR2GRAY)
        (Imgproc/equalizeHist gray gray)
        (.detectMultiScale face-classifier gray faces)
        (doseq [face (.toList faces)]
          (let [center (Point. (+ (.x face)
                                  (/ (.width face) 2))
                               (+ (.y face)
                                  (/ (.height face) 2)))
                size (Size. (quot (.width face) 2)
                            (quot (.height face) 2))
                color (Scalar. 255 0 255)
                roi (.submat gray face)]
            (Imgproc/ellipse frame center size 0 0 360 color)
            (.detectMultiScale eye-classifier roi eyes)
            (doseq [eye (.toList eyes)]
              (println eye)
              (let [center (Point. (+ (.x face)
                                      (.x eye)
                                      (/ (.width eye) 2))
                                   (+ (.y face)
                                      (.y eye)
                                      (/ (.height eye) 2)))
                    radius (* (+ (.width eye)
                                 (.height eye))
                              0.25)
                    color (Scalar. 255 0 0)]
                (Imgproc/drawMarker frame center color))))))
      (Imgcodecs/imwrite "test.png" frame)))

#_(defn detect-faces-2
    [camera]
    (let [frame (Mat.)
          gray (Mat.)
          faces (MatOfRect.)
          features (MatOfRect.)
          landmarks (java.util.ArrayList.)
          facemark (Face/createFacemarkLBF)]
      (.loadModel facemark "resources/lbfmodel.yaml")

      (when (.read camera frame)
        (Imgproc/cvtColor frame gray Imgproc/COLOR_BGR2GRAY)
        (Imgproc/equalizeHist gray gray)
        (.detectMultiScale face-classifier gray faces)
        (doseq [face (.toList faces)]
          (let [center (Point. (+ (.x face)
                                  (/ (.width face) 2))
                               (+ (.y face)
                                  (/ (.height face) 2)))
                size (Size. (quot (.width face) 2)
                            (quot (.height face) 2))
                color (Scalar. 255 0 255)]
            (Imgproc/rectangle frame face color)))
        (when (.fit facemark gray faces landmarks)
          (println "fit")
          (doseq [l landmarks]
            (println "landmark")
            (Face/drawFacemarks frame l (Scalar. 0 0 255))))
        #_#_(.detectMultiScale face-classifier gray faces)
          (doseq [face (.toList faces)]))
      (Imgcodecs/imwrite "test.png" frame)))