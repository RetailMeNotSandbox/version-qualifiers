(ns com.rmn.version-qualified.core-test
  (:require [clojure.test :refer :all]
            [clojure.test.check.generators :as gen]
            [com.gfredericks.test.chuck.clojure-test :refer [checking]]
            [com.rmn.version-qualified.core :as v]))

(defmethod v/eval-qualifier 'test-elide
  [& _]
  '(::v/delete))

(defmethod v/eval-qualifier 'test-include
  [_ & body]
  body)

(defmethod v/eval-qualifier 'test-only
  [_ version & body]
  (if (= version v/*version*)
    body
    '(::v/delete)))


(deftest version-qualified-examples
  (is (= `(case ~'*the-version*
            (:V0) ~'(+ 0)
            (:V1) ~'(+ 0 1)
            (:V2) ~'(+ 0 2)
            (v/version-qualified-error "*the-version*" ~'*the-version*
                                       [:V0 :V1 :V2]))
         (v/version-qualified
           '*the-version* '(:V0 :V1 :V2)
           '(+ (test-include 0)
               (test-only :V1 1)
               (test-only :V2 2)
               (test-elide 3)))))
  (is (= `(case ~'*the-version*
            (:V0 :V3) ~'(+)
            (:V1) ~'(+ 1 1)
            (:V2) ~'(+ 2 2)
            (v/version-qualified-error "*the-version*" ~'*the-version*
                                       [:V0 :V1 :V2 :V3]))
         (v/version-qualified
           '*the-version* '(:V0 :V1 :V2 :V3)
           '(+ (test-only :V1 1 1)
               (test-only :V2 2 2)))))
  (is (= `(case ~'*the-version*
            (:V0) ~'{:key0 "value0"}
            (:V1) ~'{:key1 "value1"}
            (v/version-qualified-error "*the-version*" ~'*the-version*
                                       [:V0 :V1]))
         (v/version-qualified
           '*the-version* '(:V0 :V1)
           '{(test-only :V0 :key0) "value0"
             :key1 (test-only :V1 "value1")}))))



;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;; Generative Tests ;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;


(def small (partial gen/scale #(Math/sqrt %)))

(def gen-any
  (gen/recursive-gen
    gen/container-type
    (gen/one-of
      [gen/int gen/large-integer (gen/double* {:NaN? false})
       (small gen/char-ascii) (small gen/string-ascii) gen/ratio gen/boolean
       (small gen/keyword) (small gen/keyword-ns) (small gen/symbol)
       (small gen/symbol-ns) (small gen/uuid)])))

(def gen-symbol
  (gen/one-of [(small gen/symbol) (gen/elements (keys (ns-publics 'clojure.core)))]))

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
  (gen/one-of [(gen/recursive-gen gen/list expr-gen)
               (gen/recursive-gen gen/vector expr-gen)
               #_(gen/recursive-gen gen/map expr-gen expr-gen)]))

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
  (checking "non-qualified expressions compile to themselves" 25
    [version-symbol gen-symbol
     versions-literal gen-versions
     body gen-unqualified-code]
    (is (= (v/version-qualified version-symbol versions-literal body)
           body)))
  (checking "qualified expressions with only one version compile to that version" 25
    [version-symbol gen-symbol
     versions-literal gen-versions
     version (gen/elements versions-literal)
     body (gen-qualified-code
            ;; 'test-elide produces the same code for every version
            gen-elide-qualified)]
    (let [processed (v/version-qualified version-symbol versions-literal body)]
      (is (= processed
             (v/process-qualifiers body version)))
      (is (not= (first processed)
                `case)))))
