(ns merkledag.data.finance.posting-test
  (:require
    [clj-time.core :as time]
    [clojure.test :refer :all]
    [merkledag.data.finance.posting :as posting]
    [merkledag.data.finance.types :as types]))


(deftest posting-weights
  (testing "non-weighted entries"
    (is (nil? (posting/entry-weight {:data/type :finance.entry/balance-check
                                     :finance.balance/amount (types/q 0.0M 'JPY)})))
    (is (nil? (posting/entry-weight {:data/type :finance.entry/posting
                                     :finance.posting/virtual true
                                     :finance.posting/amount (types/q 3.4M 'USD)})))
    (is (nil? (posting/entry-weight {:data/type :finance.entry/posting}))))
  (testing "explicit weight"
    (is (= (types/q 42M 'XYZ)
           (posting/entry-weight {:data/type :finance.entry/posting
                                  :finance.posting/amount (types/q 0.0M 'USD)
                                  :finance.posting/weight (types/q 42M 'XYZ)}))))
  (testing "price conversion"
    (is (= (types/q 300.00M 'USD)
           (posting/entry-weight {:data/type :finance.entry/posting
                                  :finance.posting/amount (types/q 10.0M 'VNQ)
                                  :finance.posting/price (types/q 30.00M 'USD)}))))
  (testing "cost conversion"
    (is (= (types/q 320.00M 'GBP)
           (posting/entry-weight {:data/type :finance.entry/posting
                                  :finance.posting/amount (types/q 32M 'SCHZ)
                                  :finance.posting/cost {:amount (types/q 10.00M 'GBP)}}))))
  (testing "plain amount"
    (is (= (types/q 80.00M 'ABCD)
           (posting/entry-weight {:data/type :finance.entry/posting
                                  :finance.posting/amount (types/q 80.00M 'ABCD)})))))
