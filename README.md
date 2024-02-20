# clj-freqt

A Clojure implementation of the **FREQT** algoritm for [Frequent Subtree Mining](https://en.wikipedia.org/wiki/Frequent_subtree_mining) published in the following paper:

[Asai, Tatsuya, et al. "Efficient substructure discovery from large semi-structured data." IEICE TRANSACTIONS on Information and Systems 87.12 (2004): 2754-2763.](https://epubs.siam.org/doi/pdf/10.1137/1.9781611972726.10)

The implementation is based on this [C++ implementaion of FREQT](http://chasen.org/~taku/software/freqt/) but has been extended in the following ways:

* Every subtree gets assigned a cost.
* Every iteration of the algorithm picks the next subtree with the lowest cost from a priority queue.
* It is possible to customize how the resulting set of subtrees should be accumulated.

## Usage


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
