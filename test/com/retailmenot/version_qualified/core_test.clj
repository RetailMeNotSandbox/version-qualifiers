(ns com.retailmenot.version-qualified.core-test
  (:require [clojure.test :refer :all]
            [com.retailmenot.version-qualified.core :as v]))


(def ^:dynamic *test-version* nil)
(def default-versions [:V0 :V1 :V2 :V3 :V4 :V5])

(defmacro eval-qualifiers
  "Generates a map of versions (in version-to-eval) to the value of executing
   body for that version. This modifies internal version namespace dynamic vars
   in order to evaluate version qualifiers. Use eval-qualified to test
   version-qualified code"
  [versions-to-eval body]
  (into {}
    (for [v versions-to-eval]
      [v `(binding [v/*versions* ~default-versions
                    v/*version* ~v]
            ~body)])))

(defmacro eval-qualified
  "Generates a map of versions (in version-to-eval) to the value of executing
   the body for that version, where body is a version-qualified expression"
  [versions-to-eval body]
  (into {}
    (for [v versions-to-eval]
      [v `(binding [*test-version* ~v]
            ~body)])))


(deftest version-qualifiers
  (testing "added qualifier deletes expression before version"
    (is (= {:V0 '(::v/delete)
            :V1 '(:key)
            :V2 '(:key)}
           (eval-qualifiers [:V0 :V1 :V2]
             (v/added :V1 :key))))
    (testing "handles multiple expressions"
      (is (= {:V0 '(::v/delete)
              :V1 '(:key1 :key2)
              :V2 '(:key1 :key2)}
             (eval-qualifiers [:V0 :V1 :V2]
               (v/added :V1 :key1 :key2))))))
  (testing "removed qualifier deletes expressions on and after version"
    (is (= {:V0 '(:key)
            :V1 '(::v/delete)
            :V2 '(::v/delete)}
           (eval-qualifiers [:V0 :V1 :V2]
             (v/removed :V1 :key))))
    (testing "handles multiple expressions"
      (is (= {:V0 '(:key1 :key2)
              :V1 '(::v/delete)
              :V2 '(::v/delete)}
             (eval-qualifiers [:V0 :V1 :V2]
               (v/removed :V1 :key1 :key2))))))
  (testing "only qualifier deletes expressions for unlisted versions"
    (is (= {:V0 '(:key)
            :V1 '(::v/delete)
            :V2 '(:key)}
           (eval-qualifiers [:V0 :V1 :V2]
             (v/only #{:V0 :V2} :key))))
    (testing "handles multiple expressions"
      (is (= {:V0 '(:key1 :key2)
              :V1 '(::v/delete)
              :V2 '(:key1 :key2)}
             (eval-qualifiers [:V0 :V1 :V2]
               (v/only #{:V0 :V2} :key1 :key2))))))
  (testing "switch qualifier"
    (is (= {:V0 '(:key0)
            :V1 '(:key1)
            :V2 '(:key2)
            :V3 '(:key3)
            :V4 '(:key4)
            :V5 '(:key5)}
           (eval-qualifiers [:V0 :V1 :V2 :V3 :V4 :V5]
             (v/switch {:V0 :key0
                        :V1 :key1
                        :V2 :key2
                        :V3 :key3
                        :V4 :key4
                        :V5 :key5}))))
    (testing "must specify every valid version"
      ;; Example of valid switch declaration
      (is (= '(nil)
             (binding [v/*versions* [:V0 :V1 :V2]]
               (v/switch {:V0 "data", :V1 "data", :V2 "data"}))))
      ;; Invalid switch declarations
      (is (thrown? java.lang.AssertionError
                   (binding [v/*versions* [:V0 :V1 :V2]]
                     (v/switch {:V0 "data", :V1 "data"}))))
      (is (thrown? java.lang.AssertionError
                   (binding [v/*versions* [:V0 :V1 :V2]]
                     (v/switch {:V0 "data", :V1 "data", :v2 "data"}))))
      (is (thrown? java.lang.AssertionError
                   (binding [v/*versions* [:V0 :V1 :V2]]
                     (v/switch {:V0 "data", :V1 "data", :V2 "data", :V3 "data"}))))))
  (testing "changed qualifier"
    (is (= {:V0 '("original")
            :V1 '("first change")
            :V2 '("first change")
            :V3 '("first change")
            :V4 '("second change")
            :V5 '("second change")}
           (eval-qualifiers [:V0 :V1 :V2 :V3 :V4 :V5]
             (v/changed "original"
                        :V1 "first change"
                        :V4 "second change"))))))


(deftest version-qualified-macro
  (testing "map structure"
    (is (= {:V0 {:title "title"
                 :removed-key "some other data"
                 :switch-key "original data"}
            :V1 {:title "title"
                 :added-key "some data"
                 :only-key "only data"
                 :switch-key "V1 data"}
            :V2 {:title "title"
                 :added-key "some data"
                 :switch-key "V2 data"}}
           (eval-qualified [:V0 :V1 :V2]
             (v/version-qualified *test-version* [:V0 :V1 :V2]
               {:title "title"
                (v/added :V1 :added-key) "some data"
                (v/removed :V1 :removed-key) "some other data"
                (v/only #{:V1} :only-key) "only data"
                :switch-key (v/changed "original data"
                                       :V1 "V1 data"
                                       :V2 "V2 data")})))))
  (testing "vector structure"
    (is (= {:V0 [19]
            :V1 [21 "The Sentinel"]
            :V2 [23 "The Sentinel 2"]}
           (eval-qualified [:V0 :V1 :V2]
             (v/version-qualified *test-version* [:V0 :V1 :V2]
               [(v/switch {:V0 19 :V1 21 :V2 23})
                (v/only #{:V1} "The Sentinel")
                (v/added :V2 "The Sentinel 2")])))))
  (testing "list structure"
    (is (= {:V0 '(19)
            :V1 '(21 "The Sentinel")
            :V2 '(23 "The Sentinel 2")}
           (eval-qualified [:V0 :V1 :V2]
             (v/version-qualified *test-version* [:V0 :V1 :V2]
               (list (v/switch {:V0 19 :V1 21 :V2 23})
                     (v/only #{:V1} "The Sentinel")
                     (v/added :V2 "The Sentinel 2"))))))))


(deftest odds-and-ends
  (testing "manual deletion using ::delete"
    (is (= {:V0 []
            :V1 [:some-val]}
           (eval-qualified [:V0 :V1]
             (v/version-qualified *test-version* [:V0 :V1]
               [(v/switch {:V0 ::v/delete
                           :V1 :some-val})])))))
  (testing "embedded multi-expression support"
    (is (= {:V0 ["zero" "three"]
            :V1 ["zero" "one" "two" "three"]}
           (eval-qualified [:V0 :V1]
             (v/version-qualified *test-version* [:V0 :V1]
               ["zero" (v/added :V1 "one" "two") "three"]))))))

(deftest optimizations
  (testing "case elision"
    (is (= {:k1 "v1"}
           (v/version-qualified *test-version* [1 2 3 4 5 6 7 8 9 10]
             {(v/added 1 :k1) "v1"
              (v/removed 1 :k2) "v2"
              (v/only #{} :k3) "v3"}))
        "If all versions in a version-qualified block are the same, the version
         var need not be bound when evaluating")))
