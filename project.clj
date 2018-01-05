(defproject com.rmn/version-qualified "1.0.2-SNAPSHOT"
  :description "A library for generating version-specific code"
  :license {:name "MIT License"
            :url "http://www.opensource.org/licenses/mit-license.php"}
  :release-tasks [["vcs" "assert-committed"]
                  ["test"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :dependencies [[org.clojure/clojure "1.7.0"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]
                                  [com.gfredericks/test.chuck "0.2.8"]]}})
