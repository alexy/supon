;; (ns 'com.suffix)

(defn make-mapper []
  (let [m (ref {})]
    {:map (fn [s] (dosync (let [to (@m s)] 
      (if to 
        to
        (let [to (count @m)] 
          (alter m assoc s to)
            to)))))
     :get-map (fn [] @m)
     :count (fn [] (count @m))  
    }))
    
    
(defn make-id-mapper [ & [keep-key]]
  (let [m (ref {})
        keep-key? keep-key]
    {:map (fn [s id] (dosync (let [[to ids] (@m s)] 
      (if to 
        ;; replace conj by set
        (do (alter m assoc s [to (conj ids id)]) to)
        (let [to (if keep-key? s (count @m))] 
          (alter m assoc s [to #{id}])
            to)))))
     :get-map (fn [] @m)
     :count (fn [] (count @m))  
    }))