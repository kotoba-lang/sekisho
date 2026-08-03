(ns sekisho.account
  "ブラウザ側の口座 —— 鍵の生成・保管・バックアップの **契約**。

  ## なぜ library にするのか

  2026-08-03 の実測（ADR-2608039950）:

      repo              authn 参照   自前 did/storage 実装
      cloud-murakumo        2              12
      local-murakumo        0               4
      net-babiniku          0               8

  **各サイトが did:key と localStorage を自前で扱っている。** 同日 murakumo に
  `/signup` を手書きしたのが 3 件目になりかけた。それぞれが少しずつ違う実装を
  持つと、**壊れ方も少しずつ違う**ので、1 箇所直しても他が残る。

  実際に起きていた壊れ方（local-murakumo、同日修正）:

    did='did:key:z6Mk'+btoa(...).replace(/[^a-zA-Z0-9]/g,'').slice(0,32);
    catch(e){ did='did:key:z6Mk'+Date.now().toString(36); }

  base64 を切り詰めたものと**タイムスタンプ**を口座にしていた。さらに
  `exportKey('raw', publicKey)` だけを取り、**秘密鍵を捨てていた** ——
  構造として正しい did:key でも、誰も鍵を持っていなければ口座ではない。

  ## 純粋な部分と副作用を最初から分ける

  この ns は **判断（純粋）だけ**を持つ。WebCrypto も localStorage も触らない。
  呼び出し側が bytes を渡し、この ns が did とバックアップの形を決める。

  そうする理由は移植性ではなく **検査可能性** —— 鍵生成を含むと、
  『壊れた口座を弾けるか』を実際の鍵無しには試せなくなる。"
  (:require [sekisho.didkey :as didkey]))

(def storage-key
  "localStorage の鍵名。**サイトを跨いで同じ名前**にする ——
   同じ人が murakumo と itonami で別々の口座を持つのは、たいてい事故。"
  "sekisho:account")

(def backup-version 1)

(defn ->backup
  "公開鍵 32 byte + 秘密鍵 JWK → バックアップ用の可搬レコード、または nil。

   did を **その場で組み立てる**（呼び出し側から受け取らない）ので、
   バックアップの did と鍵が食い違うことが構造的に起きない。"
  [pubkey jwk]
  (when-let [did (didkey/from-public-key pubkey)]
    (when (and jwk (= "Ed25519" (or (get jwk "crv") (get jwk :crv))))
      {:sekisho/version backup-version
       :sekisho/did did
       :sekisho/key jwk
       :sekisho/note
       (str "This file IS the account. Anyone holding it controls the credits. "
            "Losing it loses them — the operator holds no copy and cannot restore it.")})))

(defn valid-backup?
  "復元してよいバックアップか。

   **did と鍵の整合までは見ない**（それには署名か鍵導出が要り、この ns は
   純粋であることを選んでいる）。見るのは形だけで、それを docstring に書く
   のは、**検査したつもりの範囲を呼び出し側に誤解させない**ため。"
  [b]
  (boolean
   (and (map? b)
        (= backup-version (:sekisho/version b))
        (didkey/valid? (:sekisho/did b))
        (let [k (:sekisho/key b)]
          (and k (= "Ed25519" (or (get k "crv") (get k :crv)))
               (seq (or (get k "d") (get k :d))))))))

(defn did-of
  "バックアップ → did、不正なら nil。"
  [b]
  (when (valid-backup? b) (:sekisho/did b)))

(defn recovery-state
  "この口座は復旧できるか、を **3 値**で返す。

   → `:backed-up` / `:local-only` / `:none`

   真偽値にしないのは、**『鍵はあるがバックアップしていない』が最も危ない状態**
   だから。ここを『ある』に丸めると、ブラウザのデータを消した瞬間に credits が
   消えることを誰も警告できない。"
  [{:keys [stored? exported? bound-email?]}]
  (cond
    (not stored?) :none
    (or exported? bound-email?) :backed-up
    :else :local-only))

(defn warning
  "`recovery-state` に対応する、顧客に見せる文言のキー。

   文言そのものを持たないのは i18n の都合ではなく、**この ns が UI を持たない**
   から。状態の名前だけを決め、見せ方は各サイトに委ねる。"
  [state]
  (case state
    :none        :sekisho.warn/no-account
    :local-only  :sekisho.warn/back-up-your-key
    :backed-up   nil))
