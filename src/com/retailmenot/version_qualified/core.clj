(ns com.retailmenot.version-qualified.core
  "Defines functionality for annotating data structures with version qualifiers

   The way this is accomplished is with the version-qualified macro. It
   inspects a code body for version qualifiers (added, only, removed, switch,
   etc..) and then reformulates the code such that it is specific for each
   version listed. Then, at runtime, the desired version of the code will be
   invoked depending on the binding of a dynamic variable that represents the
   \"current\" version, which the user supplies.

   All clojure data structures (maps, lists, vectors) can have
   version-qualified members.

   It is possible to exclude an entire expression for a version using the
   namespace keyword ::delete. Many of the version-qualifiers, such as added
   and removed do this behind the scenes. Users can specify deletion in their
   version-qualifier by using the ::delete keyword _directly_ -- Note: This
   WILL NOT WORK if the user attempts to refer to ::delete through a reference
   or function call"
  (:require [clojure.walk :refer [prewalk]]))



(def ^:dynamic *versions*
  "Specifies the order and values of known versions. Bound during compile-time
   only")

(def ^:dynamic *version*
  "Specifies the \"current\" version. Bound during compile-time only")


;;;; Version Qualifiers ;;;;
;;
;; Version qualifiers are functions, but they behave like macros in the sense
;; that their inputs and outputs are unevaluated code. They are used by the
;; version-qualified macro to transform user code.
;;
;; Version qualifiers take version-qualification information and user-code as
;; input, and produce user-code as output depending on what *version* is bound
;; to
;;
;; for example, given:
;;
;; (defn version-dependent-function
;;   [a b]
;;   (v/version-qualified *version-var* [:V0, :V1, :V2]
;;     {:value (v/switch {:V0 (+ a b)
;;                        :V1 (* a b)
;;                        :V2 ::v/delete})}))
;;
;; version-qualified will generate 3 separate pieces of code:
;; If *version* is bound to :V0, it will generate {:value (+ a b)}
;; :V1 will be {:value (* a b)}
;; :V2 will be {}
;;
;; When the user then binds *version-var* at run-time, and invokes this
;; function, only the specified piece of code will be executed.

(defn switch
  "Version Qualifier - \"Do these things for these specific versions\"
   Takes a map of versions (in *versions*) to arbitrary code forms. Allows the
   user to customize functionality on a version-to-version basis"
  [version-form-map]
  {:pre [(= (set (keys version-form-map)) (set *versions*))]}
  (-> (get version-form-map *version*)
      (list)))

(defn changed
  "Version Qualifier - \"Changed to this in this version onwards\"
   Takes a starting form, and then a variable list of version and new-form
   pairs. Think of this like a change-log:
   (changed (+ 1 2)
            :V2 (- 53 10)
            :V6 (something else))
   From version 0 to 1 it is (+ 1 2), from 2 to 5 it is (- 53 10),
   and from 6 on it is (something else). The versions must be listed in order"
  [first-form & version-form-pairs]
  {:pre [(even? (count version-form-pairs)),
         (every? #(contains? (set *versions*) %) (take-nth 2 version-form-pairs))]}
  (let [change-log (->> version-form-pairs
                        (partition 2)
                        (into {} (map vec)))]
    (-> (reduce (fn [last-form version]
                  (let [next-form (get change-log version last-form)]
                    (if (= version *version*)
                      (reduced next-form)
                      next-form)))
                first-form
                *versions*)
        (list))))

(defn only
  "Version Qualifier - \"Only valid for these versions\"
   Takes a set of versions (in *versions*) and an arbitrary code form. The form
   will only be included if *version* is bound to one of the versions in
   version-set"
  [version-set & forms]
  (if (contains? version-set *version*)
    forms
    (list ::delete)))

(defn added
  "Version Qualifier - \"Was added in version X\"
   Takes a single version (in *versions*) and an arbitrary code form. The form
   will only be included if *version* is bound to the version supplied, or any
   version that comes after it"
  [version & forms]
  {:pre [(not (neg? (.indexOf *versions* version)))]}
  (if (>= (.indexOf *versions* *version*)
          (.indexOf *versions* version))
    forms
    (list ::delete)))

(defn removed
  "Version Qualifier - \"Was removed in version X\"
   Takes a single version (in *versions*) and an arbitrary code form. The form
   will only be included if *version* is bound to any version that comes before
   the version supplied"
  [version & forms]
  {:pre [(not (neg? (.indexOf *versions* version)))]}
  (if (< (.indexOf *versions* *version*)
         (.indexOf *versions* version))
    forms
    (list ::delete)))


;;;; version-qualified ;;;;

(defn apply-version
  "Walks an arbitrary data-structure (such as code) and transforms it using the
   process-form visitor function. The visitor must return a list of forms to
   replace the input form with. The visitor may return ::delete, in which case
   this function will entirely remove that expression from a map, a vector, or
   a list (or collection). Returns the modified data-structure"
  [process-form data]
  (let [delete? (partial = ::delete)
        process-kv-form
         (fn [key-or-val]
           (let [new-key-or-val (process-form key-or-val)]
             ;; map key/value qualifiers can't return a list of forms
             (when (> (count new-key-or-val) 1)
               (throw
                 (ex-info
                   (format (str "Version-qualifiers used on map keys or values"
                                " cannot return multiple forms")
                           key-or-val new-key-or-val)
                   {:form key-or-val, :produces new-key-or-val})))
             (first new-key-or-val)))]
    (prewalk
      (fn [form]
        (cond
          (map? form)
            (->> (map (partial mapv process-kv-form) form)
                 (remove (partial some delete?))
                 (into {}))
          (vector? form)
            (->> (mapcat process-form form)
                 (remove delete?)
                 (into []))
          (coll? form)
            (->> (mapcat process-form form)
                 (remove delete?))
          :else form))
      data)))

(defn qualifier?
  "Returns true if the given form is a version-qualifier"
  [form]
  (and (list? form)
       (> (count form) 0)
       (symbol? (first form))
       (= (find-ns 'com.retailmenot.version-qualifiers.core)
          (-> (first form) resolve meta :ns))))

(defn process-form-for-version
  "Takes a version and (maybe) a version-qualified expression and returns the
   user-code specific for that version"
  [version form]
  (if (qualifier? form)
    (binding [*version* version]
      (apply (resolve (first form)) (rest form)))
    (list form)))


(defn version-qualified-error
  [version-symbol version-value versions]
  (throw
    ;; Note to Bug Hunters:
    ;;  The user must bind their version dynamic var when calling code defined
    ;;  inside of the version-qualified macro
    ;;  This is accomplished like so:
    ;;
    ;;  (binding [*app-version* ...]
    ;;    (call-to-version-qualified-code))
    ;;
    ;; or, more obviously
    ;;  (binding [*app-version* :V1]
    ;;    (version-qualified *app-version* [... :V1 ...]
    ;;      ...))
    ;;
    ;; If you're seeing this while testing, you're probably trying to call some
    ;; version-sensitive code without having done this, or you are calling it
    ;; with a version that wasn't specified as an argument to the macro
    (ex-info
      (if version-value
        (format
          (str "'%s' (bound to '%s) was not specified when the"
               " version-qualified code was compiled."
               " Known versions were %s")
          version-value version-symbol versions)
        (format
          (str "attempted to call version-qualified code"
               " but '%s was not bound!")
          version-symbol))
      {(keyword version-symbol) version-value
       :*versions* versions})))

(defmacro version-qualified
  "Macro for easily writing version-dependent code. Takes a symbol, which must
   be bound at runtime to the \"current\" version, an ordered list of versions
   which the symbol may be bound to, and an arbitrary code block. The result is
   a case expression that will only will invoke the version of code specified
   by the binding of the version-symbol"
  [version-symbol versions-literal body]
  (binding [*versions* versions-literal]
    (let [process-qualifiers #(apply-version (partial process-form-for-version %) body)
          version-code-pairs (->> (group-by process-qualifiers versions-literal)
                                  (mapcat (fn [[processed-code versions]]
                                            [(apply list versions), processed-code])))]
      (if (= 2 (count version-code-pairs))
        (let [[vers, code] version-code-pairs]
          code)
        `(case ~version-symbol
           ~@version-code-pairs
           (version-qualified-error ~(name version-symbol) ~version-symbol ~versions-literal))))))
