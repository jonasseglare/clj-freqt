(ns clj-freqt.core-test
  (:require [clojure.test :refer :all]
            [clj-freqt.core :refer :all]))

(deftest test-my-freqt-impl
  (is (= '#{{:support 1,
             :weighted-support 1,
             :subtree-size 2,
             :subtree [c [b]]}
            {:support 1,
             :weighted-support 1,
             :subtree-size 3,
             :subtree [a [c [b]]]}
            {:support 2, :weighted-support 2, :subtree-size 1, :subtree [b]}
            {:support 2, :weighted-support 2, :subtree-size 1, :subtree [c]}
            {:support 1,
             :weighted-support 1,
             :subtree-size 2,
             :subtree [b [c]]}
            {:support 1,
             :weighted-support 1,
             :subtree-size 2,
             :subtree [a [c]]}
            {:support 1,
             :weighted-support 1,
             :subtree-size 3,
             :subtree [a [b [c]]]}
            {:support 2, :weighted-support 2, :subtree-size 1, :subtree [a]}
            {:support 1,
             :weighted-support 1,
             :subtree-size 2,
             :subtree [a [b]]}}
         (normalize-results (freqt '[(a (b (c))) (a (c (b)))]))))
  (is (= '#{{:support 2,
             :weighted-support 2,
             :subtree-size 2,
             :subtree [a [b]]}}
         (normalize-results
          (freqt '[(a (b (c))) (a (b (d)))]
                 {:min-support 2 :min-node-size 2}))))
  (is (= '#{{:support 1, :weighted-support 1, :subtree-size 1, :subtree [a]}
            {:support 1,
             :weighted-support 1,
             :subtree-size 4,
             :subtree [a [b] [b] [b]]}
            {:support 1,
             :weighted-support 3,
             :subtree-size 2,
             :subtree [a [b]]}
            {:support 1,
             :weighted-support 3,
             :subtree-size 3,
             :subtree [a [b] [b]]}
            {:support 1, :weighted-support 3, :subtree-size 1, :subtree [b]}}
         (normalize-results (freqt '[(a (b) (b) (b))] {}))))
  (is (= (normalize-results (freqt '[(c
                                      (a)
                                      (a)
                                      (b (d))
                                      (d (a (a (b (a)) (d))) (a (c)) (b (d)) (c (b) (b (a))) (c)))]
                                   {:min-support 3, :support-mode :weighted}))
         '#{{:support 5, :weighted-support 5, :subtree-size 1, :subtree [b]}
            {:support 7, :weighted-support 7, :subtree-size 1, :subtree [a]}
            {:support 3,
             :weighted-support 3,
             :subtree-size 2,
             :subtree [c [b]]}
            {:support 4, :weighted-support 4, :subtree-size 1, :subtree [d]}
            {:support 4, :weighted-support 4, :subtree-size 1, :subtree [c]}}))
  (is (= '#{{:support 6, :weighted-support 6, :subtree-size 1, :subtree [b]}
            {:support 6, :weighted-support 6, :subtree-size 1, :subtree [d]}
            {:support 4, :weighted-support 4, :subtree-size 1, :subtree [a]}
            {:support 4, :weighted-support 4, :subtree-size 1, :subtree [c]}
            {:support 3,
             :weighted-support 3,
             :subtree-size 2,
             :subtree [a [b]]}}
         (normalize-results
          (freqt
           '[(d (a (b (b) (d)) (c (d (b) (d)))) (a))
             (d (c (a (a (c)) (b) (b)) (d (b))) (c))]
           {:min-support 3, :support-mode :weighted}))))
  (is (= '#{{:support 2, :weighted-support 7, :subtree-size 1, :subtree [b]}
            {:support 2,
             :weighted-support 2,
             :subtree-size 2,
             :subtree [d [d]]}
            {:support 2, :weighted-support 6, :subtree-size 1, :subtree [d]}}
         (normalize-results
          (freqt
           '[(d (c) (d (a (a) (b (a (c) (c)) (c)))))
             (b (b (b (b))) (b (b)) (d (d)) (d) (d))]
           {:min-support 2, :support-mode :normal}))))
  (is (= '#{{:support 2,
             :weighted-support 2,
             :subtree-size 3,
             :subtree [b [a] [a]]}}
         (normalize-results
          (freqt
           '[(b (a (b)) (a (b (c (c))) (d (a))) (c))
             (b (a) (a) (b (b)) (b (d (a (b (d))))))]
           {:min-support 2, :support-mode :normal, :min-node-size 3}))))
  (is (= #{{:support 2,
            :weighted-support 2,
            :subtree-size 3,
            :subtree '[c [d [a]]]}
           {:support 2,
            :weighted-support 2,
            :subtree-size 3,
            :subtree '[c [d [c]]]}}
         (normalize-results
          (freqt
           '[(a (c (b) (b) (d (a))) (c (c) (d (c))))
             (b (c (d (a) (c (a) (a)))) (c (b) (c)))]
           {:min-support 2, :support-mode :normal, :min-node-size 3})))))

(deftest test-raw-freqt-cpp
  (is (= (-> '[(a (b (c))) (a (c (b)))]
             sexprs->transactions
             freq1
             set)
         '#{{:depth 0,
             :support 0,
             :locations [[0 0] [1 0]],
             :pattern [[:symbol a]],
             :key [[:symbol a]]}
            {:depth 0,
             :support 0,
             :locations [[0 1] [1 2]],
             :pattern [[:symbol b]],
             :key [[:symbol b]]}
            {:depth 0,
             :support 0,
             :locations [[0 2] [1 1]],
             :pattern [[:symbol c]],
             :key [[:symbol c]]}}))
  (is (= 2 (support {:depth 0, :support 0, :locations [[0 0] [1 0]]} default-freqt-config)))
  (is (= 1 (support {:depth 0, :support 0, :locations [[0 0] [0 0]]} default-freqt-config)))
  (is (= 2 (support {:depth 0, :support 0, :locations [[0 0] [0 0]]}
                    (assoc default-freqt-config :support-mode :weighted)))))
