(ns bulk-image-generator.core
  (:require [clojure.java.io :as io]
            [clj-http.client :as http]))

(def user-home (let [f (io/file (System/getProperty "user.home"))
                     home (or (.getAbsolutePath f) "/tmp")]
                 home))
(def img-output-dir (or (System/getenv "output-dir") (str user-home "/generated-images")))
(def prompt-input-file (or (System/getenv "prompt-path") (str user-home "/generated-images/prompts.txt")))

(defn create-prompt-dir [prompt]
  (let [dir-name (str img-output-dir "/" prompt "/" prompt ".txt")]
    (io/make-parents dir-name)))
(defn existing-dirs->set [img-output-dir]
  (->> (io/file img-output-dir)
          (.listFiles)
          (filter #(.isDirectory %))
          (map #(.getName %))
          (into #{})))

(def todo-prompts (atom #{}))
(def completed-prompts (atom #{}))

(defn gen-prompt-lists []
  (let [existing-dirs (existing-dirs->set img-output-dir)]
    (with-open [r (io/reader prompt-input-file)]
      (doseq [l (line-seq r)]
        (if (get existing-dirs l)
          (swap! completed-prompts conj l)
          (swap! todo-prompts conj l)
          ))))
  {:todos @todo-prompts
   :completed @completed-prompts})

(defn send-api-request [prompt]
    (http/post "http://127.0.0.1:7860/sdapi/v1/txt2img"
               {:form-params {
                              :enable_hr                            false,
                              :denoising_strength                   0,
                              :firstphase_width                     0,
                              :firstphase_height                    0,
                              :hr_scale                             2,
                              :hr_upscaler                          "string",
                              :hr_second_pass_steps                 0,
                              :hr_resize_x                          0,
                              :hr_resize_y                          0,
                              :hr_prompt                            "",
                              :hr_negative_prompt                   "",
                              :prompt                               "Mario reaching for a super star in a cyberpunk city",
                              :styles                               [],
                              :seed                                 -1,
                              :subseed                              -1,
                              :subseed_strength                     0,
                              :seed_resize_from_h                   -1,
                              :seed_resize_from_w                   -1,
                              :batch_size                           1,
                              :n_iter                               1,
                              :steps                                50,
                              :cfg_scale                            7,
                              :width                                512,
                              :height                               512,
                              :restore_faces                        false,
                              :tiling                               false,
                              :do_not_save_samples                  false,
                              :do_not_save_grid                     false,
                              :eta                                  0,
                              :s_min_uncond                         0,
                              :s_churn                              0,
                              :s_tmax                               0,
                              :s_tmin                               0,
                              :s_noise                              1,
                              :override_settings                    {},
                              :override_settings_restore_afterwards true,
                              :script_args                          [],
                              :sampler_index                        "Euler",
                              :send_images                          true,
                              :save_images                          true,
                              :alwayson_scripts                     {}
                              }}

               )
  )

(defn generate-images [] "Pulls from todo-prompts"
  (if (> (count todo-prompts) 0)
    (let [prompt (first @todo-prompts)]
      (swap! todo-prompts disj prompt)

      )))