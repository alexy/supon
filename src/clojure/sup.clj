;; (ns com.suffix
  (use 'clojure.set)
  (use 'clojure.contrib.duck-streams)
  (use 'clojure.contrib.seq-utils)
  (require '[clojure.contrib.str-utils2 :as s2])
  (import '[org.joda.time DateTime])

(defstruct id-user :sid :id :age :gender :city :state :country)
(defstruct user :gender :age :city :state :country)
(def *user-keys* (keys (struct user)))
(defstruct sup  :sid :id :rate :time :user :tags)


(defn parse-line [line site-id line-id] 
  (let [fields (s2/split line #",") 
    prefix-length 7 
    prefix (take prefix-length fields) 
    [rate time age gender city state country] prefix 
    [rate time age gender] (map #(Integer/parseInt %) [rate time age gender]) 
    time (DateTime. (* (long time) 1000)) 
    user (struct id-user site-id line-id age gender city state country)
    tags (->> (drop prefix-length fields) 
          (map #(let [[tag n] (s2/split % #":")] [(keyword tag) (Integer/parseInt n)])) (into {}))
          ] 
    (struct sup site-id line-id rate time user tags)))
    
(defn user-no-id [user-with-id]
  "select a subset of id-user without any ids,
  return as a hash-map.  NB: not struct user!"
  (select-keys user-with-id *user-keys*))

(defn group-user [umap iduser]
  (let [
    {:keys [sid id]} iduser
    user (user-no-id iduser)
    sids (or (umap user) {})
    ids  (or (sids sid) [])
    sids (assoc sids sid (conj ids id))
    ]
    (assoc umap user sids)))
  
(defn group-users [users]
    (reduce group-user {} users))
          
(defn user-id [iduser]
  (let [{:keys [sid id]} iduser]
    [sid id]))
    

(def *city-map*    (make-id-mapper))
(def *state-map*   (make-id-mapper))
(def *country-map* (make-id-mapper))
(def *age-map*     (make-id-mapper :keep-keys))
(def *gender-map*  (make-id-mapper :keep-keys))
(def *tag-map*     (make-id-mapper))
    
(defn numeric-user [usr]
  (let [{:keys [sid id age gender city state country]} usr
    uid [sid id]
    city-num    ((*city-map*    :map) city    uid)
    state-num   ((*state-map*   :map) state   uid)
    country-num ((*country-map* :map) country uid)]
    ((*age-map*     :map) age     uid)
    ((*tag-map*     :map) city uid)
    (struct id-user sid id age gender city-num state-num country-num)))

(defn numeric-tags [tags id]
  (map #((*tag-map* :map) % id) tags))

(defn numeric-data [{:keys [sid id rate time user tags]}]
  (let [
    lid        [sid id]
    nuser      (numeric-user user)
    tag-keys   (keys tags)
    tag-counts (vals tags)
    ntag-keys  (numeric-tags tag-keys lid)
    ntags      (apply hash-map (interleave ntag-keys tag-counts))]
    (struct sup sid id rate time nuser ntags)))
    
(defn top-maps [mapper & [n]]
  (let [tops (->> ((mapper :get-map)) 
          (map (fn [[k v]] [k ((comp count second) v)])) (sort-by second >))]
        (if n (take n tops) tops)))

(defn all-tags [dv]
  (apply concat (map #(keys (:tags %)) dv)))
  
(defn tag-simple-set [dv]    
  ;; same as: (reduce conj #{} (apply concat (map #(keys (:tags %)) us)))
  (set (all-tags dv)))

(defn tag-multi-set [dv]    
  (reduce (fn [res e] (update-in res [e] #(inc (or % 0)))) 
    {} (all-tags dv)))
    
(defn subtract-maps [m1 m2]
  (apply dissoc m1 (keys m2)))
  
(defn uniq-tags [dv f-test]
  (let [[us them] (separate f-test dv) 
    ours   (tag-simple-set us)
    theirs (tag-simple-set them)]
    (difference ours theirs)))
            
(defn uniq-tags-counted [dv f-test]
  (let [[us them] (separate f-test dv) 
    ours   (tag-multi-set us)
    theirs (tag-multi-set them)]
  (subtract-maps ours theirs)))
        
