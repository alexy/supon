;; this is the only part reading from the data files
;; data structs are defined for passing around
;; function load-data reads all the files for URLs
;; and defines globals dm, dv, tags

;; since we had recently switched to Clojure 1.2.x,
;; while the majority supports 1.1.x, we enable both
;; with version-specific files doing importing
(def *clojure-minor* (:minor *clojure-version*))
(def *clojure-new* (= *clojure-minor* 2))
(load-file (str "meat/clojure_1_" *clojure-minor* ".clj"))
   
;; common formats to keep the data in
(defstruct id-user :sid :id :age :gender :city :state :country)
(defstruct user :gender :age :city :state :country)
(def *user-keys* (keys (struct user)))
(defstruct sup  :sid :id :rate :time :user :tags)

(defn parse-line [line site-id line-id] 
  "parse a single SU data line and return struct sup, containing id-user, tags, etc., and site-line id"
  (let [
    fields (s2/split #"," line)
    prefix-length 7 
    prefix (take prefix-length fields) 
    [rate time age gender city state country] prefix 
    [rate time age gender] (map #(Integer/parseInt %) [rate time age gender]) 
    time (DateTime. (* (long time) 1000)) 
    user (struct id-user site-id line-id age gender city state country)
    tags (->> (drop prefix-length fields) 
          (map #(let [[tag n] (split #":" %)]
            [(keyword tag) (Integer/parseInt n)])) (into {}))
          ] 
    (struct sup site-id line-id rate time user tags)))
    
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
  (def tags (vec (map :tags dv))))
