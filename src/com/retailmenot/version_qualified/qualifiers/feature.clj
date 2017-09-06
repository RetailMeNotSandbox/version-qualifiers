(ns com.retailmenot.version-qualified.qualifiers.feature
  (:require [com.retailmenot.version-qualified.core :as v]))


;; Bound by the user. This is a map from a version (which v/*version*) may be
;; set to, to a SET of feature identifiers (probably keywords)
(declare ^:dynamic *version->features*)


(defn active-features
  "Returns a set of feature identifiers specified by the current v/*version*"
  []
  (set (get *version->features* v/*version*)))

(defn feature-active?
  "Predicate which returns true if the supplied feature is included in the set
   of active-features"
  [feature]
  (contains? (active-features)
             feature))

(defn known-feature?
  "Predicate which returns true if the supplied feature identifier exists
   anywhere in the *version->features* map"
  [feature]
  (contains? (set (apply concat (vals *version->features*)))
             feature))


;; Feature Qualifier - "Part of feature X"
;; Takes a single feature and an arbitrary code form. The form
;; will only be included if the value behind *version* in *version-feature-manifest*
;; contains feature.
(defmethod v/eval-qualifier 'feature
  [_ feature & forms]
  {:pre [(known-feature? feature)]}
  (if (feature-active? feature)
    forms
    (list ::v/delete)))


;; Feature Case - "If this feature is active, do this (otherwise do this)"
;; Modeled after clojure.core/case. Takes any number of clauses followed by an
;; optional default expr, where a clause is of the form:
;; features expr
;; where features may be a single feature identifier or a list of multiple
;; feature identifiers.
;; features which come before others will take precedence over the others.
;; Accepts an optional default expr after all the clauses which defines whats
;; this qualifer should return in the case that none of the listed features are
;; active. If not specified, the expression will be removed by
;; version-qualified
(defmethod v/eval-qualifier 'feature-case
  [_ & flat-feature-form-pairs]
  (let [default-form-supplied? (odd? (count flat-feature-form-pairs))
        default-form (if default-form-supplied?
                       (last flat-feature-form-pairs)
                       ::v/delete)
        feature-form-pairs (partition 2 (if default-form-supplied?
                                          (drop-last flat-feature-form-pairs)
                                          flat-feature-form-pairs))
        features (flatten (map first feature-form-pairs))
        matching-forms (->> feature-form-pairs
                            (filter (fn [[feature form]]
                                      ;; Clojure's case expression handles a list of conditions
                                      (if (list? feature)
                                        (some feature-active? feature) ;; Clojure's case it behaves as an OR expression (some) not an AND expresssion (every?)
                                        (feature-active? feature))))
                            (map (fn [[feature form]] form)))]
    (assert (every? known-feature? features)
            (str "Unknown features provided to feature-case expression: "
                 (clojure.string/join "; " (filter (complement known-feature?) features))))
    ;; note that the first form may be false or nil, so we must check the
    ;; length of matching-forms, not simply the result of (first matching-forms)
    (if (seq matching-forms)
      (list (first matching-forms))
      (list default-form))))
