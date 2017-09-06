(ns com.retailmenot.version-qualified.core-test
  (:require [clojure.test :refer :all]
            [com.retailmenot.version-qualified.core :as v]))


;; (deftest odds-and-ends
;;   (testing "manual deletion using ::delete"
;;     (is (= {:V0 []
;;             :V1 [:some-val]}
;;            (eval-qualified [:V0 :V1]
;;              (v/version-qualified *test-version* [:V0 :V1]
;;                [(v/switch {:V0 ::v/delete
;;                            :V1 :some-val})])))))
;;   (testing "embedded multi-expression support"
;;     (is (= {:V0 ["zero" "three"]
;;             :V1 ["zero" "one" "two" "three"]}
;;            (eval-qualified [:V0 :V1]
;;              (v/version-qualified *test-version* [:V0 :V1]
;;                ["zero" (added :V1 "one" "two") "three"]))))))
;;
;; (deftest optimizations
;;   (testing "case elision"
;;     (is (= {:k1 "v1"}
;;            (v/version-qualified *test-version* [1 2 3 4 5 6 7 8 9 10]
;;              {(added 1 :k1) "v1"
;;               (removed 1 :k2) "v2"
;;               (v/only #{} :k3) "v3"}))
;;         "If all versions in a version-qualified block are the same, the version
;;          var need not be bound when evaluating")))
