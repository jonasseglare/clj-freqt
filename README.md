# clj-freqt

A Clojure implementation of the **FREQT** algoritm for [Frequent Subtree Mining](https://en.wikipedia.org/wiki/Frequent_subtree_mining) published in the following paper:

[Asai, Tatsuya, et al. "Efficient substructure discovery from large semi-structured data." IEICE TRANSACTIONS on Information and Systems 87.12 (2004): 2754-2763.](https://epubs.siam.org/doi/pdf/10.1137/1.9781611972726.10)

The implementation is based on this [C++ implementaion of FREQT](http://chasen.org/~taku/software/freqt/) but has been extended in the following ways:

* Every subtree gets assigned a cost.
* Every iteration of the algorithm picks the next subtree with the lowest cost from a priority queue.
* It is possible to customize how the resulting set of subtrees should be accumulated.

## Usage

This program takes as input the first argument being a forest of trees. Every tree is represented as an s-expression with the label of every node in the tree being the first element in the s-expression.

It outputs a list of all subtrees that satisfy some criteria and the support of each subtree:

```clojure
  (freqt
   '[(d (a (b (b) (d)) (c (d (b) (d)))) (a))
     (d (c (a (a (c)) (b) (b)) (d (b))) (c))]
   {:min-support 3, :support-mode :weighted})
;; => {:status :completed, :subtrees [{:support 6, :weighted-support 6, :subtree-size 1, :subtree [b]} {:support 6, :weighted-support 6, :subtree-size 1, :subtree [d]} {:support 4, :weighted-support 4, :subtree-size 1, :subtree [c]} {:support 4, :weighted-support 4, :subtree-size 1, :subtree [a]} {:support 3, :weighted-support 3, :subtree-size 2, :subtree [a [b]]}]}
```

The **support** of a subtree is the number of times it occurs. By normal support, we count the number of trees in the forest where the subtree occurs at least once. By weighted support, we count all occurrences in all trees.

The algorithm can be configured using the second argument, see `default-freqt-config` for the default configuration:

```clojure
(def default-freqt-config
  {;; Minimum support required to include the subtree in the result.
   :min-support 1

   ;; Whether or not to display some extra info while running the algorithm.
   :verbose false

   ;; Minimum subtree size to include it in the result.
   :min-node-size 1

   ;; Maximum subtree size to include it in the result.
   :max-node-size int-max

   ;; How support is counted. `:normal` means that we count the number
   ;; of trees in the forest where it occurs at least once. `:weighted`
   ;; means that we count all occurrences.
   :support-mode :normal

   ;; Stop the algoritm after this many seconds.
   :timeout-seconds 20.0

   ;; Stop the algoritm when the number of results is this number.
   :max-result-count nil

   ;; Cost associated with each subtree being explored. In every iteration,
   ;; the algorithm picks the tree with the lowest cost. Therefore, this cost
   ;; can be used to guide the exploration.
   :costfn default-freqt-cost

   ;; The report function is used to accumulate the result and has the
   ;; following arities:
   ;; * 0: Return an empty result data structure.
   ;; * 1: Called on the result before it is returned to finalize it.
   ;; * 2: Called on the arguments `context` and `subtree` with `subtree` being
   ;;      the subtree considered, to accumulate the result.
   :reportfn default-report})
```

## License

Copyright 2024 Jonas Östlund

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
