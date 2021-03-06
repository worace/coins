(ns block-chain.queries-test
  (:require [clojure.test :refer :all]
            [block-chain.db :as db]
            [block-chain.test-helper :as th]
            [clj-leveldb :as ldb]
            [block-chain.utils :refer :all]
            [block-chain.queries :refer :all]))

(defn fake-next-block [{{hash :hash} :header :as block}]
  (-> block
      (assoc-in [:header :parent-hash] hash)
      (update-in [:header :hash] sha256)))

(defn fake-chain
  ([] (fake-chain db/genesis-block))
  ([{{hash :hash} :header :as block}]
   (lazy-seq (cons block
                   (-> block
                       fake-next-block
                       (fake-chain))))))

(def sample-chain (take 5 (fake-chain)))
(def empty-db (atom nil))
(def sample-db (atom nil))

(defn setup [tests]
  (with-open [empty-conn (th/temp-db-conn)
              sample-conn (th/temp-db-conn)]
    (reset! empty-db (db/db-map empty-conn))
    (reset! sample-db (db/db-map sample-conn))
    (doseq [b sample-chain] (add-block! sample-db b))
    (tests)))

(use-fixtures :each setup)

(defn sample-txn [db] (-> (highest-block db) :transactions first) )
(defn utxo [db] (-> (sample-txn db) :outputs first))

(deftest test-highest-block
  (is (= (last sample-chain)
         (highest-block @sample-db))))

(deftest test-highest-hash (is (= (bhash (last sample-chain))
                                  (highest-hash @sample-db))))

(deftest test-longest-chain
  (is (= (list) (longest-chain @empty-db)))
  (is (= (highest-hash @sample-db)
         (bhash (first (longest-chain @sample-db)))))
  (is (= (reverse (map bhash sample-chain))
         (map bhash (longest-chain @sample-db)))))

(deftest test-adding-block
  (let [updated (add-block @empty-db db/genesis-block)]
    (is (= db/genesis-block (get-block updated (bhash db/genesis-block))))
    (is (= 1 (chain-length updated (bhash db/genesis-block))))
    (is (= 1 (count (all-txns updated))))
    (is (= #{(bhash db/genesis-block)}
           (children updated (phash db/genesis-block))))))

(deftest test-adding-child-twice-doesnt-duplicate-in-child-listing
  (let [parent (first sample-chain)
        child (second sample-chain)]
    (is (= #{(bhash child)} (children @sample-db (bhash parent))))
    (add-block! sample-db child)
    (is (= #{(bhash child)} (children @sample-db (bhash parent))))))

(deftest test-adding-block-clears-its-txns-from-pool
  (let [next (fake-next-block (highest-block @sample-db))]
    (add-transaction-to-pool! sample-db
                              (first (:transactions next)))
    (is (= 1 (count (transaction-pool @sample-db))))
    (add-block! sample-db next)
    (is (empty? (transaction-pool @sample-db)))))

(deftest test-adding-block-clears-txns-with-overlapping-inputs-from
  (let [txn1 {:inputs [{:source-hash "pizza"
                        :source-index "0"
                        :signature "sig1234"}]
              :outputs [{:address "1234" :amount 10}]}
        txn2 {:inputs [{:source-hash "pizza"
                        :source-index "0"
                        :signature "differentsig"}]
              :outputs [{:address "diffaddr" :amount 10}]}
        next (update (fake-next-block (highest-block @sample-db))
                     :transactions
                     conj
                     txn1)]
    (add-transaction-to-pool! sample-db txn2)
    (is (= 1 (count (transaction-pool @sample-db))))
    (add-block! sample-db next)
    (is (empty? (transaction-pool @sample-db)))))

(deftest test-blocks-since
  (is (= 4 (count (blocks-since @sample-db (bhash db/genesis-block)))))
  (is (= (map bhash (drop 1 (reverse (longest-chain @sample-db))))
         (map bhash (blocks-since @sample-db (bhash db/genesis-block))))))

(deftest test-fetching-txn
  (let [t (-> @sample-db longest-chain last :transactions first)]
    (is (= t (get-txn @sample-db (:hash t))))))

(deftest test-source-output
  (let [t (-> @sample-db longest-chain last :transactions first)
        i {:source-hash (:hash t) :source-index 0 :signature "pizza"}]
    (is (= (-> t :outputs first)
           (source-output @sample-db i)))))

;; TODO this is important enough to warrant more testing
(deftest test-utxos
  (is (= (->> @sample-db
              all-txns
              (mapcat :outputs)
              (into #{}))
         (utxos @sample-db))))

;; ;; input: {source-hash "FFF.." source-index 0}
;; ;; output {address "pizza" amount "50"}
;; ;; UTXO:
;; ;; Map of...? Coords? Vector of txn hash and index?
;; ;; OR
;; ;; Set of Coords
;; ;; #{["txn-hash 1" 0] ["txn-hash 2" 0] }

(deftest test-output-assigned-to-key
  (let [utxo (utxo @sample-db)]
    (is (assigned-to-key? (:address utxo) utxo))
    (is (not (assigned-to-key? "pizza" utxo)))))
