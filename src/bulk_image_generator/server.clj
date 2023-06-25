(ns bulk-image-generator.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [reitit.core :as r]
            [reitit.ring :as ring]
            [reitit.ring.middleware.muuntaja :as muuntaja]
            [muuntaja.core :as m]
            [reitit.ring.coercion :as rrc]
            [clojure.tools.logging :as log]
            [reitit.ring.middleware.exception :as exception]
            [bulk-image-generator.middleware.cors :as cors]
            [bulk-image-generator.middleware.headers :as headers]
            ))

(def port 8666)

; Based heavily off Startrek repo.
(defn ^:private exception-info-handler
  [exception-info request] ; exception-info and request both come from reitit.
  (log/infof "EXCEPTION-INFO-HANDLER %s --- %s" (str exception-info) (str request))
  (let [{:keys [http-status error cause] :or {http-status 500} :as exception-data} (ex-data exception-info)
        body  {:message (ex-message exception-info)}]
    {:status http-status :body (m/encode "application/json" body)}))

(def exceptions-middleware
  (exception/create-exception-middleware
    (merge
      exception/default-handlers ;; reitit default handlers
      {
       clojure.lang.ExceptionInfo exception-info-handler
       :muuntaja m/instance
       :reitit.coercion/request-coercion (fn [request] (log/infof "REQUEST %s" (str request)))
       :reitit.coercion/response-coercion (fn [request] (log/infof "RESPONSE %s" (str request)))
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

(defn my-routes []
  ["/"
   ["" {:get {:handler    (fn [request]
                            (prn request)
                            (prn "////////")
                            {:status 200 :body {:a 1} :headers {}})
              :parameters {}
              :response   {}}}]
   ["next" {:get {:handler    (fn [request]
                                (prn request)
                                (prn "///next////")
                                {:status 200 :body "{:a 2}" :headers {}})
                  :parameters {}
                  :response   {}}}]])

(defn my-handler []
  (log/infof "Starting my handler")
  (ring/ring-handler
    (ring/router
      ["" (my-routes)]
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