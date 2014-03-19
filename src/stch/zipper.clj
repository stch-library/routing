(ns stch.zipper
  "Zipper for sequential data. Maintains a
  history of the traversed positions."
  (:require [clojure.core :as core])
  (:use
   [stch.fingertree :only [double-list conjl]]
   [stch.schema])
  (:import stch.fingertree.DoubleList)
  (:refer-clojure :exclude [next empty? remove replace]))

(defrecord' Zipper
  [left :- DoubleList
   right :- DoubleList
   index :- Int
   traversed :- #{Int}])

(defn' ->zip :- Zipper
  "Returns a zipper from the given collection."
  [coll :- [Any]]
  (let [left (double-list)
        right (into (double-list) coll)]
    (Zipper. left right -1 #{})))

(defn' zip :- Zipper
  "Returns a zipper from the given arguments."
  [& args :- [Any]]
  (->zip args))

(defn' prev :- Zipper
  "Move the zipper to the left."
  [z :- Zipper]
  (if (core/empty? (:left z))
    z
    (Zipper. (pop (:left z))
             (conjl (:right z) (peek (:left z)))
             (dec (:index z))
             (:traversed z))))

(defn' next :- Zipper
  "Move the zipper to the right."
  [z :- Zipper]
  (if (core/empty? (:right z))
    z
    (let [index (inc (:index z))]
      (Zipper. (conj (:left z) (first (:right z)))
               (rest (:right z))
               index
               (conj (:traversed z) index)))))

(def left "Alias for prev." prev)
(def right "Alias for next." next)

(defn' start? :- Boolean
  "Are we at the start of the zipper?"
  [z :- Zipper]
  (core/empty? (:left z)))

(def not-start?
  "Are we not at the start of the zipper?"
  (comp not start?))

(defn' end? :- Boolean
  "Are we at the end of the zipper?"
  [z :- Zipper]
  (core/empty? (:right z)))

(def not-end?
  "Are we not at the end of the zipper?"
  (comp not end?))

(defn' empty? :- Boolean
  "Is the zipper empty?"
  [z :- Zipper]
  (and (start? z) (end? z)))

(def not-empty?
  "Is the zipper not empty?"
  (comp not empty?))

(defn' first? :- Boolean
  "Are we at the first position?"
  [z :- Zipper]
  (= (:index z) 0))

(defn' traversed? :- Boolean
  "Has the given position been traversed?"
  [z :- Zipper, position :- Int]
  (contains? (:traversed z) position))

(defn' next-traversed? :- Boolean
  "Has the next position been traversed?"
  [z :- Zipper]
  (traversed? z (-> z :index inc)))

(defn' start :- Zipper
  "Move the zipper to the start."
  [z :- Zipper]
  (loop [z z]
    (if (start? z)
      z
      (recur (left z)))))

(defn' end :- Zipper
  "Move the zipper to the end."
  [z :- Zipper]
  (loop [z z]
    (if (end? z)
      z
      (recur (right z)))))

(def leftmost "Alias for start." start)
(def rightmost "Alias for end." end)

(defn' lefts :- DoubleList
  "Get the left side of the zipper."
  [z :- Zipper]
  (:left z))

(defn' rights :- DoubleList
  "Get the right side of the zipper."
  [z :- Zipper]
  (:right z))

(defn' preview :- Any
  "Preview the next element in the zipper."
  [z :- Zipper]
  (first (:right z)))

(defn' elem :- Any
  "Get the current element in the zipper."
  [z :- Zipper]
  (peek (:left z)))

(defn' remove :- Zipper
  "Remove the current element from the zipper."
  [z :- Zipper]
  (if-not (core/empty? (:left z))
    (-> (update-in z [:left] pop)
        (update-in [:index] dec))
    z))

(defn' replace :- Zipper
  "Replace the current element in the zipper
  with el."
  [z :- Zipper, el :- Any]
  (if-not (core/empty? (:left z))
    (update-in z [:left] #(-> % pop (conj el)))
    z))

(defn' truncate :- Zipper
  "Truncate the right side of the zipper."
  [z :- Zipper]
  (assoc z :right (double-list)))

(defn' update :- Zipper
  "Update the current element in the zipper,
  calling f on it."
  [z :- Zipper, f :- (Fn Any [Any])]
  (if-not (core/empty? (:left z))
    (update-in z [:left] #(let [v (f (peek %))]
                            (-> % pop (conj v))))
    z))

(defn' insert-right :- Zipper
  "Insert el to the right of the current
  element in the zipper."
  [z :- Zipper, el :- Any]
  (let [index (inc (:index z))]
    (-> (update-in z [:left] conj el)
        (assoc :index index)
        (update-in [:traversed] conj index))))























