(ns daw.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.util.response :refer [response content-type]]
            [compojure.core :refer [defroutes GET PUT]]
            [compojure.route :as route]
            [daw.core :as core]))

(defroutes app-routes
  (GET "/" []
    (content-type (response (slurp "resources/public/index.html")) "text/html"))
  (GET "/api/state" []
    (response @core/state))
  (PUT "/api/master" {body :body}
    (swap! core/state assoc :master (:value body))
    (response @core/state))
  (PUT "/api/mixer/:ch" [ch :as {body :body}]
    (let [ch-idx (Integer/parseInt ch)]
      (swap! core/state update :mixer assoc ch-idx (:value body)))
    (response @core/state))
  (PUT "/api/bpm" {body :body}
    (swap! core/state assoc :bpm (:value body))
    (response @core/state))
  (PUT "/api/playing" {body :body}
    (swap! core/state assoc :playing (:value body))
    (response @core/state))
  (route/not-found "Not Found"))

(def app
  (-> app-routes
      (wrap-json-body {:keywords? true})
      wrap-json-response
      (wrap-resource "public")))

(defn start []
  (println "Starting server on http://localhost:3015")
  (run-jetty app {:port 3015 :join? false}))

(defn -main []
  (start)
  @(promise))
