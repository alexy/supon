(ns 'com.suffix
  (require clojure.contrib.duck-streams
    [clojure.contrib.str-utils2 :as s2])
  (import [org.joda.time DateTime]))

(defn vec-to-map [v]
  (->> v (reduce (fn [res e] (assoc! res e (inc (or (res e) 0)))) (transient {})) persistent!))
  
(defn vec-hist [v]
  (let [
    m (vec-to-map v)]
    (sort m)))
