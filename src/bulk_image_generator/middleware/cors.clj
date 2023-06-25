(ns bulk-image-generator.middleware.cors
  (:require [ring.util.response :as response]
            [clojure.tools.logging :as log]))

; This should probably be moved to a config for better handling
(def ^:private default-cors {
                   :access-control-allow-origin "*"
                   :access-control-allow-headers "*"
                   :access-control-allow-methods "DELETE, GET, OPTIONS, POST, PUT"
                   })

; Based heavily on David Harrigan's Startrek cors implementation.
(defn ^:private add-cors-headers [response {:keys [access-control-allow-origin
                                                   access-control-allow-headers
                                                   access-control-allow-methods] :as cors}]
  (log/infof "Attaching cors headers to response. %s" response)
  (-> response
      (assoc-in [:headers "Access-Control-Allow-Origin"] access-control-allow-origin)
      (assoc-in [:headers "Access-Control-Allow-Headers"] access-control-allow-headers)
      (assoc-in [:headers "Access-Control-Allow-Methods"] access-control-allow-methods)
      ))

(defn ^:private wrap-cors-middleware [handler]
  (fn [{:keys [request-method app-config] :as request}]
    (if (= :options request-method)
      (add-cors-headers (response/response nil) default-cors)
      (let [response (handler request)]
        (log/infof "Response pre-cors is %s" response)
        (add-cors-headers response default-cors)
        ))))

(def cors-middleware
  {:name ::cors
   :description "Adds cors headers"
   :wrap wrap-cors-middleware})


