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



(def ^:dynamic *version*
  "Specifies the \"current\" version. Bound during compile-time only"
  nil)

(defmulti eval-qualifier (fn [first-arg & _] first-arg))

;;;; version-qualified ;;;;

(defn qualifier?
  "Returns true if the given form is a version-qualifier"
  [form]
  (and
   (list? form)
   (contains? (methods eval-qualifier) (first form))))

(defn apply-version
  "Walks an arbitrary data-structure (such as code) and transforms it using the
   process-form visitor function. The visitor must return a list of forms to
   replace the input form with. The visitor may return ::delete, in which case
   this function will entirely remove that expression from a map, a vector, or
   a list (or collection). Returns the modified data-structure"
  [process-form* data version]
  (let [delete? (partial = ::delete)
        process-form (partial process-form* version)
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
          (qualifier? form)
            (let [nforms (process-form form)]
              ;; All qualifiers *should* be processed by the time we walk them,
              ;; except for if there is a qualifier wrapping the entire body --
              ;; i.e. the (= form data)
              (assert (= form data), "all qualifiers should've been processed by the time we walk them")
              (assert (= 1 (count nforms))
                      (format
                        (str "Version qualified expression '%s returns multiple"
                             " forms: '%s, for version '%s, but is also the"
                             " outermost expression")
                        form nforms version))
              (assert (not (delete? (first nforms)))
                      (format
                        (str "Version qualified expression '%s is removed in"
                             " version '%s, but it is also the outermost"
                             " expression")
                        form, version))
              (first nforms))
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
                 (remove delete?)
                 (apply list))
          :else form))
      data)))

(defn process-form-for-version
  "Takes a version and (maybe) a version-qualified expression and returns the
   user-code specific for that version"
  [version form]
  (if (qualifier? form)
    (binding [*version* version]
      (apply eval-qualifier form))
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
    ;; with a version that wasn't specified as an argument to version-qualified
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
      {(keyword version-symbol) version-value})))

(defn process-qualifiers
  "Takes a list of supported versions, a body and a version. Binds the supported
  versions and returns user specific code for that version.
  WARNING: Must be called from same namespace as where body is defined
  or body must use qualifiers which are fully namespace-qualified"
  [body version]
  (apply-version process-form-for-version body version))

(defn version-qualified
  "Meants to be used inside of user-defined macros. Transforms code that
   contains version qualifiers into a case expression which switches on the
   supplied symbol. Takes a symbol, which must be bound at runtime to the
   \"current\" version, a collection of versions which that symbol may be bound
   to, and an arbitrary code block which contains version qualifiers. The
   result is a case expression that will only invoke the version of code
   specified by the binding of the version-symbol."
  [version-symbol versions-literal body]
  (let [process-qualifiers (partial process-qualifiers body)
        version-code-pairs (->> (group-by process-qualifiers versions-literal)
                                (mapcat (fn [[processed-code versions]]
                                          [(apply list versions), processed-code])))]
    (if (= 2 (count version-code-pairs))
      (let [[vers, code] version-code-pairs]
        code)
      `(case ~version-symbol
         ~@version-code-pairs
         (version-qualified-error ~(name version-symbol) ~version-symbol ~versions-literal)))))
