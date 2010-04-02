This project performs some exploratory data analysis on the sample StumbleUpon data.

1.  Setup

We use leiningen for dependency management:

http://github.com/technomancy/leiningen

Once you have leiningen installed, in the top directory, say

lein deps

-- this will get all the encessary jars and place them in the lib/ subdirectory.

Create a toplevel subdirectory data/ and from there, tar zxf urls.tgz.

Now you have the following diretcory structure:

supon/
  lib/
    clojure-1.2.0-master-SNAPSHOT.jar
    ...
  data/
    gigaohm.com.csv
    ...
  src/
    clojure/
      sup.clj
      ...

The project.clj file contains a line which specifies which version of Clojure, and clojure-contrib, should be used.
We use the development version of Clojure, which is the head of the git, or 1.2.0-master-SNAPSHOT in Maven.  It has some changes incompatible with the current 1.1.x version, notably:

-- duck-streams is now io
-- str-utils2 is now string
-- seq-utils is now seq

In order to use the 1.2 version correctly, you have to either launch lein-swank and connect from Emacs's Slime, as discussed, e.g., in

http://data-sorcery.org/2009/12/20/getting-started/

or use the supplied ./clojure driver, which requires zsh to run, and launch the REPL with just ./clojure.  We currently run on a system with 8 cores and 64 GB of RAM.  We specify, in the shell,

export JAVA_OPTS=-Xmx30g

prior to launching ./clojure or lein repl.

You can run the REPL under Clojure 1.1.0 simply with

lein repl

2.  Experiments

Once in REPL, follow the session in src/clojure/vars.clj.  Sample output is provided after each command yielding interesting results.

3.  Notes on Algorithms

We implemented a version of KMeans clustering for tags.  The tag space is quite large, about 21,000 different tags, which makes straightforward numeric representation wasteful.  We represent tags as sparse vectors, stored as Clojure maps, and implement distance as a negation of the cosine distance.  The subsequent averaging of the vectors in a cluster is done with the merge-with function, which takes a parameter function to merge any two values under the same key -- it happens in our function average.  The standard approach would be to join all the values under the same key into a vector and then take its mean.  However, it's a very expensive operation while growing the joints in memory, and we replaced it with either mean2, which takes a pairwise mean of a previous and current value, or max, simply preserving the maximum one.  For the cosine similarity, individual counts on tags are not as important as the actual presence or absence of some tags -- which is fairly representative of the problem at hand.

We achieved a dramatic speedup of our kmeans by employing Clojure concurrency.  It's barely noticeable, consisting of just two pmap functions replacing a map in reassign-points and readjust-clusters.  However, they load our box to 800% and achieve almost linear speedup.