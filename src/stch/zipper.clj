(ns stch.zipper
  (:require [clojure.core :as core])
  (:use
   [stch.fingertree :only [double-list conjl]]
   [stch.schema])
  (:import [stch.fingertree DoubleList])
  (:refer-clojure :exclude [next empty? remove replace]))

(defrecord' Zipper
  [left :- DoubleList
   right :- DoubleList
   index :- Int
   traversed :- #{Int}])

(defn' ->zip :- Zipper
  [coll :- [Any]]
  (let [left (double-list)
        right (into (double-list) coll)]
    (Zipper. left right -1 #{})))

(defn' zip :- Zipper
  [& args :- [Any]]
  (->zip args))

(defn' left :- Zipper
  [z :- Zipper]
  (if (core/empty? (:left z))
    z
    (Zipper. (pop (:left z))
             (conjl (:right z) (peek (:left z)))
             (dec (:index z))
             (:traversed z))))

(defn' right :- Zipper
  [z :- Zipper]
  (if (core/empty? (:right z))
    z
    (let [index (inc (:index z))]
      (Zipper. (conj (:left z) (first (:right z)))
               (rest (:right z))
               index
               (conj (:traversed z) index)))))

(def prev left)
(def next right)

(defn' start? :- Boolean
  [z :- Zipper]
  (core/empty? (:left z)))

(def not-start? (comp not start?))

(defn' end? :- Boolean
  [z :- Zipper]
  (core/empty? (:right z)))

(def not-end? (comp not end?))

(defn' empty? :- Boolean
  [z :- Zipper]
  (and (start? z) (end? z)))

(def not-empty? (comp not empty?))

(defn' first? :- Boolean
  [z :- Zipper]
  (= (:index z) 0))

(defn' traversed? :- Boolean
  [z :- Zipper, index :- Int]
  (contains? (:traversed z) index))

(defn' next-traversed? :- Boolean
  [z :- Zipper]
  (traversed? z (-> z :index inc)))

(defn' start :- Zipper
  [z :- Zipper]
  (loop [z z]
    (if (start? z)
      z
      (recur (left z)))))

(defn' end :- Zipper
  [z :- Zipper]
  (loop [z z]
    (if (end? z)
      z
      (recur (right z)))))

(def leftmost start)
(def rightmost end)

(defn' lefts :- DoubleList
  [z :- Zipper]
  (:left z))

(defn' rights :- DoubleList
  [z :- Zipper]
  (:right z))

(defn' preview :- Any
  [z :- Zipper]
  (first (:right z)))

(defn' elem :- Any
  [z :- Zipper]
  (peek (:left z)))

(defn' remove :- (Option Zipper)
  [z :- Zipper]
  (when-not (core/empty? (:left z))
    (-> (update-in z [:left] pop)
        (update-in [:index] dec))))

(defn' replace :- (Option Zipper)
  [z :- Zipper, el :- Any]
  (when-not (core/empty? (:left z))
    (update-in z [:left] #(-> % pop (conj el)))))

(defn' truncate :- Zipper
  [z :- Zipper]
  (assoc z :right (double-list)))

(defn' update :- (Option Zipper)
  [z :- Zipper, f :- (Fn Any [Any])]
  (when-not (core/empty? (:left z))
    (update-in z [:left] #(let [v (f (peek %))]
                            (-> % pop (conj v))))))

(defn' insert-right :- Zipper
  [z :- Zipper, el :- Any]
  (let [index (inc (:index z))]
    (-> (update-in z [:left] conj el)
        (assoc :index index)
        (update-in [:traversed] conj index))))























