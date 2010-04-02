;; (ns com.suffix
  (use 'clojure.set)
  (import '[org.joda.time DateTime])
  (require 'mapper)
  (require 'cluster)

    
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
