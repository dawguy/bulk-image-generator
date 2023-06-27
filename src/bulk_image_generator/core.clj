(ns bulk-image-generator.core
  (:require [clojure.java.io :as io]
            [clj-http.client :as http]
            [taoensso.carmine :as car]
            [taoensso.carmine.message-queue :as car-mq]
            [cheshire.core :as ches]
            [bulk-image-generator.file-manip :as fm])
  (:import (java.io File)))

(def user-home (let [f (io/file (System/getProperty "user.home"))
                     home (or (.getAbsolutePath f) "/tmp")]
                 home))
(def img-output-dir (or (System/getenv "output-dir") (str user-home "/generated-images")))
(def prompt-input-file (or (System/getenv "prompt-path") (str user-home "/generated-images/prompts.txt")))

(defonce redis-conn-pool (car/connection-pool {}))
(def redis-conn-spec {:uri "redis://0.0.0.0:6379"})

(def wcar-opts {:pool redis-conn-pool
                :spec redis-conn-spec})
(defmacro wcar* [& body] `(car/wcar ~@wcar-opts ~@body))

(comment "car/get and car/ping are automatically generated. Not sure if I'm a fan of this, but it is what it is"
   (clojure.repl/doc car/get)
   (clojure.repl/doc car/ping)
   (clojure.repl/doc car/keys)
   (car/wcar wcar-opts (car/ping))
   (car/wcar wcar-opts (car/set "foo" "bar"))
   (car/wcar wcar-opts (car/set "foobar" [1 2 3 4 5]))
   (car/wcar wcar-opts (car/get "foo"))
   (car/wcar wcar-opts (car/get "broccoli"))
   (wcar* (car/ping))
   .)

(def todo-prompts (atom []))
(def completed-prompts (atom {}))

(defn prn-prompts []
  (prn @todo-prompts)
  (prn @completed-prompts))

(defn gen-prompt-lists []
  (prn "Attempting to generate prompt lists")
  (let [existing-dirs (fm/existing-dirs->set img-output-dir)]
    (reset! todo-prompts [])
    (reset! completed-prompts {})
    (prn "Opening prompt file")
    (with-open [r (io/reader prompt-input-file)]
      (doseq [l (line-seq r)]
        (prn (str "Line found: " l))
        (if (get existing-dirs l)
          (swap! completed-prompts assoc l {:seeds []})
          (swap! todo-prompts conj l)
          ))))
  {:todos @todo-prompts
   :completed @completed-prompts})

(defn send-api-request [prompt seed]
    (http/post "http://127.0.0.1:7860/sdapi/v1/txt2img"
               {:as           :auto
                :coerce       :always
                :content-type :json
                :body         (ches/generate-string {
                                            :enable_hr                            false,
                                            :prompt                               prompt,
                                            :negative-prompt                      "shelves, shelf, shoppers"
                                            :styles                               [],
                                            :seed                                 seed,
                                            :batch_size                           1,
                                            :batch_count                          2,
                                            :n_iter                               1,
                                            :steps                                20,
                                            :cfg_scale                            7,
                                            :width                                512,
                                            :height                               512,
                                            :override_settings                    {},
                                            :override_settings_restore_afterwards true,
                                            :script_args                          [],
                                            :sampler_index                        "Euler",
                                            :save_images                          true,
                                            })}))
;(defn send-api-request [prompt seed]
;  (prn (str "Should be sending API request right now for " prompt " and " seed)))

(defn send-create-image-requests [prompt]
  (prn "Creating images for " prompt)
  (let [prompt-seeds (take 5 (repeatedly 10 #(rand-int 2000000000)))]
    (doseq [seed prompt-seeds]
      (send-api-request (str "several " prompt " on a white background") seed))
    ;(doseq [seed prompt-seeds]
    ;  (send-api-request (str prompt " upright on a shelf with no background") seed))
    ;(doseq [seed prompt-seeds]
    ;  (send-api-request (str "group of " prompt " with no background") seed))
    (prn (str "Updating completed prompts with " prompt " and seeds " (vec prompt-seeds)))
    (swap! completed-prompts update-in [prompt :seeds] (fn [_] (vec prompt-seeds)))
    (prn "Setting to REDIS: " prompt " val " (vec prompt-seeds))
    (try
      (car/wcar wcar-opts (car/set prompt (vec prompt-seeds)))
      (car/wcar wcar-opts (car/lpush "prompts" prompt))
      (catch Exception e (prn (str "Redis seems to be down."))))
    ))


(defn generate-images [] "Pulls from todo-prompts"
  (prn "Generating images")
  (if (> (count @todo-prompts) 0)
    (let [prompt (first @todo-prompts)]
      (prn "Prompt found " prompt)
      (swap! todo-prompts subvec 1)
      (send-create-image-requests prompt)
      (fm/mv-images prompt))
    (prn "No prompt found."))
  (prn-prompts))

(defn refill-completed-queue []
  (prn "Grabbing images for " img-output-dir)
  (let [f (io/file img-output-dir)
        children-f (filter #(.isDirectory %) (.listFiles f))
        dir-names (into #{} (map #(.getName %) children-f))]
    (prn dir-names)
    (prn (disj dir-names "selected" ".git"))
    (doseq [name (disj dir-names "selected" ".git")]
      (car/wcar wcar-opts (car/lpush "prompts" name))
      )))

(defn main []
  (gen-prompt-lists)
  (loop [todo @todo-prompts]
    (prn "Remaining: " todo)
    (if (> (count todo) 0)
      (do (generate-images)
          (recur @todo-prompts))
      @completed-prompts)))

(comment
  (main)
  (refill-completed-queue)
  (car/wcar wcar-opts (car/lpush "prompts" "eggs"))
  (car/wcar wcar-opts (car/lpop "prompts"))
  (take-while #(not (nil? %)) (repeatedly #(car/wcar wcar-opts (car/lpop "prompts"))))
  )
