(ns block-chain.transaction-validations
  (:require [schema.core :as s]
            [block-chain.chain :as c]
            [block-chain.transactions :as t]
            [block-chain.queries :as q]
            [block-chain.key-serialization :as ks]
            [block-chain.wallet :as w]
            [block-chain.utils :refer :all]
            [block-chain.schemas :refer :all]))

(defn new-transaction? [txn _ txn-pool]
  (not (contains? txn-pool txn)))

(defn sufficient-inputs? [txn db txn-pool]
  (let [sources (compact (map (partial c/source-output (q/longest-chain db))
                              (:inputs txn)))
        outputs (:outputs txn)]
    (>= (reduce + (map :amount sources))
        (reduce + (map :amount outputs)))))

(defn verify-input-signature [input source txn]
  (if (and input source txn)
    (let [source-key (ks/der-string->pub-key (:address source))]
      (w/verify (:signature input)
                (t/txn-signable txn)
                source-key))
    false))

(defn signatures-valid? [txn db _]
  (let [inputs-sources (c/inputs-to-sources (:inputs txn) (q/longest-chain db))]
    (every? (fn [[input source]]
              (verify-input-signature input source txn))
            inputs-sources)))

(defn txn-structure-valid? [txn _ _]
  (try
    (s/validate Transaction txn)
    (catch Exception e
        false)))

(defn inputs-properly-sourced? [txn db _]
  (let [inputs-sources (c/inputs-to-sources (:inputs txn)
                                            (q/longest-chain db))]
    (and (every? identity (keys inputs-sources))
         (every? identity (vals inputs-sources))
         (= (count (vals inputs-sources))
            (count (into #{} (vals inputs-sources)))))))

(defn inputs-unspent? [txn db _]
  (let [sources (vals (c/inputs-to-sources (:inputs txn)
                                           (q/longest-chain db)))]
    (every? (partial c/unspent? (q/longest-chain db)) sources)))

(defn valid-hash? [txn chain _]
  (= (:hash txn) (t/txn-hash txn)))

(def txn-validations
  {new-transaction? "Transaction rejected because it already exists in this node's pending txn pool."
   txn-structure-valid? "Transaction structure invalid."
   inputs-properly-sourced? "One or more transaction inputs is not properly sourced, OR multiple inputs attempt to source the same output."
   inputs-unspent? "Outputs referenced by one or more txn inputs has already been spent."
   sufficient-inputs? "Transaction lacks sufficient inputs to cover its outputs."
   signatures-valid? "One or more transactions signatures is invalid."
   valid-hash? "Transaction's hash does not match its contents."
   })

(defn validate-transaction [txn db txn-pool]
  (mapcat (fn [[validation message]]
            (if-not (validation txn db txn-pool)
              [message]
              []))
          txn-validations))

(defn valid-transaction? [txn chain txn-pool]
  (empty? (validate-transaction txn chain txn-pool)))
