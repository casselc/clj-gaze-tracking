(ns casselc.clj-gaze-tracking.capture
  (:require [casselc.clj-gaze-tracking.state :as state])
  (:import
   (org.bytedeco.opencv opencv_java)
   (org.opencv.core Mat MatOfByte MatOfPoint2f MatOfRect RotatedRect Scalar)
   (org.opencv.face Face)
   (org.opencv.imgproc Imgproc)
   (org.opencv.imgcodecs Imgcodecs)
   (org.opencv.objdetect CascadeClassifier)
   (org.opencv.videoio VideoCapture Videoio)))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(opencv_java.)

(def ^:private ^CascadeClassifier face-classifier (CascadeClassifier. "resources/haarcascade_frontalface_alt.xml"))
(def ^:private ^CascadeClassifier eye-classifier (CascadeClassifier. "resources/haarscascade_eye_tree_eyeglasses.xml"))

(defn- get-capture-device
  ^VideoCapture []
  (cond
    (Videoio/hasBackend Videoio/CAP_DSHOW) (VideoCapture. 0 Videoio/CAP_DSHOW)
    (Videoio/hasBackend Videoio/CAP_V4L2) (VideoCapture. "/dev/video0" Videoio/CAP_V4L2)
    (Videoio/hasBackend Videoio/CAP_AVFOUNDATION) (VideoCapture. 0 Videoio/CAP_AVFOUNDATION)
    :else (VideoCapture. 0)))

(defn capture-thread
  [sleep-ms]
  (future
    (println "Capture thread started")
    (let [camera (get-capture-device)
          facemark (Face/createFacemarkLBF)
          buff-frame (Mat. 720 1280 16)
          gray-frame (Mat. 720 1280 0)
          faces (MatOfRect.)
          landmarks (java.util.ArrayList.)]
      (try
        (println "Got capture device:" (.getBackendName camera) "open:" (.isOpened camera) "exceptions:" (.getExceptionMode camera))
        #_(.set camera Videoio/CAP_PROP_FRAME_WIDTH 1280)
        #_(.set camera Videoio/CAP_PROP_FRAME_HEIGHT 720)
        (.set camera Videoio/CAP_PROP_FPS 1)
        (.set camera Videoio/CAP_PROP_AUTOFOCUS 0)
        (.set camera Videoio/CAP_PROP_AUTO_EXPOSURE 1)

        (.loadModel facemark "resources/lbfmodel.yaml")
        (loop [{:keys [shutting-down? capturing?]} @state/*state]
          (when-not shutting-down?
            (when capturing?
              (when (.read camera buff-frame)
                (Imgproc/cvtColor buff-frame gray-frame Imgproc/COLOR_BGR2GRAY)
                (Imgproc/equalizeHist gray-frame gray-frame)
                (.detectMultiScale face-classifier gray-frame faces)
                (when (.fit facemark gray-frame faces landmarks)
                  ;; find the largest face and draw landmarks and bounding box
                  (let [[_ bounds ^MatOfPoint2f marks] (reduce (fn [[^double largest-area
                                                                     _
                                                                     ^MatOfPoint2f largest-marks
                                                                     :as largest]
                                                                    ^MatOfPoint2f markers]
                                                                 (let [bounds (Imgproc/minAreaRect markers)
                                                                       area (.area (.size bounds))]
                                                                   (if (> area largest-area)
                                                                     (do
                                                                       (some-> largest-marks .release)
                                                                       [area (.boundingRect bounds) markers])
                                                                     (do
                                                                       (.release markers)
                                                                       largest))))
                                                               [0 nil nil]
                                                               landmarks)]
                    (Imgproc/rectangle buff-frame bounds (Scalar. 0 255 0))
                    (Face/drawFacemarks buff-frame marks (Scalar. 255 0 0))
                    (.release  marks))
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