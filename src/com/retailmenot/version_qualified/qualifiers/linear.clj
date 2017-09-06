(ns com.retailmenot.version-qualified.qualifiers.linear
  (:require [com.retailmenot.version-qualified.core :as v]))


;; This var must be bound when version-qualified is called in order for these qualifiers to work
(declare ^:dynamic *known-versions*)

;; Version Qualifier - "Was added in version X"
;; Takes a single version (in *known-versions*) and an arbitrary code form. The form
;; will only be included if v/*version* is bound to the version supplied, or any
;; version that comes after it
(defmethod v/eval-qualifier 'added
  [_ added-version & forms]
  {:pre [(not (neg? (.indexOf *known-versions* added-version)))]}
  (if (>= (.indexOf *known-versions* v/*version*)
          (.indexOf *known-versions* added-version))
    forms
    (list ::v/delete)))

;; Version Qualifier - "Was removed in version X"
;; Takes a single version (in *known-versions*) and an arbitrary code form. The form
;; will only be included if v/*version* is bound to any version that comes before
;; the version supplied
(defmethod v/eval-qualifier 'removed
  [_ removed-version & forms]
  {:pre [(not (neg? (.indexOf *known-versions* removed-version)))]}
  (if (< (.indexOf *known-versions* v/*version*)
         (.indexOf *known-versions* removed-version))
    forms
    (list ::v/delete)))

;; Version Qualifier - "Changed to this in this version onwards"
;; Takes a starting form, and then a variable list of version and new-form
;; pairs. Think of this like a change-log:
;; (changed (+ 1 2)
;;          :V2 (- 53 10)
;;          :V6 (something else))
;; From version 0 to 1 it is (+ 1 2), from 2 to 5 it is (- 53 10),
;; and from 6 on it is (something else). The versions must be listed in order
(defmethod v/eval-qualifier 'changed
  [_ first-form & version-form-pairs]
  {:pre [(even? (count version-form-pairs)),
         (every? #(contains? (set *known-versions*) %) (take-nth 2 version-form-pairs))]}
  (let [change-log (->> version-form-pairs
                        (partition 2)
                        (into {} (map vec)))]
    (-> (reduce (fn [last-form version]
                  (let [next-form (get change-log version last-form)]
                    (if (= version v/*version*)
                      (reduced next-form)
                      next-form)))
                first-form
                *known-versions*)
        (list))))

;; Version Qualifier - "Only valid for these versions"
;; Takes a set of versions (in *known-versions*) and an arbitrary code form. The form
;; will only be included if v/*version* is bound to one of the versions in
;; version-set
(defmethod v/eval-qualifier 'only
  [_ version-set & forms]
  (if (contains? version-set v/*version*)
    forms
    (list ::v/delete)))

;; Version Qualifier - "Do these things for these specific versions"
;; Takes a map of versions (in *known-versions*) to arbitrary code forms. Allows the
;; user to customize functionality on a version-to-version basis
(defmethod v/eval-qualifier 'switch
  [_ version-form-map]
  {:pre [(= (set (keys version-form-map)) (set *known-versions*))]}
  (-> (get version-form-map v/*version*)
      (list)))
