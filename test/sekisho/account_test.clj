(ns sekisho.account-test
  "口座の契約。**壊れた口座を弾けること**と、
  『鍵はあるがバックアップしていない』を丸めないことが主眼。"
  (:require [clojure.test :refer [deftest testing is]]
            [sekisho.account :as a]
            [sekisho.didkey :as dk]))

(def canonical "did:key:z6MkhaXgBZDvotDkL5257faiztiGiC2QtKLGpbnnEGta2doK")
(def pub (dk/public-key-of canonical))
(def jwk {"kty" "OKP" "crv" "Ed25519" "d" "c2VjcmV0" "x" "cHVi"})

(deftest backup-derives-the-did-itself
  (testing "did を呼び出し側から受け取らないので、
            バックアップの did と鍵が食い違うことが構造的に起きない"
    (let [b (a/->backup pub jwk)]
      (is (= canonical (:sekisho/did b)))
      (is (= jwk (:sekisho/key b)))
      (is (a/valid-backup? b)))))

(deftest backup-refuses-broken-input
  (testing "壊れた鍵長からバックアップを作らない"
    (is (nil? (a/->backup (butlast pub) jwk)))
    (is (nil? (a/->backup pub nil)))
    (is (nil? (a/->backup pub {"kty" "OKP" "crv" "P-256" "d" "x"}))
        "Ed25519 でない鍵を受け入れない")))

(deftest valid-backup-checks-shape-not-cryptography
  (testing "形だけを見る。did と鍵の整合は見ない —— それには署名か鍵導出が要り、
            この ns は純粋であることを選んでいる。**検査したつもりの範囲を
            呼び出し側に誤解させない**ために docstring と test で明示する"
    (is (false? (a/valid-backup? {:sekisho/version 1 :sekisho/did "did:key:z6MkBuyer"
                                  :sekisho/key jwk}))
        "壊れた did は弾く")
    (is (false? (a/valid-backup? {:sekisho/version 99 :sekisho/did canonical
                                  :sekisho/key jwk}))
        "版が違えば弾く")
    (is (false? (a/valid-backup? {:sekisho/version 1 :sekisho/did canonical
                                  :sekisho/key (dissoc jwk "d")}))
        "秘密鍵が無ければバックアップではない")))

(deftest recovery-state-does-not-round-local-only-into-safe
  (testing "**『鍵はあるがバックアップしていない』が最も危ない状態**。
            真偽値に丸めると、ブラウザのデータを消した瞬間に credits が消える
            ことを誰も警告できない"
    (is (= :none (a/recovery-state {:stored? false})))
    (is (= :local-only (a/recovery-state {:stored? true})))
    (is (= :backed-up (a/recovery-state {:stored? true :exported? true})))
    (is (= :backed-up (a/recovery-state {:stored? true :bound-email? true})))
    (is (= :sekisho.warn/back-up-your-key (a/warning :local-only)))
    (is (nil? (a/warning :backed-up)))))
