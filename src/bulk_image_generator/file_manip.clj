(ns bulk-image-generator.file-manip
  (:require [clojure.java.io :as io])
  (:import (java.io File)))

(def user-home (let [f (io/file (System/getProperty "user.home"))
                     home (or (.getAbsolutePath f) "/tmp")]
                 home))
(def img-output-dir (or (System/getenv "output-dir") (str user-home "/generated-images")))
(def prompt-input-file (or (System/getenv "prompt-path") (str user-home "/generated-images/prompts.txt")))

(defn images-in-dir [dir]
  (prn "Grabbing images for " dir)
  (let [f (io/file dir)
        children-f (filter #(not (.isDirectory %)) (.listFiles f))]
    (reduce (fn [acc fi]
              (if-let [_ (javax.imageio.ImageIO/read fi)]
                (conj acc fi)
                acc))
            [] children-f)))
(defn get-images [prompt]
  (let [images (images-in-dir (str img-output-dir "/" prompt))]
    (prn images)
    (prn "Prompt: " prompt)
    (map (fn [^File img] (str prompt "/" (.getName ^File img))) images)))
(defn create-prompt-dir [prompt]
  (let [dir-name (str img-output-dir "/" prompt "/" prompt ".txt")]
    (io/make-parents dir-name)))
(defn existing-dirs->set [img-output-dir]
  (->> (io/file img-output-dir)
       (.listFiles)
       (filter #(.isDirectory %))
       (map #(.getName %))
       (into #{})))

(defn cp-image [prompt image-name]
  (let [image-path (str img-output-dir "/" prompt "/" image-name)
        f (io/file image-path)]
  (create-prompt-dir "selected")
  (io/copy f (io/file (str img-output-dir "/selected/" image-name)))))

(defn mv-images [prompt]
  (let [images-f (images-in-dir img-output-dir)]
    (prn "Moving images for " prompt)
    (create-prompt-dir prompt)
    (doseq [f images-f]
      (let [f-name (.getName ^File f)]
        (io/copy f (io/file (str img-output-dir "/" prompt "/" f-name)))
        (io/delete-file f)))))
