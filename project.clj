(defproject com.rmn/version-qualified "1.0.0"
  :description "A library for generating version-specific code"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :repositories [["whaleshark-releases"
                  {:description "Whaleshark Release Repository"
                   :url "http://pkg.rmn.io/content/repositories/releases"
                   :snapshots false}]]
  :deploy-repositories [["releases" {:url "http://pkg.rmn.io/content/repositories/releases"
                                     :sign-releases false}]]
  :release-tasks [["vcs" "assert-committed"]
                  ["test"]
                  ["change" "version" "leiningen.release/bump-version" "release"]
                  ["vcs" "commit"]
                  ["vcs" "tag" "--no-sign"]
                  ["deploy"]
                  ["change" "version" "leiningen.release/bump-version"]
                  ["vcs" "commit"]
                  ["vcs" "push"]]
  :dependencies [[org.clojure/clojure "1.8.0"]]
)
