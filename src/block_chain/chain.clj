(ns block-chain.chain
  (:require [clojure.java.io :as io]
            [clojure.math.numeric-tower :as math]
            [block-chain.utils :refer :all]
            [block-chain.target :as target]))

(def coinbase-reward 25)

(defn bhash [block] (get-in block [:header :hash]))

(defn block-by-hash
  [hash blocks]
  (first
   (filter #(= hash (get-in % [:header :hash]))
           blocks)))

(defn index-of [predicate seq]
  (loop [idx 0 items seq]
    (cond
      (empty? items) nil
      (predicate (first items)) idx
      :else (recur (inc idx) (rest items)))))

(defn block-index [hash blocks]
  (index-of (fn [b] (= hash (bhash b))) blocks))

(defn transactions [blocks] (mapcat :transactions blocks))
(defn inputs [blocks] (mapcat :inputs (transactions blocks)))
(defn outputs [blocks] (mapcat :outputs (transactions blocks)))

(defn txn-by-hash
  [hash blocks]
  (first (filter #(= hash (get % :hash))
                 (transactions blocks))))

(defn source-output [blocks input]
  (if-let [t (txn-by-hash (:source-hash input)
                          blocks)]
    (get (:outputs t) (:source-index input))))

(defn inputs-to-sources [inputs chain]
  (into {}
        (map (fn [i] [i (source-output chain i)])
             inputs)))

(defn latest-block-hash
  "Look up the hash of the latest block in the provided chain.
   Useful for getting parent hash for new blocks."
  [chain]
  (if-let [parent (last chain)]
    (get-in parent [:header :hash])
    (hex-string 0)))

(defn next-target
  "Calculate the appropriate next target based on the time frequency
   of recent blocks."
  [blocks]
  (let [recent-blocks (take-last 10 blocks)]
    (if (> (count recent-blocks) 1)
      (target/adjusted-target recent-blocks target/frequency)
      target/default)))

(defn consumes-output?
  [source-hash source-index input]
  (and (= source-hash (:source-hash input))
       (= source-index (:source-index input))))

(defn unspent?
  "takes a txn hash and output index identifying a
   Transaction Output in the block chain. Searches the
   chain to find if this output has been spent."
  [blocks output]
  (let [inputs (inputs blocks)
        {:keys [transaction-id index]} (:coords output)
        spends-output? (partial consumes-output? transaction-id index)]
    (not-any? spends-output? inputs)))

(defn assigned-to-key? [key output]
  (= key (:address output)))

(defn unspent-outputs [key blocks]
  (->> (outputs blocks)
       (filter (partial assigned-to-key? key))
       (filter (partial unspent? blocks))))

(defn balance [address blocks]
  (reduce +
          (map :amount
               (unspent-outputs address blocks))))

(defn unspent-output-coords [key blocks]
  (mapcat (fn [txn]
            (mapcat (fn [output index]
                      (if (assigned-to-key? key output)
                        [{:source-hash (:hash txn) :source-index index}]
                        nil))
                    (:outputs txn)
                    (range (count (:outputs txn)))))
          (transactions blocks)))

(defn txn-fees
  "Finds available txn-fees from a pool of txns by finding the diff
   between cumulative inputs and cumulative outputs"
  [txns chain]
  (let [sources (map (partial source-output chain)
                     (mapcat :inputs txns))
        outputs (mapcat :outputs txns)]
    (- (reduce + (map :amount sources))
       (reduce + (map :amount outputs)))))
