(ns com.retailmenot.version-qualified.qualifiers.feature-test
  (:require [com.retailmenot.version-qualified.core :as v]
            [com.retailmenot.version-qualified.qualifiers.feature :as feature])
  (:use clojure.test))



(declare ^:dynamic *user-version*)

(def version->features
  {:V0 #{:A}
   :V1 #{:A :B :C}
   :V2 #{   :B :C :D}})

(def known-versions (keys version->features))


(defmacro eval-qualified
  "Generates a map of versions (in version-to-eval) to the value of executing
   the body for that version, where body is a version-qualified expression"
  [versions-to-eval body]
  (into {}
    (for [v versions-to-eval]
      [v `(binding [*user-version* ~v]
            ~body)])))

(defmacro versioned
  [body]
  (binding [feature/*version->features* version->features]
    (v/version-qualified `*user-version* known-versions body)))

;;;;

(deftest feature-case
  (testing "no default, all cases covered, and order matters"
    (is (= {:V0 "A"
            :V1 "A"
            :V2 "B"}
           (eval-qualified [:V0 :V1 :V2]
             (versioned
               (feature-case
                 :A "A"
                 :B "B"
                 :C "C"
                 :D "D")))))
    (is (= {:V0 "A"
            :V1 "C"
            :V2 "D"}
           (eval-qualified [:V0 :V1 :V2]
             (versioned
               (feature-case
                 :D "D"
                 :C "C"
                 :B "B"
                 :A "A"))))))
  (testing "expression is removed by default"
    (is (= {:V0 []     ;; wrapping in a vector is necessary because the form is deleted (feature-case can't be directly beneath versioned)
            :V1 ["B"]
            :V2 ["B"]}
           (eval-qualified [:V0 :V1 :V2]
             (versioned
               [(feature-case
                  #_:A #_"A"
                  :B "B"
                  :C "C"
                  :D "D")])))))
  (testing "multiple feature clauses in a single case"
    (is (= {:V0 "in :V0"
            :V1 "after :V0"
            :V2 "after :V0"}
           (eval-qualified [:V0 :V1 :V2]
             (versioned
               (feature-case
                 (:B :C :D) "after :V0"
                 :A         "in :V0"))))))
  (testing "the default case supplied"
    (is (= {:V0 "in :V0"
            :V1 "after :V0"
            :V2 "after :V0"}
           (eval-qualified [:V0 :V1 :V2]
             (versioned
               (feature-case
                 (:B :C :D) "after :V0"
                 #_DEFAULT "in :V0")))))))

(deftest feature-oriented-versioned-macro
  (testing "maps"
    (is (= {:V0 {:a "A"}
            :V1 {:a "A", :b "B", :c "C"}
            :V2 {:b "B", :c "C", :d "D"}}
           (eval-qualified [:V0 :V1 :V2]
             (versioned
               {(feature :A :a) "A"
                (feature :B :b) "B"
                (feature :C :c) "C"
                (feature :D :d) "D"})))))
  (testing "vectors"
    (is (= {:V0 [\a]
            :V1 [\a \b \c]
            :V2 [\b \c \d]}
           (eval-qualified [:V0 :V1 :V2]
             (versioned
               [(feature :A \a)
                (feature :B \b)
                (feature :C \c)
                (feature :D \d)]))))
    (is (= {:V0 [\a]
            :V1 [\a, \b \b, \c \c \c]
            :V2 [    \b \b, \c \c \c, \d \d \d \d]}
           (eval-qualified [:V0 :V1 :V2]
             (versioned
               [(feature :A \a)
                (feature :B \b \b)
                (feature :C \c \c \c)
                (feature :D \d \d \d \d)])))))
  (testing "list"
    (is (= {:V0 '(\a)
            :V1 '(\a \b \c)
            :V2 '(\b \c \d)}
           (eval-qualified [:V0 :V1 :V2]
             (versioned
               (list (feature :A \a)
                     (feature :B \b)
                     (feature :C \c)
                     (feature :D \d))))))
    (is (= {:V0 '(\a)
            :V1 '(\a, \b \b, \c \c \c)
            :V2 '(    \b \b, \c \c \c, \d \d \d \d)}
           (eval-qualified [:V0 :V1 :V2]
             (versioned
               (list (feature :A \a)
                     (feature :B \b \b)
                     (feature :C \c \c \c)
                     (feature :D \d \d \d \d))))))))
