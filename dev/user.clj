(ns user
  (:require [clj-async-profiler.core :as profile]))

(comment
  (profile/serve-ui "0.0.0.0" 8181))