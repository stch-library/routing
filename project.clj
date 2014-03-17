(defproject stch-library/routing "0.1.0"
  :description "Ring-compatible HTTP routing library."
  :url "https://github.com/stch-library/routing"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [stch-library/schema "0.3.2"]
                 [ring/ring-core "1.2.2"]
                 [cheshire "5.3.1"]]
  :profiles {:dev {:dependencies [[speclj "3.0.2"]]}}
  :plugins [[speclj "3.0.2"]
            [codox "0.6.7"]]
  :codox {:src-dir-uri "https://github.com/stch-library/routing/blob/master/"
          :src-linenum-anchor-prefix "L"}
  :test-paths ["spec"])
