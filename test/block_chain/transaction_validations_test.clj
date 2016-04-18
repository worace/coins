(ns block-chain.transaction-validations-test
  (:require [clojure.test :refer :all]
            [block-chain.wallet :as wallet]
            [clojure.pprint :refer [pprint]]
            [block-chain.utils :refer :all]
            [clojure.math.numeric-tower :as math]
            [block-chain.miner :as miner]
            [block-chain.blocks :as blocks]
            [block-chain.transaction-validations :refer :all]))

(def easy-difficulty (hex-string (math/expt 2 248)))
(def chain (atom []))

(def key-a (wallet/generate-keypair 512))
(def key-b (wallet/generate-keypair 512))

(def a-coinbase (miner/coinbase (:address key-a)))
(def b-coinbase (miner/coinbase (:address key-b)))

(def a-paid (blocks/generate-block [a-coinbase]
                                  {:target easy-difficulty}))
(def b-paid (blocks/generate-block [b-coinbase]
                                  {:target easy-difficulty}))

;; A and B both start with 25
;; A: 25 B: 25
(miner/mine-and-commit chain a-paid)
(miner/mine-and-commit chain b-paid)


;; B pays A 5
;; A: 30, B: 20
(def b-pays-a-5 (miner/generate-payment key-b
                                        (:address key-a)
                                        5
                                        @chain))
(miner/mine-and-commit chain (blocks/generate-block [b-pays-a-5]
                                                    {:target easy-difficulty}))


;; Pending payment transactions:
(def a-pays-b-15 (miner/generate-payment key-a
                                         (:address key-b)
                                         15
                                         @chain))
(def a-pays-b-50 (assoc-in a-pays-b-15 [:outputs 0 :amount] 50))


(deftest test-valid-structure
  (is (txn-structure-valid? a-coinbase [] #{}))
  (is (txn-structure-valid? a-pays-b-15 [] #{}))
  (is (not (txn-structure-valid? {} [] #{}))))

(deftest test-sufficient-balance
  (is (sufficient-inputs? a-pays-b-15 @chain #{}))
  (is (not (sufficient-inputs? a-pays-b-50 @chain #{}))))

(deftest test-all-inputs-have-sources
  (is (inputs-properly-sourced? a-pays-b-15 @chain #{}))
  (let [bogus-sourced (assoc-in a-pays-b-15
                                [:inputs 0 :source-hash]
                                "pizza")]
    (is (not (inputs-properly-sourced? bogus-sourced @chain #{})))))

(deftest test-valid-signatures
  (is (signatures-valid? a-pays-b-15 @chain #{}))
  (let [bogus-sig (assoc-in a-pays-b-15
                            [:outputs 0 :amount]
                            16)]
    (is (not (signatures-valid? bogus-sig @chain #{})))))

(deftest test-inputs-unspent
  (is (inputs-unspent? a-pays-b-15 @chain #{}))
  (is (contains? (->> @chain
                      (mapcat :transactions)
                      (into #{})) b-pays-a-5))
  (is (not (inputs-unspent? b-pays-a-5 @chain #{}))))

(deftest test-correct-hash
  (is (valid-hash? a-pays-b-15 @chain #{}))
  (is (not (valid-hash? (assoc a-pays-b-15 :hash "pizza") @chain #{}))))

(deftest test-valid-outputs)
