(ns stch.zipper-spec
  (:use [speclj.core]
        [stch.fingertree :only [double-list]]
        [stch.schema :only [with-fn-validation]])
  (:require [stch.zipper :as z :refer [->Zipper]]))

(def z (z/zip 1 2 3 4 5))

(context "stch.zipper"
  (it "prev"
    (should= (->Zipper (double-list)
                       (double-list 1 2 3 4 5)
                       -1
                       #{0})
             (-> z z/next z/prev)))
  (it "next"
    (should= (->Zipper (double-list 1)
                       (double-list 2 3 4 5)
                       0
                       #{0})
             (z/next z)))
  (it "preview"
    (should= 1 (z/preview z)))
  (it "elem"
    (should= 1 (-> z z/next z/elem))))
