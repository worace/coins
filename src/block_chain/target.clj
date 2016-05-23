(ns block-chain.target
  (:require [block-chain.utils :refer :all]
            [environ.core :refer [env]]
            [clojure.math.numeric-tower :as math]))

(def hard "0000000000000000000000010000000000000000000000000000000000000000")
(def default (env :default-target))
(def frequency 300000)

(defn avg-spacing
  "Finds average time spacing in seconds of a series of times"
  [times]
  (if-not (> (count times) 1)
    0
    (->> times
       (partition 2 1)
       (map reverse)
       (map #(apply - %))
       (avg))))

(defn capped [ratio]
  (cond
    (> ratio 1.15) 1.15
    (< ratio 0.85) 0.85
    :else (float ratio)))

(defn target-value [block]
  (hex->int (get-in block [:header :target])))

(defn adjusted-target [blocks frequency]
  "Finds the appropriate next target for the given collection of
   blocks at the desired block-generation-frequency. First finds the
   average spacing among this sequence of blocks, then adjusts the
   most recent target by the ratio between this average and the
   desired frequency.

   Also caps the amount of change at 15%, to avoid wild fluctuations.
   So an adjustment greater than 15% or less than -15% will be capped.

   Note that a higher target is easier and lower target is harder, so
   an average spacing longer than the desired frequency will result in
   increasing the target, and vice versa."
  (let [times (map #(get-in % [:header :timestamp]) blocks)
        latest-target (target-value (last blocks))
        ratio (/ (avg-spacing times) frequency)
        adjustment (capped ratio)]
    (hex-string (bigint (* adjustment latest-target)))))

(defn next-target
  "Calculate the appropriate next target based on the time frequency
   of recent blocks."
  [blocks]
  (if (> (count blocks) 8)
    (adjusted-target blocks frequency)
    default))
