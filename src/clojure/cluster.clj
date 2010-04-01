(defn sqrt [x] (. Math sqrt x))
(defn sum [s] (apply + s))
(defn mean [s] (if (number? s) s (if (empty? s) 0 (/ (sum s) (count s)))))
(defn sqr [x]
  "square each element of a sequence"
  (* x x))

(defn length [v]
  "length of a real-valued vector"
  (->> v (map sqr) sum sqrt))

(defn distance [a b]
  "cosine distance between two sparse vectors, here tag-count maps"
  (let [
    v1 (vals a)
    v2 (vals b)
    len1 (length v1)
    len2 (length v2)
    keywise-prod (->> a (map (fn [[k v]] (when-let [w (b k)] (* v w)))) (remove nil?))
    numer (->> keywise-prod sqrt)
    denom (* len1 len2)
    ]
  (if (zero? denom) 0 (/ numer denom))))

(defn init-clusters [points n]
  (take n points))

(defn ensure-coll [x] (cond (coll? x) x (nil? x) [] :default [x]))
;;  (reduce (fn [m [k v]] (assoc m k (conj (m k []) v))) {} [[:a 1] [:a 2] [:b 1] [:b 7]])  

(defn average [points]
  (let [joint (apply merge-with #(conj (ensure-coll %1) %2) points)]
    (->> joint (map (fn [[k v]] [k (mean v)])) (into {})))) 