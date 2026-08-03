# sekisho（関所）— 共通認証の判断層

旅人の身元と通行手形を検める場所。設計の正本は superproject の
**ADR-2608039950**。

## `kotoba-lang/authentication` との違い

| repo | 役割 |
|---|---|
| `authentication` | **factor の substrate** — WebAuthn / OIDC / SAML / OTP / CACAO の結果を統合する |
| `authorization` | 認可の facade — `policy` / `cacao` へ委譲 |
| `identity` | subject / attestation の可搬 EDN |
| **`sekisho`** | **プロダクト層の判断** — 口座・組織・復旧。各サイトが自前で持っていたものを 1 箇所に |

## なぜ要るのか（実測、2026-08-03）

    repo              authn 参照   自前 did/storage 実装
    cloud-murakumo        2              12
    local-murakumo        0               4
    net-babiniku          0               8

**各サイトが did:key と localStorage を自前で扱っていた。** それぞれ少しずつ
違う実装を持つと**壊れ方も少しずつ違う**ので、1 箇所直しても他が残る。

実際に起きていた壊れ方（同日修正済み）:

```js
did = 'did:key:z6Mk' + btoa(...).replace(/[^a-zA-Z0-9]/g,'').slice(0,32);
catch(e) { did = 'did:key:z6Mk' + Date.now().toString(36); }
```

base64 を切り詰めたものと**タイムスタンプ**を口座にし、さらに秘密鍵を捨てて
いた —— 構造として正しい did:key でも、**誰も鍵を持っていなければ口座ではない**。

## 何を持ち、何を持たないか

| ns | 役割 |
|---|---|
| `sekisho.didkey` | did:key（Ed25519）の唯一の構造判定。multicodec `0xed01` と 34 byte を実際に検査 |
| `sekisho.tenant` | 組織 / 人 / credential の 3 層。**既存の accountDid を改名しない** |
| `sekisho.account` | 口座の契約 —— バックアップの形と復旧状態の 3 値 |

**持たないもの**: 鍵生成（WebCrypto）・保管（localStorage）・HTTP・UI。
判断だけを持つのは移植性のためではなく **検査可能性** のため —— 鍵生成を含むと
『壊れた口座を弾けるか』を実際の鍵無しには試せなくなる。

## authority を増やさない

DID の権威は `kotobase.net` 1 つ（ADR-2608039950）。第 2 authority を立てるのは
この設計で唯一取り返しのつかない選択で、ブランド名は後から変えられるが
**発行済み DID の namespace は変えられない**。

```bash
clojure -M:test
```
