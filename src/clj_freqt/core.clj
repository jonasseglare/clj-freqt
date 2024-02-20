(ns clj-freqt.core
  (:require [taoensso.timbre :as timbre]))

(defn rate-limiter
  ([period-seconds] (rate-limiter period-seconds nil))
  ([period-seconds timeout]
   (let [start (System/nanoTime)
         period-ns (long (* 1.0e9 period-seconds))
         timeout (if timeout
                   (+ start (* 1.0e9 timeout))
                   Long/MAX_VALUE)
         state (atom start)]
     (fn []
       (let [now (System/nanoTime)]
         (when (< timeout now)
           (throw (ex-info "Timeout" {})))
         (apply not= (swap-vals! state
                                 (fn [threshold]
                                   (if (< now threshold)
                                     threshold
                                     (+ now period-ns))))))))))

(def int-max Integer/MAX_VALUE)

(defn default-freqt-cost [_projected _config]
  0.0)

(defn pattern-close? [x]
  (= [:close] x))

(defn pattern-size [pattern]
  (count (remove pattern-close? pattern)))

(defn pattern-node-symbol [[k v]]
  (when (= :symbol k)
    v))

(defn default-report
  ([] [])
  ([result] result)
  ([result
    {:keys [min-node-size max-result-count]}
    {:keys [support locations pattern]}]
   (let [size (pattern-size pattern)
         result (if (<= min-node-size size)
                  (conj
                   result
                   {:support support
                    :weighted-support (count locations)
                    :subtree-size size
                    :pattern pattern})
                  result)]
     (if (or (nil? max-result-count)
             (<= (count result) max-result-count))
       result
       (reduced result)))))

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

(defn sexpr? [x]
  (and (sequential? x) (seq x)))

(defn check-sexpr [x]
  (if (sexpr? x)
    x
    (throw (ex-info "Invalid sexpr" {:expr x}))))

(defn postwalk-sexpr [f sexpr]
  (let [[label & args] (check-sexpr sexpr)]
    (f (into [label]
             (map #(postwalk-sexpr f %))
             args))))

(defn check-sexpr-labels [pred sexpr]
  (postwalk-sexpr (fn [[label & _args :as subexpr]]
                    (when-not (pred label)
                      (throw (ex-info "Invalid label"
                                      {:label label
                                       :subexpr subexpr}))))
                  sexpr))

(defn normed-symbol?
  ([x] (and (vector? x)
            (= 2 (count x))))
  ([x type]
   (and (normed-symbol? x)
        (= type (first x)))))

(defn index-symbol? [x]
  (normed-symbol? x :index))

(defn normalize-sexprs [sexprs]
  (let [m (atom {})
        get-key (fn [x]
                  (let [m2 (swap!
                            m
                            (fn [label-map]
                              (update
                               label-map
                               x #(or %
                                      (symbol
                                       (format
                                        "s%d"
                                        (count label-map)))))))]
                    (m2 x)))
        result (into []
                     (map (fn [expr]
                            (postwalk-sexpr
                             (fn [x]
                               (let [[label & args] (check-sexpr x)]
                                 (seq (into [(get-key label)]

                                            ;; Always sort!!!
                                            (sort-by first args)))))
                             expr)))
                     sexprs)]
    [(into {}
           (map (fn [[k v]] [v k]))
           (deref m)) result]))

(defn rebuild-sexpr [sexpr label-map]
  (postwalk-sexpr
   (fn [x]
     (let [[label & args] (check-sexpr x)]
       (into [(label-map label)] args)))
   sexpr))

(defn rebuild-sexprs [label-map sexprs]
  (into []
        (map #(rebuild-sexpr % label-map))
        sexprs))

(defn flatten-sexpr [sexpr]
  (loop [stack (list [:input sexpr])
         result []]
    (if (empty? stack)
      result
      (let [[[op-type x] & stack] stack]
        (case op-type
          :input (let [[f & args] (check-sexpr x)]
                   (recur (into (conj stack [:close])
                                (map (fn [subexpr] [:input subexpr]))
                                (reverse args))
                          (conj result [:symbol f])))
          :close (recur stack (conj result [:close])))))))

(defn flat-item? [x]
  (and (vector? x) (#{:symbol :close} (first x))))

(defn or-1 [a b]
  {:pre [(number? a)
         (number? b)]}
  (if (= -1 a)
    b
    a))

(defn symbol-count [flat]
  (transduce (map (fn [[k _]] (case k :symbol 1 :close 0)))
             +
             flat))

(defn nodes-from-flattened [flat]
  {:pre [(every? flat-item? flat)]}
  (let [size (symbol-count flat)
        n (count flat)]
    (loop [node (vec (repeat size {:parent -1 :child -1 :sibling -1 :val nil}))
           sibling (vec (repeat size -1))
           sr []
           id 0
           top 0
           i 0]
      (if (<= n i)
        node
        (let [x (nth flat i)
              i (inc i)]
          (if (= x [:close])
            (let [top (dec (count sr))]
              (if (< top 1)
                (recur node sibling sr id top i)
                (let [child (nth sr top)
                      parent (nth sr (dec top))
                      node (-> node
                               (assoc-in [child :parent] parent)
                               (update-in [parent :child] #(or-1 % child)))
                      k (nth sibling parent)
                      node (cond-> node
                             (not= -1 k) (assoc-in [k :sibling] child))
                      sibling (assoc sibling parent child)
                      sr (subvec sr 0 top)]
                  (recur node sibling sr id top i))))
            (recur (assoc-in node [id :val] (second x))
                   sibling
                   (conj sr id)
                   (inc id)
                   top
                   i)))))))

(defn sexpr->transaction [sexpr]
  (-> sexpr
      flatten-sexpr
      nodes-from-flattened))

(defn sexprs->transactions [sexprs]
  (mapv sexpr->transaction sexprs))

(def projected-t {:depth 0
                  :support 0
                  :locations []
                  :pattern nil})

(defn projected-t? [x]
  (and (map? x)
       (contains? x :support)
       (contains? x :locations)
       (contains? x :depth)))

(defn support [projected {:keys [support-mode]}]
  {:pre [(keyword? support-mode)]}
  (let [locations (:locations projected)
        n (count locations)]
    (if (= :weighted support-mode)
      n
      (loop [old int-max
             i 0
             size 0]
        (if (< i n)
          (let [[new _] (nth locations i)]
            (recur new (inc i) (if (not= old new) (inc size) size)))
          size)))))

(defn freq1 [transactions]
  (vals
   (reduce (fn [dst [v loc]]
             (update dst v #(-> %
                                (or projected-t)
                                (update :locations conj loc)
                                (assoc :pattern v
                                       :key v))))
           {}
           (for [[i transaction] (map-indexed vector transactions)
                 [j node] (map-indexed vector transaction)]
             [[[:symbol (:val node)]] [i j]]))))

(def empty-candidate-queue (sorted-map))

(defn candidate-queue? [x]
  (and (map? x) (sorted? x)))

(defn prefix-element? [x]
  (and (vector? x)
       (#{:symbol :close} (first x))))

(defn prefix? [x]
  (and (vector? x)
       (every? prefix-element? x)))

(defn project-sub [pattern transactions locations depth]
  (let [candidates (java.util.HashMap.)
        expanded-positions (java.util.HashMap.)]
    (doseq [[id pos] locations]
      (let [transaction (nth transactions id)]
        (loop [d -1
               pos pos
               prefix []]
          (when (and (< d depth) (not= pos -1))
            (let [node-pos (nth transaction pos)
                  is-first-d (= -1 d)
                  start ((if is-first-d :child :sibling) node-pos)
                  new-depth (- depth d)]
              (loop [l start]
                (when-not (= -1 l)
                  (let [node-l (nth transaction l)
                        item (conj prefix [:symbol (:val node-l)])
                        tmp (-> candidates
                                (get item projected-t)
                                (update :locations conj [id l])
                                (assoc :depth new-depth
                                       :pattern (into pattern item)
                                       :key item))]
                    (assert (prefix? item))
                    (.put expanded-positions pos pos)
                    (.put candidates item tmp)
                    (recur (:sibling node-l)))))
              (recur (inc d)
                     (if is-first-d pos (:parent node-pos))
                     (conj prefix [:close])))))))
    {:candidates (into [] (map val) candidates)
     :expanded-positions (into #{} (map key) expanded-positions)}))

(defn project [{:keys [transactions max-node-size]}
               {:keys [pattern locations depth]}]
  {:pre [(vector? transactions)
         (vector? pattern)]}
  (let [pattern-size (count pattern)]
    (if (< pattern-size max-node-size)
      (project-sub pattern transactions locations depth)
      {:candidates []
       :expanded-positions #{}})))

(defn set-candidate-cost [candidate cost-data]
  {:pre [(or (and (map? cost-data) (contains? cost-data :cost))
             (number? cost-data)
             (sequential? cost-data))]}
  (if (map? cost-data)
    (merge candidate cost-data)
    (assoc candidate :cost cost-data)))

(defn push-candidate [dst candidate {:keys [min-support costfn] :as cfg}]
  {:pre [(candidate-queue? dst)
         (projected-t? candidate)]}
  (let [sup (support candidate cfg)]
    (if (<= min-support sup)
      (let [candidate (assoc candidate :support sup)
            projection (project cfg candidate)
            candidate (merge candidate projection)
            candidate (set-candidate-cost candidate (costfn candidate cfg))
            pattern (:pattern candidate)
            key (:key candidate)]
        (assert (vector? pattern))
        (assert (vector? key))
        (assoc dst
               [(:cost candidate) pattern key]
               candidate))
      dst)))

(defn push-candidates [dst candidates cfg]
  (reduce (fn [dst c] (push-candidate dst c cfg))
          dst
          candidates))

(defn not-open? [x]
  (not= x [:open]))

(defn reconstruct-sexpr-unwrap [[k x]]
  {:pre [(keyword? k)]}
  x)

(defn reconstruct-sexpr-close-stack [stack]
  (let [element (into '()
                      (comp (take-while not-open?)
                            (map reconstruct-sexpr-unwrap))
                      stack)]
    (->> stack
         (drop-while not-open?)
         rest
         (cons [:expr element]))))

(defn reconstruct-sexpr [pattern]
  (loop [input pattern
         stack '()]
    (if (empty? input)
      (case (count stack)
        0 (throw (ex-info "Empty stack" {:pattern pattern}))
        1 (reconstruct-sexpr-unwrap (first stack))
        (recur input (reconstruct-sexpr-close-stack stack)))
      (let [[[op-type x] & input] input]
        (case op-type
          :close (recur input (reconstruct-sexpr-close-stack stack))
          :symbol (recur input (conj stack [:open] [:expr x])))))))

(defn build-subtrees [expr-map results]
  (mapv (fn [item]
          (let [subtree (-> item
                            :pattern
                            reconstruct-sexpr
                            (rebuild-sexpr expr-map))]
            (-> item                               
                (assoc :subtree subtree)
                (dissoc :pattern))))
        results))


(defn freqt
  ([sexprs] (freqt sexprs {}))
  ([sexprs0 config]
   (let [config (merge default-freqt-config config)
         verbose (:verbose config)
         report (:reportfn config)

         ;; TODO: Make it possible to skip normalization, so
         ;; that we can do it once instead.
         [expr-map sexprs] (normalize-sexprs sexprs0)
         
         transactions (sexprs->transactions sexprs)
         rl (rate-limiter 1.0 (:timeout-seconds config))
         context (assoc config
                        :transactions transactions
                        :rate-limiter rl
                        :expr-map expr-map)
         initial-candidates (freq1 transactions)]
     (when verbose
       (timbre/info "Freqt on" (count sexprs0) "sexprs"))
     (loop [queue (push-candidates empty-candidate-queue
                                   initial-candidates
                                   context)
            result (report)]
       (when (and (rl) verbose)
         (timbre/info (format "Freqt queue=%d" (count queue))))
       (let [make-result (fn [status result]
                           {:status status
                            :subtrees (build-subtrees expr-map (report result))})]
         (cond
           (reduced? result) (make-result
                              :reduced
                              (deref result))
           (empty? queue) (make-result
                           :completed
                           (report result))
           :else
           (let [[k candidate] (first queue)
                 queue (dissoc queue k)
                 candidates (:candidates candidate)]
             (recur (push-candidates queue candidates context)
                    (report result context candidate)))))))))



(comment
  
  (freqt
   '[(d (a (b (b) (d)) (c (d (b) (d)))) (a))
     (d (c (a (a (c)) (b) (b)) (d (b))) (c))]
   {:min-support 3, :support-mode :weighted})
;; => {:status :completed, :subtrees [{:support 6, :weighted-support 6, :subtree-size 1, :subtree [b]} {:support 6, :weighted-support 6, :subtree-size 1, :subtree [d]} {:support 4, :weighted-support 4, :subtree-size 1, :subtree [c]} {:support 4, :weighted-support 4, :subtree-size 1, :subtree [a]} {:support 3, :weighted-support 3, :subtree-size 2, :subtree [a [b]]}]}


  )
