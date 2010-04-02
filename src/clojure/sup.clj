;; (ns com.suffix
  (use 'clojure.set)
  ;; (use 'clojure.contrib.duck-streams)
  (use 'clojure.contrib.io)
  ;; (use 'clojure.contrib.seq-utils)
  (use 'clojure.contrib.seq)
  ;; (require '[clojure.contrib.str-utils2 :as s2])
  (require '[clojure.contrib.string :as s2])
  (import '[org.joda.time DateTime])
  (require 'mapper)
  (require 'cluster)

;; common formats to keep the data in
(defstruct id-user :sid :id :age :gender :city :state :country)
(defstruct user :gender :age :city :state :country)
(def *user-keys* (keys (struct user)))
(defstruct sup  :sid :id :rate :time :user :tags)


(defn parse-line [line site-id line-id] 
  "parse a single SU data line and return struct sup, containing id-user, tags, etc., and site-line id"
  (let [
    fields (s2/split #"," line)  ; line  #"," in 1.1
    prefix-length 7 
    prefix (take prefix-length fields) 
    [rate time age gender city state country] prefix 
    [rate time age gender] (map #(Integer/parseInt %) [rate time age gender]) 
    time (DateTime. (* (long time) 1000)) 
    user (struct id-user site-id line-id age gender city state country)
    tags (->> (drop prefix-length fields) 
          (map #(let [[tag n] (s2/split #":" %)]  ;  % #":" in 1.1 
            [(keyword tag) (Integer/parseInt n)])) (into {}))
          ] 
    (struct sup site-id line-id rate time user tags)))
    
(defn user-no-id [user-with-id]
  "select a subset of id-user without any ids,
  return as a hash-map.  NB: not struct user!"
  (select-keys user-with-id *user-keys*))

(defn group-user [umap iduser]
  "match a single user with identical non-id data into site-line map,
  thus allowing partial matching on similar demographics"
  (let [
    {:keys [sid id]} iduser
    user (user-no-id iduser)
    sids (or (umap user) {})
    ids  (or (sids sid) [])
    sids (assoc sids sid (conj ids id))
    ]
    (assoc umap user sids)))
  
(defn group-users [users]
  "group users with the same demographics together in a map as key,
  with the value containing a map site=>lines where such demographics occurs"
    (reduce group-user {} users))
          
(defn user-id [iduser]
  "select the id part only form an id-user"
  (let [{:keys [sid id]} iduser]
    [sid id]))
    
;; globally defined closures to count occurrences of same demographics
(def *city-map*    (make-id-mapper))
(def *state-map*   (make-id-mapper))
(def *country-map* (make-id-mapper))
(def *age-map*     (make-id-mapper :keep-keys))
(def *gender-map*  (make-id-mapper :keep-keys))
(def *tag-map*     (make-id-mapper))
    
;; map a user's data from categorical to numerical space 
;; with the mapper closures defined above
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
  "map tags into numeric space"
  (map #((*tag-map* :map) % id) tags))

(defn numeric-data [{:keys [sid id rate time user tags]}]
  "map an entire data line into numeric space"  
  (let [
    lid        [sid id]
    nuser      (numeric-user user)
    tag-keys   (keys tags)
    tag-counts (vals tags)
    ntag-keys  (numeric-tags tag-keys lid)
    ntags      (apply hash-map (interleave ntag-keys tag-counts))]
    (struct sup sid id rate time nuser ntags)))
    
(defn top-maps [mapper & [n]]
  "find top keys in a mapper, those with the most counts"
  (let [tops (->> ((mapper :get-map)) 
          (map (fn [[k v]] [k ((comp count second) v)])) (sort-by second >))]
        (if n (take n tops) tops)))

(defn all-tags [dv]
  "get all tags from all data points into a vector"
  (apply concat (map #(keys (:tags %)) dv)))
  
(defn tag-simple-set [dv]
  "make a set of unique tags"
  ;; same as: (reduce conj #{} (apply concat (map #(keys (:tags %)) us)))
  (set (all-tags dv)))

(defn tag-multi-set [dv]
  "create a multiset out of the tag list as a map, 
  where each tag will be a unique key
  and the number of occurrences will the be value"
  (reduce (fn [res e] (update-in res [e] #(inc (or % 0)))) 
    {} (all-tags dv)))
    
(defn subtract-maps [m1 m2]
  "leave only those keys of m1 not present in m2"
  (apply dissoc m1 (keys m2)))
  
(defn uniq-tags [dv f-test]
  "using a boolean function f-test, separate the data into two halves,
  then find the unique tags in the first half not encountered in the second"
  (let [[us them] (separate f-test dv) 
    ours   (tag-simple-set us)
    theirs (tag-simple-set them)]
    (difference ours theirs)))
            
(defn uniq-tags-counted [dv f-test]
  "same as uniq-tags, but works on multisets of tags,
  thus preserving the number of users who applied each tag,
  allowing for subsequent sorting by that number"
  (let [[us them] (separate f-test dv) 
    ours   (tag-multi-set us)
    theirs (tag-multi-set them)]
  (subtract-maps ours theirs)))
        
(defn load-data []
  "load data into globally-defined vars"
  ;; dm is the data matrix, where each site has its own list and the data is a list of lists
  (def dm (->> (map vector ["free411.com" "gigaom.com" "hubspot.com" "leadertoleader.org" "simplyexplained.com"] 
              (iterate inc 0))
    (map (fn [[site site-id]] [(str "data/" site ".csv") site-id])) 
    (map (fn [[filename site-id]] 
      (vec (map parse-line (read-lines filename) (repeat site-id) (iterate inc 0))))) vec))

  ;; in dv, the matrix is unrolled into a vector; since we preserve site id in each row, no data is lost
  (def dv (vec (apply concat dm)))

  ;; tags are just the tag parts of each line, for kmeans clustering
  (def tags (vec (map :tags dv)))
)
