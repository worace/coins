(ns block-chain.block-validations-test)
(ns block-chain.block-validations-test
  (:require [clojure.test :refer :all]
            [block-chain.wallet :as wallet]
            [clojure.pprint :refer [pprint]]
            [block-chain.utils :refer :all]
            [block-chain.target :as target]
            [clojure.math.numeric-tower :as math]
            [block-chain.miner :as miner]
            [block-chain.blocks :as blocks]
            [block-chain.chain :as c]
            [block-chain.block-validations :refer :all]))

(def easy-difficulty (hex-string (math/expt 2 248)))
(def med-difficulty (hex-string (math/expt 2 244)))
(def chain (atom []))

(def key-a (wallet/generate-keypair 512))
(def key-b (wallet/generate-keypair 512))

(def a-coinbase (miner/coinbase (:address key-a)))
(def b-coinbase (miner/coinbase (:address key-b)))

(def a-paid (blocks/generate-block [a-coinbase]
                                  {:target easy-difficulty}))

;; A: 25
(miner/mine-and-commit chain a-paid)

;; A pays B 5
(def a-pays-b-5 (miner/generate-payment key-a
                                        (:address key-b)
                                        5
                                        @chain
                                        2))

;; Block contains 5 A -> B
;; And 25 coinbase -> A
(def un-mined-block (blocks/hashed
                     (blocks/generate-block
                      [(miner/coinbase (:address key-a) [a-pays-b-5] @chain)
                       a-pays-b-5]
                      {:target easy-difficulty
                       :blocks @chain})))


(deftest test-valid-block-hash
  (is (valid-hash? (first @chain) []))
  (is (not (valid-hash? (assoc-in a-paid [:header :hash] "pizza") []))))

(deftest test-proper-parent-hash
  (is (valid-parent-hash? (first @chain) []))
  (is (not (valid-parent-hash? (first @chain) @chain)))
  (is (valid-parent-hash? un-mined-block @chain)))

(deftest test-hash-meets-target
  (is (hash-meets-target? (first @chain) []))
  (is (not (hash-meets-target? un-mined-block []))))

(defn hex-* [i hex]
  (-> hex
      hex->int
      (* i)
      hex-string))

(deftest test-block-target-within-threshold
  (with-redefs [target/default easy-difficulty]
    (is (valid-target? (first @chain) @chain))
    (is (valid-target? un-mined-block @chain))
    (is (valid-target? (update-in un-mined-block
                                  [:header :target]
                                  (partial hex-* 1001/1000))
                       @chain))
    (is (not (valid-target? (update-in un-mined-block
                                       [:header :target]
                                       (partial hex-* 5))
                            @chain)))))

(deftest test-valid-txn-hash
  (is (valid-txn-hash? un-mined-block []))
  (is (not (valid-txn-hash? (assoc-in un-mined-block
                                      [:transactions 0 :hash]
                                      "pizza")
                            []))))

(deftest test-valid-coinbase
  (is (valid-coinbase? un-mined-block @chain))
  (is (not (valid-coinbase? (update-in un-mined-block
                                       [:transactions 0 :outputs 0 :amount]
                                       inc)
                            @chain)))
  (with-redefs [c/coinbase-reward 15]
    (is (not (valid-coinbase? un-mined-block @chain)))))

(deftest test-valid-timestamp
  (let [b (blocks/hashed
           (blocks/generate-block
            [(miner/coinbase (:address key-a) [a-pays-b-5] @chain)
             a-pays-b-5]
            {:target easy-difficulty
             :blocks @chain}))]
    (is (valid-timestamp? b []))
    (is (not (valid-timestamp? (assoc-in b
                                         [:header :timestamp]
                                         (+ (current-time-millis) 7000))
                               [])))
    (is (not (valid-timestamp? (assoc-in b
                                         [:header :timestamp]
                                         (- (current-time-millis)
                                            70000))
                               [])))))

(deftest test-all-txns-valid
  (is (valid-transactions? un-mined-block @chain))
  (is (not (valid-transactions? (update-in un-mined-block
                                           [:transactions 1 :outputs 0 :amount]
                                           inc)
                                @chain))))

(deftest test-no-transactions-spend-same-inputs
  (is (unique-txn-inputs? un-mined-block @chain))
  (let [duped-txn (get-in un-mined-block [:transactions 1])]
    (is (not (unique-txn-inputs? (update-in un-mined-block
                                            [:transactions]
                                            #(conj % duped-txn))
                                 @chain)))))

(deftest test-validate-whole-block
  (with-redefs [target/default easy-difficulty]
    (is (empty? (validate-block (miner/mine un-mined-block) @chain)))))
