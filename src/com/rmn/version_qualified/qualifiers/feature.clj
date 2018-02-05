(ns com.rmn.version-qualified.qualifiers.feature
  (:require [com.rmn.version-qualified.core :as v]))



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

(defn eval-feature-expr
  [feature-expr]
  (when (list? feature-expr)
    (assert (not (known-feature? (first feature-expr)))
            (format (str "feature expression '%s' is likely invalid: The first"
                         " form in the list is a feature - did you want a"
                         " logical operator instead?")
                    feature-expr)))
  (let [reified-expr
        (clojure.walk/postwalk
          (fn [form]
            (if (known-feature? form)
              (feature-active? form)
              form))
          feature-expr)
        evaled-expr
        (try (eval reified-expr)
             (catch Exception e
               (throw (ex-info
                       "feature expression threw an error while being evaluated"
                       {:expr feature-expr
                        :reified-expr reified-expr
                        :exception e}))))]
    evaled-expr))


;; Feature Qualifier - "Part of feature X"
;; Takes a single feature and an arbitrary code form. The form
;; will only be included if the value behind *version* in *version-feature-manifest*
;; contains feature.
(defmethod v/eval-qualifier 'feature
  [_ feature-expr & forms]
  (if (eval-feature-expr feature-expr)
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
        expr-form-pairs (partition 2 (if default-form-supplied?
                                       (drop-last flat-feature-form-pairs)
                                       flat-feature-form-pairs))
        matching-forms
        (->> expr-form-pairs
             (filter
              (fn [[case-expr form]]
                (eval-feature-expr
                 (if (and (list? case-expr)
                          (every? known-feature? case-expr))
                  (let [new-case-expr (conj case-expr 'or)]
                    (println
                     (format "Form '%s' is deprecated - please change to '%s'"
                             case-expr
                             new-case-expr))
                    new-case-expr)
                  case-expr))))
             (map (fn [[feature form]] form)))]
    ;; note that the first form may be false or nil, so we must check the
    ;; length of matching-forms, not simply the result of (first matching-forms)
    (if (seq matching-forms)
      (list (first matching-forms))
      (list default-form))))
