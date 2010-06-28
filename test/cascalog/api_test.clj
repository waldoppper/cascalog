(ns cascalog.api-test
  (:use clojure.test
        cascalog.testing
        cascalog.api)
  (:import [cascading.tuple Fields])
  (:require [cascalog [ops :as c]]))

(defmapop mk-one [& tuple] 1)

(deftest test-no-input
  (with-tmp-sources [nums [[1] [2] [3]]]
    (test?<- [[1 1] [2 1] [3 1]] [?n ?n2] (nums ?n) (mk-one ?n2))
    (test?<- [[1 1] [1 2] [1 3] [2 1] [2 2] [2 3] [3 1] [3 2] [3 3]] [?n ?n3] (nums ?n) (mk-one ?n2) (nums ?n3))
    ))

(deftest test-simple-query
  (with-tmp-sources [age [["n" 24] ["n" 23] ["i" 31] ["c" 30] ["j" 21] ["q" nil]]]
     (test?<- [["j"] ["n"]] [?p] (age ?p ?a) (< ?a 25))
     (test?<- [["j"] ["n"] ["n"]] [?p] (age ?p ?a) (< ?a 25) (:distinct false))
    ))

(deftest test-larger-tuples
  (with-tmp-sources [stats [["n" 6 190 nil] ["n" 6 195 nil] ["i" 5 180 31] ["g" 5 150 60]]
                     friends [["n" "i" 6] ["n" "g" 20] ["g" "i" nil]]]
     (test?<- [["g" 60]] [?p ?a] (stats ?p _ _ ?a) (friends ?p _ _))
     (test?<- [] [?p ?a] (stats ?p 1000 _ ?a))
     (test?<- [["n"] ["n"]] [?p] (stats ?p _ _ nil) (:distinct false))
    ))

(deftest test-multi-join
  (with-tmp-sources [age [["n" 24] ["a" 15] ["j" 24] ["d" 24] ["b" 15] ["z" 62] ["q" 24]]
                     friends [["n" "a" 16] ["n" "j" 12] ["j" "n" 10] ["j" "d" nil]
                              ["d" "q" nil] ["b" "a" nil] ["j" "a" 1] ["a" "z" 1]]]
        (test?<- [["n" "j"] ["j" "n"] ["j" "d"] ["d" "q"] ["b" "a"]]
          [?p ?p2] (age ?p ?a) (age ?p2 ?a) (friends ?p ?p2 _))
        ))

(deftest test-many-joins
  (with-tmp-sources [age-prizes [[10 "toy"] [20 "animal"] [30 "car"] [40 "house"]]
                     friends    [["n" "j"] ["n" "m"] ["n" "a"] ["j" "a"] ["a" "z"]]
                     age         [["z" 20] ["a" 10] ["n" 15]]]
        (test?<- [["n" "animal-!!"] ["n" "house-!!"] ["j" "house-!!"]]
          [?p ?prize] (friends ?p ?p2) (friends ?p2 ?p3) (age ?p3 ?a)
                          (* 2 ?a :> ?a2) (age-prizes ?a2 ?prize2) (str ?prize2 "-!!" :> ?prize))
        ))

(deftest test-bloated-join
  (with-tmp-sources [gender     [["n" "male"] ["j" "male"] ["a" nil] ["z" "female"]]
                     interest   [["n" "bball"] ["n" "dl"] ["j" "tennis"] ["z" "stuff"] ["a" "shoes"]]
                     friends    [["n" "j"] ["n" "m"] ["n" "a"] ["j" "a"] ["a" "z"] ["z" "a"]]
                     age        [["z" 20] ["a" 10] ["n" 15]]]
        (test?<- [["n" "bball" 15 "male"] ["n" "dl" 15 "male"]
                  ["a" "shoes" 10 nil] ["z" "stuff" 20 "female"]]
          [!p !interest !age !gender] (friends !p _) (age !p !age) (interest !p !interest) (gender !p !gender))
        ))

(defmapcatop split [#^String words]
  (seq (.split words "\\s+")))

(deftest test-countall
  (with-tmp-sources [sentence [["hello this is a"] ["say hello hello to the man"] ["this is the cool beans man"]]]
    (test?<- [["hello" 3] ["this" 2] ["is" 2]
              ["a" 1] ["say" 1] ["to" 1] ["the" 2]
              ["man" 2] ["cool" 1] ["beans" 1]]
          [?w ?c] (sentence ?s) (split ?s :> ?w) (c/count ?c))
    ))

(deftest test-multi-sink)

(deftest test-multi-agg
  (with-tmp-sources [value [["a" 1] ["a" 2] ["b" 10] ["c" 3] ["b" 2] ["a" 6]] ]
    (test?<- [["a" 12] ["b" 14] ["c" 4]] [?v ?a] (value ?v ?n) (c/count ?c) (c/sum ?n :> ?s) (+ ?s ?c :> ?a))
    ))

(deftest test-joins-aggs
  (with-tmp-sources [friend [["n" "a"] ["n" "j"] ["n" "q"] ["j" "n"] ["j" "a"] ["j" "z"] ["z" "t"]]
                     age    [["n" 25] ["z" 26] ["j" 20]] ]
    (test?<- [["n"] ["j"]] [?p] (age ?p _) (friend ?p _) (c/count ?c) (> ?c 2))
    ))

(deftest test-global-agg
   (with-tmp-sources [num [[1] [2] [5] [6] [10] [12]] ]
     (test?<- [[6]] [?c] (num _) (c/count ?c))
     (test?<- [[6 72]] [?c ?s2] (num ?n) (c/count ?c) (c/sum ?n :> ?s) (* 2 ?s :> ?s2))
  ))

(defaggregateop evens-vs-odds
  ([] 0)
  ([context val] (if (odd? val) (dec context) (inc context)))
  ([context] [context]))

(deftest test-complex-noncomplex-agg-mix
  (with-tmp-sources [num [["a" 1] ["a" 2] ["a" 5] ["c" 6] ["d" 9] ["a" 12] ["c" 16] ["e" 16]] ]
     (test?<- [["a" 4 0 20] ["c" 2 2 22] ["d" 1 -1 9] ["e" 1 1 16]]
       [?a ?c ?e ?s] (num ?a ?n) (c/count ?c) (c/sum ?n :> ?s) (evens-vs-odds ?n :> ?e))
  ))

(defn mk-agg-test-tuples []
  (conj (vec (take 12000 (iterate (fn [[a b]] [(inc a) b]) [0 1]))) [0 10]))

(defn mk-agg-test-results []
  (conj (vec (take 11999 (iterate (fn [[a b c]] [(inc a) b c]) [1 1 1]))) [0 11 2]))

(deftest test-complex-agg-more-than-10000
  (with-tmp-sources [num (mk-agg-test-tuples)]
     (test?<- (mk-agg-test-results)
       [?n ?s ?c] (num ?n ?v) (c/sum ?v :> ?s) (c/count ?c))))

(deftest test-multi-rule
  (with-tmp-sources [age [["n" 24] ["c" 40] ["j" 23] ["g" 50]]
                     interest [["n" "bb" nil] ["n" "fb" 20] ["g" "ck" 30] ["j" "nz" 10] ["j" "hk" 1] ["jj" "ee" nil]]
                     follows [["n" "j"] ["j" "n"] ["j" "a"] ["n" "a"] ["g" "q"]] ]
      (let [many-follow (<- [?p] (follows ?p _) (c/count ?c) (> ?c 1))
            active-follows (<- [?p ?p2] (many-follow ?p) (many-follow ?p2) (follows ?p ?p2))
            unknown-interest (<- [?p] (age ?p ?a) (interest ?p _ !i) (nil? !i))
            weird-follows (<- [?p ?p2] (active-follows ?p ?p2) (unknown-interest ?p2))]
          (test?- [["n" "j"] ["j" "n"]] active-follows
                  [["j" "n"]] weird-follows
                  [["n"]] unknown-interest)
        )))

(deftest test-filter-same-field
  (with-tmp-sources [nums [[1 1] [0 0] [1 2] [3 7] [8 64] [7 1] [2 4] [6 6]]]
    (test?<- [[1] [0] [6]] [?n] (nums ?n ?n))
    (test?<- [[1 1] [0 0] [8 64] [2 4]] [?n ?n2] (nums ?n ?n2) (* ?n ?n :> ?n2))
    (test?<- [[0]] [?n] (nums ?n ?n) (* ?n ?n :> ?n) (+ ?n ?n :> ?n))
    (test?<- [[1 1] [1 2] [0 0] [6 6]] [?n ?n2] (nums ?n ?n) (nums ?n ?n2))
    (test?<- [[14]] [?s] (nums ?n ?n) (* 2 ?n :> ?n2) (c/sum ?n2 :> ?s))
    (test?<- [[6] [0]] [?n2] (nums ?n ?n) (nums ?n2 ?n2) (* 6 ?n :> ?n2))
    ))

(defbufferop select-first [tuples]
  [(first tuples)])

(deftest test-sort
  (with-tmp-sources [pairs [["a" 1] ["a" 2] ["a" 3] ["b" 10] ["b" 20]]]
    (test?<- [["a" 1] ["b" 10]] [?f1 ?f2] (pairs ?f1 ?v) (:sort ?v) (select-first ?v :> ?f2))
    (test?<- [["a" 3] ["b" 20]] [?f1 ?f2] (pairs ?f1 ?v) (:sort ?v) (:reverse true) (select-first ?v :> ?f2))
    ))

(defn existence2str [obj]
  (if obj "some" "none"))

(defmapop outer-join-tester [obj]
  (if obj "o" "n"))

(defmapop outer-join-tester2 [obj]
  (if obj "o2" "n2"))

(defmapcatop outer-join-tester3 [obj]
  (if obj [1] [1 1]))

(deftest test-outer-join-basic
  (with-tmp-sources [person [["a"] ["b"] ["c"] ["d"]]
                     follows [["a" "b"] ["c" "e"] ["c" "d"]]]
    (test?<- [["a" "b"] ["c" "e"] ["c" "d"] ["b" nil] ["d" nil]]
      [?p !!p2] (person ?p) (follows ?p !!p2))
    (test?<- [["a" "b" "b"] ["c" "e" "d"] ["c" "e" "e"]
              ["c" "d" "d"] ["c" "d" "e"] ["b" nil nil]
              ["d" nil nil]]
      [?p !!p2 !!p3] (person ?p) (follows ?p !!p2) (follows ?p !!p3))
    (test?<- [["a" 1 1] ["c" 2 2] ["b" 0 1] ["d" 0 1]]
      [?p ?c ?t] (person ?p) (follows ?p !!p2) (c/!count !!p2 :> ?c) (c/count ?t))
    (test?<- [["a" "some"] ["b" "none"] ["c" "some"] ["d" "none"]]
      [?p ?s] (person ?p) (follows ?p !!p2) (existence2str !!p2 :> ?s))
  ))

(deftest test-outer-join-complex
 (with-tmp-sources [age [["a" 20] ["b" 30] ["c" 27] ["d" 40]]
                    rec1 [["a" 1 2] ["b" 30 16] ["e" 3 4]]
                    rec2 [["a" 20 6] ["c" 27 25] ["c" 1 11] ["f" 30 1] ["b" 100 16]] ]
   (test?<- [["a" 20 1 2 6] ["c" 27 nil nil 25] ["d" 40 nil nil nil] ["b" 30 30 16 nil]]
     [?p ?a !!f1 !!f2 !!f3] (age ?p ?a) (rec1 ?p !!f1 !!f2) (rec2 ?p ?a !!f3))
  ))

(deftest test-full-outer-join
  (with-tmp-sources [age [["A" 20] ["B" 30] ["C" 27] ["D" 40]]
                    gender [["A" "m"] ["B" "f"] ["E" "m"] ["F" "f"]]
                    follows [["A" "B"] ["B" "E"] ["B" "G"] ["E" "D"]]
                    age-tokens [["B" 20 "d"] ["A" 30 "a"]]
                    ]
   (test?<- [["A" 20 "m"] ["B" 30 "f"] ["C" 27 nil] ["D" 40 nil] ["E" nil "m"] ["F" nil "f"]]
     [?p !!a !!g] (age ?p !!a) (gender ?p !!g))
   (test?<- [["A" "o" "o2"] ["B" "o" "o2"] ["C" "o" "n2"] ["D" "o" "n2"] ["E" "n" "o2"] ["F" "n" "o2"]]
     [?p ?s ?s2]
    (age ?p !!a) (gender ?p !!g) (outer-join-tester !!a :> ?s) (outer-join-tester2 !!g :> ?s2))
   (test?<- [["A" 1] ["B" 1] ["C" 1] ["D" 1] ["E" 2] ["F" 2]]
     [?p ?c] (age ?p !!a) (gender ?p !!g) (outer-join-tester3 !!a :> ?t) (c/count ?c))
   (test?<- [["A" "a"] ["E" nil]]
     [?p !!t] (follows ?p ?p2) (age ?p2 ?a2) (age-tokens ?p ?a2 !!t))
   (test?<- [["E"]]
     [?p] (follows ?p ?p2) (age ?p2 ?a2) (age-tokens ?p ?a2 !!t) (nil? !!t))
  ))

(defmapop [hof-add [a]] [n]
  (+ a n))

(defmapop [hof-arithmetic [a b]] [n]
  (+ b (* a n)))

;; TODO: stateful operations should return a map containing :init, :op, :finish
(defbufferop [sum-plus [a]] {:stateful true}
  ([] (* 3 a))
  ([state tuples] [(apply + state (map first tuples))])
  ([state] nil))

(deftest test-hof-ops
  (with-tmp-sources [integer [[1] [2] [6]]]
    (test?<- [[4] [5] [9]] [?n] (integer ?v) (hof-add 3 ?v :> ?n))
    (test?<- [[-5] [-4] [0]] [?n] (integer ?v) (hof-add [-6] ?v :> ?n))
    (test?<- [[3] [5] [13]] [?n] (integer ?v) (hof-arithmetic [2 1] ?v :> ?n))
    (test?<- [[72]] [?n] (integer ?v) (sum-plus [21] ?v :> ?n))
    ))

(defn lala-appended [source]
  (let [outvars ["?a"]]
    (<- outvars (source ?line) (str ?line "lalala" :>> outvars) (:distinct false))))

(deftest test-dynamic-vars
  (with-tmp-sources [sentence [["nathan david"] ["chicken"]]]
    (test?<- [["nathan davidlalala"] ["chickenlalala"]] [?out] ((lala-appended sentence) ?out))
    (test?<- [["nathan davida"] ["chickena"]] [?out] (sentence :>> [?line]) (str :<< ["?line" "a"] :>> ["?out"]))
    ))

(defbufferop nothing-buf [tuples]
  tuples )

(deftest test-limit
  (with-tmp-sources [pair [["a" 1] ["a" 3] ["a" 2] ["a" 4] ["b" 1] ["b" 6] ["b" 7] ["c" 0]]]
     (test?<- [[0] [1] [1] [2] [3] [4] [6] [7]] [?n2] (pair _ ?n) (:sort ?n) (nothing-buf ?n :> ?n2))
     (test?<- [[0] [1]] [?n2] (pair _ ?n) (:sort ?n) (c/limit [2] ?n :> ?n2))
     (test?<- [[1 1] [2 2] [3 3] [4 4] [1 5]] [?n2 ?r] (pair ?l ?n) (:sort ?l ?n) (c/limit-rank [5] ?n :> ?n2 ?r))
     (test?<- [["c" 0] ["b" 7]] [?l2 ?n2] (pair ?l ?n) (:sort ?l ?n) (:reverse true) (c/limit [2] ?l ?n :> ?l2 ?n2))
     (test?<- [[0] [1] [1]] [?n2] (pair _ ?n) (:sort ?n) (c/limit [3] ?n :> ?n2))
     (test?<- [[0 1] [1 2] [1 3]] [?n2 ?r] (pair _ ?n) (:sort ?n) (c/limit-rank [3] ?n :> ?n2 ?r))
     (test?<- [[6] [7]] [?n2] (pair _ ?n) (:sort ?n) (:reverse true) (c/limit [2] ?n :> ?n2))
     (test?<- [[6 2] [7 1]] [?n2 ?r] (pair _ ?n) (:sort ?n) (:reverse true) (c/limit-rank [2] ?n :> ?n2 ?r))
     (test?<- [["a" 1] ["a" 2] ["b" 1] ["b" 6] ["c" 0]] [?l ?n2] (pair ?l ?n) (:sort ?n) (c/limit [2] ?n :> ?n2))
   ))

(deftest test-outer-join-anon
  (with-tmp-sources [person [["a"] ["b"] ["c"]]
                     follows [["a" "b" 1] ["c" "e" 2] ["c" "d" 3]]]
    (test?<- [["a" "b"] ["c" "e"] ["c" "d"] ["b" nil]]
      [?p !!p2] (person ?p) (follows ?p !!p2 _))
  ))

(defbufferiterop itersum [tuples-iter]
  [(reduce + (map first (iterator-seq tuples-iter)))])

(deftest test-bufferiter
  (with-tmp-sources [nums [[1] [2] [4]]]
    (test?<- :info [[7]] [?s] (nums ?n) (itersum ?n :> ?s))
    ))

(defn inc-tuple [& tuple]
  (map inc tuple))

(deftest test-pos-out-selectors
  (with-tmp-sources [wide [["a" 1 nil 2 3] ["b" 1 "c" 5 1] ["a" 5 "q" 3 2]]]
    (test?<- [[3 nil] [1 "c"] [2 "q"]] [?l !m] (wide :#> 5 {4 ?l 2 !m}) (:distinct false))
    (test?<- [[4] [2] [3]] [?n3] (wide _ ?n1 _ _ ?n2) (inc-tuple ?n1 ?n2 :#> 2 {1 ?n3}))
    (test?<- [["b"]] [?a] (wide :#> 5 {0 ?a 1 ?b 4 ?b}))
    ))

(deftest test-select-tap-fields
  (let [data (memory-source-tap ["f1" "f2" "f3" "f4"] [[1 2 3 4] [11 12 13 14] [21 22 23 24]])]
    (test?<- [[4 2] [14 12] [24 22]] [?a ?b] ((select-tap-fields data ["f4" "f2"]) ?a ?b))
    (test?<- [[1 3 4] [11 13 14] [21 23 24]] [?f1 ?f2 ?f3] ((select-tap-fields data ["f1" "f3" "f4"]) ?f1 ?f2 ?f3))
    ))

(deftest test-predicate-macro
  (let [mac1 (<- [?a :> ?b ?c] (+ ?a 1 :> ?t) (* ?t 2 :> ?b) (+ ?a ?t :> ?c))
        mac2 (<- [:< ?a] (* ?a ?a :> ?a))]
    (with-tmp-sources [num1 [[0] [1] [2] [3]]]
      (test?<- [[-1 1] [0 3] [1 5] [2 7]] [?t ?o] (num1 ?n) (mac1 ?n :> _ ?o) (dec ?n :> ?t))
      (test?<- [[0] [1]] [?n] (num1 ?n) (mac2 ?n))
    )))

(deftest test-avg
  (with-tmp-sources [num1 [[1] [2] [3] [4] [10]]
                     pair [["a" 1] ["a" 2] ["a" 3] ["b" 3]]]
    (test?<- [[4]] [?avg] (num1 ?n) (c/avg ?n :> ?avg))
    (test?<- [["a" 2] ["b" 3]] [?l ?avg] (pair ?l ?n) (c/avg ?n :> ?avg))
    ))

(deftest test-distinct-count
  (with-tmp-sources [num1 [[1] [2] [2] [4] [1] [6] [19] [1]]
                     pair [["a" 1] ["a" 2] ["b" 3] ["a" 1]]]
    (test?<- [[5]] [?c] (num1 ?n) (c/distinct-count ?n :> ?c))
    (test?<- [["a" 2] ["b" 1]] [?l ?c] (pair ?l ?n) (c/distinct-count ?n :> ?c))
    ))

(deftest test-outer-join-with-funcs
  ;; TODO: needed
)

(deftest test-mongo
  ;; function required for join
  ;; 2 inner join, 2 outer join portion
  ;; functions -> joins -> functions -> joins
  ;; functions that run after outer join
  ;; no aggregator
)

(deftest test-funcs)

(deftest test-only-complex-agg)

(deftest test-only-noncomplex-agg)

(deftest test-only-one-buffer)

(deftest test-error-on-lacking-output-fields)