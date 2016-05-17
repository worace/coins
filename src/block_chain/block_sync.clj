(ns block-chain.block-sync
  (:require [block-chain.peer-client :as pc]
            [block-chain.db :as db]
            [block-chain.queries :as q]
            [block-chain.log :as log]
            [block-chain.block-validations :as bv]
            [block-chain.utils :refer :all]))

(defn block-hashes [chain] (map q/bhash chain))

(defn synced-chain [db peer]
  (loop [this-batch (pc/blocks-since peer (q/highest-hash db))
         db db]
    (if (empty? this-batch)
      (let [next-batch (pc/blocks-since peer (q/highest-hash db))]
        (if (empty? next-batch)
          db
          (recur next-batch db)))
      (let [next-block (pc/block peer (first this-batch))]
        (do
          (let [val-errs (bv/validate-block db next-block)]
            (if (empty? val-errs)
              (do
                (log/info "Pulled valid block: " (q/bhash next-block))
                (recur (rest this-batch) (q/add-block db next-block)))
              (log/info "Tried to pull block " (q/bhash next-block) "but got errors: " val-errs))))))))

(defn sync-if-needed! [db-ref peer]
  (try
    (let [h (pc/block-height peer)]
      (if (> h (q/chain-length @db-ref))
        (do (log/info "Found longer block chain in peer" peer "-" h)
            (swap! db-ref synced-chain peer))))
    (catch Exception e (println "Error syncing with peer: " peer ": " (.getMessage e)))))
