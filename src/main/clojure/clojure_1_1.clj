(use 'clojure.contrib.duck-streams)
(use 'clojure.contrib.seq-utils)
(require '[clojure.contrib.str-utils2 :as s2])
(defn split [re st] (s2/split st re))
