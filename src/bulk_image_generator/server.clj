(ns bulk-image-generator.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [muuntaja.core :as m]
            [reitit.ring.coercion :as rrc]
            [reitit.coercion.spec :as rcs]
            [clojure.tools.logging :as log]
            [reitit.ring.middleware.exception :as exception]
            [bulk-image-generator.middleware.cors :as cors]
            [bulk-image-generator.middleware.headers :as headers]
            [hiccup2.core :as h]
            [clojure.java.io :as io]
            [expound.alpha :as expound]
            )
  (:import (java.io File)))

(def port 8666)

; Based heavily off Startrek repo.
(defn ^:private exception-info-handler
  [exception-info request] ; exception-info and request both come from reitit.
  (log/infof "EXCEPTION-INFO-HANDLER %s --- %s" (str exception-info) (str request))
  (let [{:keys [http-status error cause] :or {http-status 500} :as exception-data} (ex-data exception-info)
        body  {:message (ex-message exception-info)}]
    {:status http-status :body (m/encode "application/json" body)}))

(defn coercion-error-handler [status]
  (let [printer (expound/custom-printer {:theme :figwheel-theme, :print-specs? false})
        handler (exception/create-coercion-handler status)]
    (fn [exception request]
      (printer (-> exception ex-data :problems))
      (handler exception request))))

(def exceptions-middleware
  (exception/create-exception-middleware
    (merge
      exception/default-handlers ;; reitit default handlers
      {
       clojure.lang.ExceptionInfo exception-info-handler
       :muuntaja m/instance
       :reitit.coercion/request-coercion (coercion-error-handler 400)
       :reitit.coercion/response-coercion (coercion-error-handler 500)
       ;;
       ;; this ↓ wraps every handler (above), including the retit
       ;; default handlers, exception-info-handler and the exception-handler.
       ;;
       ::exception/wrap (fn [handler exception request]
                          (let [response (handler exception request)]
                            (log/warnf "Exception handler response %s" response)
                            response))})))

(def a-request (atom {}))
(comment ""
         (deref a-request)
         .)
(defn my-middleware [handler]
  (prn "Middleware hit")
  (prn handler)
  (fn [request]
    (log/infof "Request hit my-middleware %s." request)
    (log/infof "Body: %s" (str (:body request)))
    (reset! a-request request)
    (handler request)))

(def site-image-base-path bulk-image-generator.core/img-output-dir)
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
  (let [images (images-in-dir (str site-image-base-path "/" prompt))]
    (prn images)
    (prn "Prompt: " prompt)
    (map (fn [^File img] (str prompt "/" (.getName ^File img))) images)))

(defn image-url->image [prompt image-url]
  (let [port 8855
        url (str "http://localhost:" port "/" image-url)]
    [:image {:src    url
             :width  256
             :height 256
             :data-url url
             :data-prompt prompt}]))

(defn page [prompt]
  [:html
   [:head
    [:script {:type "text/javascript"
              :src "http://localhost:8855/events.js"
              :defer true}]
    ]
   [:body
    [:div
     (map (partial image-url->image prompt) (get-images prompt))
     ]]
])

(defn my-routes []
  ["/"
   ["" {:get {:handler    (fn [request]
                            {:status  200 :body (str (h/html (page "eggs")))
                             :headers {"Content-Type" "text/html"}})
              :parameters {}
              :response   {}}}]
   ["select" {:post {:handler (fn [request]
                                (prn request)
                                (prn (:body-params request))
                                {:status 200 :body {:a 2}
                                 :headers {"Content-Type" "application/json"}})
                  :parameters {}
                  :response   {}}}]])

(defn my-handler []
  (log/infof "Starting my handler")
  (ring/ring-handler
    (ring/router
      ["" (#'my-routes)]
      {:data {:muuntaja m/instance
              :middleware [my-middleware
                           cors/cors-middleware
                           headers/headers-middleware
                           muuntaja/format-middleware
                           exceptions-middleware
                           rrc/coerce-request-middleware
                           rrc/coerce-response-middleware
                           ]}})
    (ring/routes
      (ring/create-default-handler
        {:not-found (constantly (do (log/infof "NOT FOUND") {:status 404 :body "Not found"}))}))))

(comment ""
         (def http-server (run-jetty (#'my-handler)
                                     {:port port, :join? false}))
         (.stop http-server)
         )