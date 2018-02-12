(ns com.rmn.version-qualified.core-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [com.rmn.version-qualified.core :as v]))

(defmethod v/eval-qualifier 'test-elide
  [& _]
  '(::v/delete))

(defmethod v/eval-qualifier 'test-only
  [_ version & body]
  (if (= version v/*version*)
    body
    '(::v/delete)))

(defmethod v/eval-qualifier 'test-after
  [_ version & body]
  (if (> v/*version* version)
    body
    '(::v/delete)))


(deftest code-compaction
  ;; When the body of code is large, having a large number of versions may
  ;; produce method code too large exceptions. But versions which resolve to the
  ;; same code should not need to duplicate the code, and thus shouldn't trigger
  ;; the exception
  (testing "Method code too large exceptions"
    (let [versions (map (comp keyword str) (range 100))
          body (->> (concat `[+] (repeat 500 1) ['(test-only :0 1)])
                    (apply list))
          generated (v/version-qualified 'version versions body)]

      ;; 100 versions of a 500 argument function call should trigger a method
      ;; code too large exception if the code is duplicated for each version.
      ;; Assert that this doesn't happen
      (is (= 501 (eval `(let [~'version :0] ~generated))))
      (is (= 500 (eval `(let [~'version :1] ~generated))))))

  (testing "Doesn't generate redundant code"
    (let [num-occurrences (fn [form x]
                            (let [occurrences (atom 0)]
                              (clojure.walk/postwalk
                                (fn [sub-form]
                                  (when (= sub-form x)
                                    (swap! occurrences inc))
                                  sub-form)
                                form)
                              @occurrences))
          versions (range 200)
          body '[::before (test-after 100 ::after)]
          generated (clojure.walk/macroexpand-all
                     (v/version-qualified 'version versions body))]
      (is (= 2 (num-occurrences generated ::before)))
      (is (= 1 (num-occurrences generated ::after))))))

(deftest immediately-nested-qualifiers
  (testing "deeply nested qualifiers"
    (let [versions [1 2 3]
          generated (v/version-qualified
                     'version versions
                     '[:0 (test-after 1
                                      :1 (test-after 2
                                                     :2 (test-elide
                                                         (test-after -10000
                                                                     :INVALID))))])]
      (is (= [:0]       (eval `(let [~'version 1] ~generated))))
      (is (= [:0 :1]    (eval `(let [~'version 2] ~generated))))
      (is (= [:0 :1 :2] (eval `(let [~'version 3] ~generated))))))

  (testing "looping limits"
    (let [code '[(test-after 1 (test-after 1 (test-after 1 :resolved!)))]]  ;; takes 3 passes to resolve
      (is (= []
             (binding [v/*max-qualifier-eval-passes* 1]
               (v/version-qualified 'version [0 1 #_2] code)))
          "One pass should be enough since first qualifier removes everything (there are no versions after 1)")
      (is (thrown? Exception
                   (binding [v/*max-qualifier-eval-passes* 2]
                     (v/version-qualified 'version [0 1 2] code)))
          "Not enough allowed passes should throw an error")
      (is (= [:resolved!]
             (eval `(let [~'version 2]
                     ~(binding [v/*max-qualifier-eval-passes* 3]
                       (v/version-qualified 'version [0 1 2] code)))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Generative Tests ;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;


(def small (partial gen/scale #(int (Math/sqrt %))))

(def gen-any
  (gen/recursive-gen
    gen/container-type
    (gen/one-of
      [gen/int gen/large-integer (gen/double* {:NaN? false})
       gen/char-ascii gen/string-ascii gen/ratio gen/boolean
       gen/keyword gen/keyword-ns (small gen/symbol)
       gen/symbol-ns gen/uuid])))

(def gen-symbol
  (gen/one-of [gen/symbol (gen/elements (keys (ns-publics 'clojure.core)))]))

(defn gen-call
  [symbol-gen]
  (gen/let [symb symbol-gen
            the-rest (gen/list gen-any)]
    (apply list symb the-rest)))

(defn gen-expr
  [call-gen]
  (gen/one-of [call-gen
               (gen/vector gen-any)
               (gen/map gen-any gen-any)]))

;; A code approximate: we'll probably end up with things like empty lists or
;; numbers as the first element of the list, but that should be OK
(defn gen-code
  [expr-gen]
  (gen/recursive-gen
   (fn [inner]
     (gen/one-of [(gen/vector inner)
                  (gen/list inner)
                  (gen/map inner inner)
                  (gen/set inner)]))
   expr-gen))

(def gen-unqualified-code
  (gen-code (gen-expr (gen-call gen-symbol))))

(defn gen-qualified-code
  [& qualifier-generators]
  (gen-code (gen-expr (gen/one-of (apply list
                                         (gen-call gen-symbol)
                                         qualifier-generators)))))

(def gen-elide-qualified
  (gen/let [the-rest (gen/list gen-unqualified-code)]
    (apply list 'test-elide the-rest)))

(defn gen-only-qualified
  [version-gen]
  (gen/let [the-rest (gen/list gen-unqualified-code)
            version version-gen]
    (apply list 'test-only version the-rest)))

(def gen-versions
  (gen/such-that #(pos? (count %)) (gen/list gen/keyword)))


(deftest version-qualified-optimizations
  (checking "non-qualified expressions compile to themselves" 101
    [version-symbol gen-symbol
     versions-literal gen-versions
     body (small gen-unqualified-code)]
    (is (= (v/version-qualified version-symbol versions-literal body)
           body)))
  (checking "qualified expressions with only one version compile to that version" 100
    [version-symbol gen-symbol
     versions-literal gen-versions
     version (gen/elements versions-literal)
     body (small (gen-qualified-code
                   ;; 'test-elide produces the same code for every version
                   gen-elide-qualified))]
    (let [processed (v/version-qualified version-symbol versions-literal body)]
      (is (= processed
             (v/process-qualifiers body version)))
      (is (not= (first processed)
                `case)))))
