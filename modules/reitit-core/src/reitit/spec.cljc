(ns reitit.spec
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]
            [reitit.core :as reitit]))

;;
;; routes
;;

(s/def ::path (s/with-gen string? #(gen/fmap (fn [s] (str "/" s)) (s/gen string?))))

(s/def ::arg (s/and any? (complement vector?)))
(s/def ::data (s/map-of keyword? any?))
(s/def ::result any?)

(s/def ::raw-route
  (s/cat :path ::path
         :arg (s/? ::arg)
         :childs (s/* (s/and (s/nilable ::raw-route)))))

(s/def ::raw-routes
  (s/or :route ::raw-route
        :routes (s/coll-of ::raw-route :into [])))

(s/def ::route
  (s/cat :path ::path
         :data ::data
         :result (s/? any?)))

(s/def ::routes
  (s/or :route ::route
        :routes (s/coll-of ::route :into [])))

;;
;; Default data
;;

(s/def ::name keyword?)
(s/def ::handler fn?)
(s/def ::default-data (s/keys :opt-un [::name ::handler]))

;;
;; router
;;

(s/def ::router reitit/router?)
(s/def :reitit.router/path ::path)
(s/def :reitit.router/routes ::routes)
(s/def :reitit.router/data ::data)
(s/def :reitit.router/expand fn?)
(s/def :reitit.router/coerce fn?)
(s/def :reitit.router/compile fn?)
(s/def :reitit.router/conflicts fn?)
(s/def :reitit.router/router fn?)

(s/def ::opts
  (s/nilable
    (s/keys :opt-un [:reitit.router/path
                     :reitit.router/routes
                     :reitit.router/data
                     :reitit.router/expand
                     :reitit.router/coerce
                     :reitit.router/compile
                     :reitit.router/conflicts
                     :reitit.router/router])))

(s/fdef reitit/router
        :args (s/or :1arity (s/cat :data (s/spec ::raw-routes))
                    :2arity (s/cat :data (s/spec ::raw-routes), :opts ::opts))
        :ret ::router)

;;
;; Route data validator
;;


(defrecord Problem [path scope data spec problems])

(defn problems-str [problems explain]
  (apply str "Invalid route data:\n\n"
         (mapv
           (fn [{:keys [path scope data spec]}]
             (str "-- On route -----------------------\n\n"
                  (pr-str path) (if scope (str " " (pr-str scope))) "\n\n" (explain spec data) "\n"))
           problems)))

(defn throw-on-problems! [problems explain]
  (throw
    (ex-info
      (problems-str problems explain)
      {:problems problems})))

(defn validate-route-data [routes spec]
  (->> (for [[p d _] routes]
         (when-let [problems (and spec (s/explain-data spec d))]
           (->Problem p nil d spec problems)))
       (keep identity) (seq)))

(defn validate-spec!
  [routes {:keys [spec ::explain]
           :or {explain s/explain-str
                spec ::default-data}}]
  (when-let [problems (validate-route-data routes spec)]
    (throw-on-problems! problems explain)))