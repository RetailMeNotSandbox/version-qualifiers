(ns com.rmn.version-qualified.qualifiers.qualifier-test-utils)


(declare ^:dynamic *user-version*)

(defmacro eval-qualified
  "Generates a map of versions (in version-to-eval) to the value of executing
   the body for that version, where body is a version-qualified expression"
  [versions-to-eval body]
  (into {}
    (for [v versions-to-eval]
      [v `(binding [*user-version* ~v]
            ~body)])))
