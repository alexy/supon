(defn errln [ & args] (.println System/err (apply str args)))

(defn sqrt [x] (. Math sqrt x))
(defn sum [s] (apply + s))
(defn mean [s] (if (number? s) s (if (empty? s) 0 (/ (sum s) (count s)))))
(defn mean2 [x y] (/ (+ x y) 2.))
;; "infinite integral sequence, 0-based, for ordering things"
(def ordinals (iterate inc 0))
  

(defn sqr [x]
  "square each element of a sequence"
  (* x x))

(defn length [v]
  "length of a real-valued vector"
  (->> v (map sqr) sum sqrt))

(defn distance [a b]
  "1 - cosine distance between two sparse vectors, here tag-count maps"
  (if (not (and (map? a) (map? b))) 1.
  (let [
    v1 (vals a)
    v2 (vals b)
    len1 (length v1)
    len2 (length v2)
    keywise-prod (->> a (map (fn [[k v]] (when-let [w (b k)] (* v w)))) (remove nil?))
    numer (->> keywise-prod sum)
    denom (* len1 len2)
    cosine (if (zero? denom) 0. (/ numer denom))
    ]
  (- 1. cosine))))

  
(defstruct cluster :id :point-ids :center)

(defn init-clusters [points n]
  (->> points (take n) (map #(struct cluster %1 [] %2) ordinals) vec))

(defn empty-clusters [n]
  (->> (range n) (map #(struct cluster %1 [] 0.0)) vec))
  
(defn ensure-coll [x] (cond (coll? x) x (nil? x) [] :default [x]))
;; another way to think of merge-with maps with first scalar value into a vector value:
;;  (reduce (fn [m [k v]] (assoc m k (conj (m k []) v))) {} [[:a 1] [:a 2] [:b 1] [:b 7]])  

(defn average [points]
  (let [joint (apply merge-with #(conj (ensure-coll %1) %2) points)] 
    (->> joint (map (fn [[k v]] [k (mean v)])) (into {}))))

(defn find-nearest-cluster [clusters point]
  ;; (errln "point:" (take 5 point) " first center: " (take 5 (:center (first clusters))))
  (let [dists (map #(vector (distance point (:center %1)) %2) clusters ordinals)]
    (->> dists (reduce (fn [[r _ :as prev] [d _ :as curr]] (if (< d r) curr prev))) second)))
    
(defn readjust-centroid [points cluster]
  (let [{:keys [id point-ids center]} cluster
    points (map #(points %) point-ids)
    center (average points)]
    (assoc cluster :center center)))
    
(defn readjust-clusters [points clusters]
  (vec (pmap (partial readjust-centroid points) clusters)))
    
(defn reassign-points [points clusters]
  "assign each point to its nearest cluster"
  (let [
    nearests (->> points (pmap (partial find-nearest-cluster clusters)) vec)
    nearests-poses (map vector nearests ordinals)]
    (reduce (fn [cluvec [c p]] (update-in cluvec [c :point-ids] #(conj % p))) 
      (empty-clusters (count clusters)) nearests-poses)))
    
(defn kmeans [points num-clusters max-iterations]
  "the main algorithm"
  (loop [clusters (init-clusters points num-clusters) 
         prev-clusters (empty-clusters num-clusters) iter 0]
    (if (or (= prev-clusters clusters) (>= iter max-iterations))
      clusters
      (let [reassigned (reassign-points points clusters)]
        (errln "iteration " iter)
      (recur (readjust-clusters points reassigned) clusters (inc iter)))
    )))
