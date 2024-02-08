(ns casselc.clj-gaze-tracking.capture
  (:require [casselc.clj-gaze-tracking.state :as state])
  (:import
   (org.bytedeco.opencv opencv_java)
   (org.opencv.core Mat MatOfByte MatOfRect  Scalar)
   (org.opencv.face Face)
   (org.opencv.imgproc Imgproc)
   (org.opencv.imgcodecs Imgcodecs)
   (org.opencv.objdetect CascadeClassifier)
   (org.opencv.videoio VideoCapture Videoio)))

(opencv_java.)

(def ^:private face-classifier (CascadeClassifier. "resources/haarcascade_frontalface_alt.xml"))
#_(def ^:private eye-classifier (CascadeClassifier. "resources/haarscascade_eye_tree_eyeglasses.xml"))

(defn- get-capture-device
  []
  (cond
    (Videoio/hasBackend Videoio/CAP_MSMF) (VideoCapture. 0 Videoio/CAP_MSMF)
    (Videoio/hasBackend Videoio/CAP_DSHOW) (VideoCapture. 0 Videoio/CAP_DSHOW)
    (Videoio/hasBackend Videoio/CAP_V4L2) (VideoCapture. "/dev/video0" Videoio/CAP_V4L2)
    (Videoio/hasBackend Videoio/CAP_V4L) (VideoCapture. "/dev/video0" Videoio/CAP_V4L)
    (Videoio/hasBackend Videoio/CAP_FFMPEG) (VideoCapture. "/dev/video0" Videoio/CAP_FFMPEG)
    (Videoio/hasBackend Videoio/CAP_GSTREAMER) (VideoCapture. "/dev/video0" Videoio/CAP_GSTREAMER)
    (Videoio/hasBackend Videoio/CAP_AVFOUNDATION) (VideoCapture. 0 Videoio/CAP_AVFOUNDATION)
    :else (VideoCapture. 0)))

(defn capture-thread
  [sleep-ms]
  (future
    (println "Capture thread started")
    (let [camera (get-capture-device)
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
                (Imgproc/cvtColor buff-frame gray-frame Imgproc/COLOR_BGR2GRAY)
                (Imgproc/equalizeHist gray-frame gray-frame)
                (.detectMultiScale face-classifier gray-frame faces)
                (doseq [face (.toList faces)]
                  (Imgproc/rectangle buff-frame face (Scalar. 255 0 255)))
                (when (.fit facemark gray-frame faces landmarks)
                  (doseq [l landmarks]
                    (Face/drawFacemarks buff-frame l (Scalar. 32 192 0))
                    (.release l))
                  (.clear landmarks))
                (.convertTo buff-frame gray-frame 0)
                (let [img (MatOfByte.)]
                  (try
                    (when (Imgcodecs/imencode ".jpeg" buff-frame img)
                      (reset! state/*captured (.toArray img)))
                    (finally
                      (.release img))))))
            (Thread/sleep sleep-ms)
            (recur @state/*state)))
        (finally
          (.release faces)
          (.release gray-frame)
          (.release buff-frame)
          (when camera
            (.release camera)))))))