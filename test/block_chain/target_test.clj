(ns block-chain.target-test
  (:require [clojure.test :refer :all]
            [block-chain.utils :refer :all]
            [clojure.math.numeric-tower :as math]
            [block-chain.target :refer :all]))

(defn in-delta?
  ([x y] (in-delta? x y 0.001))
  ([x y delta] (< (math/abs (- x y)) delta)))

(deftest test-average-spacing
  (is (= 10 (avg-spacing [10 20 30 40 50]))))

(deftest test-average-spacing-of-one
  (is (= 0 (avg-spacing [10]))))

(deftest getting-average-spacing-of-list
  (is (= 4 (avg-spacing [0 4 8 12])))
  (is (= 3 (avg-spacing [0 4 7 9]))))

(deftest test-capped-ratio
  (is (in-delta? 1.13 (capped 1.13)))
  (is (in-delta? 0.95 (capped 0.95)))
  (is (in-delta? 1.15 (capped 1.26)))
  (is (in-delta? 0.85 (capped 0.16)))
  (is (in-delta? 0.85 (capped 0.36))))

(deftest test-basic-adjusting-target
  (let [blocks [{:header {:timestamp 0 :target (hex-string 100)}}
                {:header {:timestamp 60 :target (hex-string 100)}}]]
    (is (= (hex-string 100 ) (adjusted-target blocks 60))))
  (let [blocks [{:header {:timestamp 0 :target (hex-string 100)}}
                {:header {:timestamp 55 :target (hex-string 100)}}
                {:header {:timestamp 108 :target (hex-string 100)}}]]
    (is (= (hex-string 89) (adjusted-target blocks 60)))))

(deftest test-max-target-adjustment
  (let [blocks [{:header {:timestamp 0 :target (hex-string 100)}}
                {:header {:timestamp 120 :target (hex-string 100)}}
                {:header {:timestamp 240 :target (hex-string 100)}}]]
    ;; Blocks are spacing 120 s against desired freq of 60
    ;; so increases by the max adjustment of 15%
    (is (= (hex-string 114) (adjusted-target blocks 60)))))

(deftest test-min-target-adjustment
  (let [blocks [{:header {:timestamp 0 :target (hex-string 100)}}
                {:header {:timestamp 30 :target (hex-string 100)}}
                {:header {:timestamp 60 :target (hex-string 100)}}]]
    ;; blocks are spacing 30 s against desired freq of 60,
    ;; so we should reduce the target by max adjustment of 15%
    (is (= (hex-string 85) (adjusted-target blocks 60)))))


(def sample-blocks
  [{:header {:parent-hash "0000000000000000000000000000000000000000000000000000000000000000", :transactions-hash "aa44f0888e61b26c6595a36bfac5727998f5b9b6739c4ba2d59061f49e518b7b", :target "0000100000000000000000000000000000000000000000000000000000000000", :timestamp 1450476533, :nonce 554896, :hash "00000beb04e48b11ccd9767ed511745ee7ab1319dd53d89aca155571701ea2f0"}, :transactions [{:inputs [], :outputs [:amount 25 :address "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuFl76216Veu5/H2MM4lO\nNFOuZLGcwxeUQzdmW2g+da5mmjyV3RiuYueDJFlAgx2iDASQM+rK1qKp7lj352DU\n3gABqJ5Tk1mRvGHTGz+aP4sj8CKUnjJIQVmmleiRZ47wRDsnrg9N0XyfW+aiPKxl\njvr1pkKJmryO+u2d69Tc69bNsqpGzFLTdO3w1k/jxa0pUAQNqf11MJSrzF7u/Z+8\nmaqFZlzZ5o1LgqTLMpeFg0pcMIKuZb9yQ1IKqOjLsvTvYYyBbNU31FD8qVY/R64z\nbrIYbfWXNiUrYOXyIq7rqegLf3fx+aJGgwUOGYr2MJjY+ZR5Z+cIKJiAgNnpkBWR\nhwIDAQAB\n-----END PUBLIC KEY-----\n"], :timestamp 1450476533661, :hash "0ae43f5cc8327ba89b8adea66a8e2b626cd36ec3e7a45a8da4c93c0cfa666c3a"}]} {:header {:parent-hash "00000beb04e48b11ccd9767ed511745ee7ab1319dd53d89aca155571701ea2f0", :transactions-hash "0c05d89324d4a0de6d0275eab5788a116b157805b0bc44b276260fa31143c563", :target "0000100000000000000000000000000000000000000000000000000000000000", :timestamp 1450477116, :nonce 68247, :hash "000007867d4be6397348390181c2c22a9e2313ba81b5904a09127487ce3e1593"}, :transactions [{:inputs [], :outputs [:amount 25 :address "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuFl76216Veu5/H2MM4lO\nNFOuZLGcwxeUQzdmW2g+da5mmjyV3RiuYueDJFlAgx2iDASQM+rK1qKp7lj352DU\n3gABqJ5Tk1mRvGHTGz+aP4sj8CKUnjJIQVmmleiRZ47wRDsnrg9N0XyfW+aiPKxl\njvr1pkKJmryO+u2d69Tc69bNsqpGzFLTdO3w1k/jxa0pUAQNqf11MJSrzF7u/Z+8\nmaqFZlzZ5o1LgqTLMpeFg0pcMIKuZb9yQ1IKqOjLsvTvYYyBbNU31FD8qVY/R64z\nbrIYbfWXNiUrYOXyIq7rqegLf3fx+aJGgwUOGYr2MJjY+ZR5Z+cIKJiAgNnpkBWR\nhwIDAQAB\n-----END PUBLIC KEY-----\n"], :timestamp 1450477116603, :hash "566c7d768a2384ce3dcee3ab462718db38060c1f1473e83ff1f2801922bfc8bf"}]}])

(deftest test-real-block-adj
  (is (= "000012666666666665d77b75b24c1213528ed186cd962833de00000000000000"
         (adjusted-target sample-blocks 15))))